package com.appforcross.editor.pattern

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Zones used for per-run thresholds. */
enum class Zone {
    OUTLINE,
    TEXT,
    SKIN,
    SKY,
    FILL
}

/** Parameters controlling the topology merge. */
data class TopologyParams(
    val tileSize: Int = 32,
    val halo: Int = 1,
    val minRunThresholds: IntArray = intArrayOf(2, 3, 3, 4, 3),
    val edgeBlockThreshold: Float = 0.6f
) {
    init {
        require(tileSize > 0) { "tileSize must be positive" }
        require(halo >= 0) { "halo must be non-negative" }
        require(minRunThresholds.size == Zone.values().size) { "minRunThresholds must cover all zones" }
        require(edgeBlockThreshold in 0f..1f) { "edgeBlockThreshold must be within [0,1]" }
    }

    fun threshold(zoneId: Int): Int {
        val safeId = zoneId.coerceIn(0, minRunThresholds.lastIndex)
        val value = minRunThresholds[safeId]
        return if (value <= 0) 1 else value
    }
}

/** Metrics collected on topology states. */
data class TopologyMetrics(
    val threadChangesPer100: Float,
    val smallIslandsPer1000: Float,
    val runMedian: Float
)

/** Helper operations to clean topology maps. */
object TopologyOps {
    private val zoneCount = Zone.values().size

    fun merge(
        labels: IntArray,
        width: Int,
        height: Int,
        zones: IntArray,
        edgeMask: FloatArray,
        params: TopologyParams = TopologyParams()
    ): IntArray {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(labels.size == width * height) { "labels length mismatch" }
        require(zones.size == labels.size) { "zones length mismatch" }
        require(edgeMask.size == labels.size) { "edgeMask length mismatch" }

        val working = labels.copyOf()
        pottsLite(working, width, height, edgeMask, params)
        minRunMerge(working, width, height, zones, edgeMask, params)
        return working
    }

    fun computeMetrics(labels: IntArray, width: Int, height: Int): TopologyMetrics {
        val total = (width * height).coerceAtLeast(1)
        val threadChanges = countThreadChanges(labels, width, height)
        val smallIslands = countSmallIslands(labels, width, height)
        val runMedian = computeRunMedian(labels, width, height)
        return TopologyMetrics(
            threadChangesPer100 = 100f * threadChanges / total,
            smallIslandsPer1000 = 1000f * smallIslands / total,
            runMedian = runMedian
        )
    }

    private fun pottsLite(
        labels: IntArray,
        width: Int,
        height: Int,
        edgeMask: FloatArray,
        params: TopologyParams
    ) {
        val tile = params.tileSize
        val halo = params.halo
        val candidate = IntArray(9)
        val neighborOffsets = intArrayOf(-1, 1, -width, width)
        for (ty in 0 until height step tile) {
            val yStart = ty
            val yEnd = min(height, ty + tile)
            val yMin = max(0, yStart - halo)
            val yMax = min(height, yEnd + halo)
            for (tx in 0 until width step tile) {
                val xStart = tx
                val xEnd = min(width, tx + tile)
                val xMin = max(0, xStart - halo)
                val xMax = min(width, xEnd + halo)
                for (y in yStart until yEnd) {
                    for (x in xStart until xEnd) {
                        val idx = y * width + x
                        var count = 0
                        candidate[count++] = labels[idx]
                        for (dy in -1..1) {
                            val ny = y + dy
                            if (ny < yMin || ny >= yMax) continue
                            for (dx in -1..1) {
                                val nx = x + dx
                                if (nx < xMin || nx >= xMax) continue
                                if (dx == 0 && dy == 0) continue
                                val nIdx = ny * width + nx
                                val label = labels[nIdx]
                                var seen = false
                                for (i in 0 until count) {
                                    if (candidate[i] == label) {
                                        seen = true
                                        break
                                    }
                                }
                                if (!seen && count < candidate.size) {
                                    candidate[count++] = label
                                }
                            }
                        }
                        var bestLabel = labels[idx]
                        var bestEnergy = Float.MAX_VALUE
                        for (i in 0 until count) {
                            val label = candidate[i]
                            var energy = 0f
                            for (offset in neighborOffsets) {
                                val nIdx = idx + offset
                                if (nIdx < 0 || nIdx >= labels.size) continue
                                val nx = nIdx % width
                                val ny = nIdx / width
                                if (abs(nx - x) + abs(ny - y) != 1) continue
                                val edgeWeight = 1f - ((edgeMask[idx] + edgeMask[nIdx]) * 0.5f).coerceIn(0f, 1f)
                                if (labels[nIdx] != label) {
                                    energy += edgeWeight
                                }
                            }
                            if (energy < bestEnergy || (energy == bestEnergy && label < bestLabel)) {
                                bestEnergy = energy
                                bestLabel = label
                            }
                        }
                        labels[idx] = bestLabel
                    }
                }
            }
        }
    }

