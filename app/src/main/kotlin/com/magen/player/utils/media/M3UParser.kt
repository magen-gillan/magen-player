package com.magen.player.utils.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Data class representing a local media entry in an M3U file
 */
data class M3UPlaylistItem(
  val path: String,
  val title: String? = null,
  val duration: Int = -1, // Duration in seconds, -1 if unknown
  val groupTitle: String? = null, // Group title for categorization
)

/**
 * Result of M3U playlist parsing
 */
sealed class M3UParseResult {
  data class Success(val playlistName: String, val items: List<M3UPlaylistItem>) : M3UParseResult()
  data class Error(val message: String, val exception: Throwable? = null) : M3UParseResult()
}

/**
 * Parser for M3U and M3U8 playlist files
 * Supports both simple M3U format and extended M3U format with EXTINF tags
 */
object M3UParser {
  private const val TAG = "M3UParser"

  /**
   * Parse an M3U/M3U8 playlist from a local file URI
   */
  suspend fun parseFromUri(context: Context, uri: Uri): M3UParseResult = withContext(Dispatchers.IO) {
    try {
      if (uri.scheme?.lowercase() !in setOf("content", "file")) {
        return@withContext M3UParseResult.Error("Only local M3U files are supported")
      }
      Log.d(TAG, "Parsing M3U playlist from URI: $uri")

      val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
          reader.readText()
        }
      } ?: return@withContext M3UParseResult.Error("Failed to open file")

      // Get filename for playlist name
      val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
          cursor.getString(nameIndex)
        } else null
      } ?: uri.lastPathSegment ?: "Local M3U Playlist"

      parseContent(content, filename, getLocalParentPath(context, uri))
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing M3U playlist from URI", e)
      M3UParseResult.Error("Failed to parse playlist: ${e.message}", e)
    }
  }

  /**
   * Parse local M3U/M3U8 content from string
   */
  fun parseContent(
    content: String,
    sourceName: String? = null,
    baseDirectory: String? = null,
  ): M3UParseResult {
    try {
      val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

      if (lines.isEmpty()) {
        return M3UParseResult.Error("Playlist is empty")
      }

      // Check if it's an HLS stream playlist (should not be parsed as an M3U playlist)
      if (lines.any { it.startsWith("#EXT-X-") }) {
        return M3UParseResult.Error("HLS stream playlist")
      }

      // Check if it's an extended M3U format
      lines.firstOrNull()?.startsWith("#EXTM3U") == true

      val items = mutableListOf<M3UPlaylistItem>()
      var currentTitle: String? = null
      var currentDuration: Int = -1


      for (line in lines) {
        when {
          line.startsWith("#EXTM3U") -> {
            // Playlist header, skip
            continue
          }

          line.startsWith("#EXTINF:") -> {
            // Extended info line: #EXTINF:duration,title
            val info = line.substring(8) // Remove "#EXTINF:"
            val parts = info.split(",", limit = 2)

            // Parse duration
            currentDuration = parts.firstOrNull()?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull() ?: -1

            // Parse title (second part after comma)
            currentTitle = if (parts.size > 1) parts[1].trim() else null

          }

          line.startsWith("#EXT-X-") -> {
            // HLS-specific tags, skip for now
            continue
          }

          line.startsWith("#") -> {
            // Other comment lines, skip
            continue
          }

          else -> {
            // Keep only local paths and local document URIs; network streams are unsupported.
            if (!isLocalMediaReference(line)) continue
            val scheme = getScheme(line)
            val mediaUrl = if (scheme == null && !line.startsWith("/")) {
              val base = baseDirectory ?: continue
              runCatching { File(base, line).canonicalPath }
                .getOrElse { File(base, line).absolutePath }
            } else line

            // Generate a title if none was provided.
            val title = currentTitle ?: extractDisplayName(mediaUrl)

            items.add(
              M3UPlaylistItem(
                path = mediaUrl,
                title = title,
                duration = currentDuration,
              )
            )

            // Reset current info for next entry
            currentTitle = null
            currentDuration = -1
          }
        }
      }

      if (items.isEmpty()) {
        return M3UParseResult.Error("No valid media URLs found in playlist")
      }

      // Extract playlist name from the local filename or use a default.
      val playlistName = sourceName?.substringAfterLast('/')?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.trim()
        ?.ifEmpty { null }
        ?: "M3U Playlist"

      Log.d(TAG, "Successfully parsed M3U playlist with ${items.size} items")
      return M3UParseResult.Success(playlistName, items)

    } catch (e: Exception) {
      Log.e(TAG, "Error parsing M3U content", e)
      return M3UParseResult.Error("Failed to parse playlist content: ${e.message}", e)
    }
  }

  /** True only for local filesystem paths or local Android document URIs. */
  fun isLocalMediaReference(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    val scheme = getScheme(trimmed)
    return scheme == null || scheme == "file" || scheme == "content" || scheme == "android.resource"
  }

  private fun getScheme(value: String): String? {
    val separator = value.indexOf(':')
    if (separator <= 0 || !value[0].isLetter()) return null
    val candidate = value.substring(0, separator)
    return candidate.takeIf { it.all { char -> char.isLetterOrDigit() || char == '+' || char == '-' || char == '.' } }
      ?.lowercase()
  }

  /** Resolve a local playlist's parent directory when its provider exposes a document path. */
  private fun getLocalParentPath(context: Context, uri: Uri): String? = runCatching {
    if (uri.scheme == "file") return@runCatching File(uri.path ?: return@runCatching null).parent
    if (uri.scheme != "content" || !DocumentsContract.isDocumentUri(context, uri)) return@runCatching null

    val documentId = DocumentsContract.getDocumentId(uri)
    val filePath = when {
      documentId.startsWith("raw:") -> documentId.removePrefix("raw:")
      else -> {
        val separator = documentId.indexOf(':')
        if (separator <= 0) return@runCatching null
        val volume = documentId.substring(0, separator)
        val relativePath = documentId.substring(separator + 1)
        val root = if (volume.equals("primary", ignoreCase = true)) {
          Environment.getExternalStorageDirectory()
        } else {
          File("/storage/$volume")
        }
        File(root, relativePath).path
      }
    }
    File(filePath).parent
  }.getOrNull()

  /** Extract a display name from a local file path or local document URI. */
  private fun extractDisplayName(path: String): String {
    val cleanPath = path.substringBefore('?').substringBefore('#')
    val withoutAuthority = if (cleanPath.contains("://")) {
      cleanPath.substringAfter("://").substringAfter('/', "")
    } else {
      cleanPath
    }
    val segment = withoutAuthority.substringAfterLast('/')
    val name = segment.substringBeforeLast('.', segment)
    return runCatching { java.net.URLDecoder.decode(name, "UTF-8") }
      .getOrDefault(name)
      .replace('_', ' ')
      .replace('-', ' ')
      .take(80)
  }


}
