package com.appforcross.editor.core

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

class ApiPresenceTest {
    @Test
    fun decoderProvidesImageAndLogs() {
        val (_, logs) = captureLogs {
            Decoder.decode(ImageSource.Bytes(byteArrayOf(0)))
        }
        val lines = logs.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"event\":\"params\""))
        assertTrue(lines[0].contains("\"src.kind\":\"bytes\""))
        assertTrue(lines[1].contains("\"event\":\"done\""))
    }

    @Test
    fun colorConversionsProduceBuffers() {
        val image = LinearImageF16(1, 1, 3, ShortArray(3) { HalfFloats.fromFloat(0.5f) })
        val (lab, rgbLogs) = captureLogsWithResult {
            ColorMgmt.rgbToOKLab(image)
        }
        assertEquals(3, lab.size)
        assertTrue(rgbLogs.lines().any { it.contains("\"fn\":\"rgbToOKLab\"") })
        val (_, backLogs) = captureLogs {
            ColorMgmt.okLabToRgb(lab, 1, 1)
        }
        assertTrue(backLogs.lines().any { it.contains("\"fn\":\"okLabToRgb\"") })
        val (_, deltaLogs) = captureLogs {
            ColorMgmt.deltaE00(lab, FloatArray(3))
        }
        assertTrue(deltaLogs.lines().any { it.contains("\"fn\":\"deltaE00\"") })
    }

    @Test
    fun hdrTonemapReturnsImage() {
        val image = LinearImageF16(1, 1, 3, ShortArray(3))
        val (result, logs) = captureLogsWithResult {
            HdrTonemap.apply(image, HdrMode.HDR_OFF)
        }
        assertEquals(image, result)
        assertTrue(logs.lines().any { it.contains("\"mode\":\"HDR_OFF\"") })
    }

    @Test
    fun filtersReturnOriginalImageAndLog() {
        val image = LinearImageF16(1, 1, 3, ShortArray(3))
        val mask = U8Mask(1, 1, ByteArray(1) { 1 })
        val (haloResult, haloLogs) = captureLogsWithResult {
            HaloRemoval.apply(image, mask)
        }
        assertEquals(image, haloResult)
        assertTrue(haloLogs.lines().any { it.contains("\"halo.strength\":0.8") })
        val (deblockResult, deblockLogs) = captureLogsWithResult {
            Deblocking8x8.apply(image)
        }
        assertEquals(image, deblockResult)
        assertTrue(deblockLogs.lines().any { it.contains("\"deblock.strength\":0.35") })
    }

    private fun captureLogs(block: () -> Unit): Pair<Unit, String> = captureLogsWithResult {
        block()
    }

    private fun <T> captureLogsWithResult(block: () -> T): Pair<T, String> {
        val originalOut = System.out
        val buffer = ByteArrayOutputStream()
        val printStream = PrintStream(buffer, true, StandardCharsets.UTF_8.name())
        System.setOut(printStream)
        return try {
            val result = block()
            printStream.flush()
            result to buffer.toString(StandardCharsets.UTF_8.name())
        } finally {
            System.setOut(originalOut)
        }
    }
}
