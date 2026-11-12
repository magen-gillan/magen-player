package com.magen.player.utils.media

import android.util.Log
import com.magen.player.preferences.SubtitlesPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale

/**
 * Simple utility for automatically loading subtitle files
 * Finds subtitles in the same directory that match the video filename
 */
object SubtitleOps : KoinComponent {
  private const val TAG = "SubtitleOps"
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val context: android.content.Context by inject()

  suspend fun autoloadSubtitles(
    videoFilePath: String,
    videoFileName: String,
    networkConnectionId: Long = -1L,
  ) = withContext(Dispatchers.IO) {
    if (videoFilePath.startsWith("fd://") || videoFilePath.startsWith("content://")) return@withContext
    if (networkConnectionId != -1L || videoFilePath.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) {
      return@withContext
    }
    runCatching { autoloadLocalSubtitles(videoFilePath, videoFileName) }
      .onFailure { Log.e(TAG, "Error loading local subtitles", it) }
  }

  private suspend fun autoloadLocalSubtitles(
    videoFilePath: String,
    videoFileName: String,
  ) {
    val videoFile = File(videoFilePath)
    val videoDirectory = videoFile.parentFile ?: return
    val baseName = videoFileName.substringBeforeLast('.')

    val subtitles =
      videoDirectory.listFiles()?.filter { file ->
        file.isFile &&
          isSubtitleFile(file.name) &&
          file.nameWithoutExtension.startsWith(baseName, ignoreCase = true)
      } ?: emptyList()

    if (subtitles.isNotEmpty()) {
      withContext(Dispatchers.Main) {
        subtitles.forEachIndexed { index, subtitle ->
          // MPV command format: sub-add <url> [<flags> [<title>]]
          // Use "select" for the first autoloaded subtitle unless disabled by default
          val disableByDefault = subtitlesPreferences.disableSubtitlesByDefault.get()
          val flag = if (index == 0 && !disableByDefault) "select" else "auto"
          val preferredEncoding = subtitlesPreferences.subtitleEncoding.get()
          val normalizedPath = SubtitleEncodingUtils.normalizeLocalSubtitleFile(
            context,
            subtitle,
            preferredEncoding
          )
          MPVLib.command("sub-add", normalizedPath, flag, subtitle.name)
          Log.d(TAG, "Loaded local subtitle: ${subtitle.name} (flag=$flag, path=$normalizedPath)")
        }
      }
    }
  }

  private fun isSubtitleFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return extension in setOf(
      // Common & modern
      "srt", "vtt", "ass", "ssa",
      // DVD / Blu-ray
      "sub", "idx", "sup",
      // Streaming / XML / Professional
      "xml", "ttml", "dfxp", "itt", "ebu", "imsc", "usf",
      // Online platforms
      "sbv", "srv1", "srv2", "srv3", "json",
      // Legacy & niche
      "sami", "smi", "mpl", "pjs", "stl", "rt", "psb", "cap",
      // Broadcast captions
      "scc", "vttx",
      // Karaoke / lyrics
      "lrc", "krc",
      // Fallback / raw text
      "txt", "pgs"
    )
  }
}
