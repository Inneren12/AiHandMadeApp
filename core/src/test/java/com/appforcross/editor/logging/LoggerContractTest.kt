package com.appforcross.editor.logging

import com.appforcross.editor.color.ColorMgmt
import com.appforcross.editor.color.HdrTonemap
import com.appforcross.editor.filters.Deblocking8x8
import com.appforcross.editor.filters.HaloRemoval
import com.appforcross.editor.io.Decoder
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.HdrMode
import com.appforcross.editor.types.ImageSource
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.U8Mask
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggerContractTest {
    @Test
    fun loggerOutputsJsonAndStoresLast() {
        val (logs, trip) = captureSingleLog {
            Logger.i("TEST", "params", mapOf("key" to 1, "flag" to true, "text" to "value"))
        }
        assertTrue(logs.trim().startsWith("{\"level\":\"INFO\""))
        assertTrue(logs.contains("\"payload\":{\"key\":1"))
        assertEquals("INFO", trip.level)
        assertEquals("TEST", trip.tag)
        assertEquals("params", trip.event)
        assertEquals(mapOf("key" to 1, "flag" to true, "text" to "value"), trip.payload)
    }

    @Test
    fun errorLogUpdatesLastEntry() {
        val (_, trip) = captureSingleLog {
            Logger.e("TEST", "fatal", mapOf("code" to "SAMPLE"))
        }
        assertEquals("ERROR", trip.level)
        assertEquals("fatal", trip.event)
        assertEquals(mapOf("code" to "SAMPLE"), trip.payload)
    }

    @Test
    fun apiLogsContainMandatoryKeys() {
        val decoderLogs = captureMultiLineLog {
            Decoder.decode(ImageSource.Bytes(byteArrayOf(1)))
        }
        assertTrue(decoderLogs.any { it.contains("\"src.kind\":\"bytes\"") })
        assertTrue(decoderLogs.any { it.contains("\"colorspace.out\":\"SRGB_LINEAR\"") })
        assertTrue(decoderLogs.any { it.contains("\"hdr.mode\":\"HDR_OFF\"") })

        val image = LinearImageF16(1, 1, 3, ShortArray(3) { HalfFloats.fromFloat(0f) })
        val colorLogs = captureMultiLineLog {
            ColorMgmt.rgbToOKLab(image)
        }
        assertTrue(colorLogs.any { it.contains("\"fn\":\"rgbToOKLab\"") })
        val tonemapLogs = captureMultiLineLog {
            HdrTonemap.apply(image, HdrMode.HDR_OFF)
        }
        assertTrue(tonemapLogs.any { it.contains("\"gainmap.present\":false") })
        val haloLogs = captureMultiLineLog {
            HaloRemoval.apply(image, U8Mask(1, 1, ByteArray(1)))
        }
        assertTrue(haloLogs.any { it.contains("\"halo.strength\":0.8") })
        val deblockLogs = captureMultiLineLog {
            Deblocking8x8.apply(image)
        }
        assertTrue(deblockLogs.any { it.contains("\"deblock.strength\":0.35") })
    }

    private fun captureSingleLog(block: () -> Unit): Pair<String, Logger.Trip> {
        val originalOut = System.out
        val buffer = ByteArrayOutputStream()
        val printStream = PrintStream(buffer, true, StandardCharsets.UTF_8.name())
        System.setOut(printStream)
        return try {
            block()
            printStream.flush()
            val logs = buffer.toString(StandardCharsets.UTF_8.name())
            logs to (Logger.last() ?: error("No log recorded"))
        } finally {
            System.setOut(originalOut)
        }
    }

    private fun captureMultiLineLog(block: () -> Unit): List<String> {
        val (logs, _) = captureSingleLog(block)
        return logs.lines().filter { it.isNotBlank() }
    }
}
