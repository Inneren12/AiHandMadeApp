package com.appforcross.editor.filters.discrete

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.U8Mask

internal class SauvolaWolfBinarizer(private val config: BinarizationConfig) {

    fun apply(image: LinearImageF16, scratch: FloatArray, scratchSq: FloatArray, buffer: ByteArray): U8Mask {
        Logger.i(
            TAG,
            "params",
            mapOf(
                "stage" to "binarize",
                "disc.enabled" to config.enabled,
                "disc.bin.enabled" to config.enabled,
                "disc.bin.w" to config.wBin,
                "disc.bin.k" to config.k,
                "disc.bin.smooth" to config.smoothing,
            ),
        )
        val start = System.nanoTime()
        require(image.planes >= 1) { "Binarizer expects at least one plane" }
        require(config.wBin >= 3 && config.wBin % 2 == 1) { "Window size must be odd and >=3" }
        if (!config.enabled) {
            buffer.fill(0)
            Logger.i(TAG, "done", mapOf("stage" to "binarize", "ms" to elapsedMs(start), "memMB" to 0))
            return U8Mask(image.width, image.height, buffer.copyOf())
        }

        val width = image.width
        val height = image.height
        val planeOffset = width * height
        val floats = scratch
        for (i in 0 until planeOffset) {
            floats[i] = HalfFloats.toFloat(image.data[i])
        }

        val radius = max(1, config.wBin / 2)
        val padW = width + radius * 2
        val padH = height + radius * 2
        val padded = ensureSize(padW * padH, scratchSq)
        reflectPad(floats, width, height, radius, padded, padW, padH)

        val integral = FloatArray((padW + 1) * (padH + 1))
        val integralSq = FloatArray((padW + 1) * (padH + 1))
        buildIntegral(padded, padW, padH, integral, integralSq)

        var globalMin = Float.POSITIVE_INFINITY
        var globalMax = Float.NEGATIVE_INFINITY
        var globalMean = 0f
        var globalCount = 0
        for (value in floats) {
            globalMin = min(globalMin, value)
            globalMax = max(globalMax, value)
            globalMean += value
            globalCount++
        }
        globalMean /= max(1, globalCount)
        var globalVariance = 0f
        for (value in floats) {
            val d = value - globalMean
            globalVariance += d * d
        }
        val globalStd = sqrt(max(globalVariance / max(1, globalCount - 1), 1e-6f))

        val maskData = buffer
        val area = config.wBin * config.wBin
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sum = regionSum(integral, x, y, radius, padW)
                val sumSq = regionSum(integralSq, x, y, radius, padW)
                val mean = sum / area
                val variance = max(sumSq / area - mean * mean, 0f)
                val std = sqrt(variance)
                val value = floats[y * width + x]
                val normalizedStd = if (globalStd > 0f) std / globalStd else 0f
                val minTerm = (value - globalMin) / max(1e-3f, globalMax - globalMin)
                val threshold = mean + config.k * (normalizedStd - 1f) * mean + 0.1f * (minTerm - 0.5f)
                maskData[y * width + x] = if (value >= threshold) 1 else 0
            }
        }

        if (config.smoothing) {
            smooth3x3(maskData, width, height)
        }

        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "binarize",
                "ms" to elapsedMs(start),
                "memMB" to ((integral.size + integralSq.size + padded.size) * 4) / 1_048_576,
            ),
        )

        return U8Mask(width, height, maskData.copyOf())
    }

    private fun ensureSize(size: Int, scratchSq: FloatArray): FloatArray {
        return if (scratchSq.size >= size) {
            scratchSq
        } else {
            FloatArray(size)
        }
    }

    private fun reflectPad(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        dst: FloatArray,
        padW: Int,
        padH: Int,
    ) {
        for (y in 0 until padH) {
            val srcY = reflectIndex(y - radius, height)
            for (x in 0 until padW) {
                val srcX = reflectIndex(x - radius, width)
                dst[y * padW + x] = src[srcY * width + srcX]
            }
        }
    }

    private fun reflectIndex(index: Int, size: Int): Int {
        var i = index
        while (i < 0 || i >= size) {
            i = if (i < 0) abs(i) - 1 else size - (i - size) - 1
        }
        return i
    }

    private fun buildIntegral(
        src: FloatArray,
        width: Int,
        height: Int,
        integral: FloatArray,
        integralSq: FloatArray,
    ) {
        val rowStride = width + 1
        for (y in 1..height) {
            var sumRow = 0f
            var sumRowSq = 0f
            for (x in 1..width) {
                val value = src[(y - 1) * width + (x - 1)]
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
        val copy = mask.copyOf()
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

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    companion object {
        private const val TAG = "FILTERS"
    }
}
