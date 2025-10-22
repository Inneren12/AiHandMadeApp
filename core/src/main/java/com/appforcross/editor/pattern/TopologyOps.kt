package com.appforcross.editor.pattern

import java.util.HashMap
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

/** Edge barrier threshold for min-run replacement guard (in [0,1]). */
private const val EDGE_BLOCK_THR: Float = 0.25f

/** Минимум голосов большинства, чтобы перекрасить короткий ран. */
private const val MIN_VOTES_FOR_MERGE: Int = 2

/** Parameters controlling the topology merge. */
data class TopologyParams(
    val tileSize: Int = 32,
    val halo: Int = 1,
    val minRunThresholds: IntArray = intArrayOf(2, 3, 3, 4, 3),
    /* Edge barrier threshold for min-run replacement guard (in [0,1]). */
    val edgeBlockThreshold: Float = EDGE_BLOCK_THR
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

    /** Локальный максимум edgeMask в окрестности 7×7 (радиус=3) для консервативной защиты. */
    private fun localMax7x7(
        edgeMask: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Float {
        var maxValue = 0f
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        for (dy in -3..3) {
            for (dx in -3..3) {
                val px = (cx + dx).coerceIn(0, width - 1)
                val py = (cy + dy).coerceIn(0, height - 1)
                val value = edgeMask[py * width + px]
                if (value > maxValue) {
                    maxValue = value
                }
            }
        }
        return maxValue
    }

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
        minRunMerge(working, width, height, zones, edgeMask, params, params.edgeBlockThreshold)
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
        params: TopologyParams,
        edgeThreshold: Float
    ) {
        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val members = IntArray(total)
        val boundaryVotes = HashMap<Int, Int>(8)

        fun selectThreshold(zoneId: Int): Int {
            return params.threshold(zoneId)
        }

        for (start in 0 until total) {
            if (visited[start]) continue
            val label = labels[start]
            if (label < 0) {
                visited[start] = true
                continue
            }

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var size = 0
            var protectedByEdge = false
            var nearEdgeHit = false
            boundaryVotes.clear()
            val zoneCounts = IntArray(zoneCount)

            while (head < tail) {
                val idx = queue[head++]
                members[size++] = idx

                val x = idx % width
                val y = idx / width
                zoneCounts[zones[idx].coerceIn(0, zoneCount - 1)]++
                if (!protectedByEdge && localMax7x7(edgeMask, width, height, x, y) >= edgeThreshold) {
                    protectedByEdge = true
                }

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (abs(dx) + abs(dy) != 1) continue // 4-соседи без диагоналей
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue

                        val nIdx = ny * width + nx
                        val nLabel = labels[nIdx]
                        if (nLabel == label) {
                            if (!visited[nIdx]) {
                                visited[nIdx] = true
                                queue[tail++] = nIdx
                            }
                        } else {
                            if (hasStrongEdgeBetween(x, y, nx, ny, edgeMask, width, height, edgeThreshold)) {
                                protectedByEdge = true
                                continue
                            }
                            if (!nearEdgeHit && hasStrongEdgeBetween(
                                    x,
                                    y,
                                    nx,
                                    ny,
                                    edgeMask,
                                    width,
                                    height,
                                    edgeThreshold * 0.95f,
                                )
                            ) {
                                nearEdgeHit = true
                            }
                            // Периметральная защита: если у соседней метки локальный максимум ≥ threshold — блокируем замену.
                            if (!protectedByEdge && localMax7x7(edgeMask, width, height, nx, ny) >= edgeThreshold) {
                                protectedByEdge = true
                                continue
                            }
                            if (nLabel >= 0) {
                                boundaryVotes[nLabel] = (boundaryVotes[nLabel] ?: 0) + 1
                            }
                        }
                    }
                }
            }

            val zoneId = zoneCounts.indices.maxByOrNull { zoneCounts[it] } ?: Zone.FILL.ordinal
            val threshold = selectThreshold(zoneId)
            if (size >= threshold) {
                continue
            }

            if (protectedByEdge || nearEdgeHit || boundaryVotes.isEmpty()) {
                continue
            }

            val best = boundaryVotes.entries
                .maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { -it.key }))

            if (best == null || best.value < MIN_VOTES_FOR_MERGE) {
                continue
            }

            val replacement = best.key
            if (replacement == label) {
                continue
            }

            for (i in 0 until size) {
                labels[members[i]] = replacement
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
        val dx = nx - x
        val dy = ny - y
        val manhattan = abs(dx) + abs(dy)
        if (manhattan == 0) {
            return localMax7x7(edgeMask, width, height, x, y) >= threshold
        }
        if (manhattan == 1) {
            val samples = floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
            var maxValue = 0f
            for (t in samples) {
                val fx = x + t * dx
                val fy = y + t * dy
                val sx = kotlin.math.floor((fx + 0.5f).toDouble()).toInt()
                val sy = kotlin.math.floor((fy + 0.5f).toDouble()).toInt()
                val localMax = localMax7x7(edgeMask, width, height, sx, sy)
                if (localMax > maxValue) {
                    maxValue = localMax
                    if (maxValue >= threshold) {
                        return true
                    }
                }
            }
            return maxValue >= threshold
        }
        if (abs(dx) == 1 && abs(dy) == 1 && manhattan == 2) {
            // Диагональный сосед: запрещаем corner-cut, пробуем два ортогональных пути.
            if (hasStrongEdgeBetween(x, y, nx, y, edgeMask, width, height, threshold)) {
                return true
            }
            if (hasStrongEdgeBetween(x, y, x, ny, edgeMask, width, height, threshold)) {
                return true
            }
        }
        return false
    }
}
