package com.appforcross.editor.palette

import com.appforcross.editor.palette.dither.DitherParams
import com.appforcross.editor.palette.dither.OrderedDither
import kotlin.test.Test
import kotlin.test.assertTrue

class DitherOrderedTest {
    @Test
    fun banding_reduces_on_gradient() {
        val W = 128
        val H = 48
        val rgb = FloatArray(W * H * 3)
        var p = 0
        for (y in 0 until H) for (x in 0 until W) {
            val v = x.toFloat() / (W - 1).toFloat()
            rgb[p++] = v
            rgb[p++] = v
            rgb[p++] = v
        }
        val K = 6
        val pal = FloatArray(K * 3)
        for (k in 0 until K) {
            val v = k / (K - 1f)
            pal[k * 3] = v
            pal[k * 3 + 1] = v
            pal[k * 3 + 2] = v
        }
        val assign = IntArray(W * H) { i ->
            val v = rgb[i * 3]
            ((v * (K - 1)) + 0.5f).toInt().coerceIn(0, K - 1)
        }
        val noDitherGBI = gbi(assign, W, H)
        val out = OrderedDither.apply(rgb, assign, pal, W, H, DitherParams(ampFlat = 0.30f))
        val withDitherGBI = gbi(out, W, H)
        assertTrue(withDitherGBI < noDitherGBI, "GBI should drop with ordered dither")
    }

    private fun gbi(index: IntArray, W: Int, H: Int): Float {
        var same = 0
        var all = 0
        for (y in 0 until H) for (x in 0 until W - 1) {
            if (index[y * W + x] == index[y * W + x + 1]) same++
            all++
        }
        return same.toFloat() / all.toFloat()
    }
}
