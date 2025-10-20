package com.appforcross.editor.prescale

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.color.ColorMgmt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VerifyReport(
    val ssimProxy: Float,
    val edgeKeep: Float,
    val bandIdx: Float,
    val deltaE95Proxy: Float,
    val bandSky: Float = 0f,
    val bandSkin: Float = 0f,
    val deltaE95Skin: Float = 0f
)

object Verify {
    /** Старый компактный вариант (без ссылочного кадра) */
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

    /**
     * Детальная проверка с раздельными метриками по маскам и ΔE2000 на Skin.
     * @param rgbRef  линейный sRGB 0..1 (W*H*3) — эталон (вход).
     * @param rgbProc линейный sRGB 0..1 (W*H*3) — результат (после PreScale).
     * @param W,H     размеры
     * @param maskSky/ maskSkin — BooleanArray (W*H) или null (если нет явных масок).
     */
    fun computeDetailed(
        rgbRef: FloatArray,
        rgbProc: FloatArray,
        W: Int,
        H: Int,
        maskSky: BooleanArray? = null,
        maskSkin: BooleanArray? = null
    ): VerifyReport {
        val Lref = luminance(rgbRef, W, H)
        val Lout = luminance(rgbProc, W, H)
        val edRef = edgeDensity(Lref, W, H)
        val edOut = edgeDensity(Lout, W, H)
        val edgeKeep = if (edRef <= 1e-6f) 1f else clamp01(edOut / edRef)
        val bandAll = bandingIndex(Lout, W, H)
        val bandSky = bandingIndex(Lout, W, H, maskSky)
        val bandSkin = bandingIndex(Lout, W, H, maskSkin)

        val de95Skin = if (maskSkin != null) {
            deltaE95Skin(rgbRef, rgbProc, W, H, maskSkin)
        } else 0f
        val ssim = clamp01(0.9f + 0.1f * (1f - bandAll))

        Logger.i(
            "VERIFY",
            "report+",
            mapOf(
                "ssimProxy" to "%.4f".format(ssim),
                "edgeKeep" to "%.4f".format(edgeKeep),
                "bandAll" to "%.4f".format(bandAll),
                "bandSky" to "%.4f".format(bandSky),
                "bandSkin" to "%.4f".format(bandSkin),
                "dE95Skin" to "%.2f".format(de95Skin)
            )
        )
        return VerifyReport(ssim, edgeKeep, bandAll, max(0f, 5f * bandAll), bandSky, bandSkin, de95Skin)
    }

    private fun luminance(rgb: FloatArray, W: Int, H: Int): FloatArray {
        val L = FloatArray(W * H)
        var p = 0
        for (i in 0 until W * H) {
            val r = rgb[p]
            val g = rgb[p + 1]
            val b = rgb[p + 2]
            L[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            p += 3
        }
        return L
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

    private fun bandingIndex(L: FloatArray, W: Int, H: Int, mask: BooleanArray? = null): Float {
        var flat = 0
        var jump = 0
        var all = 0
        for (y in 0 until H) {
            for (x in 0 until W - 1) {
                val i = y * W + x
                if (mask != null && !mask[i]) continue
                val d = abs(L[i] - L[i + 1])
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

    private fun deltaE95Skin(
        rgbRef: FloatArray,
        rgbProc: FloatArray,
        W: Int,
        H: Int,
        maskSkin: BooleanArray
    ): Float {
        val vals = ArrayList<Double>(1024)
        var p = 0
        for (i in 0 until W * H) {
            if (maskSkin[i]) {
                val L1 = ColorMgmt.linearRgbToLab(rgbRef[p], rgbRef[p + 1], rgbRef[p + 2])
                val L2 = ColorMgmt.linearRgbToLab(rgbProc[p], rgbProc[p + 1], rgbProc[p + 2])
                vals.add(ColorMgmt.deltaE00(L1, L2))
            }
            p += 3
        }
        if (vals.isEmpty()) return 0f
        vals.sort()
        val k = ((vals.size - 1) * 0.95).toInt().coerceIn(0, vals.size - 1)
        return vals[k].toFloat()
    }
}
