package com.appforcross.editor.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreedyQuantGBITest {

    @Test
    fun gbiProxyMatchesSyntheticBoundary() {
        val width = 3
        val height = 2
        val assignments = intArrayOf(
            0, 0, 1,
            0, 1, 1
        )
        val total = width * height
        val edges = FloatArray(total) { 1f }
        val zeros = FloatArray(total)
        val roi = RoiMap(edges, zeros, zeros, zeros, FloatArray(total) { 0.5f })
        val gbi = Scores.gbiProxy(assignments, width, height, roi)
        assertTrue(gbi in 0f..1f)
        assertEquals(3f / 7f, gbi, 1e-4f)
    }
}
