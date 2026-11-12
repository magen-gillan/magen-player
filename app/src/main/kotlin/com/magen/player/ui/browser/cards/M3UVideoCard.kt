package com.magen.player.ui.browser.cards

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magen.player.domain.media.model.Video
import com.magen.player.preferences.UiSettings

@Composable
fun M3UVideoCard(
  title: String,
  path: String,
  uiSettings: UiSettings,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  isRecentlyPlayed: Boolean = false,
) {
  val maxLines = if (uiSettings.unlimitedNameLines) Int.MAX_VALUE else 2
  BaseMediaCard(
    title = title,
    modifier = modifier,
    thumbnailSize = 128.dp,
    thumbnail = null,
    thumbnailAspectRatio = 16f / 9f,
    onClick = onClick,
    onLongClick = onLongClick,
    isSelected = isSelected,
    maxTitleLines = maxLines,
    thumbnailIcon = {
      Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.secondary,
      )
    },
  )
}
