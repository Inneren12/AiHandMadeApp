package com.appforcross.editor.palette

import com.appforcross.editor.logging.Logger

data class ThreadColor(val code: String, val name: String, val okLab: FloatArray)
data class CatalogFit(val avgDE: Float, val maxDE: Float, val mapping: IntArray)

object CatalogMapper {
    fun mapToCatalog(paletteOKLab: FloatArray, catalog: List<ThreadColor>): CatalogFit {
        require(paletteOKLab.size % 3 == 0) { "Palette must be multiple of 3" }
        require(catalog.isNotEmpty()) { "Catalog is empty" }
        val colors = paletteOKLab.size / 3
        val map = IntArray(colors)
        var sum = 0.0
        var mx = 0.0
        for (k in 0 until colors) {
            val L = paletteOKLab[k * 3 + 0]
            val a = paletteOKLab[k * 3 + 1]
            val b = paletteOKLab[k * 3 + 2]
            var best = 0
            var bestDE = Double.POSITIVE_INFINITY
            for ((idx, tc) in catalog.withIndex()) {
                val lab = tc.okLab
                val de = PaletteMath.deltaE(L, a, b, lab[0], lab[1], lab[2])
                if (de < bestDE) {
                    bestDE = de
                    best = idx
                }
            }
            map[k] = best
            sum += bestDE
            if (bestDE > mx) mx = bestDE
        }
        val avg = (sum / colors).toFloat()
        Logger.i(
            "CATALOG",
            "fit",
            mapOf(
                "avgDE" to "%.3f".format(avg),
                "maxDE" to "%.3f".format(mx)
            )
        )
        return CatalogFit(avg, mx.toFloat(), map)
    }
}
