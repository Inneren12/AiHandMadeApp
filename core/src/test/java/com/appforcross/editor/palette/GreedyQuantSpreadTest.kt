package com.appforcross.editor.palette

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreedyQuantSpreadTest {

    @Test
    fun spreadEnforceGuaranteesMinimumDistance() {
        val palette = floatArrayOf(
            0.52f, 0.02f, -0.01f,
            0.51f, 0.03f, -0.02f,
            0.80f, 0.10f, -0.15f
        )
        val enforced = Spread.enforce(palette, 3.5f)
        var minDelta = Float.POSITIVE_INFINITY
        for (i in 0 until enforced.size / 3) {
            for (j in i + 1 until enforced.size / 3) {
                val baseI = i * 3
                val baseJ = j * 3
                val de = PaletteMath.deltaE(
                    enforced[baseI + 0], enforced[baseI + 1], enforced[baseI + 2],
                    enforced[baseJ + 0], enforced[baseJ + 1], enforced[baseJ + 2]
                ).toFloat()
                if (de < minDelta) {
                    minDelta = de
                }
            }
        }
        assertTrue(minDelta >= 3.5f - 1e-3f)
    }

    @Test
    fun spreadDeterministicForIdenticalInput() {
        val palette = floatArrayOf(
            0.45f, -0.08f, 0.12f,
            0.45f, -0.08f, 0.12f,
            0.70f, 0.05f, -0.20f
        )
        val first = Spread.enforce(palette, 3.5f)
        val second = Spread.enforce(palette, 3.5f)
        assertArrayEquals(first, second, 1e-6f)
    }
}
