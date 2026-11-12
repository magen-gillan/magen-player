package com.magen.player.utils.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.core.net.toUri
import com.magen.player.preferences.BrowserPreferences
import com.magen.player.preferences.FolderSortType
import com.magen.player.repository.MediaFileRepository
import com.magen.player.utils.sort.SortUtils
import com.magen.player.utils.storage.FileFilterUtils
import com.magen.player.utils.storage.FileTypeUtils
import com.magen.player.utils.storage.MediaScanPolicy
import com.magen.player.utils.storage.VideoScanUtils
import java.io.File
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Shared utility operations for auto-generating playlists from local folders or media library.
 * Used by both PlayerActivity and MediaUtils (for direct mini player playback).
 */
object FolderPlaylistOps : KoinComponent {
  private val browserPreferences: BrowserPreferences by inject()
  /**
   * Generates a playlist of URIs in the same folder as [currentPath].
   *
   * @return Pair of (uris, initialIndex) or null if single/invalid file.
   */
  suspend fun generateFolderPlaylist(
    context: Context,
    currentPath: String,
    launchSource: String? = null,
  ): Pair<List<Uri>, Int>? {
    runCatching {
      val currentFile = resolveLocalFile(context, currentPath) ?: return null
      if (!currentFile.exists()) return null

      val parentFolder = currentFile.parentFile ?: return null
      val scanPolicy = MediaScanPolicy(
        includeNoMediaContent = browserPreferences.includeNoMediaContent.get(),
      )
      if (!scanPolicy.includeNoMediaContent &&
        FileFilterUtils.isWithinNoMediaBoundary(parentFolder)
      ) {
        return null
      }

      val showAudio = browserPreferences.showAudioFiles.get()
      val files = parentFolder.listFiles { file ->
        file.isFile &&
          !FileFilterUtils.shouldSkipFile(file) &&
          (FileTypeUtils.isVideoFile(file) || (showAudio && FileTypeUtils.isAudioFile(file)))
      } ?: return null

      val lSource = launchSource ?: ""
      val siblingFiles = if (lSource == "video_list" || lSource == "recently_played_button" || lSource == "first_video_button") {
        val videoSortType = browserPreferences.videoSortType.get()
        val videoSortOrder = browserPreferences.videoSortOrder.get()
        val bucketId = parentFolder.absolutePath.replace("\\", "/")
        val videosInFolder = VideoScanUtils.getVideosInFolder(context, bucketId, scanPolicy)
        val sortedVideos = SortUtils.sortVideos(videosInFolder, videoSortType, videoSortOrder)
        sortedVideos.mapNotNull { video -> files.find { it.absolutePath == video.path } }
      } else if (lSource == "tree_mode") {
        val folderSortType = browserPreferences.folderSortType.get()
        val folderSortOrder = browserPreferences.folderSortOrder.get()
        val videosInFolder = VideoScanUtils.getVideosInFolder(context, parentFolder.absolutePath, scanPolicy)
        val sortedVideos = when (folderSortType) {
          FolderSortType.Title -> videosInFolder.sortedWith { t1, t2 -> SortUtils.NaturalOrderComparator.DEFAULT.compare(t1.displayName, t2.displayName) }
          FolderSortType.Duration -> videosInFolder.sortedBy { it.duration }
          FolderSortType.Date -> videosInFolder.sortedBy { File(it.path).lastModified() }
          FolderSortType.Size -> videosInFolder.sortedBy { it.size }
          FolderSortType.VideoCount -> videosInFolder.sortedBy { it.duration }
        }
        val orderedVideos = if (folderSortOrder.isAscending) sortedVideos else sortedVideos.reversed()
        orderedVideos.mapNotNull { video -> files.find { it.absolutePath == video.path } }
      } else {
        files.sortedWith { f1, f2 -> SortUtils.NaturalOrderComparator.DEFAULT.compare(f1.name, f2.name) }
      }

      if (siblingFiles.size <= 1) return null

      val newPlaylist = siblingFiles.map { it.toUri() }
      val newIndex = siblingFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }

      if (newIndex != -1) {
        return Pair(newPlaylist, newIndex)
      }
    }
    return null
  }

  private fun resolveLocalFile(context: Context, value: String): File? {
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    if (uri?.scheme != "content") return File(value)

    return runCatching {
      context.contentResolver.query(
        uri,
        arrayOf(android.provider.MediaStore.MediaColumns.DATA),
        null,
        null,
        null,
      )?.use { cursor ->
        val dataColumn = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
        if (dataColumn >= 0 && cursor.moveToFirst()) {
          cursor.getString(dataColumn)?.let(::File)
        } else {
          null
        }
      }
    }.getOrNull()
  }

  /**
   * Generates a playlist of URIs from the media library.
   *
   * @return Pair of (uris, initialIndex) or null if single/invalid file.
   */
  suspend fun generateMediaLibraryPlaylist(
    context: Context,
    currentPath: String,
  ): Pair<List<Uri>, Int>? {
    runCatching {
      val allVideos = MediaFileRepository.getAllVideos(context)
      val videoSortType = browserPreferences.videoSortType.get()
      val videoSortOrder = browserPreferences.videoSortOrder.get()

      var filteredVideos = allVideos
      if (!browserPreferences.showAudioFiles.get()) {
        filteredVideos = allVideos.filterNot { it.isAudio }
      }

      val sortedVideos = SortUtils.sortVideos(filteredVideos, videoSortType, videoSortOrder)
      if (sortedVideos.size <= 1) return null

      val newPlaylist = sortedVideos.map { it.uri }
      val newIndex = sortedVideos.indexOfFirst { it.path == currentPath || it.uri.toString() == currentPath }

      if (newIndex != -1) {
        return Pair(newPlaylist, newIndex)
      }
    }
    return null
  }
  /**
   * Represents an external playlist parsed from an external file manager intent (MX Player API).
   */
  data class ExternalPlaylist(
    val items: List<Uri>,
    val titles: List<String>,
    val initialIndex: Int,
    val isExplicit: Boolean,
  )

  /**
   * Extracts external playlist extras adhering to the MX Player intent API standard:
   * `video_list`, `video_list.name`, and `video_list_is_explicit`.
   */
  fun extractExternalPlaylist(intent: Intent): ExternalPlaylist? {
    val rawList: List<Uri> = run {
      val parcelableList = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra("video_list", Uri::class.java)
            ?: intent.getParcelableArrayListExtra("video_list", Parcelable::class.java)?.filterIsInstance<Uri>()
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra<Parcelable>("video_list")?.filterIsInstance<Uri>()
        }
      }.getOrNull()?.filterNotNull()
      if (!parcelableList.isNullOrEmpty()) {
        return@run parcelableList
      }

      val array = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayExtra("video_list", Uri::class.java)
            ?: intent.getParcelableArrayExtra("video_list", Parcelable::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayExtra("video_list")
        }
      }.getOrNull()
      val filteredArray = array?.mapNotNull { it as? Uri }
      if (!filteredArray.isNullOrEmpty()) {
        return@run filteredArray
      }

      val stringUris = (runCatching { intent.getStringArrayExtra("video_list")?.toList() }.getOrNull()
        ?: runCatching { intent.getStringArrayListExtra("video_list") }.getOrNull())
        ?.takeIf { it.isNotEmpty() }
        ?.mapNotNull { str -> str?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() } }
      if (!stringUris.isNullOrEmpty()) {
        return@run stringUris
      }

      emptyList()
    }

    if (rawList.isEmpty()) return null

    val rawNames: List<String>? = runCatching {
      intent.getStringArrayExtra("video_list.name")?.filterNotNull()?.takeIf { it.isNotEmpty() }
        ?: intent.getStringArrayListExtra("video_list.name")?.filterNotNull()?.takeIf { it.isNotEmpty() }
        ?: intent.getCharSequenceArrayExtra("video_list.name")?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }
        ?: intent.getCharSequenceArrayListExtra("video_list.name")?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    val isExplicit = intent.getBooleanExtra("video_list_is_explicit", false)

    val currentData = intent.data
    val initialIndex = currentData?.let { dataUri ->
      rawList.indexOfFirst { it == dataUri || it.toString().equals(dataUri.toString(), ignoreCase = true) }
        .takeIf { it >= 0 }
        ?: dataUri.path?.trimEnd('/')?.let { cleanDataPath ->
          rawList.indexOfFirst { it.path?.trimEnd('/')?.equals(cleanDataPath, ignoreCase = true) == true }.takeIf { it >= 0 }
        }
    } ?: 0

    val titles = rawList.mapIndexed { index, uri ->
      rawNames?.getOrNull(index)?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "Video ${index + 1}"
    }

    return ExternalPlaylist(
      items = rawList,
      titles = titles,
      initialIndex = initialIndex,
      isExplicit = isExplicit,
    )
  }
}
