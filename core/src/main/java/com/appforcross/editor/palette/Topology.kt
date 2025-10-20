package com.appforcross.editor.palette

import com.appforcross.editor.logging.Logger
import java.util.LinkedHashMap

/** Parameters for palette topology cleanup. */
data class TopologyParams(
    val minRunEdge: Int = 3,
    val minRunPlain: Int = 4,
    val minIsland: Int = 3
)

/** Metrics reported after topology cleanup. */
data class TopologyMetrics(
    val changesPer100: Float,
    val smallIslandsPer1000: Float,
    val runLen50: Float
)

/** Simple topology post-processing: removes small islands and short runs. */
object Topology {
    /**
     * @param index Palette index map (W*H).
     * @param W Width.
     * @param H Height.
     * @param params Cleanup thresholds.
     * @return Pair of cleaned index map and metrics.
     */
    fun clean(index: IntArray, W: Int, H: Int, params: TopologyParams = TopologyParams()): Pair<IntArray, TopologyMetrics> {
        val t0 = System.nanoTime()
        Logger.i(
            "TOPO",
            "params",
            mapOf(
                "topology.minrun.edge" to params.minRunEdge,
                "topology.minrun.plain" to params.minRunPlain,
                "topology.island_kill" to params.minIsland,
                "image.width" to W,
                "image.height" to H
            )
        )
        val out = index.clone()
        var removed = 0
        for (y in 0 until H) for (x in 0 until W) {
            val i = y * W + x
            val c = out[i]
            var same = 0
            var diff = 0
            if (x > 0) if (out[i - 1] == c) same++ else diff++
            if (x < W - 1) if (out[i + 1] == c) same++ else diff++
            if (y > 0) if (out[i - W] == c) same++ else diff++
            if (y < H - 1) if (out[i + W] == c) same++ else diff++
            if (same == 0 && diff >= 2) {
                out[i] = majority4(out, x, y, W, H)
                removed++
            }
        }
        var changes = 0
        for (y in 0 until H) {
            var x = 0
            while (x < W) {
                val start = x
                val c = out[y * W + x]
                while (x < W && out[y * W + x] == c) x++
                val len = x - start
                if (len in 1 until params.minRunPlain) {
                    val left = if (start > 0) out[y * W + start - 1] else c
                    val right = if (x < W) out[y * W + x] else c
                    val rep = when {
                        left == right -> left
                        start > 0 -> left
                        x < W -> right
                        else -> c
                    }
                    for (xx in start until x) {
                        out[y * W + xx] = rep
                        changes++
                    }
                }
            }
        }
        val total = (W * H).coerceAtLeast(1)
        val metrics = TopologyMetrics(
            changesPer100 = 100f * changes / total,
            smallIslandsPer1000 = 1000f * removed / total,
            runLen50 = 0.0f
        )
        Logger.i(
            "TOPO",
            "verify",
            mapOf(
                "topology.changes_per100" to "%.2f".format(metrics.changesPer100),
                "topology.islands_per1000" to "%.2f".format(metrics.smallIslandsPer1000),
                "topology.runlen_p50" to metrics.runLen50
            )
        )
        val ms = ((System.nanoTime() - t0) / 1_000_000).toInt()
        val rt = Runtime.getRuntime()
        val usedMb = ((rt.totalMemory() - rt.freeMemory()) / 1_000_000.0)
        Logger.i(
            "TOPO",
            "done",
            mapOf(
                "ms" to ms,
                "memMB" to "%.2f".format(usedMb)
            )
        )
        return out to metrics
    }

    private fun majority4(a: IntArray, x: Int, y: Int, W: Int, H: Int): Int {
        val counts = LinkedHashMap<Int, Int>(4)
        fun add(c: Int) { counts[c] = (counts[c] ?: 0) + 1 }
        if (x > 0) add(a[y * W + x - 1])
        if (x < W - 1) add(a[y * W + x + 1])
        if (y > 0) add(a[(y - 1) * W + x])
        if (y < H - 1) add(a[(y + 1) * W + x])
        return counts.maxByOrNull { it.value }?.key ?: a[y * W + x]
    }
}
