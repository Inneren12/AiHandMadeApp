package com.appforcross.editor.prescale

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildSpecTest {

    @Test
    fun decide_within_corridor() {
        val width = 400
        val height = 300
        val luminance = FloatArray(width * height) { index ->
            val x = index % width
            x.toFloat() / width.toFloat()
        }

        val decision = BuildSpec.decide(width, height, luminance)
        val params = BuildParams()
        val fabric = params.fabric

        assertTrue(decision.wst in fabric.corridorMin..fabric.corridorMax, "wst=${decision.wst}")
        assertEquals((height.toFloat() / width.toFloat() * decision.wst).roundToInt(), decision.hst)
        assertTrue(decision.sigma >= 0f)
        assertTrue(decision.phase.dx in 0..1)
        assertTrue(decision.phase.dy in 0..1)
        assertTrue(decision.filter == "EWA_Mitchell" || decision.filter == "EWA_Lanczos3")
    }

    @Test
    fun phase_changes_with_grid() {
        val width = 400
        val height = 128
        val evenColumns = syntheticPhaseImage(width, height) { x -> x % 2 == 0 }
        val oddColumns = syntheticPhaseImage(width, height) { x -> x % 2 == 1 }

        val evenDecision = BuildSpec.decide(width, height, evenColumns)
        val oddDecision = BuildSpec.decide(width, height, oddColumns)

        assertEquals(0, evenDecision.phase.dx, "expected phase dx=0 for even columns")
        assertEquals(1, oddDecision.phase.dx, "expected phase dx=1 for odd columns")
        assertTrue(evenDecision.phase.dy in 0..1)
        assertTrue(oddDecision.phase.dy in 0..1)
    }

    @Test
    fun frcheck_bump_wst_on_fail() {
        val width = 400
        val height = 300
        val luminance = FloatArray(width * height) { 0f }
        val params = BuildParams()
        val fabric = params.fabric
        val mid = fabric.corridorMin + (fabric.corridorMax - fabric.corridorMin) / 2
        val base = mid.coerceIn(fabric.corridorMin, fabric.corridorMax)
        val expectedMin = (base * 1.1f).roundToInt().coerceAtMost(fabric.corridorMax)

        val decision = BuildSpec.decide(width, height, luminance, params)

        assertTrue(decision.wst >= expectedMin, "wst=${decision.wst} expected>=$expectedMin")
    }

    private fun syntheticPhaseImage(width: Int, height: Int, edgeColumn: (Int) -> Boolean): FloatArray {
        val data = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if (edgeColumn(x)) {
                    if (y % 2 == 0) 1f else 0f
                } else {
                    0f
                }
                data[y * width + x] = value
            }
        }
        return data
    }
}
