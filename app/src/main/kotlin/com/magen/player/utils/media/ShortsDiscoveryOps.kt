package com.magen.player.utils.media

import android.content.Context
import android.util.Log
import com.magen.player.database.dao.ShortsMediaDao
import com.magen.player.database.repository.VideoMetadataCacheRepository
import com.magen.player.domain.media.model.Video
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.utils.storage.CoreMediaScanner
import com.magen.player.utils.storage.MediaScanPolicy
import com.magen.player.utils.storage.VideoScanUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Operations for discovering and filtering vertical videos (Shorts).
 */
object ShortsDiscoveryOps {
    private const val TAG = "ShortsDiscoveryOps"

    /**
     * Discovers all vertical videos across all scanned media folders.
     *
     * Kept for callers that need the complete list at once; prefer
     * [discoverShortsProgressively] when the result feeds UI, so the first item can be
     * shown before the whole library has been probed.
     */
    suspend fun discoverShorts(
        context: Context,
        shortsMediaDao: ShortsMediaDao,
        metadataCache: VideoMetadataCacheRepository,
        browserPreferences: BrowserPreferences,
        sortOrder: Comparator<Video>,
    ): List<Video> {
        val result = mutableListOf<Video>()
        discoverShortsProgressively(context, shortsMediaDao, metadataCache, browserPreferences, sortOrder)
            .collect { result.add(it) }
        return result
    }

