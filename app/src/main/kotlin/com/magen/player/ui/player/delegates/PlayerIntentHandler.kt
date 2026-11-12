package com.magen.player.ui.player.delegates

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.magen.player.ui.player.PlayerActivity
import com.magen.player.ui.player.PlayerActivity.Companion.TAG
import com.magen.player.ui.player.PlayerOrientation
import com.magen.player.ui.player.ResumePlaybackMode
import com.magen.player.ui.player.openContentFd
import com.magen.player.ui.player.resolveUri
import com.magen.player.utils.media.FolderPlaylistOps

/**
 * Controller responsible for parsing incoming launch intents, content URIs,
 * subtitle and header extras, initial orientation detection, and activity result dispatch.
 */
class PlayerIntentHandler(
  private val activity: PlayerActivity,
) {
  /**
   * Sets the result intent with current playback position and duration.
   * Called when activity is finishing to return data to caller.
   */
  fun setReturnIntent() {
    Log.d(TAG, "Setting return intent")

    val resultIntent =
      Intent(RESULT_INTENT).apply {
        activity.viewModel.pos?.let { putExtra("position", it * MILLISECONDS_TO_SECONDS) }
        activity.viewModel.duration?.let { putExtra("duration", it * MILLISECONDS_TO_SECONDS) }
      }

    activity.setResult(Activity.RESULT_OK, resultIntent)
  }

  /**
   * Parses the file path from the intent.
   *
   * This method checks the intent action and data to determine the file path.
   * It supports the following actions:
   * - ACTION_VIEW: The file path is contained in the intent data.
   * - ACTION_SEND: The file path is contained in the intent extras.
   *
   * @param intent The intent containing the file URI
   * @return The resolved file path, or null if not found
   */
  fun parsePathFromIntent(intent: Intent): String? =
    when (intent.action) {
      Intent.ACTION_VIEW -> localPath(intent.data)
      Intent.ACTION_SEND -> parsePathFromSendIntent(intent)
      else -> localPath(intent.getStringExtra("uri")?.toUri())
    }

  /**
   * Parses the file path from a SEND intent.
   *
   * This method checks the intent extras for the file path.
   *
   * @param intent The SEND intent
   * @return The resolved file path, or null if not found
   */
  fun parsePathFromSendIntent(intent: Intent): String? =
    if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
      uri?.resolveUri(activity)
    } else {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
        val uri = text.toUri()
        if (uri.scheme == null && text.startsWith('/')) text else localPath(uri)
      }
    }

  /**
   * Extracts and resolves the file name from the intent.
   *
   * @param intent The intent containing the file URI
   * @return The display name of the file, or empty string if not found
   */
  fun getFileName(intent: Intent): String {
    // First check if a custom title/filename was provided via intent extras
    intent.getStringExtra("title")?.let { return it }
    intent.getStringExtra("filename")?.let { return it }

    val uri = extractUriFromIntent(intent) ?: return ""

    // Try content resolver first for content:// URIs
    getDisplayNameFromUri(uri)?.let { return it }

    // Extract filename from URL/URI
    return extractFileNameFromUri(uri)
  }

  /**
   * Extracts filename from URI, handling URL encoding and network URLs properly.
   * For network streams, returns a temporary name that will be updated async via HTTP headers.
   *
   * @param uri The URI to extract filename from
   * @return The extracted filename
   */
  fun extractFileNameFromUri(uri: Uri): String {
    val lastSegment = uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: "Unknown Video"
    return try {
      java.net.URLDecoder.decode(lastSegment, "UTF-8")
    } catch (e: Exception) {
      lastSegment
    }
  }

  /**
   * Extracts the URI from the intent based on intent type.
   *
   * @param intent The intent to extract URI from
   * @return The extracted URI, or null if not found
   */
  fun extractUriFromIntent(intent: Intent): Uri? {
    val candidate = if (intent.type == "text/plain" || intent.action == Intent.ACTION_SEND) {
      val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
      stream ?: intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
        text.toUri().takeIf { it.scheme == "content" || it.scheme == "file" || (it.scheme == null && text.startsWith('/')) }
      }
    } else {
      intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
    }
    return candidate?.takeIf(::isLocalUri)
  }

  private fun isLocalUri(uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
    "content", "file" -> true
    null -> uri.path?.startsWith('/') == true
    else -> false
  }

  private fun localPath(uri: Uri?): String? {
    if (uri == null || !isLocalUri(uri)) return null
    return when (uri.scheme?.lowercase()) {
      "content" -> uri.resolveUri(activity)
      "file" -> uri.resolveUri(activity) ?: uri.path
      else -> uri.path ?: uri.toString()
    }
  }

  /**
   * Queries the content resolver to get the display name for a URI.
   *
   * @param uri The URI to query
   * @return The display name, or null if not found
   */
  fun getDisplayNameFromUri(uri: Uri): String? =
    runCatching {
      activity.contentResolver
        .query(
          uri,
          arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
          null,
          null,
          null,
        )?.use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.onFailure { e ->
      Log.e(TAG, "Error getting display name from URI", e)
    }.getOrNull()

  /**
   * Converts the intent URI to a playable URI string for MPV.
   *
   * @param intent The intent containing the file URI
   * @return A playable URI string, or null if unable to resolve
   */
  fun getPlayableUri(intent: Intent): String? {
    val source = parsePathFromIntent(intent)?.toUri()
      ?: (if (intent.action == Intent.ACTION_VIEW || intent.action == null) {
        FolderPlaylistOps.extractExternalPlaylist(intent)?.items?.getOrNull(0)
      } else null)
      ?: return null
    if (!isLocalUri(source)) return null
    return when (source.scheme?.lowercase()) {
      "content" -> source.openContentFd(activity)
      "file" -> source.resolveUri(activity) ?: source.path
      else -> source.path ?: source.toString()
    }
  }

  /**
   * Loads subtitles specified in intent extras.
   *
   * @param extras Bundle containing subtitle URIs
   */
  fun addSubtitlesFromExtras(extras: Bundle) {
    if (!extras.containsKey("subs")) return

    val subList = Utils.getParcelableArray<Uri>(extras, "subs")
    val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

    activity.lifecycleScope.launch(Dispatchers.Default) {
      val preferredEncoding = activity.subtitlesPreferences.subtitleEncoding.get()
      for (suburi in subList) {
        val (normalizedPath, _) = com.magen.player.utils.media.SubtitleEncodingUtils.normalizeSubtitleUri(
          activity,
          suburi,
          preferredEncoding
        )
        val subfile = if (normalizedPath.startsWith("content://") || normalizedPath.startsWith("file://")) {
          suburi.resolveUri(activity) ?: continue
        } else {
          normalizedPath
        }
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }
  }


  /**
   * Sets initial orientation synchronously from intent parameters or metadata cache
   * to avoid orientation jumps on activity launch or intent update.
   */
  fun applyInitialOrientationFromIntent(targetIntent: Intent) {
    val orient = activity.playerPreferences.orientation.get()
    if (orient != PlayerOrientation.Video && orient != PlayerOrientation.Smart) {
      activity.setOrientation()
      return
    }

    // 1. Try saved orientation from intent extras (for Smart mode)
    val intentSavedOrientation = targetIntent.getIntExtra("saved_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    if (orient == PlayerOrientation.Smart && intentSavedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      activity.requestedOrientation = intentSavedOrientation
      activity.isOrientationRestored = true
      Log.d(TAG, "applyInitialOrientationFromIntent - Smart mode: using restored orientation ${activity.requestedOrientation} from intent")
      return
    }

    // 2. Try dimensions from intent extras (for Video/Smart mode)
    val intentWidth = targetIntent.getIntExtra("width", -1)
    val intentHeight = targetIntent.getIntExtra("height", -1)
    val intentRotation = targetIntent.getIntExtra("rotation", 0)
    if (intentWidth > 0 && intentHeight > 0) {
      activity.setOrientation(intentWidth, intentHeight, intentRotation)
      return
    }

    // 3. Fallback: try saved orientation from DB or metadata cache asynchronously
    val targetFileName = getFileName(targetIntent).ifBlank { targetIntent.data?.lastPathSegment ?: "Unknown Video" }
    activity.lifecycleScope.launch(Dispatchers.IO) {
      if (orient == PlayerOrientation.Smart) {
        val state = activity.playbackStateRepository.getVideoDataByTitle(targetFileName)
        if (state?.savedOrientation != null && state.savedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
          withContext(Dispatchers.Main) {
            activity.requestedOrientation = state.savedOrientation!!
            activity.isOrientationRestored = true
            Log.d(TAG, "applyInitialOrientationFromIntent - Smart mode: using restored orientation ${activity.requestedOrientation} from DB")
          }
          return@launch
        }
      }

      val path = parsePathFromIntent(targetIntent)
      if (path != null) {
        val file = File(path)
        if (file.exists() && !com.magen.player.utils.storage.FileTypeUtils.isAudioFile(file)) {
          val metadata = activity.metadataCache.getOrExtractMetadata(file, targetIntent.data ?: "".toUri(), targetFileName)
          if (metadata != null && metadata.width > 0 && metadata.height > 0) {
            withContext(Dispatchers.Main) {
              activity.setOrientation(metadata.width, metadata.height, metadata.rotation)
            }
          }
        }
      }
    }
  }

  /**
   * Handles new intents to load a different file without recreating the activity.
   */
  fun handleNewIntent(intent: Intent) {
    activity.isAutoplayNextTriggered = false
    activity.pendingIntentExtras = true
    activity.setIntent(intent)

    val hasIntentMedia = intent.data != null || intent.hasExtra("video_list") || parsePathFromIntent(intent) != null
    val incomingFileName = getFileName(intent).ifBlank { intent.data?.lastPathSegment ?: "" }
    val incomingMediaIdentifier = if (incomingFileName.isNotBlank()) activity.getMediaIdentifier(intent, incomingFileName) else ""

    val isSameMedia = activity.isReady && hasIntentMedia &&
      ((incomingMediaIdentifier.isNotBlank() && incomingMediaIdentifier == activity.mediaIdentifier) ||
       (incomingFileName.isNotBlank() && incomingFileName == activity.fileName))

    // If expanding active session without intent media, or if the exact same media is already active in foreground
    if (activity.isReady && (!hasIntentMedia || (isSameMedia && !activity.isInBackgroundPlayback && !activity.isManualBackgroundPlayback))) {
      // PRELOAD-RESUME-STATE-V1: this intent did not start a new file load.
      activity.pendingIntentExtras = false
      Log.d(TAG, "onNewIntent: current media already playing or expanding active session, restoring player without reload")
      activity.enableVideoAfterBackground()
      @Suppress("DEPRECATION")
      activity.overridePendingTransition(android.R.anim.fade_in, 0)
      return
    }
    val externalPlaylist = if (intent.action == Intent.ACTION_VIEW || intent.action == null) {
      FolderPlaylistOps.extractExternalPlaylist(intent)
    } else null
    // Check if this intent has playlist information
    val hasPlaylistExtras = intent.hasExtra("playlist_id") ||
      intent.hasExtra("playlist") ||
      externalPlaylist != null

    // Clean up background playback state when loading a different video
    if (activity.isManualBackgroundPlayback || activity.isInBackgroundPlayback) {
      activity.isManualBackgroundPlayback = false
      activity.endBackgroundPlayback()
      activity.enableVideoAfterBackground()
      activity.miniPlayerStateManager.clearState()
    }

    // Clear stale playlist from previous video so auto-generate runs fresh for the new file
    activity.viewModel.playlistManager.setPlaylist(items = emptyList(), index = 0)

    // Load playlist from intent extras first (fast path)
    val playlistFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }

    // Only update playlist state if we have new playlist information
    if (externalPlaylist != null) {
      activity.viewModel.playlistManager.setPlaylist(
        items = externalPlaylist.items,
        index = externalPlaylist.initialIndex,
        titles = externalPlaylist.titles
      )
      Log.d(TAG, "onNewIntent: Loaded external playlist: ${externalPlaylist.items.size} items at index ${externalPlaylist.initialIndex}")
      activity.updateMiniPlayerPlaylistState()
    } else if (hasPlaylistExtras || playlistFromIntent.isNotEmpty()) {
      val newPlaylistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }
      val newPlaylistIndex = intent.getIntExtra("playlist_index", 0)
      val titlesFromIntent = intent.getStringArrayListExtra("playlist_titles") ?: emptyList()

      activity.viewModel.playlistManager.setPlaylist(
        items = playlistFromIntent,
        index = newPlaylistIndex,
        id = newPlaylistId,
        titles = titlesFromIntent
      )
    }

    // If playlist is empty but playlist_id is provided, load from database
    if (activity.viewModel.playlistManager.playlist.value.isEmpty() && activity.viewModel.playlistManager.playlistId != null) {
      activity.lifecycleScope.launch(Dispatchers.IO) {
        val pid = activity.viewModel.playlistManager.playlistId ?: return@launch
        try {
          val playlistItems = activity.playlistRepository.getPlaylistItems(pid)
          val items = playlistItems.map { item ->
            if (item.filePath.startsWith("/") || item.filePath.startsWith("file://")) {
              val path = if (item.filePath.startsWith("file://")) item.filePath.removePrefix("file://") else item.filePath
              Uri.fromFile(File(path))
            } else {
              Uri.parse(item.filePath)
            }
          }
          val titles = playlistItems.map { it.fileName }
          val totalCount = items.size
          withContext(Dispatchers.Main) {
            activity.viewModel.playlistManager.setPlaylist(
              items = items,
              index = activity.viewModel.playlistManager.currentIndex.value,
              id = pid,
              totalCount = totalCount,
              titles = titles
            )
            Log.d(TAG, "onNewIntent: Loaded ${items.size} items from playlist $pid")
            activity.updateMiniPlayerPlaylistState()
          }
        } catch (e: Exception) {
          Log.e(TAG, "onNewIntent: Failed to load playlist from database", e)
        }
      }
    }

    // Auto-generate playlist from folder if playlist mode is enabled and no playlist_id
    if (activity.viewModel.playlistManager.playlist.value.isEmpty() && activity.viewModel.playlistManager.playlistId == null && activity.playerPreferences.playlistMode.get()) {
      activity.autoGeneratePlaylist(intent)
    }

    // Extract the new fileName before loading the file
    var fileName = if (externalPlaylist != null && !externalPlaylist.titles.getOrNull(externalPlaylist.initialIndex).isNullOrBlank()) {
      externalPlaylist.titles[externalPlaylist.initialIndex]
    } else {
      getFileName(intent)
    }
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    activity.fileName = fileName
    activity.mediaIdentifier = activity.getMediaIdentifier(intent, fileName)
    activity.viewModel.setMediaTitle(fileName)
    activity.viewModel.setMediaIdentifier(activity.mediaIdentifier)
    // Synchronously set orientation for the new file before displaying activity
    applyInitialOrientationFromIntent(intent)

    // Load the new file
    getPlayableUri(intent)?.let { uriStr ->
      val parsedUri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return@let
      if (!isLocalUri(parsedUri)) return@let
      val fastDurationMs = activity.getFastDurationMsForUri(parsedUri)
      val fastDurationSec = if (fastDurationMs > 0L) fastDurationMs / 1000f else null
      activity.viewModel.prepareForFileLoad(fastDurationSec)

      if (activity.isUriM3U(parsedUri)) {
        activity.loadM3uPlaylistOrPlayDirectly(uriStr)
      } else {
        activity.loadLocalMedia(uriStr)
      }
    }
  }

  companion object {
    private const val RESULT_INTENT = "com.magen.player.ui.player.PlayerActivity.result"
    private const val MILLISECONDS_TO_SECONDS = 1000
  }
}
