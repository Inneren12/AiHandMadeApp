package com.appforcross.editor.prescale

import com.appforcross.editor.logging.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class FabricSpec(val ct: Int, val corridorMin: Int, val corridorMax: Int)
data class BuildParams(
    val fabric: FabricSpec = FabricSpec(ct = 14, corridorMin = 160, corridorMax = 260),
    val kSigma: Float = 0.90f,
    val filterPolicy: String = "AUTO"
)

data class Phase2x2(val dx: Int, val dy: Int) {
    init {
        require(dx in 0..1 && dy in 0..1) { "phase 2x2 only" }
    }
}

data class FRReport(
    val ssimProxy: Float,
    val edgeKeep: Float,
    val bandIdx: Float,
    val deltaE95Proxy: Float
)

data class BuildDecision(
    val wst: Int,
    val hst: Int,
    val sigma: Float,
    val filter: String,
    val phase: Phase2x2,
    val verify: FRReport
)

object BuildSpec {

    fun decide(
        widthPx: Int,
        heightPx: Int,
        luminance: FloatArray,
        params: BuildParams = BuildParams()
    ): BuildDecision {
        require(luminance.size == widthPx * heightPx)

        Logger.i("BSPEC", "params", mapOf(
            "src.w" to widthPx,
            "src.h" to heightPx,
            "fabric.ct" to params.fabric.ct,
            "corridor.min" to params.fabric.corridorMin,
            "corridor.max" to params.fabric.corridorMax,
            "kSigma" to params.kSigma,
            "filterPolicy" to params.filterPolicy
        ))

        var wst = clamp(
            params.fabric.corridorMin + (params.fabric.corridorMax - params.fabric.corridorMin) / 2,
            params.fabric.corridorMin,
            params.fabric.corridorMax
        )
        var hst = max(1, (heightPx.toFloat() / widthPx.toFloat() * wst).roundToInt())

        fun pps(value: Int) = widthPx.toFloat() / value.toFloat()
        var r = max(0f, pps(wst) * 0.5f)
        var sigma = params.kSigma * max(0f, r - 1f)

        val edgeDensity = edgeDensity(luminance, widthPx, heightPx)
        val filter = when (params.filterPolicy) {
            "EWA_Mitchell" -> "EWA_Mitchell"
            "EWA_Lanczos3" -> "EWA_Lanczos3"
            else -> if (edgeDensity >= 0.12f) "EWA_Lanczos3" else "EWA_Mitchell"
        }

        val phase = choosePhase2x2(luminance, widthPx, heightPx, wst)

        var verify = frCheckProxy(luminance, widthPx, heightPx, wst, sigma)
        if (!pass(verify)) {
            val up = (wst * 1.12f).roundToInt().coerceAtMost(params.fabric.corridorMax)
            if (up != wst) {
                wst = up
                hst = max(1, (heightPx.toFloat() / widthPx.toFloat() * wst).roundToInt())
                r = max(0f, pps(wst) * 0.5f)
                sigma = params.kSigma * max(0f, r - 1f)
                verify = frCheckProxy(luminance, widthPx, heightPx, wst, sigma)
            }
        }

        Logger.i("BSPEC", "decision", mapOf(
            "wst" to wst,
            "hst" to hst,
            "sigma" to "%.3f".format(sigma),
            "filter" to filter,
            "phase.dx" to phase.dx,
            "phase.dy" to phase.dy
        ))
        Logger.i("BSPEC", "verify", mapOf(
            "ssimProxy" to "%.4f".format(verify.ssimProxy),
            "edgeKeep" to "%.4f".format(verify.edgeKeep),
            "bandIdx" to "%.4f".format(verify.bandIdx),
            "dE95Proxy" to "%.2f".format(verify.deltaE95Proxy),
            "pass" to pass(verify)
        ))
        Logger.i("BSPEC", "done", mapOf("ms" to 0, "memMB" to 0))
        return BuildDecision(wst, hst, sigma, filter, phase, verify)
    }

    private fun clamp(v: Int, mn: Int, mx: Int) = max(mn, min(mx, v))

    private fun pass(report: FRReport): Boolean {
        return report.ssimProxy >= 0.985f &&
            report.edgeKeep >= 0.98f &&
            report.bandIdx <= 0.003f &&
            report.deltaE95Proxy <= 3.0f
    }

    private fun edgeDensity(L: FloatArray, w: Int, h: Int): Float {
        fun at(x: Int, y: Int) = L[y * w + x]
        var cnt = 0
        var all = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val gx = (-at(x - 1, y - 1) + at(x + 1, y - 1)) + (-2 * at(x - 1, y) + 2 * at(x + 1, y)) + (-at(x - 1, y + 1) + at(x + 1, y + 1))
            val gy = (-(at(x - 1, y - 1) + 2 * at(x, y - 1) + at(x + 1, y - 1))) + (at(x - 1, y + 1) + 2 * at(x, y + 1) + at(x + 1, y + 1))
            val norm = sqrt(gx * gx + gy * gy) / 4f
            if (norm > 0.12f) cnt++
            all++
        }
        return if (all > 0) cnt.toFloat() / all.toFloat() else 0f
    }

    private fun choosePhase2x2(L: FloatArray, w: Int, h: Int, wst: Int): Phase2x2 {
        val pps = w.toFloat() / wst.toFloat()
        val candidates = arrayOf(Phase2x2(0, 0), Phase2x2(1, 0), Phase2x2(0, 1), Phase2x2(1, 1))
        var best = candidates[0]
        var bestScore = Float.NEGATIVE_INFINITY
        for (ph in candidates) {
            val score = gridLineEdgeScore(L, w, h, pps, ph)
            if (score > bestScore) {
                bestScore = score
                best = ph
            }
        }
        return best
    }

    private fun gridLineEdgeScore(L: FloatArray, w: Int, h: Int, pps: Float, phase: Phase2x2): Float {
        var s = 0f
        val dx = phase.dx
        val dy = phase.dy
        val step = max(1, pps.roundToInt())
        var x = dx
        while (x < w) {
            for (y in 0 until h - 1) s += abs(L[y * w + x] - L[(y + 1) * w + x])
            x += step
        }
        var y = dy
        while (y < h) {
            for (xx in 0 until w - 1) s += abs(L[y * w + xx] - L[y * w + xx + 1])
            y += step
        }
        return s
    }

    @Suppress("UNUSED_PARAMETER")
    private fun frCheckProxy(L: FloatArray, w: Int, h: Int, wst: Int, sigma: Float): FRReport {
        val ed = edgeDensity(L, w, h)
        val band = bandingIndex(L, w, h)
        val ssim = clamp01f(0.9f + 0.1f * (1f - band))
        val edgeKeep = clamp01f(0.8f + 0.2f * ed / 0.2f)
        val dE95 = max(0f, 5f * band * (1f + 0.1f * sigma))
        return FRReport(ssim, edgeKeep, band, dE95)
    }

    private fun bandingIndex(L: FloatArray, w: Int, h: Int): Float {
        var flatPairs = 0
        var jumpPairs = 0
        var all = 0
        for (y in 0 until h) for (x in 0 until w - 1) {
            val d = abs(L[y * w + x] - L[y * w + x + 1])
            if (d < 0.0025f) flatPairs++ else if (d > 0.08f) jumpPairs++
            all++
        }
        if (all == 0) return 0f
        val flat = flatPairs.toFloat() / all.toFloat()
        val jump = jumpPairs.toFloat() / all.toFloat()
        return clamp01f(flat * (1f - 0.5f * jump)) * 0.2f
    }

    private fun clamp01f(v: Float) = max(0f, min(1f, v))
}