    /**
     * Discovers vertical videos, emitting each one as soon as its metadata is known.
     *
     * Previously the entire library was probed with MediaInfo before the first page
     * could render, so a cold cache meant a spinner for minutes. Results are now
     * produced in three passes, cheapest first:
     *
     * 1. rows whose MediaStore resolution already proves they are shorts,
     * 2. rows with a store duration but no usable resolution (cheap retriever probe),
     * 3. everything else, via MediaInfo.
     *
     * Emissions follow [sortOrder]: a candidate is only sent once every candidate
     * ahead of it has been resolved, so the UI list grows as an ordered prefix of the
     * final feed. Without this, an item whose orientation probe is slow (e.g. video 8)
     * would land after already-emitted neighbours (7, 9) and the pager would appear to
     * skip it entirely.
     */
    fun discoverShortsProgressively(
        context: Context,
        shortsMediaDao: ShortsMediaDao,
        metadataCache: VideoMetadataCacheRepository,
        browserPreferences: BrowserPreferences,
        sortOrder: Comparator<Video>,
    ): Flow<Video> = channelFlow {
        val configuredFolders = browserPreferences.shortsSourceFolders.get()
            .map { runCatching { File(it).canonicalPath }.getOrDefault(File(it).absolutePath) }
        val sourceFolders = if (configuredFolders.isNotEmpty()) {
            configuredFolders
        } else {
            CoreMediaScanner.getFlatMediaFolders(context).map { it.path }
        }
        val scanPolicy = MediaScanPolicy(
            includeNoMediaContent = configuredFolders.isNotEmpty() || browserPreferences.includeNoMediaContent.get(),
        )
        val allVideos = sourceFolders.flatMap { folderPath ->
            VideoScanUtils.getVideosInFolder(context, folderPath, scanPolicy)
        }.distinctBy { it.path }.filter { !it.isAudio }

        val elimination = SelectionRules(
            includeHorizontal = browserPreferences.includeShortHorizontalVideos.get(),
            maxHorizontalMs = browserPreferences.maxHorizontalVideoDurationMinutes.get() * 60 * 1000L,
            includeAllSourceVideos = configuredFolders.isNotEmpty(),
            manuallyAdded = shortsMediaDao.getAllShortsMedia()
                .filter { it.isManuallyAdded }
                .map { it.path }
                .toSet(),
            blocked = shortsMediaDao.getAllShortsMedia()
                .filter { it.isBlocked }
                .map { it.path }
                .toSet(),
        )

        // Final feed order is decided up-front from MediaStore-backed fields
        // (name/size/date), which do not depend on the metadata passes below.
        val orderedVideos = allVideos.sortedWith(sortOrder)
        val resolvedVideos = HashMap<String, Video>(orderedVideos.size)
        val acceptedVideos = HashSet<String>(orderedVideos.size)
        var nextEmitIndex = 0

        fun resolve(path: String, video: Video, width: Int, height: Int, durationMs: Long) {
            if (resolvedVideos.containsKey(path)) return
            resolvedVideos[path] = video
            if (elimination.isAccepted(path, width, height, durationMs)) {
                acceptedVideos.add(path)
            }
        }

        fun flushResolved() {
            while (nextEmitIndex < orderedVideos.size) {
                val candidate = orderedVideos[nextEmitIndex]
                val resolved = resolvedVideos[candidate.path] ?: break
                nextEmitIndex++
                if (candidate.path in acceptedVideos) {
                    trySend(resolved)
                }
            }
        }

        try {
            // --- Pass 1: orientation already known from MediaStore ---
            for (video in orderedVideos) {
                val (w, h) = displayDimensions(video.width, video.height, video.rotation)
                if (elimination.includeAllSourceVideos || w > 0 && h > 0) {
                    resolve(video.path, video, w, h, video.duration)
                }
            }
            flushResolved()

            // --- Pass 2: cheap probe on rows MediaStore already timed ---
            val toProbe = orderedVideos.filter { it.path !in resolvedVideos && it.duration > 0 }
            for (video in toProbe) {
                val probed = metadataCache.probeResolution(video)
                if (probed != null) {
                    resolve(video.path, video, probed.first, probed.second, video.duration)
                    flushResolved()
                }
            }

            // --- Pass 3: MediaInfo for everything still undecided ---
            val undecided = orderedVideos.filter { it.path !in resolvedVideos }
            val files = undecided.mapNotNull { video ->
                val file = File(video.path)
                if (file.exists()) Triple(file, video.uri, video.displayName) else null
            }
            val byPath = undecided.associateBy { it.path }

            metadataCache.walkMetadata(files) { path, metadata ->
                val video = byPath[path] ?: return@walkMetadata
                if (resolvedVideos.containsKey(path)) return@walkMetadata

                // If MediaInfo could not report a resolution but MediaStore already said
                // portrait, keep the video instead of losing a valid short.
                val w: Int
                val h: Int
                if (metadata.width > 0 && metadata.height > 0) {
                    val rotated = displayDimensions(metadata.width, metadata.height, metadata.rotation)
                    w = rotated.first
                    h = rotated.second
                } else if (video.width > 0 && video.height > 0) {
                    val rotated = displayDimensions(video.width, video.height, video.rotation)
                    w = rotated.first
                    h = rotated.second
                } else {
                    // Orientation undeterminable: resolve without accepting so later
                    // candidates behind it can still be flushed.
                    resolvedVideos[path] = video
                    flushResolved()
                    return@walkMetadata
                }

                val durationMs = if (metadata.durationMs > 0) metadata.durationMs else video.duration
                resolve(path, enrich(video, metadata), w, h, durationMs)
                flushResolved()
            }

            flushResolved()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering shorts", e)
        }
    }.flowOn(Dispatchers.IO)

    /** Applies extracted metadata to a store-scanned row. */
    private fun enrich(video: Video, metadata: MediaInfoOps.VideoMetadata): Video = video.copy(
        duration = metadata.durationMs,
        durationFormatted = MediaFormatter.formatDuration(metadata.durationMs),
        width = metadata.width,
        height = metadata.height,
        fps = metadata.fps,
        resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
        hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
        subtitleCodec = metadata.subtitleCodec,
    )

    /** Applies display rotation: 90/270 degree rotations swap the axes. */
    private fun displayDimensions(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (rotation % 180 == 0) width to height else height to width

    /** Pure eligibility rules shared by every discovery pass. */
    private class SelectionRules(
        private val includeHorizontal: Boolean,
        private val maxHorizontalMs: Long,
        val includeAllSourceVideos: Boolean,
        private val manuallyAdded: Set<String>,
        private val blocked: Set<String>,
    ) {
        fun isAccepted(path: String, width: Int, height: Int, durationMs: Long): Boolean {
            if (path in blocked) return false
            if (includeAllSourceVideos) return true

            val isVertical = height > width && height > 0
            val isShortHorizontal = width > 0 &&
                height <= width &&
                includeHorizontal &&
                durationMs > 0 &&
                durationMs <= maxHorizontalMs

            return isVertical || path in manuallyAdded || isShortHorizontal
        }
    }
}
