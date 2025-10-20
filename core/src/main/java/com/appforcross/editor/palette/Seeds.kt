package com.appforcross.editor.palette

import kotlin.math.max
import kotlin.math.min

object Seeds {
    fun initialOKLab(labPixels: FloatArray, kStart: Int): FloatArray {
        val total = labPixels.size / 3
        require(kStart in 1..total) { "Invalid kStart" }
        val palette = FloatArray(kStart * 3)
        val stride = max(1, total / kStart)
        var cursor = 0
        for (k in 0 until kStart) {
            val idx = min(total - 1, k * stride)
            palette[cursor++] = labPixels[idx * 3 + 0]
            palette[cursor++] = labPixels[idx * 3 + 1]
            palette[cursor++] = labPixels[idx * 3 + 2]
        }
        return palette
    }
}
