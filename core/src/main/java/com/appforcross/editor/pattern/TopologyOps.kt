package com.appforcross.editor.pattern

import com.appforcross.editor.logging.Logger
import java.util.HashMap
import kotlin.jvm.Volatile
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

/** Edge barrier base threshold for min-run replacement guard (in [0,1]). */
internal const val EDGE_BASE_THRESHOLD: Float = 0.30f

internal const val EDGE_NEAR_RATIO: Float = 0.95f

internal const val EDGE_WINDOW_RADIUS: Int = 3

internal const val EDGE_WINDOW_SIZE: Int = EDGE_WINDOW_RADIUS * 2 + 1

internal const val EDGE_PROBE_SAMPLES: Int = 9

internal const val TOPO_CONNECTIVITY: Int = 4

/** Минимум голосов большинства, чтобы перекрасить короткий ран. */
internal const val MIN_VOTES_FOR_MERGE: Int = 2

/** Parameters controlling the topology merge. */
data class TopologyParams(
    val tileSize: Int = 32,
    val halo: Int = 1,
    val minRunThresholds: IntArray = intArrayOf(2, 3, 3, 4, 3),
    /* Edge barrier threshold for min-run replacement guard (in [0,1]). */
    val edgeBlockThreshold: Float = EDGE_BASE_THRESHOLD
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

internal data class MergeGuardStats(
    val components: Int = 0,
    val mergesApplied: Int = 0,
    val cancelledByBarrier: Int = 0,
    val cancelledByProbe: Int = 0,
    val cancelledByNear: Int = 0,
    val cancelledByVotes: Int = 0,
    val cancelledByTie: Int = 0,
    val votesWinner: Int = 0,
    val votesOriginal: Int = 0,
    val votesWinnerLabel: Int = -1
)

/** Helper operations to clean topology maps. */
object TopologyOps {
    private val zoneCount = Zone.values().size
    @Volatile
    private var latestGuardStats: MergeGuardStats = MergeGuardStats()

    internal fun lastGuardStats(): MergeGuardStats = latestGuardStats

    private fun buildNoCrossMask(
        edgeMask: FloatArray,
        width: Int,
        height: Int,
        baseThreshold: Float
    ): BooleanArray {
        val total = width * height
        val mask = BooleanArray(total)
        for (i in 0 until total) {
            if (edgeMask[i] >= baseThreshold) {
                val x = i % width
                val y = i / width
                mask[i] = true
                if (x > 0) mask[i - 1] = true
                if (x + 1 < width) mask[i + 1] = true
                if (y > 0) mask[i - width] = true
                if (y + 1 < height) mask[i + width] = true
            }
        }
        return mask
    }

    /** Локальный максимум edgeMask в окрестности (2·r+1)×(2·r+1) для консервативной защиты. */
    private fun localMaxWindow(
        edgeMask: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Float {
        var maxValue = 0f
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        for (dy in -EDGE_WINDOW_RADIUS..EDGE_WINDOW_RADIUS) {
            for (dx in -EDGE_WINDOW_RADIUS..EDGE_WINDOW_RADIUS) {
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

        val original = labels.copyOf()
        val working = original.copyOf()
        val baseThreshold = max(params.edgeBlockThreshold, EDGE_BASE_THRESHOLD).coerceIn(0f, 1f)
        val noCrossMask = buildNoCrossMask(edgeMask, width, height, baseThreshold)
        pottsLite(working, width, height, edgeMask, params, noCrossMask)
        minRunMerge(working, original, width, height, zones, edgeMask, params, baseThreshold, noCrossMask)
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
        params: TopologyParams,
        noCrossMask: BooleanArray
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
                        if (noCrossMask[idx]) {
                            continue
                        }
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

    private fun pottsLite(
        labels: IntArray,
        width: Int,
        height: Int,
        edgeMask: FloatArray,
        params: TopologyParams
    ) {
        pottsLite(labels, width, height, edgeMask, params, BooleanArray(width * height))
    }

    private fun minRunMerge(
        labels: IntArray,
        originalLabels: IntArray,
        width: Int,
        height: Int,
        zones: IntArray,
        edgeMask: FloatArray,
        params: TopologyParams,
        baseThreshold: Float,
        noCrossMask: BooleanArray
    ) {
        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val members = IntArray(total)
        val perimeterValues = FloatArray(total)
        val probeValues = FloatArray(total)
        val barrierFlags = BooleanArray(total)
        val boundaryLabelsWorking = IntArray(total)
        val boundaryLabelsOriginal = IntArray(total)
        val boundaryVotesWorking = HashMap<Int, Int>(8)
        val boundaryVotesOriginal = HashMap<Int, Int>(8)

        fun selectThreshold(zoneId: Int): Int {
            return params.threshold(zoneId)
        }

        var components = 0
        var mergesApplied = 0
        var cancelledByBarrier = 0
        var cancelledByProbe = 0
        var cancelledByVotes = 0
        var cancelledByTie = 0
        var cancelledByNear = 0
        var peakWinnerVotes = 0
        var peakOriginalVotes = 0
        var lastWinnerLabel = -1

        val neighborDx = intArrayOf(0, -1, 1, 0)
        val neighborDy = intArrayOf(-1, 0, 0, 1)

        fun restoreComponent(size: Int, targetLabel: Int) {
            for (i in 0 until size) {
                labels[members[i]] = targetLabel
            }
        }

        for (start in 0 until total) {
            if (visited[start]) continue
            val componentLabel = originalLabels[start]
            visited[start] = true
            if (componentLabel < 0) {
                continue
            }

            components++

            var head = 0
            var tail = 0
            queue[tail++] = start

            var size = 0
            var perimeterCount = 0
            val zoneCounts = IntArray(zoneCount)

            while (head < tail) {
                val idx = queue[head++]
                members[size++] = idx

                val x = idx % width
                val y = idx / width
                zoneCounts[zones[idx].coerceIn(0, zoneCount - 1)]++

                for (dir in neighborDx.indices) {
                    val nx = x + neighborDx[dir]
                    val ny = y + neighborDy[dir]
                    if (nx !in 0 until width || ny !in 0 until height) continue

                    val nIdx = ny * width + nx
                    val neighborOriginal = originalLabels[nIdx]
                    if (neighborOriginal == componentLabel) {
                        if (!visited[nIdx]) {
                            visited[nIdx] = true
                            queue[tail++] = nIdx
                        }
                    } else {
                        if (perimeterCount < total) {
                            val boundaryIndex = perimeterCount
                            val edgeValue = max(
                                localMaxWindow(edgeMask, width, height, x, y),
                                localMaxWindow(edgeMask, width, height, nx, ny)
                            )
                            perimeterValues[boundaryIndex] = edgeValue
                            probeValues[boundaryIndex] = probeEdgeBetween(
                                x,
                                y,
                                nx,
                                ny,
                                edgeMask,
                                width,
                                height
                            )
                            barrierFlags[boundaryIndex] = noCrossMask[idx] || noCrossMask[nIdx]
                            // Сохраняем рабочую и оригинальную метку соседа для последующего голосования
                            // (working влияет на решение, original — на детект ничьих pre-Potts).
                            val neighborLabelWorking = labels[nIdx]
                            val neighborLabelOriginal = originalLabels[nIdx]
                            boundaryLabelsWorking[boundaryIndex] =
                                if (neighborLabelWorking >= 0) neighborLabelWorking else -1
                            boundaryLabelsOriginal[boundaryIndex] =
                                if (neighborLabelOriginal >= 0) neighborLabelOriginal else -1
                            perimeterCount++
                        }
                    }
                }
            }

            val zoneId = zoneCounts.indices.maxByOrNull { zoneCounts[it] } ?: Zone.FILL.ordinal
            val threshold = selectThreshold(zoneId)
            if (size >= threshold) {
                continue
            }

            if (perimeterCount == 0) {
                cancelledByVotes++
                restoreComponent(size, componentLabel)
                continue
            }

            val perimeterCopy = perimeterValues.copyOfRange(0, perimeterCount)
            perimeterCopy.sort()
            val p90Index = ((perimeterCount - 1) * 0.9f).toInt().coerceIn(0, perimeterCount - 1)
            val adaptiveThreshold = max(baseThreshold, perimeterCopy[p90Index])
            val nearThreshold = adaptiveThreshold * EDGE_NEAR_RATIO

            var barrierHit = false
            var perimeterStrong = false
            var probeStrong = false
            var nearHit = false
            boundaryVotesWorking.clear()
            boundaryVotesOriginal.clear()

            for (i in 0 until perimeterCount) {
                val neighborWorking = boundaryLabelsWorking[i]
                val neighborOriginal = boundaryLabelsOriginal[i]
                val edgeValue = perimeterValues[i]
                val probeValue = probeValues[i]
                val blocked = barrierFlags[i]
                if (blocked) {
                    barrierHit = true
                    continue
                }
                if (edgeValue >= adaptiveThreshold) {
                    perimeterStrong = true
                    continue
                }
                if (edgeValue >= nearThreshold) {
                    nearHit = true
                    continue
                }
                if (probeValue >= adaptiveThreshold) {
                    probeStrong = true
                    continue
                }
                if (probeValue >= nearThreshold) {
                    nearHit = true
                    continue
                }
                if (neighborWorking >= 0) {
                    boundaryVotesWorking[neighborWorking] =
                        (boundaryVotesWorking[neighborWorking] ?: 0) + 1
                }
                if (neighborOriginal >= 0) {
                    boundaryVotesOriginal[neighborOriginal] =
                        (boundaryVotesOriginal[neighborOriginal] ?: 0) + 1
                }
            }

            if (barrierHit || perimeterStrong) {
                cancelledByBarrier++
                restoreComponent(size, componentLabel)
                continue
            }
            if (probeStrong) {
                cancelledByProbe++
                restoreComponent(size, componentLabel)
                continue
            }
            if (nearHit) {
                cancelledByNear++
                restoreComponent(size, componentLabel)
                continue
            }
            if (boundaryVotesWorking.isEmpty()) {
                cancelledByVotes++
                restoreComponent(size, componentLabel)
                continue
            }

            val maxWorking = boundaryVotesWorking.values.maxOrNull() ?: 0
            val maxOriginal = boundaryVotesOriginal.values.maxOrNull() ?: 0
            val originalVotes = boundaryVotesOriginal[componentLabel] ?: 0
            if (maxWorking > peakWinnerVotes) {
                peakWinnerVotes = maxWorking
            }
            if (originalVotes > peakOriginalVotes) {
                peakOriginalVotes = originalVotes
            }
            if (maxWorking < MIN_VOTES_FOR_MERGE) {
                cancelledByVotes++
                restoreComponent(size, componentLabel)
                continue
            }
            val workingWinners = boundaryVotesWorking.filterValues { it == maxWorking }.keys
            val originalWinners = boundaryVotesOriginal.filterValues { it == maxOriginal }.keys
            val tieWorking = workingWinners.size != 1
            val tieOriginal = (maxOriginal >= 1) && (originalWinners.size > 1)
            if (tieWorking || tieOriginal) {
                cancelledByTie++
                restoreComponent(size, componentLabel)
                continue
            }

            val replacement = workingWinners.first()
            lastWinnerLabel = replacement
            // Если победитель совпадает с исходной меткой компоненты — восстанавливаем исходное состояние
            // и не считаем это отменой.
            if (replacement == componentLabel) {
                restoreComponent(size, componentLabel)
                continue
            }
            var changed = false
            for (i in 0 until size) {
                val idx = members[i]
                if (labels[idx] != replacement) {
                    labels[idx] = replacement
                    changed = true
                }
            }
            if (changed) {
                mergesApplied++
            }
        }

        latestGuardStats = MergeGuardStats(
            components = components,
            mergesApplied = mergesApplied,
            cancelledByBarrier = cancelledByBarrier,
            cancelledByProbe = cancelledByProbe,
            cancelledByNear = cancelledByNear,
            cancelledByVotes = cancelledByVotes,
            cancelledByTie = cancelledByTie,
            votesWinner = peakWinnerVotes,
            votesOriginal = peakOriginalVotes,
            votesWinnerLabel = lastWinnerLabel
        )
        Logger.i(
            "TOPO",
            "merge_guard",
            mapOf(
                "topology.merge.components" to latestGuardStats.components,
                "topology.merge_applied" to latestGuardStats.mergesApplied,
                "topology.merge_cancelled_by_barrier" to latestGuardStats.cancelledByBarrier,
                "topology.merge_cancelled_by_probe" to latestGuardStats.cancelledByProbe,
                "topology.merge_cancelled_by_near" to latestGuardStats.cancelledByNear,
                "topology.merge_cancelled_by_votes" to latestGuardStats.cancelledByVotes,
                "topology.merge_cancelled_by_tie" to latestGuardStats.cancelledByTie,
                "topology.votes_winner" to latestGuardStats.votesWinner,
                "topology.votes_original" to latestGuardStats.votesOriginal,
                "topology.votes_winner_label" to latestGuardStats.votesWinnerLabel
            )
        )
    }

    private fun minRunMerge(
        labels: IntArray,
        width: Int,
        height: Int,
        zones: IntArray,
        edgeMask: FloatArray,
        params: TopologyParams,
        configuredThreshold: Float
    ) {
        val originalCopy = labels.copyOf()
        val baseThreshold = max(configuredThreshold, EDGE_BASE_THRESHOLD).coerceIn(0f, 1f)
        val noCrossMask = buildNoCrossMask(edgeMask, width, height, baseThreshold)
        minRunMerge(labels, originalCopy, width, height, zones, edgeMask, params, baseThreshold, noCrossMask)
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

    private fun probeEdgeBetween(
        x: Int,
        y: Int,
        nx: Int,
        ny: Int,
        edgeMask: FloatArray,
        width: Int,
        height: Int
    ): Float {
        val dx = nx - x
        val dy = ny - y
        val manhattan = abs(dx) + abs(dy)
        if (manhattan == 0) {
            return localMaxWindow(edgeMask, width, height, x, y)
        }
        if (manhattan == 1) {
            var maxValue = 0f
            val steps = EDGE_PROBE_SAMPLES - 1
            for (i in 0 until EDGE_PROBE_SAMPLES) {
                val t = if (steps == 0) 0f else i.toFloat() / steps
                val fx = x + t * dx
                val fy = y + t * dy
                val sx = kotlin.math.floor((fx + 0.5f).toDouble()).toInt()
                val sy = kotlin.math.floor((fy + 0.5f).toDouble()).toInt()
                val localMax = localMaxWindow(edgeMask, width, height, sx, sy)
                if (localMax > maxValue) {
                    maxValue = localMax
                }
            }
            return maxValue
        }
        if (abs(dx) == 1 && abs(dy) == 1 && manhattan == 2) {
            // Диагональный сосед: запрещаем corner-cut, проверяем ортогональные обходы.
            val orthoA = probeEdgeBetween(x, y, nx, y, edgeMask, width, height)
            val orthoB = probeEdgeBetween(x, y, x, ny, edgeMask, width, height)
            return max(orthoA, orthoB)
        }
        return 0f
    }
}
