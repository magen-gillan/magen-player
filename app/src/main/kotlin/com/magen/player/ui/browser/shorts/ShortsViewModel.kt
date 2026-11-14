package com.magen.player.ui.browser.shorts

import android.app.Application
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.magen.player.database.dao.ShortsMediaDao
import com.magen.player.database.entities.ShortsMediaEntity
import com.magen.player.database.repository.VideoMetadataCacheRepository
import com.magen.player.domain.media.model.Video
import com.magen.player.domain.thumbnail.ThumbnailRepository
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.preferences.ShortsSortType
import com.magen.player.R
import com.magen.player.utils.media.CopyPasteOps
import com.magen.player.utils.media.ShortsDiscoveryOps
import com.magen.player.utils.permission.PermissionUtils
import com.magen.player.utils.sort.SortUtils
import com.magen.player.utils.storage.MediaScanPolicy
import com.magen.player.utils.storage.VideoScanUtils
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ShortsViewModel(
    application: Application
) : AndroidViewModel(application), KoinComponent {

    private val shortsMediaDao: ShortsMediaDao by inject()
    private val browserPreferences: BrowserPreferences by inject()
    private val metadataCache: VideoMetadataCacheRepository by inject()
    private val thumbnailRepository: ThumbnailRepository by inject()

    private val _shorts = MutableStateFlow<List<Video>>(emptyList())
    val shorts: StateFlow<List<Video>> = _shorts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _totalShortsCount = MutableStateFlow(0)
    val totalShortsCount: StateFlow<Int> = _totalShortsCount.asStateFlow()

    private val _sortType = MutableStateFlow(browserPreferences.shortsSortType.get())
    val sortType: StateFlow<ShortsSortType> = _sortType.asStateFlow()

    private val _sortDescending = MutableStateFlow(browserPreferences.shortsSortDescending.get())
    val sortDescending: StateFlow<Boolean> = _sortDescending.asStateFlow()

    val quickDeleteEnabled: StateFlow<Boolean> = browserPreferences.quickDeleteShorts.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), browserPreferences.quickDeleteShorts.get())

    val lovedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isLoved }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val autoSwipe: StateFlow<Boolean> = browserPreferences.autoSwipeShorts.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), browserPreferences.autoSwipeShorts.get())

    private val _currentSpeed = MutableStateFlow(1.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()

    private var currentLoadedMode: Boolean? = null
    private var loadJob: Job? = null

    init {
        // Keep the feed in sync when the sort preference changes from anywhere
        // (e.g. a settings screen): the visible list re-sorts immediately.
        viewModelScope.launch {
            browserPreferences.shortsSortType.changes().collect { type ->
                if (type != _sortType.value) {
                    _sortType.value = type
                    if (_shorts.value.isNotEmpty()) {
                        _shorts.value = orderVideos(_shorts.value)
                    }
                }
            }
        }
        viewModelScope.launch {
            browserPreferences.shortsSortDescending.changes().collect { descending ->
                if (descending != _sortDescending.value) {
                    _sortDescending.value = descending
                    if (_shorts.value.isNotEmpty()) {
                        _shorts.value = orderVideos(_shorts.value)
                    }
                }
            }
        }
    }

    fun loadShorts(
        initialVideoPath: String? = null,
        favoritesOnly: Boolean = false,
        forceRefresh: Boolean = false,
    ) {
        val requestedMode = favoritesOnly
        if (!forceRefresh && _shorts.value.isNotEmpty() && currentLoadedMode == requestedMode && initialVideoPath == null) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            currentLoadedMode = requestedMode
            _shorts.value = emptyList()
            _totalShortsCount.value = 0
            pendingInitialVideo = null

            if (forceRefresh) {
                browserPreferences.lastWatchedShortPath.set("")
            }

            try {
                val received = mutableListOf<Video>()

                if (favoritesOnly) {
                    val ordered = orderVideos(loadFavoriteShorts())
                    _totalShortsCount.value = ordered.size
                    _shorts.value = ordered
                    return@launch
                }

                ShortsDiscoveryOps.discoverShortsProgressively(
                    getApplication(),
                    shortsMediaDao,
                    metadataCache,
                    browserPreferences,
                    sortOrder = currentComparator(),
                ).collect { video ->
                    // The first item must be able to render before discovery finishes,
                    // otherwise the spinner from #450 comes straight back.
                    if (received.isEmpty()) {
                        _isLoading.value = false
                    }

                    // Honor an explicit start video: hold it back until the rest of the
                    // feed is known so it can be guaranteed to occupy page 0.
                    if (initialVideoPath != null && video.path == initialVideoPath) {
                        pendingInitialVideo = video
                        return@collect
                    }
                    // Discovery already emits in the selected sort order (ordered
                    // prefix of the final feed), so appending keeps the list sorted
                    // without reshuffling pages the user is currently browsing.
                    received.add(video)
                    _shorts.value = withInitialFirst(received)
                    _totalShortsCount.value = received.size
                }

                // Keep an explicitly requested start video first; otherwise the feed
                // is already sorted (with a final pass for tail consistency).
                val initial = pendingInitialVideo
                pendingInitialVideo = null
                val ordered = if (initial != null) {
                    listOf(initial) + received.filter { it.path != initial.path }
                } else {
                    orderVideos(received)
                }
                _shorts.value = ordered
                _totalShortsCount.value = ordered.size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Loading must never take the whole app down (e.g. favorites or a
                // scan hitting an unexpected state): surface an empty feed instead.
                Log.e(TAG, "Failed to load shorts feed", e)
                _shorts.value = emptyList()
                _totalShortsCount.value = 0
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.shorts_feed_load_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private var pendingInitialVideo: Video? = null

    /**
     * Keeps the requested start video in front while more discoveries land, so the
     * pager does not jump once discovery finishes.
     */
    private fun withInitialFirst(received: List<Video>): List<Video> {
        val pending = pendingInitialVideo ?: return received
        return listOf(pending) + received
    }

    /** Apply a sort (type + direction) immediately and return the new page of the video currently being watched. */
    fun sortShorts(type: ShortsSortType, descending: Boolean, currentPath: String?): Int {
        _sortType.value = type
        _sortDescending.value = descending
        browserPreferences.shortsSortType.set(type)
        browserPreferences.shortsSortDescending.set(descending)
        val sorted = orderVideos(_shorts.value)
        _shorts.value = sorted
        return currentPath?.let { path -> sorted.indexOfFirst { it.path == path }.takeIf { it >= 0 } } ?: 0
    }

    /** Comparator matching the currently selected shorts sort and direction. */
    private fun currentComparator(): Comparator<Video> {
        val base = when (_sortType.value) {
            ShortsSortType.Name -> Comparator { first, second ->
                SortUtils.NaturalOrderComparator.DEFAULT.compare(first.displayName, second.displayName)
            }
            ShortsSortType.Size -> compareBy<Video> { it.size }
            ShortsSortType.DateAdded -> compareBy<Video> { it.dateAdded }
        }
        return if (_sortDescending.value) base.reversed() else base
    }

    private fun orderVideos(videos: List<Video>): List<Video> =
        videos.sortedWith(currentComparator())

    private suspend fun loadFavoriteShorts(): List<Video> = runCatching {
        loadFavoriteShortsInternal()
    }.onFailure { error ->
        Log.e(TAG, "Failed to load favorite shorts", error)
    }.getOrDefault(emptyList())

    private suspend fun loadFavoriteShortsInternal(): List<Video> {
        // Anything physically inside Movies/المفضلة is a favorite — even when the
        // database has no row for it (e.g. after a reinstall with a new signature,
        // or a video moved into the folder manually with a file manager).
        val favoriteDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "المفضلة",
        )
        val folderFavorites = if (favoriteDirectory.isDirectory) {
            runCatching {
                VideoScanUtils.getVideosInFolder(
                    getApplication(),
                    favoriteDirectory.absolutePath,
                    MediaScanPolicy(includeNoMediaContent = true),
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to scan favorites folder", error)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val favorites = LinkedHashMap<String, Video>()
        folderFavorites.forEach { favorites[it.path] = it }

        // Loved rows pointing outside the favorites folder still resolve through
        // their own folders, exactly as before.
        val favoriteRecords = shortsMediaDao.getAllShortsMedia().filter { it.isLoved }
        val lovedOutsideParents = favoriteRecords
            .map { it.path }
            .filterNot { it in favorites }
            .map { File(it) }
            .filter { it.isFile }
            .mapNotNull { it.parent }
            .distinct()
        if (lovedOutsideParents.isNotEmpty()) {
            val outsideByPath = lovedOutsideParents.flatMap { parent ->
                runCatching {
                    VideoScanUtils.getVideosInFolder(
                        getApplication(),
                        parent,
                        MediaScanPolicy(includeNoMediaContent = true),
                    )
                }.onFailure { error ->
                    Log.e(TAG, "Failed to scan favorite folder: $parent", error)
                }.getOrDefault(emptyList())
            }.associateBy { it.path }
            favoriteRecords.forEach { record ->
                val video = outsideByPath[record.path] ?: return@forEach
                favorites.putIfAbsent(video.path, video)
            }
        }
        return favorites.values.toList()
    }

    fun getInitialPageIndex(initialVideoPath: String? = null): Int {
        val currentShorts = _shorts.value
        if (currentShorts.isEmpty()) return 0
        if (initialVideoPath != null) {
            val idx = currentShorts.indexOfFirst { it.path == initialVideoPath }
            if (idx != -1) return idx
        }
        val lastWatchedPath = browserPreferences.lastWatchedShortPath.get()
        if (lastWatchedPath.isNotBlank()) {
            val idx = currentShorts.indexOfFirst { it.path == lastWatchedPath }
            if (idx != -1) return idx
        }
        return 0
    }

    private fun buildElasticFeed(
        loved: MutableList<Video>,
        others: MutableList<Video>,
        targetOthersPerLoved: Int = 2
    ): List<Video> {
        if (loved.isEmpty()) return others.shuffled()
        if (others.isEmpty()) return loved.shuffled()

        val interleaved = mutableListOf<Video>()
        
        // Calculate dynamic ratio based on pool sizes
        val effectiveRatio = if (others.size >= loved.size * targetOthersPerLoved) {
            targetOthersPerLoved
        } else {
            // Gracefully degrade to 1:1 if we don't have enough "others"
            maxOf(1, others.size / loved.size)
        }

        while (loved.isNotEmpty() || others.isNotEmpty()) {
            val othersToTake = minOf(effectiveRatio, others.size)
            repeat(othersToTake) { interleaved.add(others.removeAt(0)) }

            if (loved.isNotEmpty()) interleaved.add(loved.removeAt(0))

            // If others are exhausted, dump the rest of the loved videos
            if (others.isEmpty() && loved.isNotEmpty()) {
                interleaved.addAll(loved.shuffled())
                loved.clear()
            }
        }
        return interleaved
    }
    
    fun onShortSettled(video: Video) {
        browserPreferences.lastWatchedShortPath.set(video.path)
    }

    suspend fun getThumbnail(video: Video): Bitmap? {
        return thumbnailRepository.getThumbnail(video, 1080, 1920, forceFirstFrame = true)
    }

    fun syncPlaybackSpeed() {
        MPVLib.setPropertyDouble("speed", _currentSpeed.value)
    }

    fun setPlaybackSpeed(speed: Double) {
        MPVLib.setPropertyDouble("speed", speed)
        _currentSpeed.value = speed
    }

    fun cycleSpeed() {
        val speeds = listOf(1.0, 1.5, 2.0, 0.5)
        val current = _currentSpeed.value
        val nextIndex = (speeds.indexOf(current) + 1) % speeds.size
        setPlaybackSpeed(speeds[nextIndex])
    }

    fun toggleAutoSwipe() {
        val newState = !browserPreferences.autoSwipeShorts.get()
        browserPreferences.autoSwipeShorts.set(newState)
    }

    fun toggleLove(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            if (current?.isLoved == true) {
                val originalPath = current.originalPath
                val originalDirectory = originalPath?.let { File(it).parentFile }
                if (originalDirectory == null) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.shorts_favorite_original_path_unavailable),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                CopyPasteOps.moveVideo(getApplication(), video, originalDirectory.absolutePath).fold(
                    onSuccess = { restoredVideo ->
                        val updated = current.copy(path = restoredVideo.path, isLoved = false, originalPath = null)
                        shortsMediaDao.upsert(updated)
                        if (restoredVideo.path != video.path) shortsMediaDao.deleteByPath(video.path)
                        updateAfterFavoriteMove(video, restoredVideo, removeFromFavorites = true)
                    },
                    onFailure = { error -> showFavoriteMoveError(error.localizedMessage) },
                )
                return@launch
            }

            val favoriteDirectory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "المفضلة",
            )
            val alreadyInFavorites = runCatching {
                File(video.path).parentFile?.canonicalFile == favoriteDirectory.canonicalFile
            }.getOrDefault(false)
            val moveResult = if (alreadyInFavorites) Result.success(video) else {
                CopyPasteOps.moveVideo(getApplication(), video, favoriteDirectory.absolutePath)
            }
            moveResult.fold(
                onSuccess = { favoriteVideo ->
                    try {
                        val existingAtTarget = shortsMediaDao.getShortsMediaByPath(favoriteVideo.path)
                        val saved = (existingAtTarget ?: current ?: ShortsMediaEntity(path = favoriteVideo.path)).copy(
                            path = favoriteVideo.path,
                            isLoved = true,
                            originalPath = current?.originalPath ?: if (alreadyInFavorites) null else video.path,
                        )
                        shortsMediaDao.upsert(saved)
                        if (favoriteVideo.path != video.path) shortsMediaDao.deleteByPath(video.path)
                        updateAfterFavoriteMove(video, favoriteVideo, removeFromFavorites = false)
                    } catch (_: Exception) {
                        Toast.makeText(
                            getApplication(),
                            getApplication<Application>().getString(R.string.shorts_favorite_saved_path_error),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                onFailure = { error -> showFavoriteMoveError(error.localizedMessage) },
            )
        }
    }

    private fun updateAfterFavoriteMove(original: Video, moved: Video, removeFromFavorites: Boolean) {
        val updated = if (currentLoadedMode == true && removeFromFavorites) {
            _shorts.value.filterNot { it.path == original.path || it.path == moved.path }
        } else {
            _shorts.value.map { if (it.path == original.path) moved else it }
        }
        _shorts.value = updated
        _totalShortsCount.value = updated.size
    }

    private fun showFavoriteMoveError(reason: String?) {
        Toast.makeText(
            getApplication(),
            getApplication<Application>().getString(
                R.string.shorts_favorite_move_failed,
                reason?.takeIf { it.isNotBlank() } ?: "Unknown error",
            ),
            Toast.LENGTH_LONG,
        ).show()
    }

    suspend fun deleteShort(video: Video): Boolean {
        val (deleted, _) = PermissionUtils.StorageOps.deleteVideos(getApplication(), listOf(video))
        if (deleted <= 0) {
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.shorts_quick_delete_failed),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        shortsMediaDao.deleteByPath(video.path)
        val updated = _shorts.value.filterNot { it.path == video.path }
        _shorts.value = updated
        _totalShortsCount.value = updated.size
        return true
    }

    companion object {
        private const val TAG = "ShortsViewModel"

        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShortsViewModel(application) as T
        }
    }
}
