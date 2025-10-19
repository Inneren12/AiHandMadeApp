package com.appforcross.editor.scene

import com.appforcross.editor.logging.Logger
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Анализатор сцены по превью RGB (линейный, row-major).
 */
object SceneAnalyzer {
    private const val TAG = "SCENE"

    data class SceneParams(
        val maxPreview: Int = 1024,
        val sobelThresh: Float = 0.12f,
        val paletteLevels: Int = 32,
        val topK: Int = 8,
        val checkerWindow: Int = 2
    )

    data class SceneFeatures(
        val width: Int,
        val height: Int,
        val colors5bit: Int,
        val top8Coverage: Float,
        val edgeDensity: Float,
        val checker2x2: Float,
        val entropy: Float
    )

    data class SceneDecision(
        val kind: SceneKind,
        val confidence: Float,
        val features: SceneFeatures
    )

    /**
     * Анализирует превью RGB (линейный, row-major), 3 компоненты на пиксель.
     * @param rgbLinear Значения в диапазоне 0..1, длина массива = width * height * 3.
     */
    fun analyzePreview(
        rgbLinear: FloatArray,
        width: Int,
        height: Int,
        params: SceneParams = SceneParams()
    ): SceneDecision {
        require(rgbLinear.size == width * height * 3) { "rgbLinear size mismatch" }
        val startNs = System.nanoTime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "width" to width,
                "height" to height,
                "maxPreview" to params.maxPreview,
                "sobelThresh" to params.sobelThresh,
                "paletteLevels" to params.paletteLevels,
                "topK" to params.topK,
                "checkerWindow" to params.checkerWindow
            )
        )
        val features = computeFeaturesInternal(rgbLinear, width, height, params)
        Logger.i(
            TAG,
            "features",
            mapOf(
                "colors5bit" to features.colors5bit,
                "top8Coverage" to features.top8Coverage,
                "edgeDensity" to features.edgeDensity,
                "checker2x2" to features.checker2x2,
                "entropy" to features.entropy
            )
        )
        val decision = decide(features)
        Logger.i(
            TAG,
            "decision",
            mapOf(
                "kind" to decision.kind.name,
                "confidence" to decision.confidence
            )
        )
        val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        Logger.i(
            TAG,
            "done",
            mapOf(
                "ms" to String.format(Locale.US, "%.3f", durationMs),
                "memMB" to usedMb
            )
        )
        return decision
    }

    /**
     * Вычисляет признаки сцены.
     */
    fun computeFeatures(
        rgbLinear: FloatArray,
        width: Int,
        height: Int,
        params: SceneParams = SceneParams()
    ): SceneFeatures {
        require(rgbLinear.size == width * height * 3) { "rgbLinear size mismatch" }
        val startNs = System.nanoTime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "width" to width,
                "height" to height,
                "maxPreview" to params.maxPreview,
                "sobelThresh" to params.sobelThresh,
                "paletteLevels" to params.paletteLevels,
                "topK" to params.topK,
                "checkerWindow" to params.checkerWindow
            )
        )
        val features = computeFeaturesInternal(rgbLinear, width, height, params)
        Logger.i(
            TAG,
            "features",
            mapOf(
                "colors5bit" to features.colors5bit,
                "top8Coverage" to features.top8Coverage,
                "edgeDensity" to features.edgeDensity,
                "checker2x2" to features.checker2x2,
                "entropy" to features.entropy
            )
        )
        val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        Logger.i(
            TAG,
            "done",
            mapOf(
                "ms" to String.format(Locale.US, "%.3f", durationMs),
                "memMB" to usedMb
            )
        )
        return features
    }

    private fun computeFeaturesInternal(
        rgbLinear: FloatArray,
        width: Int,
        height: Int,
        params: SceneParams
    ): SceneFeatures {
        val levels = max(2, params.paletteLevels)
        val levelFactor = levels.toFloat()
        val totalPixels = width * height
        val colorCounts = IntArray(levels * levels * levels)
        val luma = FloatArray(totalPixels)

        var idx = 0
        var lIndex = 0
        while (idx < rgbLinear.size) {
            val r = clamp01(rgbLinear[idx])
            val g = clamp01(rgbLinear[idx + 1])
            val b = clamp01(rgbLinear[idx + 2])
            val lr = floor(r * levelFactor).toInt().coerceIn(0, levels - 1)
            val lg = floor(g * levelFactor).toInt().coerceIn(0, levels - 1)
            val lb = floor(b * levelFactor).toInt().coerceIn(0, levels - 1)
            val key = (lr shl 10) or (lg shl 5) or lb
            colorCounts[key] = colorCounts[key] + 1
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            luma[lIndex] = y
            idx += 3
            lIndex += 1
        }

        var uniqueColors = 0
        for (count in colorCounts) {
            if (count > 0) uniqueColors += 1
        }

        val topK = min(params.topK, colorCounts.size)
        val topCounts = IntArray(topK) { 0 }
        for (count in colorCounts) {
            if (count == 0) continue
            var pos = topK - 1
            if (count <= topCounts[pos]) continue
            while (pos > 0 && count > topCounts[pos - 1]) {
                topCounts[pos] = topCounts[pos - 1]
                pos -= 1
            }
            topCounts[pos] = count
        }
        var covered = 0
        for (i in 0 until topK) {
            covered += topCounts[i]
        }
        val coverage = if (totalPixels == 0) 0f else covered.toFloat() / totalPixels

        val edgeDensity = computeEdgeDensity(luma, width, height, params.sobelThresh)
        val checker = computeCheckerRatio(luma, width, height, params.checkerWindow)
        val entropy = computeEntropy(luma)

        return SceneFeatures(
            width = width,
            height = height,
            colors5bit = uniqueColors,
            top8Coverage = coverage,
            edgeDensity = edgeDensity,
            checker2x2 = checker,
            entropy = entropy
        )
    }

    private fun decide(features: SceneFeatures): SceneDecision {
        val isDiscrete = when {
            features.top8Coverage >= 0.70f && features.colors5bit <= 64 -> true
            features.checker2x2 >= 0.30f && features.edgeDensity >= 0.08f -> true
            else -> false
        }
        val score = 8f * (features.top8Coverage - 0.70f) +
            6f * (features.checker2x2 - 0.30f) +
            (-4f) * (0.08f - features.edgeDensity)
        val probability = sigmoid(score)
        val confidence = if (isDiscrete) probability else 1f - probability
        val kind = if (isDiscrete) SceneKind.DISCRETE else SceneKind.PHOTO
        return SceneDecision(kind = kind, confidence = confidence.coerceIn(0f, 1f), features = features)
    }

    private fun sigmoid(x: Float): Float {
        val clamped = x.coerceIn(-20f, 20f)
        return (1f / (1f + exp(-clamped)))
    }

    private fun clamp01(v: Float): Float = when {
        v < 0f -> 0f
        v > 1f -> 1f
        else -> v
    }

    private fun computeEdgeDensity(
        luma: FloatArray,
        width: Int,
        height: Int,
        sobelThresh: Float
    ): Float {
        if (width < 3 || height < 3) return 0f
        var edges = 0
        var samples = 0
        val norm = 4f
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val idx = row + x
                val gx = (-1f * luma[idx - width - 1]) + (1f * luma[idx - width + 1]) +
                    (-2f * luma[idx - 1]) + (2f * luma[idx + 1]) +
                    (-1f * luma[idx + width - 1]) + (1f * luma[idx + width + 1])
                val gy = (-1f * luma[idx - width - 1]) + (-2f * luma[idx - width]) + (-1f * luma[idx - width + 1]) +
                    (1f * luma[idx + width - 1]) + (2f * luma[idx + width]) + (1f * luma[idx + width + 1])
                val magnitude = sqrt(gx * gx + gy * gy) / norm
                if (magnitude > sobelThresh) {
                    edges += 1
                }
                samples += 1
            }
        }
        return if (samples == 0) 0f else edges.toFloat() / samples
    }

    private fun computeCheckerRatio(
        luma: FloatArray,
        width: Int,
        height: Int,
        window: Int
    ): Float {
        if (width < window || height < window || window < 2) return 0f
        var checkerWindows = 0
        var totalWindows = 0
        val maxX = width - window + 1
        val maxY = height - window + 1
        for (y in 0 until maxY) {
            for (x in 0 until maxX) {
                val a = luma[y * width + x]
                val b = luma[y * width + x + 1]
                val c = luma[(y + 1) * width + x]
                val d = luma[(y + 1) * width + x + 1]
                val avgDiag1 = (a + d) * 0.5f
                val avgDiag2 = (b + c) * 0.5f
                val diffDiag = abs(avgDiag1 - avgDiag2)
                val diffOpposite = max(abs(a - d), abs(b - c))
                val diffAdjacent = max(
                    abs(a - b),
                    max(abs(b - d), max(abs(c - d), abs(a - c)))
                )
                if (diffDiag > 0.1f && diffOpposite < 0.08f && diffAdjacent > 0.12f) {
                    checkerWindows += 1
                }
                totalWindows += 1
            }
        }
        return if (totalWindows == 0) 0f else checkerWindows.toFloat() / totalWindows
    }

    private fun computeEntropy(luma: FloatArray): Float {
        if (luma.isEmpty()) return 0f
        val bins = IntArray(256)
        for (value in luma) {
            val bin = floor(clamp01(value) * 255f + 0.5f).toInt().coerceIn(0, 255)
            bins[bin] = bins[bin] + 1
        }
        val total = luma.size.toFloat()
        var entropy = 0.0
        for (count in bins) {
            if (count == 0) continue
            val p = count / total
            entropy -= p * (ln(p.toDouble()) / ln(2.0))
        }
        return entropy.toFloat()
    }
}
