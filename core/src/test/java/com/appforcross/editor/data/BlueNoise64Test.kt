package com.appforcross.editor.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlueNoise64Test {
    @BeforeTest
    fun setUp() {
        BlueNoise64.logInterceptor = null
    }

    @AfterTest
    fun tearDown() {
        BlueNoise64.logInterceptor = null
    }

    @Test
    fun determinismForDefaultSpec() {
        val spec = BN64Spec()
        val first = BlueNoise64.generate(spec)
        val second = BlueNoise64.generate(spec)
        assertContentEquals(first, second)
        val hash1 = BlueNoise64.sha256(first)
        val hash2 = BlueNoise64.sha256(second)
        assertEquals(hash1, hash2)
    }

    @Test
    fun differentSeedProducesDifferentHash() {
        val spec = BN64Spec()
        val base = BlueNoise64.generate(spec)
        val modified = BlueNoise64.generate(spec.copy(seed = spec.seed xor 0xFFFFL))
        val hashBase = BlueNoise64.sha256(base)
        val hashModified = BlueNoise64.sha256(modified)
        assertNotEquals(hashBase, hashModified)
    }

    @Test
    fun rangeAndSizeConstraints() {
        val spec = BN64Spec()
        val bytes = BlueNoise64.generate(spec)
        assertEquals(spec.size * spec.size, bytes.size)
        for (value in bytes) {
            val unsigned = value.toInt() and 0xFF
            assertTrue(unsigned in 0..255, "Value $unsigned is out of range")
        }
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
                    totalDiff += kotlin.math.abs(center - right)
                    count++
                }
                if (y + 1 < size) {
                    val down = bytes[(y + 1) * size + x].toInt() and 0xFF
                    totalDiff += kotlin.math.abs(center - down)
                    count++
                }
            }
        }
        val average = if (count == 0) 0.0 else totalDiff / count / 255.0
        assertTrue(average > 15.0 / 255.0, "Average neighbor delta too low: $average")
    }

    @Test
    fun logsContainRequiredKeys() {
        val events = mutableListOf<Triple<String, String, Map<String, Any?>>>()
        BlueNoise64.logInterceptor = { tag, event, payload ->
            events += Triple(tag, event, payload)
        }
        val bytes = BlueNoise64.generate(BN64Spec())
        assertEquals(BN64Spec().size * BN64Spec().size, bytes.size)
        val paramsEvent = events.firstOrNull { it.second == "params" }
        val doneEvent = events.firstOrNull { it.second == "done" }
        val paramsPayload = assertNotNull(paramsEvent, "params event missing").third
        val donePayload = assertNotNull(doneEvent, "done event missing").third
        val expectedParamKeys = setOf("bn.size", "bn.passes", "bn.sigma1", "bn.sigma2", "bn.seed", "bn.method")
        assertTrue(paramsPayload.keys.containsAll(expectedParamKeys), "Missing params keys")
        val expectedDoneKeys = setOf("ms", "bytes", "hash")
        assertTrue(donePayload.keys.containsAll(expectedDoneKeys), "Missing done keys")
        assertEquals(bytes.size, donePayload["bytes"])
        val hash = BlueNoise64.sha256(bytes)
        assertEquals(hash, donePayload["hash"])
    }
}
