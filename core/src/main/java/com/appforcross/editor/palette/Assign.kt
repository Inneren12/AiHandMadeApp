package com.appforcross.editor.palette

object Assign {
    fun assignOKLab(labPixels: FloatArray, palette: FloatArray): IntArray {
        val total = labPixels.size / 3
        val colors = palette.size / 3
        val assign = IntArray(total)
        for (i in 0 until total) {
            val l = labPixels[i * 3 + 0]
            val a = labPixels[i * 3 + 1]
            val b = labPixels[i * 3 + 2]
            var best = 0
            var bestDe = Double.POSITIVE_INFINITY
            for (k in 0 until colors) {
                val kl = palette[k * 3 + 0]
                val ka = palette[k * 3 + 1]
                val kb = palette[k * 3 + 2]
                val de = PaletteMath.deltaE(l, a, b, kl, ka, kb)
                if (de < bestDe) {
                    bestDe = de
                    best = k
                }
            }
            assign[i] = best
        }
        return assign
    }
}
