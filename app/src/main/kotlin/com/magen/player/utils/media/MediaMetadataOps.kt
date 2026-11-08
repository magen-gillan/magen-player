package com.magen.player.utils.media

import android.content.Context
import android.util.Log
import com.magen.player.domain.media.model.VideoFolder
import com.magen.player.domain.playbackstate.repository.PlaybackStateRepository
import com.magen.player.preferences.AppearancePreferences
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.database.repository.HybridMediaIndexRepository
import com.magen.player.utils.storage.CoreMediaScanner
import com.magen.player.utils.storage.FileFilterUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Operations for mapping media files to high-level domain models like [VideoFolder].
 */
object MediaMetadataOps {
    private const val TAG = "MediaMetadataOps"

    /**
     * Scans and maps all folders containing media into [VideoFolder] domain objects.
     *
     * Cold-start behavior: when the persistent hybrid index is still empty (first launch),
     * a full storage scan used to block this call — and with it the folder-list spinner —
     * for the entire walk of external storage. The index refresh is now kicked off in the
     * background while folders are returned immediately from the fast MediaStore-backed
     * scanner. When the background scan completes it pings [MediaLibraryEvents] so every
     * browser reloads with the complete index data.
     */
    suspend fun getAllMediaFolders(context: Context): List<VideoFolder> =
        withContext(Dispatchers.IO) {
            try {
                val koin = GlobalContext.get()
                val browserPreferences = koin.get<BrowserPreferences>()
                val appearancePreferences = koin.get<AppearancePreferences>()
                val playbackStateRepository = koin.get<PlaybackStateRepository>()

                val isAudioEnabled = browserPreferences.showAudioFiles.get()
                val playbackStates = playbackStateRepository.getAllPlaybackStates()
                val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()

                val foldersPreferences = koin.get<com.magen.player.preferences.FoldersPreferences>()
                val blacklistedFolders = foldersPreferences.blacklistedFolders.get()

                val hybridIndex = koin.get<HybridMediaIndexRepository>()
                val includeNoMedia = browserPreferences.includeNoMediaContent.get()

                val folders = if (hybridIndex.isIndexEmpty()) {
                    Log.d(TAG, "Hybrid index is cold: returning MediaStore folders, scanning in background")
                    hybridIndex.ensureFreshAsync()
                    CoreMediaScanner.getFlatMediaFolders(
                        context = context,
                        playbackStates = playbackStates,
                        thresholdDays = thresholdDays,
                        blacklistedFolders = blacklistedFolders,
                    )
                } else {
                    hybridIndex.ensureFreshIfEmpty()
                    hybridIndex.getFlatFolders(
                        playbackStates = playbackStates,
                        thresholdDays = thresholdDays,
                        watchedThreshold = browserPreferences.watchedThreshold.get(),
                    )
                }

                folders
                    .filter { folder ->
                        (isAudioEnabled || folder.videoCount > 0) &&
                        folder.path !in blacklistedFolders &&
                        (includeNoMedia || !FileFilterUtils.isWithinNoMediaBoundary(File(folder.path)))
                    }
                    .map { folder ->
                        VideoFolder(
                            bucketId = folder.id,
                            name = folder.name,
                            path = folder.path,
                            videoCount = folder.videoCount,
                            audioCount = folder.audioCount,
                            totalSize = folder.totalSize,
                            totalDuration = folder.totalDuration,
                            videoSize = folder.videoSize,
                            audioSize = folder.audioSize,
                            videoDuration = folder.videoDuration,
                            audioDuration = folder.audioDuration,
                            lastModified = folder.lastModified,
                            newCount = folder.newCount,
                            unwatchedVideoCount = folder.unwatchedVideoCount
                        )
                    }
                    .distinctBy { it.bucketId }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping media folders", e)
                emptyList()
            }
        }
}
