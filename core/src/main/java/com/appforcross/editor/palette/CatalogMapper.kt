package com.appforcross.editor.palette

import com.appforcross.editor.color.ColorMgmt
import com.appforcross.editor.logging.Logger
import kotlin.math.abs

data class ThreadColor(val code: String, val name: String, val okLab: FloatArray)
data class CatalogFit(val avgDE: Float, val maxDE: Float, val mapping: IntArray)

object CatalogMapper {
    private const val AVG_THRESHOLD = 2.5f
    private const val MAX_THRESHOLD = 5.0f
    private const val EPS = 1e-12

    fun mapToCatalog(paletteOKLab: FloatArray, catalog: List<ThreadColor>): CatalogFit {
        require(paletteOKLab.size % 3 == 0) { "Palette must be multiple of 3" }
        require(catalog.isNotEmpty()) { "Catalog is empty" }
        val colors = paletteOKLab.size / 3
        Logger.i(
            "PALETTE",
            "params",
            mapOf(
                "palette.colors" to colors,
                "catalog.size" to catalog.size,
                "threshold.avg" to AVG_THRESHOLD,
                "threshold.max" to MAX_THRESHOLD
            )
        )
        val map = IntArray(colors) { -1 }
        var sum = 0.0
        var mx = 0.0
        var unmapped = 0
        val colorBuffer = FloatArray(3)
        for (k in 0 until colors) {
            val base = k * 3
            colorBuffer[0] = paletteOKLab[base]
            colorBuffer[1] = paletteOKLab[base + 1]
            colorBuffer[2] = paletteOKLab[base + 2]
            var bestIdx = -1
            var bestDE = Double.POSITIVE_INFINITY
            for ((idx, tc) in catalog.withIndex()) {
                require(tc.okLab.size >= 3) { "ThreadColor.okLab must have at least 3 components" }
                val de = ColorMgmt.deltaE00(colorBuffer, tc.okLab)
                if (de < bestDE - EPS || (abs(de - bestDE) <= EPS && (bestIdx == -1 || idx < bestIdx))) {
                    bestDE = de
                    bestIdx = idx
                }
            }
            val assignedIdx = if (bestDE <= MAX_THRESHOLD + EPS) bestIdx else -1
            if (assignedIdx == -1) {
                unmapped += 1
            }
            map[k] = assignedIdx
            sum += bestDE
            if (bestDE > mx) mx = bestDE
            Logger.i(
                "PALETTE",
                "iter",
                mapOf(
                    "palette.idx" to k,
                    "thread.idx" to assignedIdx,
                    "thread.code" to assignedIdx.takeIf { it >= 0 }?.let { catalog[it].code } ?: "NONE",
                    "deltaE" to "%.3f".format(bestDE),
                    "status" to if (assignedIdx == -1) "UNMAPPED" else "OK"
                )
            )
        }
        val avg = (sum / colors).toFloat()
        val status: String
        if (avg > AVG_THRESHOLD + 1e-6f || mx > MAX_THRESHOLD + EPS) {
            status = "UNMAPPED"
            for (i in map.indices) {
                if (map[i] != -1) {
                    map[i] = -1
                    unmapped += 1
                }
            }
        } else {
            status = "OK"
        }
        Logger.i(
            "PALETTE",
            "done",
            mapOf(
                "colors" to colors,
                "avgDE" to "%.3f".format(avg),
                "maxDE" to "%.3f".format(mx),
                "unmapped" to unmapped,
                "status" to status
            )
        )
        Logger.i(
            "CATALOG",
            "fit",
            mapOf(
                "avgDE" to "%.3f".format(avg),
                "maxDE" to "%.3f".format(mx),
                "threshold.avg" to AVG_THRESHOLD,
                "threshold.max" to MAX_THRESHOLD,
                "status" to status
            )
        )
        if (status == "UNMAPPED") {
            Logger.i(
                "CATALOG",
                "UNMAPPED",
                mapOf(
                    "avgDE" to "%.3f".format(avg),
                    "maxDE" to "%.3f".format(mx),
                    "colors" to colors,
                    "unmapped" to unmapped
                )
            )
        }
        return CatalogFit(avg, mx.toFloat(), map)
    }
}
