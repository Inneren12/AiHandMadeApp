package com.appforcross.editor.filters

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.U8Mask

/** Placeholder halo removal filter. */
object HaloRemoval {
    private const val TAG = "FILTERS"

    /** Applies a halo removal pass with optional edge guidance. */
    fun apply(img: LinearImageF16, edgeMask: U8Mask? = null, strength: Float = 0.8f): LinearImageF16 {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "HaloRemoval.apply",
                "w" to img.width,
                "h" to img.height,
                "halo.strength" to strength,
                "edgeMask.present" to (edgeMask != null)
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
