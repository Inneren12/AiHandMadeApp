package com.appforcross.editor.filters.discrete

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.U8Mask
import kotlin.math.max
import kotlin.math.min

internal class RoleSpreadMorphology(private val config: MorphologyConfig) {

    data class Result(val mask: U8Mask, val roiAccepted: Boolean)

    fun apply(mask: U8Mask, scratch: ByteArray, scratch2: ByteArray, roi: RoiBounds? = null): Result {
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
        val roiBounds = roi?.clampTo(width, height)
        if (roi != null && roiBounds == null) {
            Logger.i(
                TAG,
                "done",
                mapOf(
                    "stage" to "morphology",
                    "roi" to false,
                    "ratio" to "0.000",
                    "transitions" to "0.000",
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
        val processBounds = roiBounds ?: RoiBounds.full(width, height)
        val area = processBounds.width * processBounds.height
        if (area <= 0) {
            Logger.i(
                TAG,
                "done",
                mapOf(
                    "stage" to "morphology",
                    "roi" to false,
                    "ratio" to "0.000",
                    "transitions" to "0.000",
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

        var ones = 0
        var minX = processBounds.right
        var maxX = processBounds.left - 1
        var minY = processBounds.bottom
        var maxY = processBounds.top - 1
        var yScan = processBounds.top
        while (yScan < processBounds.bottom) {
            val rowIndex = yScan * width
            var xScan = processBounds.left
            while (xScan < processBounds.right) {
                if (data[rowIndex + xScan].toInt() != 0) {
                    ones++
                    if (xScan < minX) minX = xScan
                    if (xScan > maxX) maxX = xScan
                    if (yScan < minY) minY = yScan
                    if (yScan > maxY) maxY = yScan
                }
                xScan++
            }
            yScan++
        }

        val ratio = if (area > 0) ones.toFloat() / area else 0f
        val transitions = countTransitions(data, width, height, processBounds)
        val transitionDensity = if (area > 0) transitions.toFloat() / area else 0f
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

        val bufA = scratch
        val bufB = scratch2
        if (bufA.size < total || bufB.size < total) {
            throw IllegalArgumentException("Morphology buffers too small")
        }
        for (i in bufA.indices) bufA[i] = 0.toByte()
        for (i in bufB.indices) bufB[i] = 0.toByte()
        copyRegion(data, bufA, width, height, processBounds)

        if (config.closing) {
            dilate3x3(bufA, bufB, width, height, processBounds)
            erode3x3(bufB, bufA, width, height, processBounds)
        }

        when (config.selectiveKernel) {
            SelectiveKernel.OFF -> Unit
            SelectiveKernel.HORIZONTAL -> {
                dilateHorizontal(bufA, bufB, width, height, processBounds)
                clearRegion(bufA, width, height, processBounds)
                copyRegion(bufB, bufA, width, height, processBounds)
            }
            SelectiveKernel.VERTICAL -> {
                dilateVertical(bufA, bufB, width, height, processBounds)
                clearRegion(bufA, width, height, processBounds)
                copyRegion(bufB, bufA, width, height, processBounds)
            }
            SelectiveKernel.ADAPTIVE -> {
                val orientation = detectOrientation(bufA, width, height, processBounds)
                when (orientation) {
                    Orientation.HORIZONTAL -> {
                        dilateHorizontal(bufA, bufB, width, height, processBounds)
                        clearRegion(bufA, width, height, processBounds)
                        copyRegion(bufB, bufA, width, height, processBounds)
                    }
                    Orientation.VERTICAL -> {
                        dilateVertical(bufA, bufB, width, height, processBounds)
                        clearRegion(bufA, width, height, processBounds)
                        copyRegion(bufB, bufA, width, height, processBounds)
                    }
                    Orientation.MIXED -> {
                        dilateHorizontal(bufA, bufB, width, height, processBounds)
                        dilateVertical(bufB, bufA, width, height, processBounds)
                    }
                }
            }
        }

        if (config.majority) {
            majority3x3(bufA, bufB, width, height, processBounds)
            clearRegion(bufA, width, height, processBounds)
            copyRegion(bufB, bufA, width, height, processBounds)
        }

        val resultData = ByteArray(total)
        var finalOnes = 0
        var finalMinX = processBounds.right
        var finalMaxX = processBounds.left - 1
        var finalMinY = processBounds.bottom
        var finalMaxY = processBounds.top - 1
        var y = processBounds.top
        while (y < processBounds.bottom) {
            val row = y * width
            var x = processBounds.left
            while (x < processBounds.right) {
                val value = if (bufA[row + x].toInt() != 0) 1 else 0
                resultData[row + x] = value.toByte()
                if (value != 0) {
                    finalOnes++
                    if (x < finalMinX) finalMinX = x
                    if (x > finalMaxX) finalMaxX = x
                    if (y < finalMinY) finalMinY = y
                    if (y > finalMaxY) finalMaxY = y
                }
                x++
            }
            y++
        }
        val result = U8Mask(width, height, resultData)
        val finalAccepted = roiAccepted && finalOnes > 0

        val memMB = (total * 2L) / 1_048_576.0

        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "morphology",
                "roi" to finalAccepted,
                "ratio" to "%.3f".format(ratio),
                "transitions" to "%.3f".format(transitionDensity),
                "ms" to elapsedMs(start),
                "memMB" to "%.3f".format(memMB),
                "bbox.x0" to if (finalOnes > 0) finalMinX else -1,
                "bbox.y0" to if (finalOnes > 0) finalMinY else -1,
                "bbox.x1" to if (finalOnes > 0) finalMaxX else -1,
                "bbox.y1" to if (finalOnes > 0) finalMaxY else -1,
            ),
        )
        return Result(result, finalAccepted)
    }

    private fun countTransitions(data: ByteArray, width: Int, height: Int, roi: RoiBounds): Int {
        if (roi.width <= 0 || roi.height <= 0) return 0
        var transitions = 0
        for (y in roi.top until roi.bottom) {
            var prev = data[y * width + roi.left]
            for (x in roi.left + 1 until roi.right) {
                val cur = data[y * width + x]
                if (cur != prev) transitions++
                prev = cur
            }
        }
        for (x in roi.left until roi.right) {
            var prev = data[roi.top * width + x]
            for (y in roi.top + 1 until roi.bottom) {
                val cur = data[y * width + x]
                if (cur != prev) transitions++
                prev = cur
            }
        }
        return transitions
    }

    private fun dilate3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        dst.fill(0)
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                var maxVal = 0
                val y0 = max(y - 1, roi.top)
                val y1 = min(y + 1, roi.bottom - 1)
                val x0 = max(x - 1, roi.left)
                val x1 = min(x + 1, roi.right - 1)
                for (yy in y0..y1) {
                    val base = yy * width
                    for (xx in x0..x1) {
                        maxVal = maxOf(maxVal, src[base + xx].toInt())
                    }
                }
                dst[y * width + x] = maxVal.toByte()
            }
        }
    }

    private fun erode3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        dst.fill(0)
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                var minVal = 1
                val y0 = max(y - 1, roi.top)
                val y1 = min(y + 1, roi.bottom - 1)
                val x0 = max(x - 1, roi.left)
                val x1 = min(x + 1, roi.right - 1)
                for (yy in y0..y1) {
                    val base = yy * width
                    for (xx in x0..x1) {
                        minVal = minOf(minVal, src[base + xx].toInt())
                    }
                }
                dst[y * width + x] = minVal.toByte()
            }
        }
    }

    private fun dilateHorizontal(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        dst.fill(0)
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                var maxVal = 0
                val x0 = max(x - 1, roi.left)
                val x1 = min(x + 1, roi.right - 1)
                for (xx in x0..x1) {
                    maxVal = maxOf(maxVal, src[y * width + xx].toInt())
                }
                dst[y * width + x] = maxVal.toByte()
            }
        }
    }

    private fun dilateVertical(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        dst.fill(0)
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                var maxVal = 0
                val y0 = max(y - 1, roi.top)
                val y1 = min(y + 1, roi.bottom - 1)
                for (yy in y0..y1) {
                    maxVal = maxOf(maxVal, src[yy * width + x].toInt())
                }
                dst[y * width + x] = maxVal.toByte()
            }
        }
    }

    private fun majority3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        dst.fill(0)
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                var sum = 0
                var count = 0
                val y0 = max(y - 1, roi.top)
                val y1 = min(y + 1, roi.bottom - 1)
                val x0 = max(x - 1, roi.left)
                val x1 = min(x + 1, roi.right - 1)
                for (yy in y0..y1) {
                    for (xx in x0..x1) {
                        sum += src[yy * width + xx].toInt()
                        count++
                    }
                }
                val value = if (sum * 2 >= count) 1 else 0
                dst[y * width + x] = value.toByte()
            }
        }
    }

    private fun copyRegion(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        for (y in roi.top until roi.bottom) {
            val row = y * width
            for (x in roi.left until roi.right) {
                dst[row + x] = src[row + x]
            }
        }
    }

    private fun clearRegion(dst: ByteArray, width: Int, height: Int, roi: RoiBounds) {
        for (y in roi.top until roi.bottom) {
            val row = y * width
            for (x in roi.left until roi.right) {
                dst[row + x] = 0.toByte()
            }
        }
    }

    private fun detectOrientation(data: ByteArray, width: Int, height: Int, roi: RoiBounds): Orientation {
        if (roi.width <= 0 || roi.height <= 0) return Orientation.MIXED
        var horizontalRuns = 0
        var verticalRuns = 0
        for (y in roi.top until roi.bottom) {
            var runLength = 0
            for (x in roi.left until roi.right) {
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
        for (x in roi.left until roi.right) {
            var runLength = 0
            for (y in roi.top until roi.bottom) {
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
