package com.appforcross.editor.filters.discrete

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.U8Mask

internal class RoleSpreadMorphology(private val config: MorphologyConfig) {

    data class Result(val mask: U8Mask, val roiAccepted: Boolean)

    fun apply(mask: U8Mask, scratch: ByteArray, scratch2: ByteArray): Result {
        Logger.i(
            TAG,
            "params",
            mapOf(
                "stage" to "morphology",
                "disc.enabled" to config.enabled,
                "disc.morph.enabled" to config.enabled,
                "disc.morph.closing" to config.closing,
                "disc.morph.sel_kernel" to config.selectiveKernel.name.lowercase(),
                "disc.morph.majority" to config.majority,
            ),
        )
        val start = System.nanoTime()
        if (!config.enabled) {
            Logger.i(TAG, "done", mapOf("stage" to "morphology", "roi" to false, "ms" to elapsedMs(start), "memMB" to 0))
            return Result(mask, false)
        }

        val width = mask.width
        val height = mask.height
        val total = width * height
        val data = mask.data
        var ones = 0
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        var rowIndex = 0
        var yScan = 0
        while (yScan < height) {
            var xScan = 0
            while (xScan < width) {
                if (data[rowIndex + xScan].toInt() != 0) {
                    ones++
                    if (xScan < minX) minX = xScan
                    if (xScan > maxX) maxX = xScan
                    if (yScan < minY) minY = yScan
                    if (yScan > maxY) maxY = yScan
                }
                xScan++
            }
            rowIndex += width
            yScan++
        }

        val ratio = ones.toFloat() / maxOf(1, total)
        val transitions = countTransitions(data, width, height)
        val transitionDensity = transitions.toFloat() / maxOf(1, total)
        val roiAccepted = ratio in config.roiMinForeground..config.roiMaxForeground && transitionDensity >= config.roiTransitionDensity

        if (!roiAccepted) {
            Logger.i(
                TAG,
                "done",
                mapOf(
                    "stage" to "morphology",
                    "roi" to false,
                    "ratio" to "%.3f".format(ratio),
                    "transitions" to "%.3f".format(transitionDensity),
                    "ms" to elapsedMs(start),
                    "memMB" to 0,
                ),
            )
            return Result(mask, false)
        }

        if (ones == 0) {
            Logger.i(
                TAG,
                "done",
                mapOf(
                    "stage" to "morphology",
                    "roi" to false,
                    "ratio" to "%.3f".format(ratio),
                    "transitions" to "%.3f".format(transitionDensity),
                    "ms" to elapsedMs(start),
                    "memMB" to 0,
                    "bbox.x0" to -1,
                    "bbox.y0" to -1,
                    "bbox.x1" to -1,
                    "bbox.y1" to -1,
                ),
            )
            return Result(mask, false)
        }

        val margin = 0
        val bx0 = (minX - margin).coerceAtLeast(0)
        val bx1 = (maxX + margin).coerceAtMost(width - 1)
        val by0 = (minY - margin).coerceAtLeast(0)
        val by1 = (maxY + margin).coerceAtMost(height - 1)

        val bufA = scratch
        val bufB = scratch2
        if (bufA.size < total || bufB.size < total) {
            throw IllegalArgumentException("Morphology buffers too small")
        }
        for (i in 0 until total) {
            bufA[i] = data[i]
        }

        if (config.closing) {
            dilate3x3(bufA, bufB, width, height)
            erode3x3(bufB, bufA, width, height)
        }

        when (config.selectiveKernel) {
            SelectiveKernel.OFF -> Unit
            SelectiveKernel.HORIZONTAL -> {
                dilateHorizontal(bufA, bufB, width, height)
                copy(bufB, bufA, total)
            }
            SelectiveKernel.VERTICAL -> {
                dilateVertical(bufA, bufB, width, height)
                copy(bufB, bufA, total)
            }
            SelectiveKernel.ADAPTIVE -> {
                val orientation = detectOrientation(bufA, width, height)
                when (orientation) {
                    Orientation.HORIZONTAL -> {
                        dilateHorizontal(bufA, bufB, width, height)
                        copy(bufB, bufA, total)
                    }
                    Orientation.VERTICAL -> {
                        dilateVertical(bufA, bufB, width, height)
                        copy(bufB, bufA, total)
                    }
                    Orientation.MIXED -> {
                        dilateHorizontal(bufA, bufB, width, height)
                        dilateVertical(bufB, bufA, width, height)
                    }
                }
            }
        }

        if (config.majority) {
            majority3x3(bufA, bufB, width, height)
            copy(bufB, bufA, total)
        }

        val resultData = ByteArray(total)
        var y = 0
        while (y < height) {
            val row = y * width
            val withinY = y in by0..by1
            var x = 0
            while (x < width) {
                val within = withinY && x in bx0..bx1
                resultData[row + x] = if (within && bufA[row + x].toInt() != 0) 1 else 0
                x++
            }
            y++
        }
        val result = U8Mask(width, height, resultData)

        val memMB = (total * 2L) / 1_048_576.0

        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "morphology",
                "roi" to true,
                "ratio" to "%.3f".format(ratio),
                "transitions" to "%.3f".format(transitionDensity),
                "ms" to elapsedMs(start),
                "memMB" to "%.3f".format(memMB),
                "bbox.x0" to bx0,
                "bbox.y0" to by0,
                "bbox.x1" to bx1,
                "bbox.y1" to by1,
            ),
        )
        return Result(result, true)
    }

    private fun countTransitions(data: ByteArray, width: Int, height: Int): Int {
        var transitions = 0
        for (y in 0 until height) {
            var prev = data[y * width]
            for (x in 1 until width) {
                val cur = data[y * width + x]
                if (cur != prev) transitions++
                prev = cur
            }
        }
        for (x in 0 until width) {
            var prev = data[x]
            for (y in 1 until height) {
                val cur = data[y * width + x]
                if (cur != prev) transitions++
                prev = cur
            }
        }
        return transitions
    }

    private fun dilate3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxVal = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        maxVal = maxOf(maxVal, src[yy * width + xx].toInt())
                    }
                }
                dst[y * width + x] = maxVal.toByte()
            }
        }
    }

    private fun erode3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minVal = 1
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        minVal = minOf(minVal, src[yy * width + xx].toInt())
                    }
                }
                dst[y * width + x] = minVal.toByte()
            }
        }
    }

    private fun dilateHorizontal(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxVal = 0
                for (dx in -1..1) {
                    val xx = (x + dx).coerceIn(0, width - 1)
                    maxVal = maxOf(maxVal, src[y * width + xx].toInt())
                }
                dst[y * width + x] = maxVal.toByte()
            }
        }
    }

    private fun dilateVertical(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxVal = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    maxVal = maxOf(maxVal, src[yy * width + x].toInt())
                }
                dst[y * width + x] = maxVal.toByte()
            }
        }
    }

    private fun majority3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        sum += src[yy * width + xx].toInt()
                        count++
                    }
                }
                dst[y * width + x] = if (sum * 2 >= count) 1 else 0
            }
        }
    }

    private fun copy(src: ByteArray, dst: ByteArray, size: Int) {
        for (i in 0 until size) {
            dst[i] = src[i]
        }
    }

    private fun detectOrientation(data: ByteArray, width: Int, height: Int): Orientation {
        var horizontalRuns = 0
        var verticalRuns = 0
        for (y in 0 until height) {
            var runLength = 0
            for (x in 0 until width) {
                val v = data[y * width + x].toInt()
                if (v != 0) {
                    runLength++
                } else if (runLength > 0) {
                    if (runLength >= 3) horizontalRuns++
                    runLength = 0
                }
            }
            if (runLength >= 3) horizontalRuns++
        }
        for (x in 0 until width) {
            var runLength = 0
            for (y in 0 until height) {
                val v = data[y * width + x].toInt()
                if (v != 0) {
                    runLength++
                } else if (runLength > 0) {
                    if (runLength >= 3) verticalRuns++
                    runLength = 0
                }
            }
            if (runLength >= 3) verticalRuns++
        }
        return when {
            horizontalRuns > verticalRuns * 2 -> Orientation.HORIZONTAL
            verticalRuns > horizontalRuns * 2 -> Orientation.VERTICAL
            else -> Orientation.MIXED
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    private enum class Orientation { HORIZONTAL, VERTICAL, MIXED }

    companion object {
        private const val TAG = "FILTERS"
    }
}
