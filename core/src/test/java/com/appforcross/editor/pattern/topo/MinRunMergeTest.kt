package com.appforcross.editor.pattern.topo

import com.appforcross.editor.pattern.PatternRunner
import com.appforcross.editor.pattern.Zone
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
