package com.magen.player.utils.storage

/**
 * Storage discovery rules for a single scan request.
 *
 * Keeping this separate from UI preferences prevents global scanners and
 * unrelated discovery surfaces from implicitly opting into .nomedia scans.
 */
data class MediaScanPolicy(
  val includeNoMediaContent: Boolean = false,
)

