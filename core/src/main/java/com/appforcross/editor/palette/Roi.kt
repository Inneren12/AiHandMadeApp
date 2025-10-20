package com.appforcross.editor.palette

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RoiMap(
    val edges: FloatArray,
    val skin: FloatArray,
    val sky: FloatArray,
    val hitex: FloatArray,
    val flat: FloatArray
) {
    val size: Int = edges.size
}

data class RoiGrowth(
    val weight: Float,
    val priority: Int
)

object Roi {
    private const val EPS = 1e-6f

    fun computeProxy(lab: FloatArray, width: Int, height: Int): RoiMap {
        val pixels = width * height
        val luminance = FloatArray(pixels)
        for (i in 0 until pixels) {
            luminance[i] = lab[i * 3 + 0]
        }

        val edge = FloatArray(pixels)
        val tex = FloatArray(pixels)
        val skin = FloatArray(pixels)
        val sky = FloatArray(pixels)
        val flat = FloatArray(pixels)

        var maxGrad = EPS
        var maxVar = EPS
        var maxSkin = EPS
        var maxSky = EPS
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

                val l = lab[idx * 3 + 0]
                val a = lab[idx * 3 + 1]
                val b = lab[idx * 3 + 2]
                val skinHue = ((a - 0.10f) / 0.25f).let { it * it } + ((b - 0.05f) / 0.30f).let { it * it }
                val skinLight = ((l - 0.62f) / 0.20f).let { it * it }
                val skinScore = exp(-(skinHue + skinLight)).toFloat()
                skin[idx] = skinScore
                if (skinScore > maxSkin) maxSkin = skinScore

                val skyHue = ((a + 0.02f) / 0.28f).let { it * it } + ((b + 0.35f) / 0.28f).let { it * it }
                val skyLight = ((l - 0.78f) / 0.22f).let { it * it }
                val skyScore = exp(-(skyHue + skyLight)).toFloat()
                sky[idx] = skyScore
                if (skyScore > maxSky) maxSky = skyScore
            }
        }

        for (i in 0 until pixels) {
            val e = (edge[i] / maxGrad).coerceIn(0f, 1f)
            val t = (tex[i] / maxVar).coerceIn(0f, 1f)
            val s = (skin[i] / maxSkin).coerceIn(0f, 1f)
            val skyScore = (sky[i] / maxSky).coerceIn(0f, 1f)
            val f = (1f - (0.55f * e + 0.35f * t)).coerceIn(0f, 1f)
            edge[i] = e
            tex[i] = t
            skin[i] = s
            sky[i] = skyScore
            flat[i] = f
        }
        return RoiMap(edge, skin, sky, tex, flat)
    }

    fun roiGrowthAt(index: Int, roi: RoiMap, weights: ROIWeights): RoiGrowth {
        val edge = roi.edges[index]
        val hiTex = roi.hitex[index]
        val flat = roi.flat[index]
        val skinScoreRaw = roi.skin[index]
        val skyScoreRaw = roi.sky[index]

        val gradientFallback = 0.5f * (edge + hiTex)
        val varianceFallback = 0.5f * (1f - flat)

        val skinScore = if (skinScoreRaw > 1e-3f) skinScoreRaw else max(gradientFallback, 0.25f * (edge + varianceFallback))
        val skyScore = if (skyScoreRaw > 1e-3f) skyScoreRaw else max(varianceFallback, 0.25f * (1f - hiTex))

        val categories = floatArrayOf(edge, skinScore, skyScore, hiTex, flat)
        var bestCategory = 0
        var bestValue = -Float.MAX_VALUE
        for (i in categories.indices) {
            val candidate = categories[i]
            if (candidate > bestValue + 1e-6f) {
                bestValue = candidate
                bestCategory = i
            } else if (abs(candidate - bestValue) <= 1e-6f && i < bestCategory) {
                bestCategory = i
            }
        }

        val baseWeight = 1f +
            weights.edges * edge +
            weights.skin * skinScore +
            weights.sky * skyScore +
            weights.hitex * hiTex -
            weights.flat * flat
        val weight = baseWeight.coerceAtLeast(0.1f)
        return RoiGrowth(weight, bestCategory)
    }
}
