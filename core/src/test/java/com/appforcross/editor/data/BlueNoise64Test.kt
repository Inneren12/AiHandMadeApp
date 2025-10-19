package com.appforcross.editor.data

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlueNoise64Test {
    @Test
    fun determinismForDefaultSpec() {
        val spec = BN64Spec()
        val first = BlueNoise64.generate(spec)
        val second = BlueNoise64.generate(spec)
        assertTrue(first.contentEquals(second), "Bytes must be identical for the same spec")
        val hash1 = BlueNoise64.sha256(first)
        val hash2 = BlueNoise64.sha256(second)
        assertEquals(hash1, hash2, "Hashes must match for identical bytes")
    }

    @Test
    fun differentSeedProducesDifferentHash() {
        val spec = BN64Spec()
        val base = BlueNoise64.generate(spec)
        val modified = BlueNoise64.generate(spec.copy(seed = spec.seed xor 0xFFFFL))
        val hashBase = BlueNoise64.sha256(base)
        val hashModified = BlueNoise64.sha256(modified)
        assertNotEquals(hashBase, hashModified, "Hashes should differ for different seeds")
    }

    @Test
    fun rangeAndSizeConstraints() {
        val spec = BN64Spec()
        val bytes = BlueNoise64.generate(spec)
        assertEquals(spec.size * spec.size, bytes.size)
        val unsigned = bytes.map { it.toInt() and 0xFF }
        val mn = unsigned.minOrNull() ?: error("No minimum")
        val mx = unsigned.maxOrNull() ?: error("No maximum")
        assertTrue(mn >= 0, "All values must be >= 0")
        assertTrue(mx <= 255, "All values must be <= 255")
        assertTrue(mn <= 5, "Min should be near 0")
        assertTrue(mx >= 250, "Max should be near 255")
    }

    @Test
    fun neighborDeltaSanityCheck() {
        val spec = BN64Spec()
        val bytes = BlueNoise64.generate(spec)
        val size = spec.size
        var totalDiff = 0.0
        var count = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val center = bytes[y * size + x].toInt() and 0xFF
                if (x + 1 < size) {
                    val right = bytes[y * size + (x + 1)].toInt() and 0xFF
                    totalDiff += abs(center - right)
                    count++
                }
                if (y + 1 < size) {
                    val down = bytes[(y + 1) * size + x].toInt() and 0xFF
                    totalDiff += abs(center - down)
                    count++
                }
            }
        }
        val average = if (count == 0) 0.0 else totalDiff / count
        assertTrue(average > 15.0, "Average neighbor |Δ| should be sufficiently high")
    }

    @Test
    fun logsContainRequiredKeys() {
        val spec = BN64Spec()
        val oldOut = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos, true, StandardCharsets.UTF_8.name()))
        val bytes: ByteArray
        val hash: String
        try {
            bytes = BlueNoise64.generate(spec)
            hash = BlueNoise64.sha256(bytes)
        } finally {
            System.setOut(oldOut)
        }
        val log = baos.toString(StandardCharsets.UTF_8.name())
        assertTrue(log.contains("\"bn.size\":\"${spec.size}\"") || log.contains("\"bn.size\":${spec.size}"))
        assertTrue(log.contains("\"bn.passes\":\"${spec.passes}\"") || log.contains("\"bn.passes\":${spec.passes}"))
        assertTrue(log.contains("\"bn.sigma1\":\"${spec.sigma1}\"") || log.contains("\"bn.sigma1\":${spec.sigma1}"))
        assertTrue(log.contains("\"bn.sigma2\":\"${spec.sigma2}\"") || log.contains("\"bn.sigma2\":${spec.sigma2}"))
        assertTrue(log.contains("\"bn.seed\":\"0x${spec.seed.toString(16)}\""))
        assertTrue(log.contains("\"bn.method\":\"${spec.method}\""))
        assertTrue(log.contains("\"bytes\":\"${bytes.size}\"") || log.contains("\"bytes\":${bytes.size}"))
        assertTrue(log.contains(hash), "Log must contain resulting hash")
        assertTrue(log.contains("\"evt\":\"done\""), "Must contain done event")
        assertTrue(log.contains("\"evt\":\"params\""), "Must contain params event")
    }

    @Test
    fun sizeOtherThan64_throws() {
        assertFailsWith<IllegalArgumentException> {
            BlueNoise64.generate(BN64Spec(size = 32))
        }
    }
}
