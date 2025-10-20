package com.appforcross.editor.palette

object Refine {
    fun addAndRefine(
        palette: FloatArray,
        candidate: SplitCandidate,
        labPixels: FloatArray,
        currentAssign: IntArray
    ): FloatArray {
        val seed = computeMedianSeed(candidate, palette, labPixels, currentAssign)
        val newPalette = FloatArray(palette.size + 3)
        palette.copyInto(newPalette)
        newPalette[newPalette.lastIndex - 2] = seed[0]
        newPalette[newPalette.lastIndex - 1] = seed[1]
        newPalette[newPalette.lastIndex] = seed[2]

        var current = newPalette
        repeat(2) {
            val assign = Assign.assignOKLab(labPixels, current)
            current = recomputeMedians(current, labPixels, assign)
            current = twoOptAdjust(current, labPixels, assign)
        }
        return current
    }

    private fun computeMedianSeed(
        candidate: SplitCandidate,
        palette: FloatArray,
        labPixels: FloatArray,
        assign: IntArray
    ): FloatArray {
        val cluster = candidate.cluster
        val indices = ArrayList<Int>()
        for (i in assign.indices) {
            if (assign[i] == cluster) {
                indices.add(i)
            }
        }
        if (indices.isEmpty()) {
            return candidate.seedColor
        }

        val centerBase = cluster * 3
        val centerL = palette[centerBase + 0]
        val centerA = palette[centerBase + 1]
        val centerB = palette[centerBase + 2]

        val weighted = indices.map { idx ->
            val l = labPixels[idx * 3 + 0]
            val a = labPixels[idx * 3 + 1]
            val b = labPixels[idx * 3 + 2]
            val error = PaletteMath.deltaE(l, a, b, centerL, centerA, centerB).toFloat()
            idx to error
        }.sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first })

        val takeCount = maxOf(1, weighted.size / 4)
        val subset = weighted.take(takeCount)
        val bufferL = FloatArray(subset.size)
        val bufferA = FloatArray(subset.size)
        val bufferB = FloatArray(subset.size)
        for (i in subset.indices) {
            val idx = subset[i].first
            bufferL[i] = labPixels[idx * 3 + 0]
            bufferA[i] = labPixels[idx * 3 + 1]
            bufferB[i] = labPixels[idx * 3 + 2]
        }
        return floatArrayOf(
            medianOf(bufferL),
            medianOf(bufferA),
            medianOf(bufferB)
        )
    }

    private fun recomputeMedians(palette: FloatArray, labPixels: FloatArray, assign: IntArray): FloatArray {
        val colors = palette.size / 3
        val lBuckets = Array(colors) { ArrayList<Float>() }
        val aBuckets = Array(colors) { ArrayList<Float>() }
        val bBuckets = Array(colors) { ArrayList<Float>() }
        for (i in assign.indices) {
            val idx = assign[i]
            lBuckets[idx].add(labPixels[i * 3 + 0])
            aBuckets[idx].add(labPixels[i * 3 + 1])
            bBuckets[idx].add(labPixels[i * 3 + 2])
        }
        val out = palette.copyOf()
        for (k in 0 until colors) {
            val base = k * 3
            if (lBuckets[k].isNotEmpty()) {
                out[base + 0] = medianOf(lBuckets[k])
                out[base + 1] = medianOf(aBuckets[k])
                out[base + 2] = medianOf(bBuckets[k])
            }
        }
        return out
    }

    private fun twoOptAdjust(palette: FloatArray, labPixels: FloatArray, assign: IntArray): FloatArray {
        val colors = palette.size / 3
        val out = palette.copyOf()
        val buckets = Array(colors) { ArrayList<Pair<Int, Float>>() }
        for (i in assign.indices) {
            val idx = assign[i]
            val base = idx * 3
            val l = labPixels[i * 3 + 0]
            val a = labPixels[i * 3 + 1]
            val b = labPixels[i * 3 + 2]
            val err = PaletteMath.deltaE(l, a, b, out[base + 0], out[base + 1], out[base + 2]).toFloat()
            buckets[idx].add(i to err)
        }
        for (k in 0 until colors) {
            val candidates = buckets[k]
            if (candidates.isEmpty()) continue
            val worst = candidates.maxWithOrNull(compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first }) ?: continue
            val idx = worst.first
            val error = worst.second
            if (error <= 1e-3f) continue
            val base = k * 3
            val step = (error / 10f).coerceIn(0.08f, 0.35f)
            out[base + 0] = clampL(out[base + 0] * (1f - step) + labPixels[idx * 3 + 0] * step)
            out[base + 1] = clampAB(out[base + 1] * (1f - step) + labPixels[idx * 3 + 1] * step)
            out[base + 2] = clampAB(out[base + 2] * (1f - step) + labPixels[idx * 3 + 2] * step)

            var bestOther = k
            var bestOtherErr = error
            for (j in 0 until colors) {
                if (j == k) continue
                val otherBase = j * 3
                val candidateErr = PaletteMath.deltaE(
                    labPixels[idx * 3 + 0],
                    labPixels[idx * 3 + 1],
                    labPixels[idx * 3 + 2],
                    out[otherBase + 0],
                    out[otherBase + 1],
                    out[otherBase + 2]
                ).toFloat()
                if (candidateErr < bestOtherErr - 1e-3f) {
                    bestOtherErr = candidateErr
                    bestOther = j
                }
            }
            if (bestOther != k) {
                val otherBase = bestOther * 3
                val gain = (error - bestOtherErr).coerceAtLeast(0f)
                if (gain > 1e-3f) {
                    val otherStep = (gain / 8f).coerceIn(0.05f, 0.25f)
                    out[otherBase + 0] = clampL(out[otherBase + 0] * (1f - otherStep) + labPixels[idx * 3 + 0] * otherStep)
                    out[otherBase + 1] = clampAB(out[otherBase + 1] * (1f - otherStep) + labPixels[idx * 3 + 1] * otherStep)
                    out[otherBase + 2] = clampAB(out[otherBase + 2] * (1f - otherStep) + labPixels[idx * 3 + 2] * otherStep)
                }
            }
        }
        return out
    }

    private fun medianOf(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val copy = values.copyOf()
        copy.sort()
        val mid = copy.size / 2
        return if (copy.size % 2 == 0) {
            (copy[mid - 1] + copy[mid]) * 0.5f
        } else {
            copy[mid]
        }
    }

    private fun medianOf(values: MutableList<Float>): Float {
        if (values.isEmpty()) return 0f
        val copy = values.sorted()
        val mid = copy.size / 2
        return if (copy.size % 2 == 0) {
            (copy[mid - 1] + copy[mid]) * 0.5f
        } else {
            copy[mid]
        }
    }

    private fun clampL(v: Float): Float = v.coerceIn(0f, 1f)
    private fun clampAB(v: Float): Float = v.coerceIn(-1.5f, 1.5f)
}
