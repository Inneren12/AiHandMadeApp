package com.appforcross.editor.io

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.ColorMeta
import com.appforcross.editor.types.HdrMode
import com.appforcross.editor.types.ImageSource
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.HalfFloats

/** Image decoder that produces a linear half-float representation. */
object Decoder {
    private const val TAG = "IO"

    /**
     * Decodes an [ImageSource] into a [LinearImageF16] alongside [ColorMeta].
     */
    fun decode(source: ImageSource, hdrMode: HdrMode = HdrMode.HDR_OFF): Pair<LinearImageF16, ColorMeta> {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        val params = mapOf(
            "src.kind" to when (source) {
                is ImageSource.Bytes -> "bytes"
                is ImageSource.FilePath -> "file"
            },
            "hdr.mode" to hdrMode.name,
            "colorspace.in" to "AUTO",
            "colorspace.out" to "SRGB_LINEAR",
            "icc.space" to "UNKNOWN",
            "icc.confidence" to 0.0,
            "seed" to 0,
            "tile.overlap" to 0,
            "threads" to 1,
            "neon.enabled" to false,
            "gpu.enabled" to false
        )
        Logger.i(TAG, "params", params)

        val image = LinearImageF16(1, 1, 3, ShortArray(3) { HalfFloats.fromFloat(0f) })
        val meta = ColorMeta("UNKNOWN", 0f, hdrMode)

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val endMem = runtime.totalMemory() - runtime.freeMemory()
        val donePayload = mapOf(
            "ms" to elapsedMs,
            "memMB" to endMem / (1024.0 * 1024.0)
        )
        Logger.i(TAG, "done", donePayload)
        return image to meta
    }
}
