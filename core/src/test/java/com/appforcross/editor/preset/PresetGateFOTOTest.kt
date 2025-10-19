package com.appforcross.editor.preset

import com.appforcross.editor.scene.SceneFeatures
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.text.Charsets

class PresetGateFOTOTest {

    @Test
    fun architectureEdges_selected() {
        val features = SceneFeatures(
            width = 100,
            height = 100,
            colors5bit = 128,
            top8Coverage = 0.2f,
            edgeDensity = 0.18f,
            checker2x2 = 0.05f,
            entropy = 5.5f
        )
        val (decision, logs) = captureLogs { PresetGateFOTO.decide(features) }
        assertEquals("ARCHITECTURE_EDGES", decision.preset.id)
        assertTrue(decision.confidence >= 0.6f, "confidence=${decision.confidence}")
        assertLogsContain(logs, setOf("params", "decision", "done"))
    }

    @Test
    fun landscape_selected() {
        val features = SceneFeatures(
            width = 100,
            height = 100,
            colors5bit = 128,
            top8Coverage = 0.4f,
            edgeDensity = 0.03f,
            checker2x2 = 0.02f,
            entropy = 6.0f
        )
        val (decision, _) = captureLogs { PresetGateFOTO.decide(features) }
        assertEquals("LANDSCAPE_GRADIENTS", decision.preset.id)
    }

    @Test
    fun portrait_selected() {
        val features = SceneFeatures(
            width = 100,
            height = 100,
            colors5bit = 128,
            top8Coverage = 0.4f,
            edgeDensity = 0.09f,
            checker2x2 = 0.02f,
            entropy = 4.0f
        )
        val (decision, _) = captureLogs { PresetGateFOTO.decide(features) }
        assertEquals("PORTRAIT_SOFT", decision.preset.id)
    }

    private fun captureLogs(block: () -> PresetDecision): Pair<PresetDecision, List<String>> {
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        System.setOut(ps)
        return try {
            val decision = block()
            ps.flush()
            val lines = baos.toString(Charsets.UTF_8.name())
                .split('\n')
                .filter { it.isNotBlank() }
            decision to lines
        } finally {
            System.setOut(originalOut)
        }
    }

    private fun assertLogsContain(logs: List<String>, events: Set<String>) {
        val seen = events.associateWith { false }.toMutableMap()
        for (line in logs) {
            events.forEach { event ->
                if (line.contains("\"tag\":\"PGATE\"") && line.contains("\"evt\":\"$event\"")) {
                    seen[event] = true
                }
            }
        }
        events.forEach { event ->
            assertTrue(seen[event] == true, "Missing log event $event in $logs")
        }
    }
}
