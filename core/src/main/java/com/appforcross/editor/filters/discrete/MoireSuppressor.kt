package com.appforcross.editor.filters.discrete

import java.util.Arrays
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16

internal class MoireSuppressor(private val config: MoireConfig) {

    enum class Mode { AUTO, NOTCH, DOWNSCALE, MEDIAN, OFF }

    data class Decision(
        val mode: Mode,
        val freqX: Int,
        val freqY: Int,
        val scoreX: Float,
        val scoreY: Float,
    )

    private var downscaleBuffer = FloatArray(0)
    private var medianWindow = FloatArray(25)

    fun apply(image: LinearImageF16, scratch: FloatArray, work: FloatArray, aux: FloatArray): LinearImageF16 {
        Logger.i(
            TAG,
            "params",
            mapOf(
                "stage" to "moire",
                "disc.enabled" to (config.mode != Mode.OFF && config.enabled),
                "disc.moire.enabled" to config.enabled,
                "disc.moire.mode" to config.mode.name.lowercase(),
                "disc.moire.maxLag" to config.maxLag,
                "disc.moire.threshold" to "%.3f".format(config.detectionThreshold),
                "disc.moire.notch.width" to config.notchWidth,
                "disc.moire.downscale.factor" to "%.2f".format(config.downscaleFactor),
                "disc.moire.median" to config.medianSize,
            ),
        )

        val start = System.nanoTime()
        if (!config.enabled || config.mode == Mode.OFF) {
            Logger.i(TAG, "done", mapOf("stage" to "moire", "mode" to Mode.OFF.name, "ms" to elapsedMs(start), "memMB" to 0))
            return image
        }

        val width = image.width
        val height = image.height
        val planeSize = width * height
        if (scratch.size < planeSize || work.size < planeSize || aux.size < planeSize) {
            throw IllegalArgumentException("Scratch buffers too small for moire stage")
        }
        for (i in 0 until planeSize) {
            scratch[i] = HalfFloats.toFloat(image.data[i])
        }

        val auto = autoDecision(scratch, width, height)
        val decision = when (config.mode) {
            Mode.AUTO -> auto
            Mode.NOTCH -> auto.copy(mode = Mode.NOTCH)
            Mode.DOWNSCALE -> auto.copy(mode = Mode.DOWNSCALE)
            Mode.MEDIAN -> auto.copy(mode = Mode.MEDIAN)
            Mode.OFF -> auto.copy(mode = Mode.OFF)
        }

        val processed = when (decision.mode) {
            Mode.NOTCH -> {
                notchFilter(
                    scratch,
                    work,
                    width,
                    height,
                    decision.freqX,
                    decision.freqY,
                    config.notchWidth,
                )
                work
            }
            Mode.DOWNSCALE -> {
                downscaleRoute(scratch, work, aux, width, height)
            }
            Mode.MEDIAN -> {
                medianFilter(scratch, work, width, height, config.medianSize)
                work
            }
            Mode.OFF -> scratch
            Mode.AUTO -> scratch
        }

        val outData = image.data.copyOf()
        for (i in 0 until planeSize) {
            outData[i] = HalfFloats.fromFloat(processed[i])
        }

        val memMB = (planeSize.toLong() * 12L + downscaleBuffer.size.toLong() * 4L) / 1_048_576.0
        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "moire",
                "mode" to decision.mode.name.lowercase(),
                "freqX" to decision.freqX,
                "freqY" to decision.freqY,
                "scoreX" to "%.3f".format(decision.scoreX),
                "scoreY" to "%.3f".format(decision.scoreY),
                "ms" to elapsedMs(start),
                "memMB" to "%.3f".format(memMB),
            ),
        )
        return LinearImageF16(width, height, image.planes, outData)
    }

    private fun autoDecision(data: FloatArray, width: Int, height: Int): Decision {
        val maxLag = min(config.maxLag, min(width, height) / 2)
        if (maxLag <= 1) return Decision(Mode.MEDIAN, 0, 0, 0f, 0f)
        val horizontal = FloatArray(maxLag + 1)
        val vertical = FloatArray(maxLag + 1)
        val mean = data.average().toFloat()
        val variance = data.fold(0f) { acc, value ->
            val d = value - mean
            acc + d * d
        } / max(1, data.size - 1)
        val std = sqrt(max(variance, 1e-6f))
        if (std <= 1e-6f) return Decision(Mode.MEDIAN, 0, 0, 0f, 0f)
        for (lag in 1..maxLag) {
            var hAcc = 0f
            var hCount = 0
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width - lag) {
                    val a = data[row + x] - mean
                    val b = data[row + x + lag] - mean
                    hAcc += a * b
                    hCount++
                }
            }
            horizontal[lag] = if (hCount > 0) hAcc / (std * std * hCount) else 0f

            var vAcc = 0f
            var vCount = 0
            for (y in 0 until height - lag) {
                val row = y * width
                val nextRow = (y + lag) * width
                for (x in 0 until width) {
                    val a = data[row + x] - mean
                    val b = data[nextRow + x] - mean
                    vAcc += a * b
                    vCount++
                }
            }
            vertical[lag] = if (vCount > 0) vAcc / (std * std * vCount) else 0f
        }

        var bestLagX = 0
        var bestLagY = 0
        var bestScoreX = 0f
        var bestScoreY = 0f
        for (lag in 1..maxLag) {
            if (horizontal[lag] > bestScoreX) {
                bestScoreX = horizontal[lag]
                bestLagX = lag
            }
            if (vertical[lag] > bestScoreY) {
                bestScoreY = vertical[lag]
                bestLagY = lag
            }
        }

        val dominantScore = max(bestScoreX, bestScoreY)
        if (dominantScore < config.detectionThreshold) {
            return Decision(Mode.MEDIAN, 0, 0, bestScoreX, bestScoreY)
        }
        val dominantLag = if (bestScoreX >= bestScoreY) bestLagX else bestLagY
        val mode = if (dominantLag <= 4) Mode.NOTCH else Mode.DOWNSCALE
        val freqX = if (bestScoreX >= config.detectionThreshold) bestLagX else 0
        val freqY = if (bestScoreY >= config.detectionThreshold) bestLagY else 0
        return Decision(mode, freqX, freqY, bestScoreX, bestScoreY)
    }

    private fun notchFilter(
        src: FloatArray,
        dst: FloatArray,
        width: Int,
        height: Int,
        freqX: Int,
        freqY: Int,
        notchWidth: Int,
    ) {
        val radius = max(1, notchWidth)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                var sum = 0f
                var count = 0
                if (freqX > 0) {
                    for (offset in 1..radius) {
                        val step = offset * freqX
                        val left = x - step
                        val right = x + step
                        if (left >= 0) {
                            sum += src[idx - step]
                            count++
                        }
                        if (right < width) {
                            sum += src[idx + step]
                            count++
                        }
                    }
                }
                if (freqY > 0) {
                    for (offset in 1..radius) {
                        val step = offset * freqY
                        val up = y - step
                        val down = y + step
                        if (up >= 0) {
                            sum += src[idx - step * width]
                            count++
                        }
                        if (down < height) {
                            sum += src[idx + step * width]
                            count++
                        }
                    }
                }
                val average = if (count > 0) sum / count else 0f
                dst[idx] = src[idx] - average * 0.5f
            }
        }
    }

    private fun downscaleRoute(
        src: FloatArray,
        dst: FloatArray,
        aux: FloatArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val factor = if (config.downscaleFactor < 0.75f) 2 else 1
        if (factor <= 1) {
            boxBlur(src, dst, width, height, max(1, config.notchWidth))
            return dst
        }
        val downW = max(1, width / factor)
        val downH = max(1, height / factor)
        val downPixels = downW * downH
        ensureDownscaleCapacity(downPixels)
        boxBlur(src, dst, width, height, 1)
        downscale(src = dst, width = width, height = height, factor = factor, out = downscaleBuffer)
        if (aux.size < downPixels) {
            throw IllegalArgumentException("Aux scratch too small for downscale branch")
        }
        val medianSize = if (config.medianSize % 2 == 1) config.medianSize else config.medianSize + 1
        medianFloat(downscaleBuffer, aux, downW, downH, max(3, medianSize))
        upsample(aux, dst, downW, downH, width, height)
        return dst
    }

    private fun medianFilter(src: FloatArray, dst: FloatArray, width: Int, height: Int, kernelSize: Int) {
        val k = if (kernelSize % 2 == 1) kernelSize else kernelSize + 1
        val radius = max(1, k / 2)
        ensureMedianWindow(k * k)
        val window = medianWindow
        for (y in 0 until height) {
            for (x in 0 until width) {
                var idx = 0
                for (dy in -radius..radius) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -radius..radius) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        window[idx++] = src[yy * width + xx]
                    }
                }
                Arrays.sort(window, 0, idx)
                dst[y * width + x] = window[idx / 2]
            }
        }
    }

    private fun boxBlur(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
        val r = max(1, radius)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                val y0 = max(0, y - r)
                val y1 = min(height - 1, y + r)
                val x0 = max(0, x - r)
                val x1 = min(width - 1, x + r)
                for (yy in y0..y1) {
                    for (xx in x0..x1) {
                        sum += src[yy * width + xx]
                        count++
                    }
                }
                dst[y * width + x] = sum / max(1, count)
            }
        }
    }

    private fun downscale(src: FloatArray, width: Int, height: Int, factor: Int, out: FloatArray) {
        val outW = max(1, width / factor)
        val outH = max(1, height / factor)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                var sum = 0f
                var count = 0
                val y0 = y * factor
                val x0 = x * factor
                val y1 = min(height, y0 + factor)
                val x1 = min(width, x0 + factor)
                for (yy in y0 until y1) {
                    for (xx in x0 until x1) {
                        sum += src[yy * width + xx]
                        count++
                    }
                }
                out[y * outW + x] = sum / max(1, count)
            }
        }
    }

    private fun upsample(src: FloatArray, dst: FloatArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int) {
        val scaleX = dstW.toFloat() / srcW
        val scaleY = dstH.toFloat() / srcH
        for (y in 0 until dstH) {
            val gy = min(srcH - 1, (y / scaleY).toInt())
            val gyNext = min(srcH - 1, gy + 1)
            val fy = (y / scaleY) - gy
            for (x in 0 until dstW) {
                val gx = min(srcW - 1, (x / scaleX).toInt())
                val gxNext = min(srcW - 1, gx + 1)
                val fx = (x / scaleX) - gx
                val top = src[gy * srcW + gx] * (1 - fx) + src[gy * srcW + gxNext] * fx
                val bottom = src[gyNext * srcW + gx] * (1 - fx) + src[gyNext * srcW + gxNext] * fx
                dst[y * dstW + x] = top * (1 - fy) + bottom * fy
            }
        }
    }

    private fun medianFloat(src: FloatArray, dst: FloatArray, width: Int, height: Int, kernel: Int) {
        val k = if (kernel % 2 == 1) kernel else kernel + 1
        val radius = max(1, k / 2)
        ensureMedianWindow(k * k)
        val window = medianWindow
        for (y in 0 until height) {
            for (x in 0 until width) {
                var idx = 0
                for (dy in -radius..radius) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -radius..radius) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        window[idx++] = src[yy * width + xx]
                    }
                }
                Arrays.sort(window, 0, idx)
                dst[y * width + x] = window[idx / 2]
            }
        }
        for (i in 0 until width * height) {
            src[i] = dst[i]
        }
    }

    private fun ensureDownscaleCapacity(size: Int) {
        if (downscaleBuffer.size < size) {
            downscaleBuffer = FloatArray(size)
        }
    }

    private fun ensureMedianWindow(size: Int) {
        if (medianWindow.size < size) {
            medianWindow = FloatArray(size)
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    companion object {
        private const val TAG = "FILTERS"
    }
}
