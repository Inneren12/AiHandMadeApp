package com.appforcross.editor.palette

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantDeterminismTest {

    @Test
    fun greedyQuantIsDeterministic() {
        val width = 6
        val height = 5
        val rgb = FloatArray(width * height * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = (y * width + x) * 3
                val base = (x + y).toFloat() / (width + height)
                rgb[idx + 0] = (0.3f + base * 0.6f).coerceIn(0f, 1f)
                rgb[idx + 1] = (0.2f + 0.5f * base).coerceIn(0f, 1f)
                rgb[idx + 2] = (0.1f + 0.7f * (1f - base)).coerceIn(0f, 1f)
            }
        }
        val params = QuantParams(kStart = 5, kMax = 8, dE00Min = 3.5f)
        val first = GreedyQuant.run(rgb, width, height, params)
        val second = GreedyQuant.run(rgb, width, height, params)

        assertEquals(first.metrics.K, second.metrics.K)
        assertEquals(first.metrics.photoScoreStar, second.metrics.photoScoreStar, 1e-5f)
        assertEquals(first.metrics.gbiProxy, second.metrics.gbiProxy, 1e-5f)
        assertArrayEquals(first.colorsOKLab, second.colorsOKLab, 1e-4f)
        assertArrayEquals(first.assignments, second.assignments)
    }
}
