package com.appforcross.editor.palette.dither

import com.appforcross.editor.data.BN64Spec
import com.appforcross.editor.data.BlueNoise64
import kotlin.math.abs

/**
 * Ordered dithering backed by a deterministic 64×64 blue-noise rank map and ROI-aware amplitudes.
 */
internal object OrderedDither {

    fun apply(
        rgb: FloatArray,
        assign: IntArray,
        paletteRgb: FloatArray,
        W: Int,
        H: Int,
        params: DitherParams,
        context: DitherContext
    ): IntArray {
        require(params.mode == DitherParams.Mode.ORDERED) {
            "OrderedDither supports only ORDERED mode (got ${params.mode})."
        }
        val mask = BlueNoise64.generate(BN64Spec(seed = params.seed))
        val out = assign.clone()
        var p = 0
        for (y in 0 until H) {
            for (x in 0 until W) {
                val idx = y * W + x
                val current = out[idx]
                val alternative = secondNearest(rgb, p, paletteRgb, current)
                if (alternative >= 0 && alternative != current) {
                    val errCurrent = error(rgb, p, paletteRgb, current)
                    val errAlt = error(rgb, p, paletteRgb, alternative)
                    val delta = errAlt - errCurrent
                    if (delta <= 0f) {
                        out[idx] = alternative
                    } else {
                        val rank = (mask[((y and 63) shl 6) + (x and 63)].toInt() and 0xFF) / 255f
                        val jitter = ((rank - 0.5f) * 2f) * context.amplitude[idx]
                        val norm = if (errCurrent > 1e-6f) delta / (errCurrent + 1e-6f) else delta
                        if (norm < jitter) {
                            out[idx] = alternative
                        }
                    }
                }
                p += 3
            }
        }
        return out
    }

    private fun error(rgb: FloatArray, p: Int, palette: FloatArray, k: Int): Float {
        val r = rgb[p]
        val g = rgb[p + 1]
        val b = rgb[p + 2]
        val pk = k * 3
        val dr = r - palette[pk]
        val dg = g - palette[pk + 1]
        val db = b - palette[pk + 2]
        return abs(dr) + abs(dg) + abs(db)
    }

    private fun secondNearest(rgb: FloatArray, p: Int, palette: FloatArray, exclude: Int): Int {
        var best = -1
        var bestErr = Float.POSITIVE_INFINITY
        val K = palette.size / 3
        for (k in 0 until K) {
            if (k == exclude) continue
            val err = error(rgb, p, palette, k)
            if (err < bestErr - 1e-6f || (abs(err - bestErr) <= 1e-6f && k < best)) {
                bestErr = err
                best = k
            }
        }
        return best
    }
}
