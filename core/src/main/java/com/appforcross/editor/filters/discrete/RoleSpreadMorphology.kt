package com.appforcross.editor.filters.discrete

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.U8Mask

internal class RoleSpreadMorphology(private val config: MorphologyConfig) {

    data class Result(val mask: U8Mask, val roiAccepted: Boolean)

    fun apply(mask: U8Mask, scratch: ByteArray, scratch2: ByteArray, roi: Roi? = null): Result {
        Logger.i(
            TAG,
            "params",
            mapOf(
                "stage" to "morphology",
                "disc.enabled" to config.enabled,
                "disc.morph.enabled" to config.enabled,
                "disc.morph.closing" to config.closing,
                "disc.morph.selective" to config.selectiveDilation,
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
        roi?.requireWithin(width, height)
        val total = width * height
        val data = mask.data
        val roiArea = roi?.let { it.width * it.height } ?: total
        val ones = if (roi == null) {
            data.count { it.toInt() != 0 }
        } else {
            var count = 0
            for (y in roi.top until roi.bottom) {
                for (x in roi.left until roi.right) {
                    if (data[y * width + x].toInt() != 0) count++
                }
            }
            count
        }
        val ratio = ones.toFloat() / maxOf(1, roiArea)
        val transitions = countTransitions(data, width, height, roi)
        val transitionDensity = transitions.toFloat() / maxOf(1, roiArea)
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

        val bufA = scratch
        val bufB = scratch2
        if (bufA.size < total || bufB.size < total) {
            throw IllegalArgumentException("Morphology buffers too small")
        }
        for (i in 0 until total) {
            bufA[i] = data[i]
        }

        if (config.closing) {
            dilate3x3(bufA, bufB, width, height, roi)
            erode3x3(bufB, bufA, width, height, roi)
        }

        if (config.selectiveDilation) {
            val orientation = detectOrientation(bufA, width, height, roi)
            when (orientation) {
                Orientation.HORIZONTAL -> {
                    dilateHorizontal(bufA, bufB, width, height, roi)
                    copy(bufB, bufA, total)
                }
                Orientation.VERTICAL -> {
                    dilateVertical(bufA, bufB, width, height, roi)
                    copy(bufB, bufA, total)
                }
                Orientation.MIXED -> {
                    dilateHorizontal(bufA, bufB, width, height, roi)
                    dilateVertical(bufB, bufA, width, height, roi)
                }
            }
        }

        if (config.majority) {
            majority3x3(bufA, bufB, width, height, roi)
            copy(bufB, bufA, total)
        }

        val resultData = data.copyOf()
        if (roi == null) {
            for (i in 0 until total) {
                resultData[i] = if (bufA[i].toInt() != 0) 1 else 0
            }
        } else {
            for (y in roi.top until roi.bottom) {
                for (x in roi.left until roi.right) {
                    val idx = y * width + x
                    resultData[idx] = if (bufA[idx].toInt() != 0) 1 else 0
                }
            }
        }
        val result = U8Mask(width, height, resultData)

        Logger.i(
            TAG,
            "done",
            mapOf(
                "stage" to "morphology",
                "roi" to true,
                "ratio" to "%.3f".format(ratio),
                "transitions" to "%.3f".format(transitionDensity),
                "ms" to elapsedMs(start),
                "memMB" to (total * 2) / 1_048_576,
            ),
        )
        return Result(result, true)
    }

    private fun countTransitions(data: ByteArray, width: Int, height: Int, roi: Roi?): Int {
        var transitions = 0
        if (roi == null) {
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

    private fun dilate3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: Roi?) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (roi != null && !roi.contains(x, y)) {
                    dst[idx] = src[idx]
                    continue
                }
                var maxVal = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= height) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= width) continue
                        if (roi != null && !roi.contains(xx, yy)) continue
                        maxVal = maxOf(maxVal, src[yy * width + xx].toInt())
                    }
                }
                dst[idx] = maxVal.toByte()
            }
        }
    }

    private fun erode3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: Roi?) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (roi != null && !roi.contains(x, y)) {
                    dst[idx] = src[idx]
                    continue
                }
                var minVal = 1
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= height) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= width) continue
                        if (roi != null && !roi.contains(xx, yy)) continue
                        minVal = minOf(minVal, src[yy * width + xx].toInt())
                    }
                }
                dst[idx] = minVal.toByte()
            }
        }
    }

    private fun dilateHorizontal(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: Roi?) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (roi != null && !roi.contains(x, y)) {
                    dst[idx] = src[idx]
                    continue
                }
                var maxVal = 0
                for (dx in -2..2) {
                    val xx = x + dx
                    if (xx < 0 || xx >= width) continue
                    if (roi != null && !roi.contains(xx, y)) continue
                    maxVal = maxOf(maxVal, src[y * width + xx].toInt())
                }
                dst[idx] = maxVal.toByte()
            }
        }
    }

    private fun dilateVertical(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: Roi?) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (roi != null && !roi.contains(x, y)) {
                    dst[idx] = src[idx]
                    continue
                }
                var maxVal = 0
                for (dy in -2..2) {
                    val yy = y + dy
                    if (yy < 0 || yy >= height) continue
                    if (roi != null && !roi.contains(x, yy)) continue
                    maxVal = maxOf(maxVal, src[yy * width + x].toInt())
                }
                dst[idx] = maxVal.toByte()
            }
        }
    }

    private fun majority3x3(src: ByteArray, dst: ByteArray, width: Int, height: Int, roi: Roi?) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (roi != null && !roi.contains(x, y)) {
                    dst[idx] = src[idx]
                    continue
                }
                var sum = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= height) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= width) continue
                        if (roi != null && !roi.contains(xx, yy)) continue
                        sum += src[yy * width + xx].toInt()
                        count++
                    }
                }
                dst[idx] = if (count == 0) 0 else if (sum * 2 >= count) 1 else 0
            }
        }
    }

    private fun copy(src: ByteArray, dst: ByteArray, size: Int) {
        for (i in 0 until size) {
            dst[i] = src[i]
        }
    }

    private fun detectOrientation(data: ByteArray, width: Int, height: Int, roi: Roi?): Orientation {
        var horizontalRuns = 0
        var verticalRuns = 0
        val xStart = roi?.left ?: 0
        val xEnd = roi?.right ?: width
        val yStart = roi?.top ?: 0
        val yEnd = roi?.bottom ?: height
        for (y in yStart until yEnd) {
            var runLength = 0
            for (x in xStart until xEnd) {
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
        for (x in xStart until xEnd) {
            var runLength = 0
            for (y in yStart until yEnd) {
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
