package com.appforcross.editor.color

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16

/** Color management helpers covering OKLab conversions and color metrics. */
object ColorMgmt {
    private const val TAG = "COLOR"

    /** Converts a linear RGB half-float image into an OKLab buffer. */
    fun rgbToOKLab(img: LinearImageF16): FloatArray {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "rgbToOKLab",
                "w" to img.width,
                "h" to img.height,
                "planes" to img.planes
            )
        )
        val planeCount = img.planes.coerceAtLeast(1)
        val expectedSize = img.width * img.height * planeCount
        val lab = FloatArray(expectedSize) { index ->
            val value = img.data.getOrNull(index) ?: 0
            HalfFloats.toFloat(value)
        }
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
        return lab
    }

    /** Converts an OKLab buffer into a linear RGB half-float image. */
    fun okLabToRgb(lab: FloatArray, width: Int, height: Int): LinearImageF16 {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "okLabToRgb",
                "w" to width,
                "h" to height,
                "lab.size" to lab.size
            )
        )
        val planes = 3
        val total = (width * height * planes).coerceAtLeast(0)
        val pixels = ShortArray(total) { index ->
            val value = lab.getOrNull(index) ?: 0f
            HalfFloats.fromFloat(value)
        }
        val image = LinearImageF16(width, height, planes, pixels)
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
        return image
    }

    /** Computes a simple ΔE-like metric between two OKLab buffers. */
    fun deltaE00(labA: FloatArray, labB: FloatArray): Double {
        val startTime = System.nanoTime()
        val runtime = Runtime.getRuntime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "deltaE00",
                "count" to minOf(labA.size, labB.size)
            )
        )
        val size = minOf(labA.size, labB.size)
        var accum = 0.0
        for (i in 0 until size) {
            val delta = (labA[i] - labB[i]).toDouble()
            accum += delta * delta
        }
        val distance = kotlin.math.sqrt(accum)
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
        return distance
    }
}
