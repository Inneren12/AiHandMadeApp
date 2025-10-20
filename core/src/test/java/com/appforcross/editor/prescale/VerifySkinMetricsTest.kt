package com.appforcross.editor.prescale

import kotlin.test.Test
import kotlin.test.assertTrue

class VerifySkinMetricsTest {
    @Test
    fun deltaE95_skin_increases_when_tinted() {
        val W = 64
        val H = 64
        val ref = FloatArray(W * H * 3)
        val out = FloatArray(W * H * 3)
        val mask = BooleanArray(W * H)
        var p = 0
        for (y in 0 until H) {
            for (x in 0 until W) {
                val r = 0.75f
                val g = 0.60f
                val b = 0.52f
                ref[p] = r
                ref[p + 1] = g
                ref[p + 2] = b
                out[p] = (r + 0.03f).coerceAtMost(1f)
                out[p + 1] = (g - 0.01f).coerceAtLeast(0f)
                out[p + 2] = (b + 0.005f).coerceAtMost(1f)
                val i = y * W + x
                mask[i] = true
                p += 3
            }
        }
        val rep = Verify.computeDetailed(ref, out, W, H, null, mask)
        assertTrue(rep.deltaE95Skin > 0.5f, "ΔE95Skin should be noticeable, got ${'$'}{rep.deltaE95Skin}")
    }

    @Test
    fun banding_sky_vs_skin_regions_are_reported() {
        val W = 128
        val H = 64
        val grad = FloatArray(W * H * 3)
        var p = 0
        for (y in 0 until H) {
            for (x in 0 until W) {
                val v = x / (W - 1f)
                val r = if (y < H / 2) v else 0.7f
                val g = if (y < H / 2) v else 0.55f
                val b = if (y < H / 2) v else 0.50f
                grad[p++] = r
                grad[p++] = g
                grad[p++] = b
            }
        }
        val sky = BooleanArray(W * H) { i -> (i / W) < H / 2 }
        val skin = BooleanArray(W * H) { i -> (i / W) >= H / 2 }
        val rep = Verify.computeDetailed(grad, grad, W, H, sky, skin)
        // Для нашей прокси banding: skin-плоскость даёт максимум (много плоских соседей),
        // а sky-градиент — меньше (соседи отличаются). Поэтому ожидаем bandSkin >= bandSky.
        assertTrue(rep.bandSkin >= rep.bandSky, "Skin banding should be >= Sky banding")
    }
}
