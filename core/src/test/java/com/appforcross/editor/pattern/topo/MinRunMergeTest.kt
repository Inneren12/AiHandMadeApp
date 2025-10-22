package com.appforcross.editor.pattern.topo

import com.appforcross.editor.pattern.PatternRunner
import com.appforcross.editor.pattern.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinRunMergeTest {
    @Test
    fun short_runs_are_replaced_by_majority() {
        val width = 10
        val height = 4
        val size = width * height
        val labels = IntArray(size) { 0 }
        labels[2] = 1
        labels[width + 5] = 2
        labels[2 * width + 4] = 3
        labels[3 * width + 7] = 4
        val zones = IntArray(size) { Zone.TEXT.ordinal }
        val edgeMask = FloatArray(size)

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertEquals(0, result.labels[2], "isolated run should be absorbed")
        assertEquals(0, result.labels[width + 5], "isolated run should be absorbed")
        assertEquals(0, result.labels[2 * width + 4], "isolated run should be absorbed")
        assertEquals(0, result.labels[3 * width + 7], "isolated run should be absorbed")
    }

    @Test
    fun strong_edge_blocks_replacement() {
        val width = 6
        val height = 3
        val size = width * height
        val labels = IntArray(size) { 0 }
        val runIndex = width + 2
        labels[runIndex] = 5
        val zones = IntArray(size) { Zone.OUTLINE.ordinal }
        val edgeMask = FloatArray(size)
        edgeMask[runIndex] = 0.9f
        edgeMask[runIndex - 1] = 0.9f
        edgeMask[runIndex + 1] = 0.9f

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertEquals(5, result.labels[runIndex], "edge-protected run must remain")
    }

    @Test
    fun dilated_edge_mask_blocks_merge() {
        val width = 5
        val height = 5
        val size = width * height
        val labels = IntArray(size) { 0 }
        val runIndex = 2 * width + 2
        labels[runIndex] = 7
        val zones = IntArray(size) { Zone.OUTLINE.ordinal }
        val edgeMask = FloatArray(size)
        edgeMask[runIndex - 1] = 0.85f

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertEquals(7, result.labels[runIndex], "dilated barrier should protect the run")
    }

    @Test
    fun tie_votes_keep_original_label() {
        val width = 3
        val height = 3
        val labels = intArrayOf(
            1, 1, 2,
            1, 5, 2,
            3, 2, 2
        )
        val zones = IntArray(width * height) { Zone.OUTLINE.ordinal }
        val edgeMask = FloatArray(width * height)

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertEquals(5, result.labels[width + 1], "ties must retain the original run label")
    }

    @Test
    fun near_threshold_probe_cancels_merge() {
        val width = 4
        val height = 4
        val size = width * height
        val labels = IntArray(size) { 0 }
        val runIndex = width + 1
        labels[runIndex] = 6
        val zones = IntArray(size) { Zone.OUTLINE.ordinal }
        val edgeMask = FloatArray(size)
        edgeMask[runIndex] = 0.29f
        edgeMask[runIndex + width] = 0.29f

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertEquals(6, result.labels[runIndex], "near-threshold probe must cancel merge")
    }

    @Test
    fun metrics_are_reported() {
        val width = 6
        val height = 2
        val labels = IntArray(width * height) { if (it % 2 == 0) 1 else 0 }
        val zones = IntArray(width * height) { Zone.FILL.ordinal }
        val edgeMask = FloatArray(width * height)

        val runner = PatternRunner()
        val result = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertTrue(result.metricsBefore.threadChangesPer100 >= 0f)
        assertTrue(result.metricsAfter.threadChangesPer100 >= 0f)
        assertTrue(result.metricsBefore.runMedian >= 0f)
        assertTrue(result.metricsAfter.runMedian >= 0f)
    }
}
