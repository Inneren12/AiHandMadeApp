package com.appforcross.editor.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneAnalyzerTest {

    @Test
    fun checkerboard_is_discrete() {
        val w = 64
        val h = 64
        val rgb = FloatArray(w * h * 3)
        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = if (((x + y) and 1) == 0) 1f else 0f
                rgb[p++] = v
                rgb[p++] = v
                rgb[p++] = v
            }
        }
        val decision = SceneAnalyzer.analyzePreview(rgb, w, h)
        assertEquals(SceneKind.DISCRETE, decision.kind)
        assertTrue(decision.confidence >= 0.7f, "confidence=${decision.confidence}")
    }

    @Test
    fun gradient_is_photo() {
        val w = 128
        val h = 64
        val rgb = FloatArray(w * h * 3)
        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = x.toFloat() / (w - 1).toFloat()
                rgb[p++] = v
                rgb[p++] = v
                rgb[p++] = v
            }
        }
        val decision = SceneAnalyzer.analyzePreview(rgb, w, h)
        assertEquals(SceneKind.PHOTO, decision.kind)
    }

    @Test
    fun sparse_lines_look_discrete() {
        val w = 96
        val h = 64
        val rgb = FloatArray(w * h * 3)
        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = if (x % 6 == 0) 0f else 1f
                rgb[p++] = v
                rgb[p++] = v
                rgb[p++] = v
            }
        }
        val decision = SceneAnalyzer.analyzePreview(rgb, w, h)
        assertEquals(SceneKind.DISCRETE, decision.kind)
    }
}
