package com.appforcross.editor.palette

import kotlin.math.abs

data class SplitCandidate(
    val cluster: Int,
    val priority: Int,
    val pivotIndex: Int,
    val seedColor: FloatArray
)

object Split {
    fun pickNext(
        labPixels: FloatArray,
        palette: FloatArray,
        assign: IntArray,
        roiMap: RoiMap,
        weights: ROIWeights
    ): SplitCandidate {
        var bestIdx = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var bestPriority = Int.MAX_VALUE
        val total = assign.size
        for (i in 0 until total) {
            val cluster = assign[i]
            val l = labPixels[i * 3 + 0]
            val a = labPixels[i * 3 + 1]
            val b = labPixels[i * 3 + 2]
            val kl = palette[cluster * 3 + 0]
            val ka = palette[cluster * 3 + 1]
            val kb = palette[cluster * 3 + 2]
            val error = PaletteMath.deltaE(l, a, b, kl, ka, kb)
            val growth = Roi.roiGrowthAt(i, roiMap, weights)
            val weighted = error * growth.weight
            if (growth.priority < bestPriority) {
                bestPriority = growth.priority
                bestScore = weighted
                bestIdx = i
            } else if (growth.priority == bestPriority) {
                if (weighted > bestScore + 1e-9) {
                    bestScore = weighted
                    bestIdx = i
                } else if (abs(weighted - bestScore) <= 1e-9 && i < bestIdx) {
                    bestIdx = i
                }
            }
        }
        val cluster = assign[bestIdx]
        val l = labPixels[bestIdx * 3 + 0]
        val a = labPixels[bestIdx * 3 + 1]
        val b = labPixels[bestIdx * 3 + 2]
        val color = floatArrayOf(l, a, b)
        return SplitCandidate(cluster, bestPriority, bestIdx, color)
    }
}
