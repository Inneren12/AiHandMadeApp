package com.appforcross.editor.prescale

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

    /**
     * Одномерный вес с “смещённой” линейной рампой в зоне overlap.
     * eps > 0 гарантирует, что сумма весов соседних тайлов в шве не равна нулю.
     */
    private fun weight(p: Int, len: Int, ov: Int): Float {
        if (ov <= 0) return 1f
        val eps = 1e-3f
        return when {
            p < ov -> {
                val t = (p.toFloat() + 0.5f) / ov.toFloat()
                t.coerceIn(0f, 1f).coerceAtLeast(eps)
            }
            len - 1 - p < ov -> {
                val t = ((len - 1 - p).toFloat() + 0.5f) / ov.toFloat()
                t.coerceIn(0f, 1f).coerceAtLeast(eps)
            }
            else -> 1f
        }
    }
}
