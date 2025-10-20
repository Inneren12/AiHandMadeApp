package com.appforcross.editor.palette

import kotlin.test.Test
import kotlin.test.assertTrue

class TopologyTest {
    @Test
    fun island_kill_removes_singletons() {
        val W = 16
        val H = 8
        val idx = IntArray(W * H) { 0 }
        idx[3] = 1
        idx[W + 5] = 2
        idx[2 * W + 7] = 3
        val (out, metrics) = Topology.clean(idx, W, H, TopologyParams(minIsland = 2))
        assertTrue(out[3] == 0)
        assertTrue(out[W + 5] == 0)
        assertTrue(out[2 * W + 7] == 0)
        assertTrue(metrics.smallIslandsPer1000 > 0f)
    }
}
