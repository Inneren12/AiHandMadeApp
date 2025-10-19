package com.appforcross.editor.scene

import com.appforcross.editor.logging.Logger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneAnalyzerTest {
    private class CollectingSink : Logger.Sink {
        val events = mutableListOf<Logger.Trip>()
        override fun log(level: Char, tag: String, event: String, payload: Map<String, Any?>) {
            events += Logger.Trip(level, tag, event, payload)
        }
    }

    private lateinit var sink: CollectingSink

    @BeforeTest
    fun setup() {
        sink = CollectingSink()
        Logger.installSink(sink)
    }

    @Test
    fun checkerboardClassifiedAsDiscrete() {
        val size = 64
        val pixels = FloatArray(size * size * 3)
        var idx = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val value = if (((x / 2) + (y / 2)) % 2 == 0) 0f else 1f
                pixels[idx++] = value
                pixels[idx++] = value
                pixels[idx++] = value
            }
        }

        val decision = SceneAnalyzer.analyzePreview(pixels, size, size)
        assertEquals(SceneKind.DISCRETE, decision.kind)
        assertTrue(decision.confidence > 0.7f, "confidence should be high for checkerboard")
        assertLogFlow()
    }

    @Test
    fun gradientClassifiedAsPhoto() {
        val width = 128
        val height = 64
        val pixels = FloatArray(width * height * 3)
        var idx = 0
        for (y in 0 until height) {
            val t = y.toFloat() / (height - 1).coerceAtLeast(1)
            for (x in 0 until width) {
                val value = t
                pixels[idx++] = value
                pixels[idx++] = value
                pixels[idx++] = value
            }
        }

        val decision = SceneAnalyzer.analyzePreview(pixels, width, height)
        assertEquals(SceneKind.PHOTO, decision.kind)
        assertTrue(decision.confidence > 0.5f, "confidence should favor photo classification")
        assertLogFlow()
    }

    @Test
    fun textLikeLinesClassifiedAsDiscrete() {
        val width = 96
        val height = 64
        val pixels = FloatArray(width * height * 3)
        var idx = 0
        for (y in 0 until height) {
            val line = if (y % 6 == 0) 0f else 1f
            for (x in 0 until width) {
                val value = if (x % 16 < 2) 0f else line
                pixels[idx++] = value
                pixels[idx++] = value
                pixels[idx++] = value
            }
        }

        val decision = SceneAnalyzer.analyzePreview(pixels, width, height)
        assertEquals(SceneKind.DISCRETE, decision.kind)
        assertTrue(decision.confidence > 0.55f, "confidence should indicate discrete scene")
        assertLogFlow()
    }

    private fun assertLogFlow() {
        val events = sink.events.map { it.event }
        assertTrue(events.contains("params"), "params log missing")
        assertTrue(events.contains("features"), "features log missing")
        assertTrue(events.contains("decision"), "decision log missing")
        assertTrue(events.contains("done"), "done log missing")
        val firstIndex = events.indexOf("params")
        val decisionIndex = events.indexOf("decision")
        val doneIndex = events.indexOf("done")
        assertTrue(firstIndex >= 0 && decisionIndex > firstIndex, "decision must come after params")
        assertTrue(doneIndex > decisionIndex, "done must come after decision")
    }
}
