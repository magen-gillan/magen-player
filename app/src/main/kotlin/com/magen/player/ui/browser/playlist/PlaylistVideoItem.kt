package com.magen.player.ui.browser.playlist

import com.magen.player.database.entities.PlaylistItemEntity
import com.magen.player.domain.media.model.Video

/** A playlist entry paired with its resolved video, used by playlist browsing UIs. */
data class PlaylistVideoItem(
  val playlistItem: PlaylistItemEntity,
  val video: Video,
)
