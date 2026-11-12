package com.magen.player.ui.browser.shorts

import android.app.Application
import android.graphics.Bitmap
import android.os.Environment
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
import com.magen.player.utils.media.MediaFormatter
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

    val quickDeleteEnabled: StateFlow<Boolean> = browserPreferences.quickDeleteShorts.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), browserPreferences.quickDeleteShorts.get())

    val lovedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isLoved }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isBlocked }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val autoSwipe: StateFlow<Boolean> = browserPreferences.autoSwipeShorts.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), browserPreferences.autoSwipeShorts.get())

    private val _currentSpeed = MutableStateFlow(1.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()

    private var currentLoadedMode: Pair<Boolean, Boolean>? = null
    private var loadJob: Job? = null

    fun loadShorts(
        initialVideoPath: String? = null,
        blockedOnly: Boolean = false,
        favoritesOnly: Boolean = false,
        forceRefresh: Boolean = false,
    ) {
        val requestedMode = blockedOnly to favoritesOnly
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

                if (blockedOnly) {
                    val blockedShorts = loadBlockedShorts()
                    val ordered = orderVideos(blockedShorts)
                    _totalShortsCount.value = ordered.size
                    _shorts.value = ordered
                    return@launch
                }

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
                    received.add(video)
                    _shorts.value = orderVideos(withInitialFirst(received))
                    _totalShortsCount.value = received.size
                }

                // Keep an explicitly requested start video first, then apply the selected sort.
                val initial = pendingInitialVideo
                pendingInitialVideo = null
                val ordered = if (initial != null) listOf(initial) + received.filter { it.path != initial.path } else received
                _shorts.value = orderVideos(ordered)
                _totalShortsCount.value = ordered.size
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

    /** Apply a sort immediately and return the new page of the video currently being watched. */
    fun sortShorts(type: ShortsSortType, currentPath: String?): Int {
        _sortType.value = type
        browserPreferences.shortsSortType.set(type)
        val sorted = orderVideos(_shorts.value)
        _shorts.value = sorted
        return currentPath?.let { path -> sorted.indexOfFirst { it.path == path }.takeIf { it >= 0 } } ?: 0
    }

    private fun orderVideos(videos: List<Video>): List<Video> =
        when (_sortType.value) {
            ShortsSortType.Name -> videos.sortedWith { first, second ->
                SortUtils.NaturalOrderComparator.DEFAULT.compare(first.displayName, second.displayName)
            }
            ShortsSortType.Size -> videos.sortedByDescending { it.size }
            ShortsSortType.DateAdded -> videos.sortedByDescending { it.dateAdded }
        }

    /** Blocked-only feed; inherently a full scan, so it stays a single pass. */
    private suspend fun loadBlockedShorts(): List<Video> {
        val blockedInDb = shortsMediaDao.getAllShortsMedia().filter { it.isBlocked }
        val configuredFoldersSet = browserPreferences.shortsSourceFolders.get()
        val flatFolders = com.magen.player.utils.storage.CoreMediaScanner.getFlatMediaFolders(getApplication())
        val filteredFolders = if (configuredFoldersSet.isNotEmpty()) {
            flatFolders.filter { it.path in configuredFoldersSet }
        } else {
            flatFolders
        }
        val allVideos = filteredFolders.flatMap { folder ->
            com.magen.player.utils.storage.VideoScanUtils.getVideosInFolder(getApplication(), folder.path)
        }.filter { !it.isAudio }

        val blockedPathsSet = blockedInDb.map { it.path }.toSet()
        val blockedVideos = allVideos.filter { it.path in blockedPathsSet }

        val fileTriples = blockedVideos.mapNotNull { video ->
            val file = java.io.File(video.path)
            if (file.exists()) Triple(file, video.uri, video.displayName) else null
        }
        val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)
        return blockedVideos.map { video ->
            val metadata = metadataMap[video.path]
            if (metadata != null) {
                video.copy(
                    duration = metadata.durationMs,
                    durationFormatted = MediaFormatter.formatDuration(metadata.durationMs),
                    width = metadata.width,
                    height = metadata.height,
                    fps = metadata.fps,
                    resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                    hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                    subtitleCodec = metadata.subtitleCodec
                )
            } else {
                video
            }
        }
    }

    private suspend fun loadFavoriteShorts(): List<Video> {
        val favoriteRecords = shortsMediaDao.getAllShortsMedia().filter { it.isLoved }
        if (favoriteRecords.isEmpty()) return emptyList()
        val parents = favoriteRecords.map { File(it.path) }
            .filter { it.isFile }
            .mapNotNull { it.parent }
            .distinct()
        val videosByPath = parents.flatMap { parent ->
            VideoScanUtils.getVideosInFolder(
                getApplication(),
                parent,
                MediaScanPolicy(includeNoMediaContent = true),
            )
        }.associateBy { it.path }
        return favoriteRecords.mapNotNull { videosByPath[it.path] }
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
        val updated = if (currentLoadedMode?.second == true && removeFromFavorites) {
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

    fun toggleBlock(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            val isBlocked = current?.isBlocked ?: false
            val newEntity = current?.copy(isBlocked = !isBlocked)
                ?: ShortsMediaEntity(path = video.path, isBlocked = true, addedDate = System.currentTimeMillis())
            shortsMediaDao.upsert(newEntity)
        }
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
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShortsViewModel(application) as T
        }
    }
}