    private fun minRunMerge(
        labels: IntArray,
        width: Int,
        height: Int,
        zones: IntArray,
        edgeMask: FloatArray,
        params: TopologyParams
    ) {
        val neighborOffsets = intArrayOf(-width - 1, -width, -width + 1, -1, 1, width - 1, width, width + 1)
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                val idx = y * width + x
                val label = labels[idx]
                var end = x + 1
                while (end < width && labels[y * width + end] == label) end++
                val runLength = end - x
                val zoneCounts = IntArray(zoneCount)
                for (xx in x until end) {
                    zoneCounts[zones[y * width + xx]]++
                }
                val zoneId = zoneCounts.indices.maxByOrNull { zoneCounts[it] } ?: Zone.FILL.ordinal
                val threshold = params.threshold(zoneId)
                if (runLength < threshold) {
                    val boundaryLabels = mutableMapOf<Int, Int>()
                    for (xx in x until end) {
                        val p = y * width + xx
                        val px = xx
                        val py = y
                        for (offset in neighborOffsets) {
                            val n = p + offset
                            if (n < 0 || n >= labels.size) continue
                            val nx = n % width
                            val ny = n / width
                            if (abs(nx - px) > 1 || abs(ny - py) > 1) continue
                            if (nx in x until end && ny == y) continue
                            if (hasStrongEdgeBetween(px, py, nx, ny, edgeMask, width, height, params.edgeBlockThreshold)) {
                                continue
                            }
                            val value = labels[n]
                            boundaryLabels[value] = (boundaryLabels[value] ?: 0) + 1
                        }
                    }
                    if (boundaryLabels.isNotEmpty()) {
                        val replacement = boundaryLabels.entries.maxWithOrNull { a, b ->
                            if (a.value != b.value) a.value.compareTo(b.value) else a.key.compareTo(b.key)
                        }?.key ?: label
                        if (replacement != label) {
                            for (xx in x until end) {
                                labels[y * width + xx] = replacement
                            }
                        }
                    }
                }
                x = end
            }
        }
    }

    private fun countThreadChanges(labels: IntArray, width: Int, height: Int): Int {
        var changes = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (x + 1 < width && labels[idx] != labels[idx + 1]) changes++
                if (y + 1 < height && labels[idx] != labels[idx + width]) changes++
            }
        }
        return changes
    }

    private fun countSmallIslands(labels: IntArray, width: Int, height: Int): Int {
        val visited = BooleanArray(labels.size)
        val queue = IntArray(labels.size)
        var islands = 0
        for (i in labels.indices) {
            if (visited[i]) continue
            var head = 0
            var tail = 0
            val label = labels[i]
            visited[i] = true
            queue[tail++] = i
            var size = 0
            while (head < tail) {
                val idx = queue[head++]
                size++
                val x = idx % width
                val y = idx / width
                val neighbors = intArrayOf(idx - 1, idx + 1, idx - width, idx + width)
                for (n in neighbors) {
                    if (n < 0 || n >= labels.size) continue
                    val nx = n % width
                    val ny = n / width
                    if (abs(nx - x) + abs(ny - y) != 1) continue
                    if (!visited[n] && labels[n] == label) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
            }
            if (size <= 3) islands++
        }
        return islands
    }

    private fun computeRunMedian(labels: IntArray, width: Int, height: Int): Float {
        val runs = ArrayList<Int>()
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                val start = x
                val value = labels[y * width + x]
                while (x < width && labels[y * width + x] == value) x++
                runs.add(x - start)
            }
        }
        if (runs.isEmpty()) return 0f
        runs.sort()
        val mid = runs.size / 2
        return if (runs.size % 2 == 1) runs[mid].toFloat() else 0.5f * (runs[mid - 1] + runs[mid])
    }

    private fun hasStrongEdgeBetween(
        x: Int,
        y: Int,
        nx: Int,
        ny: Int,
        edgeMask: FloatArray,
        width: Int,
        height: Int,
        threshold: Float
    ): Boolean {
        fun sample(ix: Int, iy: Int): Float {
            val sx = ix.coerceIn(0, width - 1)
            val sy = iy.coerceIn(0, height - 1)
            return edgeMask[sy * width + sx]
        }

        val e1 = sample(x, y)
        val e2 = sample(nx, ny)
        val em = sample((x + nx) shr 1, (y + ny) shr 1)
        return max(e1, max(e2, em)) >= threshold
    }
}
