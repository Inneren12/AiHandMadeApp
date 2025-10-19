package com.appforcross.editor.scene

import com.appforcross.editor.logging.Logger
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "SCENE"

/** Parameters controlling scene analysis. */
data class SceneParams(
    val maxPreview: Int = 1024,
    val sobelThresh: Float = 0.12f,
    val paletteLevels: Int = 32,
    val topK: Int = 8,
    val checkerWindow: Int = 2
)

/** Features computed from a preview buffer. */
data class SceneFeatures(
    val width: Int,
    val height: Int,
    val colors5bit: Int,
    val top8Coverage: Float,
    val edgeDensity: Float,
    val checker2x2: Float,
    val entropy: Float
)

/** Final decision produced by the analyzer. */
data class SceneDecision(
    val kind: SceneKind,
    val confidence: Float,
    val features: SceneFeatures
)

object SceneAnalyzer {

    fun analyzePreview(
        rgbLinear: FloatArray,
        width: Int,
        height: Int,
        params: SceneParams = SceneParams()
    ): SceneDecision {
        require(rgbLinear.size == width * height * 3) { "rgbLinear length mismatch" }
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
        val features = computeFeaturesInternal(rgbLinear, width, height, params, logTelemetry = false)
        logFeatures(features)

        val isPaletteDiscrete = features.top8Coverage >= 0.70f && features.colors5bit <= 64
        val isCheckerDiscrete = features.checker2x2 >= 0.30f && features.edgeDensity >= 0.08f
        val kind = if (isPaletteDiscrete || isCheckerDiscrete) SceneKind.DISCRETE else SceneKind.PHOTO

        val scoreDiscrete =
            max(0f, features.top8Coverage - 0.70f) * 3.0f +
                max(0f, features.checker2x2 - 0.30f) * 2.0f +
                max(0f, features.edgeDensity - 0.08f) * 1.5f -
                max(0f, (features.colors5bit - 64).toFloat()) * 0.0f
        val confDiscrete = sigma(5.0f * scoreDiscrete)
        val confidence = if (kind == SceneKind.DISCRETE) confDiscrete else 1f - confDiscrete

        Logger.i(
            TAG,
            "decision",
            mapOf(
                "kind" to kind.name,
                "confidence" to confidence
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
        return SceneDecision(kind, confidence, features)
    }

    fun computeFeatures(
        rgbLinear: FloatArray,
        width: Int,
        height: Int,
        params: SceneParams = SceneParams()
    ): SceneFeatures {
        return computeFeaturesInternal(rgbLinear, width, height, params, logTelemetry = true)
    }

    private fun computeFeaturesInternal(
        rgbLinear: FloatArray,
        width: Int,
        height: Int,
        params: SceneParams,
        logTelemetry: Boolean
    ): SceneFeatures {
        require(rgbLinear.size == width * height * 3) { "rgbLinear length mismatch" }
        val startNs = if (logTelemetry) System.nanoTime() else 0L
        if (logTelemetry) {
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
        }

        val totalPixels = width * height
        val levels = max(2, params.paletteLevels)
        val hist = IntArray(levels * levels * levels)
        val luma = FloatArray(totalPixels)
        var px = 0
        var idx = 0
        while (px < totalPixels) {
            val r = clamp01(rgbLinear[idx])
            val g = clamp01(rgbLinear[idx + 1])
            val b = clamp01(rgbLinear[idx + 2])
            val r5 = (r * (levels - 1)).toInt().coerceIn(0, levels - 1)
            val g5 = (g * (levels - 1)).toInt().coerceIn(0, levels - 1)
            val b5 = (b * (levels - 1)).toInt().coerceIn(0, levels - 1)
            val histIndex = (r5 * levels + g5) * levels + b5
            hist[histIndex]++
            luma[px] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            idx += 3
            px += 1
        }

        var distinct = 0
        for (count in hist) {
            if (count > 0) distinct++
        }

        val top = hist.clone().sortedArrayDescending()
        val limit = min(params.topK, top.size)
        var covered = 0
        for (i in 0 until limit) {
            covered += top[i]
        }
        val topCoverage = if (totalPixels == 0) 0f else covered.toFloat() / totalPixels.toFloat()

        val edgeDensity = computeEdgeDensity(luma, width, height, params.sobelThresh)
        val checker2x2 = computeCheckerFraction(luma, width, height, params.checkerWindow)
        val entropy = computeEntropy(luma)

        val features = SceneFeatures(
            width = width,
            height = height,
            colors5bit = distinct,
            top8Coverage = topCoverage,
            edgeDensity = edgeDensity,
            checker2x2 = checker2x2,
            entropy = entropy
        )

        if (logTelemetry) {
            logFeatures(features)
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
        }

        return features
    }

    private fun logFeatures(features: SceneFeatures) {
        Logger.i(
            TAG,
            "features",
            mapOf(
                "colors5bit" to features.colors5bit,
                "top8Coverage" to String.format(Locale.US, "%.4f", features.top8Coverage),
                "edgeDensity" to String.format(Locale.US, "%.4f", features.edgeDensity),
                "checker2x2" to String.format(Locale.US, "%.4f", features.checker2x2),
                "entropy" to String.format(Locale.US, "%.3f", features.entropy)
            )
        )
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
        val w = width
        val h = height
        var y = 1
        while (y < h - 1) {
            var x = 1
            while (x < w - 1) {
                val gx =
                    -1f * luma[(y - 1) * w + (x - 1)] + 1f * luma[(y - 1) * w + (x + 1)] +
                        -2f * luma[y * w + (x - 1)] + 2f * luma[y * w + (x + 1)] +
                        -1f * luma[(y + 1) * w + (x - 1)] + 1f * luma[(y + 1) * w + (x + 1)]
                val gy =
                    -1f * luma[(y - 1) * w + (x - 1)] - 2f * luma[(y - 1) * w + x] - 1f * luma[(y - 1) * w + (x + 1)] +
                        1f * luma[(y + 1) * w + (x - 1)] + 2f * luma[(y + 1) * w + x] + 1f * luma[(y + 1) * w + (x + 1)]
                val magnitude = sqrt(gx * gx + gy * gy) / 4f
                if (magnitude > sobelThresh) {
                    edges++
                }
                samples++
                x++
            }
            y++
        }
        return if (samples == 0) 0f else edges.toFloat() / samples.toFloat()
    }

    private fun computeCheckerFraction(
        luma: FloatArray,
        width: Int,
        height: Int,
        windowSize: Int
    ): Float {
        val window = max(2, windowSize)
        if (width < window || height < window) return 0f
        var checkerWindows = 0
        var totalWindows = 0
        val maxX = width - window
        val maxY = height - window
        var y = 0
        while (y <= maxY) {
            var x = 0
            while (x <= maxX) {
                val a = luma[y * width + x]
                val b = luma[y * width + (x + window - 1)]
                val c = luma[(y + window - 1) * width + x]
                val d = luma[(y + window - 1) * width + (x + window - 1)]
                val s = (a - b) - (c - d)
                val magnitude = abs(s) / 2f
                if (magnitude > 0.6f) {
                    checkerWindows++
                }
                totalWindows++
                x++
            }
            y++
        }
        return if (totalWindows == 0) 0f else checkerWindows.toFloat() / totalWindows.toFloat()
    }

    private fun computeEntropy(luma: FloatArray): Float {
        if (luma.isEmpty()) return 0f
        val hist = IntArray(256)
        for (value in luma) {
            val bin = min(255, max(0, (value * 255f).toInt()))
            hist[bin]++
        }
        val total = luma.size.toDouble()
        var entropy = 0.0
        for (count in hist) {
            if (count > 0) {
                val p = count / total
                entropy += -p * (ln(p) / ln(2.0))
            }
        }
        return entropy.toFloat()
    }

    private fun clamp01(v: Float): Float = when {
        v < 0f -> 0f
        v > 1f -> 1f
        else -> v
    }

    private fun sigma(x: Float): Float {
        val clamped = x.coerceIn(-20f, 20f)
        return 1f / (1f + exp(-clamped))
    }
}
