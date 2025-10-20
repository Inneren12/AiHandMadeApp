package com.appforcross.editor.palette.dither

import com.appforcross.editor.data.BN64Spec
import com.appforcross.editor.data.BlueNoise64
import com.appforcross.editor.logging.Logger
import kotlin.math.abs

/**
 * Ordered dithering backed by a deterministic 64×64 blue-noise rank map.
 * Works on the nearest-colour assignment and probabilistically switches to the 2nd best choice
 * depending on pixel rank and amplitude settings.
 */
object OrderedDither {

    /**
     * @param rgb   Linear RGB 0..1, packed [r,g,b] per pixel, size = W*H*3.
     * @param assign Current palette indices per pixel (W*H).
     * @param paletteRgb Palette RGB entries, packed, len = K*3.
     * @param W Image width.
     * @param H Image height.
     * @param params Dithering parameters (mode, amplitudes, seed, etc.).
     * @return Dithered palette index map (W*H).
     */
    fun apply(
        rgb: FloatArray,
        assign: IntArray,
        paletteRgb: FloatArray,
        W: Int,
        H: Int,
        params: DitherParams = DitherParams()
    ): IntArray {
        require(params.mode == DitherParams.Mode.ORDERED) {
            "OrderedDither supports only ORDERED mode (got ${params.mode})."
        }
        val t0 = System.nanoTime()
        Logger.i(
            "DITHER",
            "params",
            mapOf(
                "dither.mode" to params.mode.name,
                "dither.amp.sky" to params.ampSky,
                "dither.amp.skin" to params.ampSkin,
                "dither.amp.hitex" to params.ampHiTex,
                "dither.amp.flat" to params.ampFlat,
                "dither.amp.edge_falloff" to params.ampEdgeFalloff,
                "dither.diffusion.cap" to params.diffusionCap,
                "blue_noise.seed" to "0x${params.seed.toString(16)}",
                "image.width" to W,
                "image.height" to H,
                "palette.size" to (paletteRgb.size / 3)
            )
        )
        val mask = BlueNoise64.generate(BN64Spec(seed = params.seed))
        val out = assign.clone()
        val amp = params.ampFlat.coerceIn(0f, 1f)
        var p = 0
        for (y in 0 until H) {
            for (x in 0 until W) {
                val idx = y * W + x
                val c0 = out[idx]
                val candidate = secondNearest(rgb, p, paletteRgb, c0)
                if (candidate >= 0) {
                    val d0 = de(rgb, p, paletteRgb, c0)
                    val d1 = de(rgb, p, paletteRgb, candidate)
                    val delta = d1 - d0
                    val rank = (mask[(y % 64) * 64 + (x % 64)].toInt() and 0xFF) / 255f - 0.5f
                    if (delta < amp * rank) out[idx] = candidate
                }
                p += 3
            }
        }
        val ms = ((System.nanoTime() - t0) / 1_000_000).toInt()
        val rt = Runtime.getRuntime()
        val usedMb = ((rt.totalMemory() - rt.freeMemory()) / 1_000_000.0)
        Logger.i(
            "DITHER",
            "done",
            mapOf(
                "ms" to ms,
                "memMB" to "%.2f".format(usedMb)
            )
        )
        return out
    }

    private fun de(rgb: FloatArray, p: Int, pal: FloatArray, k: Int): Float {
        val r = rgb[p]
        val g = rgb[p + 1]
        val b = rgb[p + 2]
        val pk = k * 3
        return abs(r - pal[pk]) + abs(g - pal[pk + 1]) + abs(b - pal[pk + 2])
    }

    private fun secondNearest(rgb: FloatArray, p: Int, pal: FloatArray, exclude: Int): Int {
        var best = -1
        var bestD = Float.POSITIVE_INFINITY
        var second = -1
        var secondD = Float.POSITIVE_INFINITY
        val K = pal.size / 3
        for (k in 0 until K) {
            val d = de(rgb, p, pal, k)
            if (k == exclude) continue
            if (d < bestD) {
                second = best
                secondD = bestD
                best = k
                bestD = d
            } else if (d < secondD) {
                second = k
                secondD = d
            }
        }
        return if (best >= 0) best else second
    }
}
