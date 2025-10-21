package com.appforcross.editor.pattern.topo

import com.appforcross.editor.pattern.PatternRunner
import com.appforcross.editor.pattern.Zone
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfettiReductionTest {
    @Test
    fun cleanup_reduces_confetti_and_extends_runs() {
        val width = 16
        val height = 10
        val size = width * height
        val labels = IntArray(size) { 0 }
        val confetti = intArrayOf(1, 3, 5, 7, width + 2, width + 9, 2 * width + 4, 3 * width + 11, 4 * width + 1, 5 * width + 13)
        for ((i, index) in confetti.withIndex()) {
            labels[index] = (i % 3) + 1
        }
        val zones = IntArray(size) { Zone.FILL.ordinal }
        val edgeMask = FloatArray(size)

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertTrue(result.metricsAfter.threadChangesPer100 < result.metricsBefore.threadChangesPer100, "thread changes should drop")
        assertTrue(result.metricsAfter.smallIslandsPer1000 < result.metricsBefore.smallIslandsPer1000, "islands should drop")
        assertTrue(result.metricsAfter.runMedian >= result.metricsBefore.runMedian, "median run should grow")
        assertTrue(result.metricsAfter.threadChangesPer100 <= 10f, "thread change acceptance threshold")
        assertTrue(result.metricsAfter.smallIslandsPer1000 <= 3f, "island acceptance threshold")
    }
}
