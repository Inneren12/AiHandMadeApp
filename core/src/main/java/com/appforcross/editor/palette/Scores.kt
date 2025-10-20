package com.appforcross.editor.palette

import kotlin.math.max

object Scores {
    fun photoScoreStar(
        labPixels: FloatArray,
        palette: FloatArray,
        assign: IntArray,
        roiMap: RoiMap
    ): Float {
        val total = assign.size
        var sumErr = 0.0
        var sumEdge = 0.0
        var sumTex = 0.0
        var sumFlat = 0.0
        for (i in 0 until total) {
            val idx = assign[i]
            val l = labPixels[i * 3 + 0]
            val a = labPixels[i * 3 + 1]
            val b = labPixels[i * 3 + 2]
            val kl = palette[idx * 3 + 0]
            val ka = palette[idx * 3 + 1]
            val kb = palette[idx * 3 + 2]
            val de = PaletteMath.deltaE(l, a, b, kl, ka, kb)
            sumErr += de
            sumEdge += roiMap.edges[i].toDouble()
            sumTex += roiMap.hitex[i].toDouble()
            sumFlat += roiMap.flat[i].toDouble()
        }
        val avgErr = (sumErr / max(1, total)).toFloat()
        val avgEdge = (sumEdge / max(1, total)).toFloat()
        val avgTex = (sumTex / max(1, total)).toFloat()
        val avgFlat = (sumFlat / max(1, total)).toFloat()
        val score = -avgErr + 0.08f * avgEdge + 0.05f * avgTex - 0.03f * avgFlat
        return score
    }

    fun deltaEStats(
        labPixels: FloatArray,
        palette: FloatArray,
        assign: IntArray
    ): Pair<Float, Float> {
        val total = assign.size
        var sum = 0.0
        var maxDe = 0.0
        for (i in 0 until total) {
            val idx = assign[i]
            val l = labPixels[i * 3 + 0]
            val a = labPixels[i * 3 + 1]
            val b = labPixels[i * 3 + 2]
            val kl = palette[idx * 3 + 0]
            val ka = palette[idx * 3 + 1]
            val kb = palette[idx * 3 + 2]
            val de = PaletteMath.deltaE(l, a, b, kl, ka, kb)
            sum += de
            if (de > maxDe) maxDe = de
        }
        val avg = if (total == 0) 0f else (sum / total).toFloat()
        return avg to maxDe.toFloat()
    }
}
