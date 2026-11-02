/*
 * Stub for is.xyz.mpv.FastClipper — native FFmpeg-based clipper.
 *
 * The original FastClipper class was provided by the MagenPlayer-libmpv fork (v0.0.9)
 * which is no longer available on JitPack. This stub provides the same API surface so
 * the rest of the app compiles; cutClip always returns failure, which causes
 * VideoClipper to fall back to its native MediaExtractor+MediaMuxer implementation
 * (slower but works on all devices).
 */

package `is`.xyz.mpv

object FastClipper {
    enum class ClipMode {
        FAST_COPY,
        FRAME_ACCURATE,
    }

    /**
     * Cut a clip from [inputPath] between [startSec] and [endSec] seconds.
     * Writes the result to [outputPath].
     *
     * Stub implementation: always returns failure so the caller falls back
     * to the Android-native MediaExtractor + MediaMuxer path.
     */
    @JvmStatic
    fun cutClip(
        inputPath: String,
        outputPath: String,
        startSec: Double,
        endSec: Double,
        mode: ClipMode,
    ): Result<Unit> {
        return Result.failure(UnsupportedOperationException(
            "FastClipper native FFmpeg not available in this build — using MediaMuxer fallback"
        ))
    }
}
