package com.appforcross.editor.color

import kotlin.test.Test
import kotlin.test.assertTrue

class ColorMgmtDeltaE2000Test {
    private fun close(a: Double, b: Double, eps: Double) = kotlin.math.abs(a - b) <= eps

    @Test
    fun sharma_pairs_match_reference() {
        val p1a = floatArrayOf(50.0f, 2.6772f, -79.7751f)
        val p1b = floatArrayOf(50.0f, 0.0f, -82.7485f)
        val d1 = ColorMgmt.deltaE00(p1a, p1b)
        assertTrue(close(d1, 2.0425, 0.01), "ΔE00 mismatch #1: $d1")

        val p2a = floatArrayOf(50.0f, 3.1571f, -77.2803f)
        val p2b = floatArrayOf(50.0f, 0.0f, -82.7485f)
        val d2 = ColorMgmt.deltaE00(p2a, p2b)
        assertTrue(close(d2, 2.8615, 0.01), "ΔE00 mismatch #2: $d2")

        val p3a = floatArrayOf(50.0f, 2.8361f, -74.0200f)
        val p3b = floatArrayOf(50.0f, 0.0f, -82.7485f)
        val d3 = ColorMgmt.deltaE00(p3a, p3b)
        assertTrue(close(d3, 3.4412, 0.02), "ΔE00 mismatch #3: $d3")
    }
}
