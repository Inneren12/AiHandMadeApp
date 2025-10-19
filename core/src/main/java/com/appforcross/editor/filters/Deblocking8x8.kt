package com.appforcross.editor.filters

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16

/** Placeholder deblocking filter. */
object Deblocking8x8 {
    private const val TAG = "FILTERS"

    /** Applies an 8x8 deblocking pass with the provided strength. */
    fun apply(img: LinearImageF16, strength: Float = 0.35f): LinearImageF16 {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "Deblocking8x8.apply",
                "w" to img.width,
                "h" to img.height,
                "deblock.strength" to strength
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
