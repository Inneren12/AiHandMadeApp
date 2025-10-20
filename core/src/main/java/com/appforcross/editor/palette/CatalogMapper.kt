package com.appforcross.editor.palette

import com.appforcross.editor.color.ColorMgmt
import com.appforcross.editor.logging.Logger
import kotlin.math.abs

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
        val eps = 1e-12
        for (k in 0 until colors) {
            val L = paletteOKLab[k * 3 + 0]
            val a = paletteOKLab[k * 3 + 1]
            val b = paletteOKLab[k * 3 + 2]
            var best = 0
            var bestDE = Double.POSITIVE_INFINITY
            for ((idx, tc) in catalog.withIndex()) {
                require(tc.okLab.size >= 3) { "ThreadColor.okLab must have at least 3 components" }
                val de = ColorMgmt.deltaE00(
                    floatArrayOf(L, a, b),
                    floatArrayOf(tc.okLab[0], tc.okLab[1], tc.okLab[2])
                )
                if (de < bestDE - eps || (abs(de - bestDE) <= eps && idx < best)) {
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
