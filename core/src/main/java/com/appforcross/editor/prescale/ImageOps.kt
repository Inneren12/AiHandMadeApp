package com.appforcross.editor.prescale

import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.max
import kotlin.math.min

/** Базовые операции над 16F-тайлами (v1, детерминированные заглушки). */
object ImageOps {
    fun extractFloatRGB(src: LinearImageF16, x: Int, y: Int, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h * 3)
        var p = 0
        for (yy in 0 until h) {
            val gy = y + yy
            for (xx in 0 until w) {
                val gx = x + xx
                val gi = (gy * src.width + gx) * 3
                out[p++] = HalfFloats.toFloat(src.data[gi])
                out[p++] = HalfFloats.toFloat(src.data[gi + 1])
                out[p++] = HalfFloats.toFloat(src.data[gi + 2])
            }
        }
        return out
    }

    fun packToF16(rgb: FloatArray, W: Int, H: Int): LinearImageF16 {
        val out = ShortArray(W * H * 3)
        var i = 0
        while (i < out.size) {
            out[i] = HalfFloats.fromFloat(rgb[i])
            i++
        }
        return LinearImageF16(W, H, 3, out)
    }

    fun luminance(rgb: FloatArray, W: Int, H: Int): FloatArray {
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

    @Suppress("UNUSED_PARAMETER")
    fun whiteBalanceNeutral(rgb: FloatArray, W: Int, H: Int): FloatArray = rgb

    fun nrLumaGuided(rgb: FloatArray, W: Int, H: Int, strength: Float): FloatArray {
        val rad = (strength * 2f).toInt().coerceAtLeast(1)
        return boxBlur(rgb, W, H, rad)
    }

    @Suppress("UNUSED_PARAMETER")
    fun nrChromaBox(rgb: FloatArray, W: Int, H: Int, strength: Float): FloatArray = rgb

    @Suppress("UNUSED_PARAMETER")
    fun antiSandMedian3(rgb: FloatArray, W: Int, H: Int): FloatArray = rgb

    @Suppress("UNUSED_PARAMETER")
    fun unifySoft(rgb: FloatArray, W: Int, H: Int): FloatArray = rgb

    @Suppress("UNUSED_PARAMETER")
    fun haloSuppress(rgb: FloatArray, W: Int, H: Int, k: Float): FloatArray = rgb

    @Suppress("UNUSED_PARAMETER")
    fun anisoAA(unify: FloatArray, w: Int, h: Int, sigma: Float): FloatArray = unify

    @Suppress("UNUSED_PARAMETER")
    fun ewaResample(rgb: FloatArray, W: Int, H: Int, filter: String, phase: Phase2x2): FloatArray = rgb

    fun normalizeByWeight(dst: FloatArray, wsum: FloatArray, W: Int, H: Int): FloatArray {
        val out = FloatArray(W * H * 3)
        var p = 0
        for (i in 0 until W * H) {
            val w = max(1e-6f, wsum[i])
            out[p] = dst[p] / w
            out[p + 1] = dst[p + 1] / w
            out[p + 2] = dst[p + 2] / w
            p += 3
        }
        return out
    }

    private fun boxBlur(rgb: FloatArray, W: Int, H: Int, rad: Int): FloatArray {
        if (rad <= 0) return rgb
        val out = FloatArray(rgb.size)
        val tmp = FloatArray(rgb.size)
        for (y in 0 until H) {
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f
            var count = 0
            fun add(x: Int) {
                val i = (y * W + x) * 3
                sumR += rgb[i]
                sumG += rgb[i + 1]
                sumB += rgb[i + 2]
                count++
            }
            fun rem(x: Int) {
                val i = (y * W + x) * 3
                sumR -= rgb[i]
                sumG -= rgb[i + 1]
                sumB -= rgb[i + 2]
                count--
            }
            for (x in 0 until W) {
                val x0 = max(0, x - rad)
                val x1 = min(W - 1, x + rad)
                if (x == 0) {
                    sumR = 0f
                    sumG = 0f
                    sumB = 0f
                    count = 0
                    var xx = x0
                    while (xx <= x1) {
                        add(xx)
                        xx++
                    }
                } else {
                    val prev = x - 1 - rad
                    if (prev >= 0) rem(prev)
                    val next = x + rad
                    if (next < W) add(next)
                }
                val idx = (y * W + x) * 3
                val inv = 1f / count.toFloat()
                tmp[idx] = sumR * inv
                tmp[idx + 1] = sumG * inv
                tmp[idx + 2] = sumB * inv
            }
        }
        for (x in 0 until W) {
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f
            var count = 0
            fun add(y: Int) {
                val i = (y * W + x) * 3
                sumR += tmp[i]
                sumG += tmp[i + 1]
                sumB += tmp[i + 2]
                count++
            }
            fun rem(y: Int) {
                val i = (y * W + x) * 3
                sumR -= tmp[i]
                sumG -= tmp[i + 1]
                sumB -= tmp[i + 2]
                count--
            }
            for (y in 0 until H) {
                val y0 = max(0, y - rad)
                val y1 = min(H - 1, y + rad)
                if (y == 0) {
                    sumR = 0f
                    sumG = 0f
                    sumB = 0f
                    count = 0
                    var yy = y0
                    while (yy <= y1) {
                        add(yy)
                        yy++
                    }
                } else {
                    val prev = y - 1 - rad
                    if (prev >= 0) rem(prev)
                    val next = y + rad
                    if (next < H) add(next)
                }
                val idx = (y * W + x) * 3
                val inv = 1f / count.toFloat()
                out[idx] = sumR * inv
                out[idx + 1] = sumG * inv
                out[idx + 2] = sumB * inv
            }
        }
        return out
    }
}
