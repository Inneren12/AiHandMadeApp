package com.appforcross.editor.palette

object Refine {
    fun addAndRefine(
        palette: FloatArray,
        newColor: FloatArray,
        labPixels: FloatArray
    ): FloatArray {
        val newPalette = FloatArray(palette.size + 3)
        palette.copyInto(newPalette)
        newPalette[newPalette.lastIndex - 2] = newColor[0]
        newPalette[newPalette.lastIndex - 1] = newColor[1]
        newPalette[newPalette.lastIndex] = newColor[2]

        var current = newPalette
        repeat(2) {
            val assign = Assign.assignOKLab(labPixels, current)
            current = recomputeMeans(current, labPixels, assign)
        }
        return current
    }

    private fun recomputeMeans(palette: FloatArray, labPixels: FloatArray, assign: IntArray): FloatArray {
        val colors = palette.size / 3
        val sums = FloatArray(colors * 3)
        val counts = IntArray(colors)
        for (i in 0 until assign.size) {
            val idx = assign[i]
            val base = idx * 3
            sums[base + 0] += labPixels[i * 3 + 0]
            sums[base + 1] += labPixels[i * 3 + 1]
            sums[base + 2] += labPixels[i * 3 + 2]
            counts[idx]++
        }
        val out = palette.copyOf()
        for (k in 0 until colors) {
            val base = k * 3
            val count = counts[k]
            if (count > 0) {
                out[base + 0] = sums[base + 0] / count
                out[base + 1] = sums[base + 1] / count
                out[base + 2] = sums[base + 2] / count
            }
        }
        return out
    }
}
