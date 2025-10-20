package com.appforcross.editor.palette

import java.util.HashSet
import org.junit.Assert.assertTrue
import org.junit.Test

class GreedyQuantTest {

    @Test
    fun grows_until_kneedle() {
        val w = 12
        val h = 10
        val rgb = FloatArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = (y * w + x) * 3
                val base = (x.toFloat() / w * 0.6f + y.toFloat() / h * 0.4f)
                rgb[idx + 0] = base
                rgb[idx + 1] = (base * 0.5f + (x % 3) * 0.15f).coerceIn(0f, 1f)
                rgb[idx + 2] = (1f - base) * 0.5f
            }
        }
        val params = QuantParams(kStart = 6, kMax = 20, kneedleTau = 0.02f)
        val result = GreedyQuant.run(rgb, w, h, params)
        assertTrue(result.metrics.K >= params.kStart)
        assertTrue(result.metrics.K < params.kMax)
        assertTrue(result.metrics.K > params.kStart)
    }

    @Test
    fun spread_enforced() {
        val w = 8
        val h = 8
        val rgb = FloatArray(w * h * 3)
        for (i in 0 until w * h) {
            val value = (i % w) / w.toFloat()
            rgb[i * 3 + 0] = value
            rgb[i * 3 + 1] = 1f - value
            rgb[i * 3 + 2] = 0.5f * value
        }
        val params = QuantParams(kStart = 4, kMax = 6, dE00Min = 4.0f)
        val result = GreedyQuant.run(rgb, w, h, params)
        val palette = result.colorsOKLab
        var minDelta = Float.MAX_VALUE
        for (i in 0 until result.metrics.K) {
            for (j in i + 1 until result.metrics.K) {
                val baseI = i * 3
                val baseJ = j * 3
                val de = PaletteMath.deltaE(
                    palette[baseI + 0], palette[baseI + 1], palette[baseI + 2],
                    palette[baseJ + 0], palette[baseJ + 1], palette[baseJ + 2]
                ).toFloat()
                if (de < minDelta) minDelta = de
            }
        }
        assertTrue(minDelta >= params.dE00Min - 0.2f)
    }

    @Test
    fun roi_bias_effect() {
        val w = 10
        val h = 6
        val rgb = FloatArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = (y * w + x) * 3
                val edgeZone = if (x >= w / 2) 1f else 0f
                val valR = edgeZone * ((x % 2) * 0.6f + 0.2f)
                val valG = (1f - edgeZone) * 0.5f + edgeZone * 0.2f
                val valB = edgeZone * 0.8f
                rgb[idx + 0] = valR
                rgb[idx + 1] = valG
                rgb[idx + 2] = valB
            }
        }
        val roiMap = Roi.computeProxy(rgb, w, h)
        val paramsDefault = QuantParams(kStart = 4, kMax = 8)
        val paramsEdge = QuantParams(kStart = 4, kMax = 8, roiWeights = ROIWeights(edges = 4f, hitex = 1.2f, flat = 0.2f))
        val resDefault = GreedyQuant.run(rgb, w, h, paramsDefault)
        val resEdge = GreedyQuant.run(rgb, w, h, paramsEdge)

        val edgeIndices = mutableListOf<Int>()
        for (i in 0 until roiMap.size) {
            if (roiMap.edges[i] > 0.5f) edgeIndices.add(i)
        }
        val uniqueDefault = uniqueColors(resDefault.assignments, edgeIndices)
        val uniqueEdge = uniqueColors(resEdge.assignments, edgeIndices)
        assertTrue(uniqueEdge >= uniqueDefault)
    }

    private fun uniqueColors(assign: IntArray, indices: List<Int>): Int {
        val seen = HashSet<Int>()
        for (i in indices) {
            seen.add(assign[i])
        }
        return seen.size
    }
}
