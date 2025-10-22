package com.appforcross.editor.filters.discrete

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16

enum class Mode { AUTO, NOTCH, DOWNSCALE, MEDIAN, OFF }

internal class MoireSuppressor(private val config: MoireConfig) {

    data class Decision(val mode: Mode, val frequency: Int, val score: Float)

    fun apply(image: LinearImageF16, scratch: FloatArray, scratch2: FloatArray, roi: Roi? = null): LinearImageF16 {
        Logger.i(
            TAG,
            "params",
            mapOf(
                "stage" to "moire",
                "disc.enabled" to (config.mode != Mode.OFF && config.enabled),
                "disc.moire.enabled" to config.enabled,
                "disc.moire.mode" to config.mode.name,
                "disc.moire.maxLag" to config.maxLag,
                "disc.moire.downscale" to config.downscaleFactor,
                "disc.moire.threshold" to config.detectionThreshold,
            ),
        )

        val start = System.nanoTime()
        if (!config.enabled || config.mode == Mode.OFF) {
            Logger.i(TAG, "done", mapOf("stage" to "moire", "mode" to Mode.OFF.name, "ms" to elapsedMs(start), "memMB" to 0))
            return image
        }

        val width = image.width
        val height = image.height
        roi?.requireWithin(width, height)
        val planeSize = width * height
        val planeFloats = scratch
        if (planeFloats.size < planeSize) {
            throw IllegalArgumentException("Scratch buffer too small for moire stage")
        }
        for (i in 0 until planeSize) {
            planeFloats[i] = HalfFloats.toFloat(image.data[i])
        }

        val decision = when (config.mode) {
            Mode.AUTO -> autoDecision(planeFloats, width, height)
            Mode.NOTCH, Mode.DOWNSCALE, Mode.MEDIAN -> Decision(config.mode, 1, 0f)
            Mode.OFF -> Decision(Mode.OFF, 1, 0f)
        }

        val work = scratch2
        if (work.size < planeSize) {
            throw IllegalArgumentException("Secondary scratch buffer too small for moire stage")
        }

        val processed = when (decision.mode) {
            Mode.NOTCH -> {
                notchFilter(planeFloats, work, width, height, max(1, decision.frequency))
                work
            }
            Mode.DOWNSCALE -> {
                downscaleBlur(planeFloats, work, width, height, max(2, config.downscaleFactor))
                work
            }
            Mode.MEDIAN -> {
                medianFilter(planeFloats, work, width, height)
                work
            }
            Mode.AUTO, Mode.OFF -> planeFloats
        }

        val outData = image.data.copyOf()
        if (roi == null) {
            for (i in 0 until planeSize) {
                outData[i] = HalfFloats.fromFloat(processed[i])
            }
        } else {
            for (y in roi.top until roi.bottom) {
                for (x in roi.left until roi.right) {
                    val idx = y * width + x
                    outData[idx] = HalfFloats.fromFloat(processed[idx])
                }
            }
        }

        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "moire",
                "mode" to decision.mode.name,
                "freq" to decision.frequency,
                "score" to "%.3f".format(decision.score),
                "ms" to elapsedMs(start),
                "memMB" to (planeSize * 8) / 1_048_576,
            ),
        )
        return LinearImageF16(width, height, image.planes, outData)
    }

    private fun autoDecision(data: FloatArray, width: Int, height: Int): Decision {
        val maxLag = min(config.maxLag, min(width, height) / 2)
        if (maxLag <= 1) return Decision(Mode.MEDIAN, 1, 0f)
        val horizontal = FloatArray(maxLag + 1)
        val vertical = FloatArray(maxLag + 1)
        val mean = data.average().toFloat()
        val std = sqrt(data.fold(0f) { acc, value ->
            val d = value - mean
            acc + d * d
        } / max(1, data.size - 1))
        if (std <= 1e-6f) return Decision(Mode.MEDIAN, 1, 0f)
        for (lag in 1..maxLag) {
            var hAcc = 0f
            var hCount = 0
            for (y in 0 until height) {
                for (x in 0 until width - lag) {
                    val a = data[y * width + x] - mean
                    val b = data[y * width + x + lag] - mean
                    hAcc += a * b
                    hCount++
                }
            }
            horizontal[lag] = if (hCount > 0) hAcc / (std * std * hCount) else 0f

            var vAcc = 0f
            var vCount = 0
            for (y in 0 until height - lag) {
                for (x in 0 until width) {
                    val a = data[y * width + x] - mean
                    val b = data[(y + lag) * width + x] - mean
                    vAcc += a * b
                    vCount++
                }
            }
            vertical[lag] = if (vCount > 0) vAcc / (std * std * vCount) else 0f
        }

        var bestLag = 1
        var bestScore = 0f
        var bestOrientation = 0
        for (lag in 1..maxLag) {
            val scoreH = horizontal[lag]
            val scoreV = vertical[lag]
            if (scoreH > bestScore) {
                bestScore = scoreH
                bestLag = lag
                bestOrientation = 0
            }
            if (scoreV > bestScore) {
                bestScore = scoreV
                bestLag = lag
                bestOrientation = 1
            }
        }

        if (bestScore < config.detectionThreshold) {
            return Decision(Mode.MEDIAN, 1, bestScore)
        }
        val mode = if (bestLag <= 4) Mode.NOTCH else Mode.DOWNSCALE
        val frequency = if (bestOrientation == 0) bestLag else bestLag
        return Decision(mode, max(1, frequency), bestScore)
    }

    private fun notchFilter(src: FloatArray, dst: FloatArray, width: Int, height: Int, lag: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                var acc = src[idx]
                var count = 1
                if (x - lag >= 0) {
                    acc += src[idx - lag]
                    count++
                }
                if (x + lag < width) {
                    acc += src[idx + lag]
                    count++
                }
                if (y - lag >= 0) {
                    acc += src[idx - lag * width]
                    count++
                }
                if (y + lag < height) {
                    acc += src[idx + lag * width]
                    count++
                }
                dst[idx] = src[idx] - (acc - src[idx]) / max(1, count - 1)
            }
        }
    }

    private fun downscaleBlur(src: FloatArray, dst: FloatArray, width: Int, height: Int, factor: Int) {
        val clamped = max(2, factor)
        val kernel = clamped
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0f
                var count = 0
                val x0 = max(0, x - kernel)
                val x1 = min(width - 1, x + kernel)
                val y0 = max(0, y - kernel)
                val y1 = min(height - 1, y + kernel)
                for (yy in y0..y1) {
                    for (xx in x0..x1) {
                        acc += src[yy * width + xx]
                        count++
                    }
                }
                dst[y * width + x] = acc / max(1, count)
            }
        }
    }

    private fun medianFilter(src: FloatArray, dst: FloatArray, width: Int, height: Int) {
        val window = FloatArray(9)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var idx = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        window[idx++] = src[yy * width + xx]
                    }
                }
                window.sort()
                dst[y * width + x] = window[window.size / 2]
            }
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    companion object {
        private const val TAG = "FILTERS"
    }
}
