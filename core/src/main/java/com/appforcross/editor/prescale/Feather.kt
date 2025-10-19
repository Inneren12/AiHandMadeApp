package com.appforcross.editor.prescale

import kotlin.math.max
import kotlin.math.min

/** Линейный feather-блендинг для тайлов. */
object Feather {
    fun blend(
        dst: FloatArray,
        wsum: FloatArray,
        src: FloatArray,
        tx: Int,
        ty: Int,
        tw: Int,
        th: Int,
        W: Int,
        H: Int,
        overlap: Int
    ) {
        var i = 0
        for (yy in 0 until th) {
            val gy = ty + yy
            if (gy >= H) break
            val wy = weight(yy, th, overlap)
            for (xx in 0 until tw) {
                val gx = tx + xx
                if (gx >= W) break
                val wx = weight(xx, tw, overlap)
                val w = wy * wx
                val g = gy * W + gx
                val gi = g * 3
                val si = i * 3
                dst[gi] += src[si] * w
                dst[gi + 1] += src[si + 1] * w
                dst[gi + 2] += src[si + 2] * w
                wsum[g] += w
                i++
            }
        }
    }

    private fun weight(p: Int, len: Int, ov: Int): Float {
        if (ov <= 0) return 1f
        val left = p.toFloat() / ov.toFloat()
        val right = (len - 1 - p).toFloat() / ov.toFloat()
        val wl = if (p < ov) max(0f, min(1f, left)) else 1f
        val wr = if (len - 1 - p < ov) max(0f, min(1f, right)) else 1f
        return min(wl, wr)
    }
}
