package com.appforcross.editor.pattern.topo

import com.appforcross.editor.pattern.PatternRunner
import com.appforcross.editor.pattern.Zone
import kotlin.test.Test
import kotlin.test.assertContentEquals

class PottsLiteDeterminismTest {
    @Test
    fun same_input_produces_same_labels() {
        val width = 48
        val height = 32
        val size = width * height
        val labels = IntArray(size) { (it * 17 + 5) % 6 }
        val zones = IntArray(size) { Zone.FILL.ordinal }
        val edgeMask = FloatArray(size) { ((it % 11) / 10f).coerceIn(0f, 1f) * 0.5f }

        val runner = PatternRunner()
        val first = runner.mergeTopology(labels, width, height, zones, edgeMask)
        val second = runner.mergeTopology(labels, width, height, zones, edgeMask)

        assertContentEquals(first.labels, second.labels)
    }
}
