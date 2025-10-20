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
        var sumSkin = 0.0
        var sumSky = 0.0
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
            sumSkin += roiMap.skin[i].toDouble()
            sumSky += roiMap.sky[i].toDouble()
            sumTex += roiMap.hitex[i].toDouble()
            sumFlat += roiMap.flat[i].toDouble()
        }
        val avgErr = (sumErr / max(1, total)).toFloat()
        val avgEdge = (sumEdge / max(1, total)).toFloat()
        val avgSkin = (sumSkin / max(1, total)).toFloat()
        val avgSky = (sumSky / max(1, total)).toFloat()
        val avgTex = (sumTex / max(1, total)).toFloat()
        val avgFlat = (sumFlat / max(1, total)).toFloat()
        val score = -avgErr + 0.09f * avgEdge + 0.07f * avgSkin + 0.05f * avgSky +
            0.05f * avgTex - 0.03f * avgFlat
        return score
    }

    data class DeltaEMetrics(
        val min: Float,
        val avg: Float,
        val max: Float,
        val p95: Float
    )

    fun deltaEMetrics(
        labPixels: FloatArray,
        palette: FloatArray,
        assign: IntArray
    ): DeltaEMetrics {
        val total = assign.size
        var sum = 0.0
        var maxDe = 0.0
        var minDe = Double.POSITIVE_INFINITY
        val errors = DoubleArray(total)
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
            if (de < minDe) minDe = de
            errors[i] = de
        }
        val avg = if (total == 0) 0f else (sum / total).toFloat()
        val percentile = if (total == 0) 0f else {
            errors.sort()
            val idx = ((total - 1) * 0.95).toInt().coerceIn(0, total - 1)
            errors[idx].toFloat()
        }
        val min = if (total == 0) 0f else minDe.toFloat()
        return DeltaEMetrics(min, avg, maxDe.toFloat(), percentile)
    }

    fun gbiProxy(
        assignments: IntArray,
        width: Int,
        height: Int,
        roi: RoiMap
    ): Float {
        if (assignments.isEmpty()) return 0f
        var weightedTransitions = 0.0
        var totalWeight = 0.0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val edgeWeight = roi.edges[idx].toDouble().coerceAtLeast(1e-6)
                if (x + 1 < width) {
                    val neighborIdx = idx + 1
                    val transition = if (assignments[idx] != assignments[neighborIdx]) 1.0 else 0.0
                    weightedTransitions += transition * edgeWeight
                    totalWeight += edgeWeight
                }
                if (y + 1 < height) {
                    val neighborIdx = idx + width
                    val transition = if (assignments[idx] != assignments[neighborIdx]) 1.0 else 0.0
                    weightedTransitions += transition * edgeWeight
                    totalWeight += edgeWeight
                }
            }
        }
        if (totalWeight <= 1e-6) return 0f
        return (weightedTransitions / totalWeight).toFloat().coerceIn(0f, 1f)
    }
}
