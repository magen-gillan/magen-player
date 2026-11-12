package com.magen.player.ui.player.delegates

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.lifecycleScope
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.magen.player.R
import com.magen.player.ui.player.MediaPlaybackService
import com.magen.player.ui.player.PlayerActivity
import com.magen.player.ui.player.PlayerActivity.Companion.TAG

import com.magen.player.ui.player.resolveUri
import com.magen.player.utils.media.FolderPlaylistOps
import com.magen.player.utils.media.M3UParseResult
import com.magen.player.utils.media.M3UParser

/**
 * Controller responsible for M3U playlist parsing, folder tree walking, auto-playlist generation,
 * and local playlist navigation.
 */
class PlayerPlaylistLoader(
  private val activity: PlayerActivity,
) {
  // PRELOAD-RESUME-STATE-V1
  var isAutoplayNextTriggered: Boolean = false

  /**
   * Check if there's a next video in the playlist
   */
  fun hasNext(): Boolean {
    val playlistHasNext = activity.viewModel.playlistManager.hasNext(activity.viewModel.shouldRepeatPlaylist())
    val mpvCount = runCatching { MPVLib.getPropertyInt("playlist-count") }.getOrNull() ?: 0
    val mpvPos = runCatching { MPVLib.getPropertyInt("playlist-pos") }.getOrNull() ?: 0
    val mpvHasNext = mpvCount > 1 && mpvPos < mpvCount - 1
    return playlistHasNext || mpvHasNext
  }

  /**
   * Check if there's a previous video in the playlist
   */
  fun hasPrevious(): Boolean {
    val playlistHasPrev = activity.viewModel.playlistManager.hasPrevious(activity.viewModel.shouldRepeatPlaylist())
    val mpvPos = runCatching { MPVLib.getPropertyInt("playlist-pos") }.getOrNull() ?: 0
    val mpvHasPrev = mpvPos > 0
    return playlistHasPrev || mpvHasPrev
  }

  fun updateMiniPlayerPlaylistState() {
    val nextIndex = activity.viewModel.playlistManager.getNextIndex(activity.viewModel.shouldRepeatPlaylist())
    val nextUri = if (nextIndex != null) activity.viewModel.playlistManager.playlist.value.getOrNull(nextIndex) else null
    val nextTitle = if (nextIndex != null) {
      activity.viewModel.playlistManager.getTitleAt(nextIndex) ?: nextUri?.let { activity.extractFileNameFromUri(it) }
    } else null

    val prevIndex = activity.viewModel.playlistManager.getPreviousIndex(activity.viewModel.shouldRepeatPlaylist())
    val prevUri = if (prevIndex != null) activity.viewModel.playlistManager.playlist.value.getOrNull(prevIndex) else null
    val prevTitle = if (prevIndex != null) {
      activity.viewModel.playlistManager.getTitleAt(prevIndex) ?: prevUri?.let { activity.extractFileNameFromUri(it) }
    } else null

    val hasNextItem = hasNext()
    val hasPrevItem = hasPrevious()

    activity.miniPlayerStateManager.updateState(
      hasNext = hasNextItem,
      hasPrevious = hasPrevItem,
      nextTitle = nextTitle,
      prevTitle = prevTitle,
    )

    activity.lifecycleScope.launch(Dispatchers.IO) {
      val cachedNextThumb = nextUri?.let { activity.getCachedThumbnailForUri(it) }
      val cachedPrevThumb = prevUri?.let { activity.getCachedThumbnailForUri(it) }
      withContext(Dispatchers.Main) {
        activity.miniPlayerStateManager.updateState(
          nextThumbnail = cachedNextThumb,
          prevThumbnail = cachedPrevThumb,
        )
      }
      val fullNextThumbnail = nextUri?.let { activity.extractThumbnailOrCoverArt(it) }
      val fullPrevThumbnail = prevUri?.let { activity.extractThumbnailOrCoverArt(it) }
      withContext(Dispatchers.Main) {
        activity.miniPlayerStateManager.updateState(
          nextThumbnail = fullNextThumbnail ?: cachedNextThumb,
          prevThumbnail = fullPrevThumbnail ?: cachedPrevThumb,
        )
      }
    }
  }

  /**
   * Play the next video in the playlist
   */
  fun playNext() {
    val nextIndex = activity.viewModel.playlistManager.getNextIndex(activity.viewModel.shouldRepeatPlaylist())
    if (nextIndex != null) {
      loadPlaylistItem(nextIndex)
    }
  }

  /**
   * Play the previous video in the playlist
   */
  fun playPrevious() {
    val prevIndex = activity.viewModel.playlistManager.getPreviousIndex(activity.viewModel.shouldRepeatPlaylist())
    if (prevIndex != null) {
      loadPlaylistItem(prevIndex)
    }
  }

  /**
   * Load a playlist item by index
   */
  fun loadPlaylistItem(index: Int) {
    val playlist = activity.viewModel.playlistManager.playlist.value
    // All items are loaded - just validate index and load directly
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }
    isAutoplayNextTriggered = true
    val loadGeneration = activity.beginPlaybackLoadRequest()
    loadPlaylistItemInternal(index, loadGeneration)
  }

  /**
   * Internal method to load a playlist item
   */
  fun loadPlaylistItemInternal(index: Int, loadGeneration: Long = activity.beginPlaybackLoadRequest()) {
    val playlist = activity.viewModel.playlistManager.playlist.value
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }

    val uri = playlist[index]

    loadResolvedPlaylistItem(
      index,
      uri,
      uri.resolveUri(activity) ?: uri.toString(),
      loadGeneration = loadGeneration,
    )
  }


  /**
   * Loads a playlist item whose playable URI is already known.
   */
  fun loadResolvedPlaylistItem(
    index: Int,
    uri: Uri,
    playableUri: String,
    mediaIdentifierOverride: String? = null,
    loadGeneration: Long = activity.beginPlaybackLoadRequest(),
  ) {
    if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return
    activity.isReady = false
    // Save current video's playback state before switching
    if (activity.fileName.isNotBlank()) {
      activity.saveVideoPlaybackState(activity.fileName)
    }

    // Update index in manager
    activity.viewModel.playlistManager.updateIndex(index)

    // Extract and set the new file name
    val customTitle = activity.viewModel.playlistManager.getTitleAt(index)
    activity.fileName = if (!customTitle.isNullOrBlank()) customTitle else getFileNameFromUri(uri)
    // Generate new media identifier for playback state
    activity.mediaIdentifier = mediaIdentifierOverride ?: getMediaIdentifierFromUri(uri, activity.fileName)
    activity.viewModel.setMediaTitle(activity.fileName)
    activity.viewModel.setMediaIdentifier(activity.mediaIdentifier)

    val cachedDurationMs = activity.viewModel.playlistManager.getDurationAt(index)
    val fastDurationMs = if (cachedDurationMs > 0L) cachedDurationMs else getFastDurationMsForUri(uri)
    val fastDurationSec = if (fastDurationMs > 0L) fastDurationMs / 1000f else null
    activity.viewModel.prepareForFileLoad(fastDurationSec)
    val loadedTitle = activity.fileName

    val targetUri = uri
    activity.lifecycleScope.launch(Dispatchers.IO) {
      val cachedThumb = activity.getCachedThumbnailForUri(targetUri)
      withContext(Dispatchers.Main) {
        if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return@withContext
        val activeThumb = cachedThumb ?: MediaPlaybackService.thumbnail ?: activity.miniPlayerStateManager.state.value.thumbnail
        MediaPlaybackService.thumbnail = activeThumb
        activity.mediaPlaybackService?.setMediaInfo(
          title = loadedTitle,
          artist = "",
          thumbnail = activeThumb
        )
        activity.miniPlayerStateManager.updateState(
          title = loadedTitle,
          thumbnail = activeThumb,
          videoPath = targetUri.toString(),
          currentPositionMs = 0L,
          durationMs = fastDurationMs,
          nextThumbnail = null,
          prevThumbnail = null,
        )
      }
    }

    // Update playlist play history if this is a custom playlist
    activity.viewModel.playlistManager.playlistId?.let { id ->
      activity.lifecycleScope.launch(Dispatchers.IO) {
        val filePath = when (uri.scheme) {
          "file" -> uri.path ?: uri.toString()
          "content" -> {
            activity.contentResolver.query(
              uri,
              arrayOf(MediaStore.MediaColumns.DATA),
              null,
              null,
              null,
            )?.use { cursor ->
              if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex != -1) cursor.getString(columnIndex) else null
              } else null
            } ?: uri.toString()
          }

          else -> uri.toString()
        }

        runCatching {
          activity.playlistRepository.updatePlayHistory(id, filePath)
          Log.d(TAG, "Updated playlist history for: $filePath in playlist $id")
        }.onFailure { e ->
          Log.e(TAG, "Error updating playlist history", e)
        }
      }
    }

    if (activity.mpvInitialized) {
      activity.safeSetPropertyString("vid", "no")
    }
    runCatching { MPVLib.setPropertyBoolean("pause", true) }
    // Prepare the resume position and cached playback state before MPV opens the item.
    activity.lifecycleScope.launch {
      activity.loadMediaWithPlaybackState(playableUri, loadGeneration)
    }

    // Update media title (this will trigger UI update)
    // Don't force media-title for standalone m3u/m3u8 streams - let MPV provide it
    // But if we are playing from an M3U playlist with custom titles, we MUST set it
    val isM3U = isUriM3U(uri)
    val isM3uPlaylist = activity.viewModel.playlistManager.isM3uPlaylist
    val hasCustomTitle = !activity.viewModel.playlistManager.getTitleAt(index).isNullOrBlank()
    if (!isM3U || isM3uPlaylist || hasCustomTitle) {
      activity.safeSetPropertyString("force-media-title", activity.fileName)
      activity.viewModel.setMediaTitle(activity.fileName)
    } else {
      activity.viewModel.setMediaTitle(activity.fileName)
    }

    // Update media session metadata
    activity.lifecycleScope.launch {
      kotlinx.coroutines.delay(100) // Wait for MPV to load the file
      if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return@launch
      val mpvDuration = MPVLib.getPropertyDouble("duration")
      val durationMs = if (mpvDuration != null && mpvDuration > 0) {
        (mpvDuration * 1000).toLong()
      } else {
        fastDurationMs
      }
      activity.updateMediaSessionMetadata(
        title = activity.fileName,
        durationMs = durationMs,
      )
      // Refresh playlist items to update the currently playing indicator
      activity.viewModel.refreshPlaylistItems()
    }
  }

  /**
   * Fast synchronous lookup for media duration in milliseconds.
   * Checks MediaStore for content:// and file:// URIs.
   */
  fun getFastDurationMsForUri(uri: Uri): Long {
    return runCatching {
      when (uri.scheme) {
        "content" -> {
          activity.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DURATION),
            null,
            null,
            null
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
              if (idx != -1) cursor.getLong(idx) else 0L
            } else 0L
          } ?: 0L
        }
        "file", null -> {
          val path = uri.path ?: return 0L
          activity.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DURATION),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(path),
            null
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
              if (idx != -1) cursor.getLong(idx) else 0L
            } else 0L
          } ?: 0L
        }
        else -> 0L
      }
    }.getOrDefault(0L)
  }

  /**
   * Get file name from URI (used for playlist items)
   */
  fun getFileNameFromUri(uri: Uri): String {
    activity.getDisplayNameFromUri(uri)?.let { return it }
    return activity.extractFileNameFromUri(uri)
  }

  /**
   * Get the current video title for controls display.
   * Used as a fallback when MPV hasn't set the media-title property yet.
   * For m3u/m3u8 streams, returns the raw media-title from MPV instead of parsing.
   */
  fun getTitleForControls(): String {
    // 1. Check if we have a custom playlist title
    val index = activity.viewModel.playlistManager.currentIndex.value
    if (activity.viewModel.playlistManager.playlist.value.isNotEmpty() && index >= 0 && index < activity.viewModel.playlistManager.playlist.value.size) {
      val customTitle = activity.viewModel.playlistManager.getTitleAt(index)
      if (!customTitle.isNullOrBlank()) {
        return customTitle
      }
    }

    // For m3u/m3u8 streams, use MPV's raw media-title directly
    if (isCurrentStreamM3U()) {
      val rawTitle = MPVLib.getPropertyString("media-title")
      if (!rawTitle.isNullOrBlank()) {
        return rawTitle
      }
    }
    return activity.fileName
  }

  /**
   * Check if the currently playing media is an m3u or m3u8 stream.
   * Checks both the intent URI and the current playlist item if playing from a playlist.
   */
  fun isCurrentStreamM3U(): Boolean {
    // First check the intent URI
    val uri = activity.extractUriFromIntent(activity.intent)
    if (uri != null && isUriM3U(uri)) {
      return true
    }

    // Also check the current playlist item if playing from a playlist
    val playlist = activity.viewModel.playlistManager.playlist.value
    val playlistIndex = activity.viewModel.playlistManager.currentIndex.value
    if (playlist.isNotEmpty() && playlistIndex >= 0 && playlistIndex < playlist.size) {
      return isUriM3U(playlist[playlistIndex])
    }

    return false
  }

  /**
   * Check if a specific URI string is an m3u or m3u8 file/stream.
   */
  fun isUriM3U(uriStr: String): Boolean {
    val lowerUrl = uriStr.lowercase()
    return lowerUrl.contains(".m3u8") || lowerUrl.contains(".m3u") ||
      lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".m3u")
  }

  /**
   * Check if a specific URI is an m3u or m3u8 file/stream.
   */
  fun isUriM3U(uri: Uri): Boolean {
    return isUriM3U(uri.toString())
  }

  /**
   * Intercepts a local M3U/M3U8 file and parses its local media paths.
   * populates the playlist, and triggers playback of the first item.
   * A local HLS playlist may fall back to direct local-file playback.
   */
  fun loadM3uPlaylistOrPlayDirectly(uriStr: String) {
    val uri = Uri.parse(uriStr)
    if (!M3UParser.isLocalMediaReference(uriStr)) {
      Log.w(TAG, "Ignoring non-local playlist URI")
      return
    }
    val loadGeneration = activity.beginPlaybackLoadRequest()
    activity.lifecycleScope.launch(Dispatchers.IO) {
      val result = M3UParser.parseFromUri(activity, uri)

      withContext(Dispatchers.Main) {
        if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return@withContext
        if (result is M3UParseResult.Success && result.items.isNotEmpty()) {
          val localItems = result.items.mapNotNull { item ->
            val itemUri = Uri.parse(item.path)
            if (itemUri.scheme == "content" || itemUri.scheme == "file" ||
              (itemUri.scheme == null && (itemUri.path?.startsWith('/') == true || item.path.startsWith('/')))) {
              item to itemUri
            } else null
          }
          val playlistUris = localItems.map { it.second }
          val playlistTitles = localItems.map { it.first.title ?: it.first.path }

          activity.viewModel.playlistManager.setPlaylist(
            items = playlistUris,
            index = 0,
            id = null,
            isM3u = true,
            titles = playlistTitles
          )

          // Play the first item
          loadPlaylistItemInternal(0, loadGeneration)

          // Set media title in UI
          val playlistName = result.playlistName
          activity.viewModel.setMediaTitle(playlistName)
          Log.d(TAG, "Loaded M3U playlist '${playlistName}' with ${localItems.size} items")
        } else {
          // Keep support for local HLS playlist files; remote sources are rejected above.
          Log.d(TAG, "Local M3U parsing failed or HLS playlist. Playing local file")
          if (activity.mpvInitialized) {
            activity.safeSetPropertyString("vid", "no")
          }
          runCatching { MPVLib.setPropertyBoolean("pause", true) }
          activity.lifecycleScope.launch {
            activity.loadMediaWithPlaybackState(uriStr, loadGeneration)
          }
        }
      }
    }
  }

  /**
   * Save recently played for a specific URI
   */
  suspend fun saveRecentlyPlayedForUri(
    uri: Uri,
    name: String,
  ) {
    activity.viewModel.historyManager.recordPlaybackStart(
      uri = uri,
      fileName = name,
      launchSource = "playlist",
      playlistId = activity.viewModel.playlistManager.playlistId
    )
  }

  /** Generate a stable identifier from a local media URI and display name. */
  fun getMediaIdentifier(intent: Intent, fileName: String): String {
    val uri = activity.extractUriFromIntent(intent)
    return if (uri != null) getMediaIdentifierFromUri(uri, fileName) else fileName
  }

  fun getMediaIdentifierFromUri(uri: Uri, fileName: String): String = fileName

  fun generatePlaylistFromMediaLibrary(currentPath: String) {
    activity.lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val result = FolderPlaylistOps.generateMediaLibraryPlaylist(activity, currentPath)
        if (result != null) {
          val (newPlaylist, newIndex) = result
          val durations = newPlaylist.map { getFastDurationMsForUri(it) }
          withContext(Dispatchers.Main) {
            activity.viewModel.playlistManager.setPlaylist(
              items = newPlaylist,
              index = newIndex,
              durations = durations
            )
            Log.d(TAG, "Auto-playlist generated from Media Library: ${newPlaylist.size} videos")
            updateMiniPlayerPlaylistState()
          }
        }
      }.onFailure { e ->
        Log.e(TAG, "Failed to auto-generate library playlist", e)
      }
    }
  }

  fun autoGeneratePlaylist(intent: Intent) {
    val path = activity.parsePathFromIntent(intent) ?: return
    if (intent.getStringExtra("launch_source") == "media_library_list") {
      generatePlaylistFromMediaLibrary(path)
    } else {
      generatePlaylistFromFolder(path)
    }
  }

  fun generatePlaylistFromFolder(currentPath: String) {
    activity.lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val launchSource = activity.intent.getStringExtra("launch_source")
        val result = FolderPlaylistOps.generateFolderPlaylist(activity, currentPath, launchSource)
        if (result != null) {
          val (newPlaylist, newIndex) = result
          val durations = newPlaylist.map { getFastDurationMsForUri(it) }
          withContext(Dispatchers.Main) {
            activity.viewModel.playlistManager.setPlaylist(
              items = newPlaylist,
              index = newIndex,
              durations = durations
            )
            Log.d(TAG, "Auto-playlist generated: ${newPlaylist.size} videos")
            updateMiniPlayerPlaylistState()
          }
        }
      }.onFailure { e ->
        Log.e(TAG, "Failed to auto-generate playlist", e)
      }
    }
  }


  /**
   * Check if the current playlist is an M3U playlist (sourced from database).
   */
  fun isCurrentPlaylistM3U(): Boolean = activity.viewModel.playlistManager.isM3uPlaylist

  fun getPlaylistWindowOffset(): Int = activity.viewModel.playlistManager.playlistWindowOffset
}
