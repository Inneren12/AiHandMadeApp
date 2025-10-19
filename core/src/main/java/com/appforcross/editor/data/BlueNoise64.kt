package com.appforcross.editor.data

import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

/**
 * Deterministic 64×64 blue-noise rank-map generator (void-and-cluster–like via DoG).
 * Returns a ByteArray of size N×N with values 0..255 (ranked, low-frequency voids).
 *
 * No file IO. No Android deps. Pure Kotlin/JVM.
 */
data class BN64Spec(
    val size: Int = 64,
    val passes: Int = 4,
    val sigma1: Double = 1.2,
    val sigma2: Double = 2.4,
    val seed: Long = 0xA1F00D01L,
    val method: String = "DOG_VOID"
)

object BlueNoise64 {

    /** Generate rank map 0..255 (size×size). Deterministic for the same spec. */
    fun generate(spec: BN64Spec = BN64Spec()): ByteArray {
        require(spec.size == 64) { "BlueNoise64: for v1 only size=64 is supported (got ${spec.size})." }
        val t0 = System.nanoTime()
        logI(
            "ASSETS", "params", mapOf(
                "bn.size" to spec.size,
                "bn.passes" to spec.passes,
                "bn.sigma1" to spec.sigma1,
                "bn.sigma2" to spec.sigma2,
                "bn.seed" to "0x${spec.seed.toString(16)}",
                "bn.method" to spec.method
            )
        )

        // 1) Init uniform noise via 32-bit LCG (deterministic).
        val n = spec.size
        val a = Array(n) { DoubleArray(n) }
        val rnd = lcg(spec.seed)
        for (y in 0 until n) for (x in 0 until n) a[y][x] = rnd.next()

        // 2) Multi-pass DoG to push energy to high frequencies.
        var cur = a
        repeat(spec.passes) {
            val g1 = gaussianBlur(cur, spec.sigma1)
            val g2 = gaussianBlur(cur, spec.sigma2)
            val dog = Array(n) { y -> DoubleArray(n) { x -> g1[y][x] - g2[y][x] } }
            cur = normalize01(dog)
        }

        // 3) Rank → 0..255
        val total = n * n
        val vals = DoubleArray(total) { i -> cur[i / n][i % n] }
        val idx = (0 until total).sortedBy { vals[it] }
        val ranks = IntArray(total)
        for (rank in idx.indices) ranks[idx[rank]] = rank
        val out = ByteArray(total) { i -> ((ranks[i] * 255) / (total - 1)).toByte() }

        val ms = ((System.nanoTime() - t0) / 1_000_000).toInt()
        val hash = sha256(out)
        logI("ASSETS", "done", mapOf("ms" to ms, "bytes" to out.size, "hash" to hash))
        return out
    }

    /** Hex SHA-256 of bytes. */
    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    // ---- internals ----

    private class Lcg(seed: Long) {
        // 32-bit LCG: x = (1664525*x + 1013904223) mod 2^32
        private var x = seed and 0xFFFF_FFFFL
        fun next(): Double {
            x = (1664525L * x + 1013904223L) and 0xFFFF_FFFFL
            return x.toDouble() / 4_294_967_296.0
        }
    }
    private fun lcg(seed: Long) = Lcg(seed)

    private fun gaussianBlur(a: Array<DoubleArray>, sigma: Double): Array<DoubleArray> {
        val (k, r) = gaussKernel1d(sigma)
        val tmp = convolveRows(a, k, r)
        return convolveCols(tmp, k, r)
    }

    private fun gaussKernel1d(sigma: Double): Pair<DoubleArray, Int> {
        val r = max(1.0, ceil(3.0 * sigma)).toInt()
        val k = DoubleArray(2 * r + 1) { j ->
            val x = j - r
            exp(-(x * x) / (2.0 * sigma * sigma))
        }
        val s = k.sum()
        for (i in k.indices) k[i] /= s
        return k to r
    }

    private fun convolveRows(a: Array<DoubleArray>, k: DoubleArray, r: Int): Array<DoubleArray> {
        val h = a.size; val w = a[0].size
        val out = Array(h) { DoubleArray(w) }
        for (y in 0 until h) {
            val row = a[y]
            for (x in 0 until w) {
                var s = 0.0
                var t = 0
                while (t < k.size) {
                    s += row[reflectIndex(x + t - r, w)] * k[t]
                    t++
                }
                out[y][x] = s
            }
        }
        return out
    }

    private fun convolveCols(a: Array<DoubleArray>, k: DoubleArray, r: Int): Array<DoubleArray> {
        val h = a.size; val w = a[0].size
        val out = Array(h) { DoubleArray(w) }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var s = 0.0
                var t = 0
                while (t < k.size) {
                    s += a[reflectIndex(y + t - r, h)][x] * k[t]
                    t++
                }
                out[y][x] = s
            }
        }
        return out
    }

    private fun reflectIndex(i0: Int, n: Int): Int {
        var i = i0
        while (i < 0 || i >= n) {
            i = if (i < 0) -i - 1 else 2 * n - i - 1
        }
        return i
    }

    private fun normalize01(a: Array<DoubleArray>): Array<DoubleArray> {
        var mn = Double.POSITIVE_INFINITY
        var mx = Double.NEGATIVE_INFINITY
        for (row in a) for (v in row) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val d = mx - mn
        if (d < 1e-12) return Array(a.size) { DoubleArray(a[0].size) }
        val s = 1.0 / d
        val out = Array(a.size) { DoubleArray(a[0].size) }
        for (y in a.indices) for (x in a[0].indices) out[y][x] = (a[y][x] - mn) * s
        return out
    }

    /** Minimal, dependency-free JSON logger. Falls back to println; no hard dep on project Logger. */
    private fun logI(tag: String, evt: String, data: Map<String, Any?>) {
        // If project logger exists at runtime, you can replace this with it. For now — plain JSON line.
        val json = buildString {
            append('{')
            append("\"lvl\":\"I\",\"tag\":\"").append(tag).append("\",\"evt\":\"").append(evt).append("\",\"data\":{")
            var first = true
            for ((k, v) in data) {
                if (!first) append(',') else first = false
                append('"').append(k).append('"').append(':')
                val s = v?.toString()?.replace("\"", "\\\"") ?: "null"
                append('"').append(s).append('"')
            }
            append("}}")
        }
        println(json)
    }
}
