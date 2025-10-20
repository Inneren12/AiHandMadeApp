package com.appforcross.editor.palette

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RoiMap(
    val edges: FloatArray,
    val hitex: FloatArray,
    val flat: FloatArray
) {
    val size: Int = edges.size
}

object Roi {
    fun computeProxy(rgb: FloatArray, width: Int, height: Int): RoiMap {
        val pixels = width * height
        val luminance = FloatArray(pixels)
        for (i in 0 until pixels) {
            val r = rgb[i * 3 + 0]
            val g = rgb[i * 3 + 1]
            val b = rgb[i * 3 + 2]
            luminance[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        val edge = FloatArray(pixels)
        val tex = FloatArray(pixels)
        val flat = FloatArray(pixels)

        var maxGrad = 1e-6f
        var maxVar = 1e-6f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val xm1 = max(0, x - 1)
                val xp1 = min(width - 1, x + 1)
                val ym1 = max(0, y - 1)
                val yp1 = min(height - 1, y + 1)

                val dx = luminance[y * width + xp1] - luminance[y * width + xm1]
                val dy = luminance[yp1 * width + x] - luminance[ym1 * width + x]
                val grad = sqrt(dx * dx + dy * dy)
                edge[idx] = grad
                if (grad > maxGrad) maxGrad = grad

                var sum = 0f
                var sumSq = 0f
                var count = 0
                for (yy in ym1..yp1) {
                    for (xx in xm1..xp1) {
                        val v = luminance[yy * width + xx]
                        sum += v
                        sumSq += v * v
                        count++
                    }
                }
                val mean = sum / count
                val variance = sumSq / count - mean * mean
                val varClamp = abs(variance)
                tex[idx] = varClamp
                if (varClamp > maxVar) maxVar = varClamp
            }
        }

        for (i in 0 until pixels) {
            val e = edge[i] / maxGrad
            val t = tex[i] / maxVar
            val f = 1f / (1f + e + t)
            edge[i] = e.coerceIn(0f, 1f)
            tex[i] = t.coerceIn(0f, 1f)
            flat[i] = f.coerceIn(0f, 1f)
        }
        return RoiMap(edge, tex, flat)
    }

    fun roiWeightAt(index: Int, roi: RoiMap, weights: ROIWeights): Float {
        val edge = roi.edges[index]
        val tex = roi.hitex[index]
        val flat = roi.flat[index]
        val base = 1f + weights.edges * edge + weights.hitex * tex + weights.flat * flat * -1f
        return max(0.1f, base)
    }
}
