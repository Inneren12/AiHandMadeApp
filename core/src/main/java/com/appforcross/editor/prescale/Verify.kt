package com.appforcross.editor.prescale

import com.appforcross.editor.logging.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VerifyReport(
    val ssimProxy: Float,
    val edgeKeep: Float,
    val bandIdx: Float,
    val deltaE95Proxy: Float
)

object Verify {
    fun compute(L: FloatArray, W: Int, H: Int): VerifyReport {
        val ed = edgeDensity(L, W, H)
        val band = bandingIndex(L, W, H)
        val ssim = clamp01(0.9f + 0.1f * (1f - band))
        val edgeKeep = clamp01(0.8f + 0.2f * ed / 0.2f)
        val dE95 = max(0f, 5f * band)
        Logger.i(
            "VERIFY",
            "report",
            mapOf(
                "ssimProxy" to "%.4f".format(ssim),
                "edgeKeep" to "%.4f".format(edgeKeep),
                "bandIdx" to "%.4f".format(band),
                "dE95Proxy" to "%.2f".format(dE95)
            )
        )
        return VerifyReport(ssim, edgeKeep, band, dE95)
    }

    private fun edgeDensity(L: FloatArray, w: Int, h: Int): Float {
        fun at(x: Int, y: Int) = L[y * w + x]
        var cnt = 0
        var all = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = (-at(x - 1, y - 1) + at(x + 1, y - 1)) + (-2 * at(x - 1, y) + 2 * at(x + 1, y)) + (-at(x - 1, y + 1) + at(x + 1, y + 1))
                val gy = (-(at(x - 1, y - 1) + 2 * at(x, y - 1) + at(x + 1, y - 1))) + (at(x - 1, y + 1) + 2 * at(x, y + 1) + at(x + 1, y + 1))
                val norm = sqrt(gx * gx + gy * gy) / 4f
                if (norm > 0.12f) cnt++
                all++
            }
        }
        return if (all > 0) cnt.toFloat() / all.toFloat() else 0f
    }

    private fun bandingIndex(L: FloatArray, W: Int, H: Int): Float {
        var flat = 0
        var jump = 0
        var all = 0
        for (y in 0 until H) {
            for (x in 0 until W - 1) {
                val d = abs(L[y * W + x] - L[y * W + x + 1])
                if (d < 0.0025f) flat++ else if (d > 0.08f) jump++
                all++
            }
        }
        if (all == 0) return 0f
        val f = flat.toFloat() / all.toFloat()
        val j = jump.toFloat() / all.toFloat()
        return clamp01(f * (1f - 0.5f * j)) * 0.2f
    }

    private fun clamp01(v: Float): Float = max(0f, min(1f, v))
}
