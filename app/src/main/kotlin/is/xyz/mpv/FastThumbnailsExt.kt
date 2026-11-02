/*
 * Extension stubs for is.xyz.mpv.FastThumbnails.
 *
 * The MagenPlayer-libmpv fork (v0.0.9) added a getEmbeddedPictureAsync method
 * that doesn't exist in the upstream mpvRex-libmpv (v1.0.2). This stub provides
 * the same API surface so ThumbnailRepository compiles; it always returns null,
 * causing the caller to fall through to the next thumbnail strategy.
 */

package `is`.xyz.mpv

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stub: returns null (no embedded picture available via this path).
 * The caller falls back to MediaMetadataRetriever or MediaStore thumbnail.
 */
suspend fun FastThumbnails.getEmbeddedPictureAsync(
    path: String,
    dimension: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    // Not available in upstream mpvRex-libmpv — return null so caller falls back
    null
}
