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
        val bestDelta = DoubleArray(colors) { Double.POSITIVE_INFINITY }
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
            bestDelta[k] = bestDE
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
        val mapBeforeThresholds = map.copyOf()
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
        logAnchors(mapBeforeThresholds, bestDelta, catalog, status)
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

    private fun logAnchors(
        mapBeforeThresholds: IntArray,
        bestDelta: DoubleArray,
        catalog: List<ThreadColor>,
        status: String
    ) {
        val primaryAnchor = anchorMatchFor(0, mapBeforeThresholds, bestDelta, catalog)
        val secondaryAnchor = anchorMatchFor(1, mapBeforeThresholds, bestDelta, catalog)
        val skinAnchor = anchorMatchFor(2, mapBeforeThresholds, bestDelta, catalog)
        val skyAnchor = anchorMatchFor(3, mapBeforeThresholds, bestDelta, catalog)
        val skinReason = if (skinAnchor == null) {
            anchorFailureReason(2, mapBeforeThresholds, bestDelta, status)
        } else {
            null
        }
        val skyReason = if (skyAnchor == null) {
            anchorFailureReason(3, mapBeforeThresholds, bestDelta, status)
        } else {
            null
        }
        Logger.i(
            "PALETTE",
            "catalog.map",
            mapOf(
                "primary" to primaryAnchor?.toMap() ?: mapOf("ok" to false),
                "secondary" to secondaryAnchor?.toMap() ?: mapOf("ok" to false),
                "skin" to skinAnchor?.let { mapOf("ok" to true) } ?: mapOf(
                    "ok" to false,
                    "reason" to skinReason
                ),
                "sky" to skyAnchor?.let { mapOf("ok" to true) } ?: mapOf(
                    "ok" to false,
                    "reason" to skyReason
                )
            )
        )
        run {
            val payload = linkedMapOf<String, Any?>()
            payload["primary"] = primaryAnchor?.toMap() ?: mapOf("ok" to false)
            payload["secondary"] = secondaryAnchor?.toMap() ?: mapOf("ok" to false)
            payload["skin"] = if (skinAnchor != null) {
                mapOf("ok" to true)
            } else {
                mapOf("ok" to false, "reason" to skinReason)
            }
            payload["sky"] = if (skyAnchor != null) {
                mapOf("ok" to true)
            } else {
                mapOf("ok" to false, "reason" to skyReason)
            }
            Logger.i("PALETTE", "catalog.map", payload)
        }
    }

    private fun anchorMatchFor(
        paletteIndex: Int,
        map: IntArray,
        bestDelta: DoubleArray,
        catalog: List<ThreadColor>
    ): AnchorMatch? {
        if (paletteIndex !in map.indices) return null
        val threadIndex = map[paletteIndex]
        if (threadIndex == -1) return null
        val thread = catalog.getOrNull(threadIndex) ?: return null
        val delta = bestDelta.getOrElse(paletteIndex) { Double.POSITIVE_INFINITY }
        if (!delta.isFinite()) return null
        return AnchorMatch(paletteIndex, threadIndex, thread, delta)
    }

    private fun anchorFailureReason(
        paletteIndex: Int,
        map: IntArray,
        bestDelta: DoubleArray,
        status: String
    ): String {
        if (paletteIndex !in map.indices) {
            return "palette.missing"
        }
        if (map[paletteIndex] == -1) {
            val delta = bestDelta.getOrElse(paletteIndex) { Double.POSITIVE_INFINITY }
            return when {
                status == "UNMAPPED" -> "catalog.thresholds"
                delta.isFinite() && delta > MAX_THRESHOLD + EPS -> "deltaE.threshold"
                !delta.isFinite() -> "no_match"
                else -> "no_candidate"
            }
        }
        return "ok"
    }

    private data class AnchorMatch(
        val paletteIndex: Int,
        val threadIndex: Int,
        val thread: ThreadColor,
        val deltaE: Double
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "ok" to true,
            "palette.idx" to paletteIndex,
            "thread.idx" to threadIndex,
            "thread.code" to thread.code,
            "thread.name" to thread.name,
            "deltaE" to "%.3f".format(deltaE)
        )
    }
}
