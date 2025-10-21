package com.appforcross.editor.pattern

import com.appforcross.editor.logging.Logger

data class PatternResult(
    val labels: IntArray,
    val metricsBefore: TopologyMetrics,
    val metricsAfter: TopologyMetrics
)

class PatternRunner(
    private val params: TopologyParams = TopologyParams()
) {
    fun mergeTopology(
        labels: IntArray,
        width: Int,
        height: Int,
        zones: IntArray,
        edgeMask: FloatArray
    ): PatternResult {
        Logger.i(
            "TOPO",
            "params",
            mapOf(
                "topology.tile" to params.tileSize,
                "topology.halo" to params.halo,
                "topology.edge_block" to params.edgeBlockThreshold,
                "topology.edge_thr" to params.edgeBlockThreshold,
                "topology.minrun.outline" to params.threshold(Zone.OUTLINE.ordinal),
                "topology.minrun.text" to params.threshold(Zone.TEXT.ordinal),
                "topology.minrun.skin" to params.threshold(Zone.SKIN.ordinal),
                "topology.minrun.sky" to params.threshold(Zone.SKY.ordinal),
                "topology.minrun.fill" to params.threshold(Zone.FILL.ordinal)
            )
        )

        val beforeMetrics = TopologyOps.computeMetrics(labels, width, height)
        Logger.i(
            "TOPO",
            "before",
            mapOf(
                "topology.tc_per100" to "%.2f".format(beforeMetrics.threadChangesPer100),
                "topology.islands_per1000" to "%.2f".format(beforeMetrics.smallIslandsPer1000),
                "topology.run_median" to "%.2f".format(beforeMetrics.runMedian)
            )
        )

        val merged = TopologyOps.merge(labels, width, height, zones, edgeMask, params)
        val afterMetrics = TopologyOps.computeMetrics(merged, width, height)
        Logger.i(
            "TOPO",
            "after",
            mapOf(
                "topology.tc_per100" to "%.2f".format(afterMetrics.threadChangesPer100),
                "topology.islands_per1000" to "%.2f".format(afterMetrics.smallIslandsPer1000),
                "topology.run_median" to "%.2f".format(afterMetrics.runMedian),
                "topology.delta.tc" to "%.2f".format(afterMetrics.threadChangesPer100 - beforeMetrics.threadChangesPer100),
                "topology.delta.islands" to "%.2f".format(afterMetrics.smallIslandsPer1000 - beforeMetrics.smallIslandsPer1000),
                "topology.delta.run_median" to "%.2f".format(afterMetrics.runMedian - beforeMetrics.runMedian)
            )
        )

        return PatternResult(merged, beforeMetrics, afterMetrics)
    }
}
