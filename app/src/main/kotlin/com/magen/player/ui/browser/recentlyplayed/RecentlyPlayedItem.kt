package com.magen.player.ui.browser.recentlyplayed

import com.magen.player.database.entities.PlaylistEntity
import com.magen.player.domain.media.model.Video

sealed class RecentlyPlayedItem {
  abstract val timestamp: Long

  data class VideoItem(
    val video: Video,
    override val timestamp: Long,
    val progress: Float? = null,
    val isWatched: Boolean = false,
  ) : RecentlyPlayedItem()

  data class PlaylistItem(
    val playlist: PlaylistEntity,
    val videoCount: Int,
    val mostRecentVideoPath: String,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()
}
