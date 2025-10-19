package com.appforcross.editor.data

import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

/**
 * Specification for BlueNoise64 generator parameters.
 */
data class BN64Spec(
    val size: Int = 64,
    val passes: Int = 4,
    val sigma1: Double = 1.2,
    val sigma2: Double = 2.4,
    val seed: Long = 0xA1F00D01L,
    val method: String = "DOG_VOID"
)

/**
 * Provides a deterministic blue-noise ranking map generator.
 */
object BlueNoise64 {
    internal var logInterceptor: ((String, String, Map<String, Any?>>) -> Unit)? = null

    /**
     * Generates a deterministic rank map (0..255) of [spec.size]×[spec.size].
     */
    fun generate(spec: BN64Spec = BN64Spec()): ByteArray {
        val startNs = System.nanoTime()
        validateSpec(spec)
        log("ASSETS", "params", mapOf(
            "bn.size" to spec.size,
            "bn.passes" to spec.passes,
            "bn.sigma1" to spec.sigma1,
            "bn.sigma2" to spec.sigma2,
            "bn.seed" to spec.seed,
            "bn.method" to spec.method
        ))

        val working = generateInitialNoise(spec.size, spec.seed)
        repeat(spec.passes) {
            val blurFine = gaussianBlur(working, spec.size, spec.sigma1)
            val blurWide = gaussianBlur(working, spec.size, spec.sigma2)
            for (i in working.indices) {
                working[i] = working[i] + (blurFine[i] - blurWide[i])
            }
            normalizeInPlace(working)
        }

        val ranked = rankToBytes(working)
        val hash = sha256(ranked)
        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        log("ASSETS", "done", mapOf(
            "ms" to durationMs,
            "bytes" to ranked.size,
            "hash" to hash
        ))
        return ranked
    }

    /**
     * Calculates the SHA-256 digest for the provided [bytes] and returns a lowercase hex string.
     */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val result = StringBuilder(hash.size * 2)
        for (b in hash) {
            result.append(((b.toInt() ushr 4) and 0xF).toString(16))
            result.append((b.toInt() and 0xF).toString(16))
        }
        return result.toString()
    }

    private fun validateSpec(spec: BN64Spec) {
        require(spec.size > 0) { "size must be positive" }
        require(spec.passes > 0) { "passes must be positive" }
        require(spec.sigma1 > 0.0) { "sigma1 must be positive" }
        require(spec.sigma2 > 0.0) { "sigma2 must be positive" }
        require(spec.sigma2 >= spec.sigma1) { "sigma2 must be >= sigma1" }
    }

    private fun generateInitialNoise(size: Int, seed: Long): DoubleArray {
        val total = size * size
        val values = DoubleArray(total)
        var state = seed
        for (i in 0 until total) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val shifted = (state ushr 11) and ((1L shl 53) - 1)
            values[i] = shifted.toDouble() / (1L shl 53).toDouble()
        }
        return values
    }

    private fun gaussianBlur(values: DoubleArray, size: Int, sigma: Double): DoubleArray {
        if (sigma <= 0.0) {
            return values.copyOf()
        }
        val kernel = gaussianKernel(sigma)
        val radius = kernel.size / 2
        val temp = DoubleArray(values.size)
        val output = DoubleArray(values.size)

        for (y in 0 until size) {
            val rowOffset = y * size
            for (x in 0 until size) {
                var acc = 0.0
                var kIndex = 0
                for (offset in -radius..radius) {
                    val sampleX = wrapIndex(x + offset, size)
                    acc += values[rowOffset + sampleX] * kernel[kIndex]
                    kIndex++
                }
                temp[rowOffset + x] = acc
            }
        }

        for (y in 0 until size) {
            for (x in 0 until size) {
                var acc = 0.0
                var kIndex = 0
                for (offset in -radius..radius) {
                    val sampleY = wrapIndex(y + offset, size)
                    acc += temp[sampleY * size + x] * kernel[kIndex]
                    kIndex++
                }
                output[y * size + x] = acc
            }
        }
        return output
    }

    private fun gaussianKernel(sigma: Double): DoubleArray {
        val radius = max(1, ceil(sigma * 3).toInt())
        val size = radius * 2 + 1
        val kernel = DoubleArray(size)
        val twoSigmaSq = 2.0 * sigma * sigma
        var sum = 0.0
        var index = 0
        for (offset in -radius..radius) {
            val value = exp(-(offset * offset) / twoSigmaSq)
            kernel[index++] = value
            sum += value
        }
        for (i in kernel.indices) {
            kernel[i] /= sum
        }
        return kernel
    }

    private fun normalizeInPlace(values: DoubleArray) {
        var min = Double.POSITIVE_INFINITY
        var maxValue = Double.NEGATIVE_INFINITY
        for (value in values) {
            if (value < min) min = value
            if (value > maxValue) maxValue = value
        }
        val range = maxValue - min
        if (range <= 1e-12) {
            java.util.Arrays.fill(values, 0.0)
            return
        }
        for (i in values.indices) {
            values[i] = (values[i] - min) / range
        }
    }

    private fun rankToBytes(values: DoubleArray): ByteArray {
        val total = values.size
        if (total <= 1) {
            return ByteArray(total)
        }
        val indices = (0 until total).sortedWith { a, b -> values[a].compareTo(values[b]) }
        val maxRank = total - 1
        val result = ByteArray(total)
        indices.forEachIndexed { rank, idx ->
            val quantized = (rank * 255) / maxRank
            result[idx] = quantized.toByte()
        }
        return result
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        var value = index % size
        if (value < 0) {
            value += size
        }
        return value
    }

    private fun log(tag: String, event: String, payload: Map<String, Any?>) {
        logInterceptor?.invoke(tag, event, payload) ?: run {
            val logger = loggerMethod
            if (logger != null) {
                val (method, receiver) = logger
                try {
                    method.invoke(receiver, tag, event, payload)
                } catch (_: Throwable) {
                    println("[$tag][$event] $payload")
                }
            } else {
                println("[$tag][$event] $payload")
            }
        }
    }

    private val loggerMethod: Pair<java.lang.reflect.Method, Any?>? by lazy {
        try {
            val clazz = Class.forName("com.appforcross.editor.logging.Logger")
            val method = clazz.getMethod(
                "i",
                String::class.java,
                String::class.java,
                Map::class.java
            )
            val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                null
            } else {
                try {
                    clazz.getField("INSTANCE").get(null)
                } catch (_: Throwable) {
                    clazz.kotlin.objectInstance
                }
            }
            Pair(method, receiver)
        } catch (_: Throwable) {
            null
        }
    }
}
