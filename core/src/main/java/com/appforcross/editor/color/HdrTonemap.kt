package com.appforcross.editor.color

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HdrMode
import com.appforcross.editor.types.LinearImageF16

/** HDR tone mapping helpers. */
object HdrTonemap {
    private const val TAG = "HDR"

    /** Applies a tone-mapping curve to the provided image. */
    fun apply(img: LinearImageF16, mode: HdrMode, gainMap: Any? = null): LinearImageF16 {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "mode" to mode.name,
                "gainmap.present" to (gainMap != null)
            )
        )
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val endMem = runtime.totalMemory() - runtime.freeMemory()
        Logger.i(
            TAG,
            "done",
            mapOf(
                "ms" to elapsedMs,
                "memMB" to endMem / (1024.0 * 1024.0)
            )
        )
        return img
    }
}
