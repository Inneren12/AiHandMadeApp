package com.appforcross.editor.palette

object Split {
    fun pickNext(
        labPixels: FloatArray,
        palette: FloatArray,
        assign: IntArray,
        roiMap: RoiMap,
        weights: ROIWeights
    ): FloatArray {
        var bestIdx = 0
        var bestScore = Double.NEGATIVE_INFINITY
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
            val roiWeight = Roi.roiWeightAt(i, roiMap, weights).toDouble()
            val weighted = error * roiWeight
            if (weighted > bestScore) {
                bestScore = weighted
                bestIdx = i
            }
        }
        val l = labPixels[bestIdx * 3 + 0]
        val a = labPixels[bestIdx * 3 + 1]
        val b = labPixels[bestIdx * 3 + 2]
        return floatArrayOf(l, a, b)
    }
}
