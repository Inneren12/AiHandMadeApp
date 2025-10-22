package com.appforcross.editor.filters.discrete

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.U8Mask

internal class SauvolaWolfBinarizer(private val config: BinarizationConfig) {

    private var padded = FloatArray(0)
    private var integral = FloatArray(0)
    private var integralSq = FloatArray(0)
    private var smoothScratch = ByteArray(0)
    private var medianWindow = FloatArray(9)

    fun apply(image: LinearImageF16, scratch: FloatArray, buffer: ByteArray): U8Mask {
        Logger.i(
            TAG,
            "params",
            mapOf(
                "stage" to "binarize",
                "disc.enabled" to config.enabled,
                "disc.bin.enabled" to config.enabled,
                "disc.bin.algorithm" to config.algorithm.name.lowercase(),
                "disc.bin.w" to config.wBin,
                "disc.bin.k" to "%.3f".format(config.k),
                "disc.bin.smooth" to config.smoothing.name.lowercase(),
            ),
        )
        val start = System.nanoTime()
        require(image.planes >= 1) { "Binarizer expects at least one plane" }
        require(config.wBin >= 3 && config.wBin % 2 == 1) { "Window size must be odd and >=3" }
        val width = image.width
        val height = image.height
        val planeOffset = width * height
        if (!config.enabled) {
            buffer.fill(0)
            Logger.i(TAG, "done", mapOf("stage" to "binarize", "ms" to elapsedMs(start), "memMB" to 0))
            return U8Mask(width, height, buffer.copyOf())
        }

        if (scratch.size < planeOffset) {
            throw IllegalArgumentException("Scratch buffer too small for binarizer")
        }
        for (i in 0 until planeOffset) {
            scratch[i] = HalfFloats.toFloat(image.data[i])
        }

        val radius = max(1, config.wBin / 2)
        val padW = width + radius * 2
        val padH = height + radius * 2
        ensurePaddedCapacity(padW * padH)
        ensureIntegralCapacity((padW + 1) * (padH + 1))
        reflectPad(scratch, width, height, radius, padW, padH)
        buildIntegral(padW, padH)

        var globalMin = Float.POSITIVE_INFINITY
        var globalMax = Float.NEGATIVE_INFINITY
        var globalMean = 0f
        for (i in 0 until planeOffset) {
            val v = scratch[i]
            globalMin = min(globalMin, v)
            globalMax = max(globalMax, v)
            globalMean += v
        }
        globalMean /= max(1, planeOffset)
        var globalVariance = 0f
        for (i in 0 until planeOffset) {
            val d = scratch[i] - globalMean
            globalVariance += d * d
        }
        val globalStd = sqrt(max(globalVariance / max(1, planeOffset - 1), 1e-6f))

        val maskData = buffer
        val area = config.wBin * config.wBin
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sum = regionSum(integral, x, y, radius, padW)
                val sumSq = regionSum(integralSq, x, y, radius, padW)
                val mean = sum / area
                val variance = max(sumSq / area - mean * mean, 0f)
                val std = sqrt(variance)
                val value = scratch[y * width + x]
                val threshold = when (config.algorithm) {
                    Algorithm.SAUVOLA -> sauvola(mean, std)
                    Algorithm.WOLF -> wolf(mean, std, value, globalMin, globalMax, globalMean, globalStd)
                }
                maskData[y * width + x] = if (value >= threshold) 1 else 0
            }
        }

        when (config.smoothing) {
            Smoothing.BOX3 -> smooth3x3(maskData, width, height)
            Smoothing.MEDIAN3 -> median3x3(maskData, width, height)
            Smoothing.NONE -> Unit
        }

        val memBytes = ((padded.size + integral.size + integralSq.size + planeOffset).toLong() * 4L) +
            (maskData.size + smoothScratch.size).toLong()
        val memMB = memBytes / 1_048_576.0
        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "binarize",
                "ms" to elapsedMs(start),
                "memMB" to "%.3f".format(memMB),
            ),
        )

        return U8Mask(width, height, maskData.copyOf())
    }

    private fun sauvola(mean: Float, std: Float): Float {
        val ratio = std / R
        return mean * (1f + config.k * (ratio - 1f))
    }

    private fun wolf(
        mean: Float,
        std: Float,
        value: Float,
        globalMin: Float,
        globalMax: Float,
        globalMean: Float,
        globalStd: Float,
    ): Float {
        val spread = max(globalMax - globalMin, 1e-3f)
        val localContrast = if (globalStd > 0f) std / globalStd else 0f
        val normalizedMean = (mean - globalMin) / spread
        val adaptive = mean + config.k * localContrast * (value - mean)
        val bias = config.k * (normalizedMean - 0.5f) * (mean - globalMin)
        return (adaptive + bias).coerceIn(globalMin, globalMax)
    }

    private fun ensurePaddedCapacity(size: Int) {
        if (padded.size < size) padded = FloatArray(size)
    }

    private fun ensureIntegralCapacity(size: Int) {
        if (integral.size < size) integral = FloatArray(size)
        if (integralSq.size < size) integralSq = FloatArray(size)
        integral.fill(0f)
        integralSq.fill(0f)
    }

    private fun reflectPad(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        padW: Int,
        padH: Int,
    ) {
        for (y in 0 until padH) {
            val srcY = reflectIndex(y - radius, height)
            for (x in 0 until padW) {
                val srcX = reflectIndex(x - radius, width)
                padded[y * padW + x] = src[srcY * width + srcX]
            }
        }
    }

    private fun buildIntegral(width: Int, height: Int) {
        val rowStride = width + 1
        for (y in 1..height) {
            var sumRow = 0f
            var sumRowSq = 0f
            for (x in 1..width) {
                val value = padded[(y - 1) * width + (x - 1)]
                sumRow += value
                sumRowSq += value * value
                val index = y * rowStride + x
                integral[index] = integral[index - rowStride] + sumRow
                integralSq[index] = integralSq[index - rowStride] + sumRowSq
            }
        }
    }

    private fun regionSum(integral: FloatArray, x: Int, y: Int, radius: Int, padW: Int): Float {
        val rowStride = padW + 1
        val x0 = x
        val y0 = y
        val x1 = x + radius * 2 + 1
        val y1 = y + radius * 2 + 1
        val a = integral[y0 * rowStride + x0]
        val b = integral[y0 * rowStride + x1]
        val c = integral[y1 * rowStride + x0]
        val d = integral[y1 * rowStride + x1]
        return d - b - c + a
    }

    private fun smooth3x3(mask: ByteArray, width: Int, height: Int) {
        ensureSmoothScratch(mask.size)
        val copy = smoothScratch
        for (i in mask.indices) {
            copy[i] = mask[i]
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= height) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= width) continue
                        sum += copy[yy * width + xx].toInt()
                        count++
                    }
                }
                mask[y * width + x] = if (sum * 2 >= count) 1 else 0
            }
        }
    }

    private fun median3x3(mask: ByteArray, width: Int, height: Int) {
        ensureSmoothScratch(mask.size)
        val copy = smoothScratch
        for (i in mask.indices) {
            copy[i] = mask[i]
        }
        if (medianWindow.size < 9) {
            medianWindow = FloatArray(9)
        }
        val window = medianWindow
        for (y in 0 until height) {
            for (x in 0 until width) {
                var idx = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        window[idx++] = copy[yy * width + xx].toFloat()
                    }
                }
                Arrays.sort(window, 0, idx)
                mask[y * width + x] = if (window[idx / 2] >= 0.5f) 1 else 0
            }
        }
    }

    private fun ensureSmoothScratch(size: Int) {
        if (smoothScratch.size < size) {
            smoothScratch = ByteArray(size)
        }
    }

    private fun reflectIndex(index: Int, size: Int): Int {
        var i = index
        while (i < 0 || i >= size) {
            i = if (i < 0) abs(i) - 1 else size - (i - size) - 1
        }
        return i
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    companion object {
        private const val TAG = "FILTERS"
        private const val R = 128f
    }
}
