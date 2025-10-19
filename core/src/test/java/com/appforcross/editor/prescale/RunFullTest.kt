package com.appforcross.editor.prescale

import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunFullTest {

    @Test
    fun tiling_no_seams() {
        val width = 640
        val height = 480
        val rgb = gradientRgb(width, height)
        val luminance = luminance(rgb, width, height)
        val source = ImageOps.packToF16(rgb, width, height)
        val build = BuildSpec.decide(width, height, luminance)
        val params = RunParams(tileMax = 128)
        val result = RunFull.run(source, build, params)
        val floats = toFloat(result.out)
        val overlap = max(2, ceil(2f * max(0f, 1.5f * build.sigma)).toInt())
        val seamMask = BooleanArray(width * height)
        val tiler = Tiler(width, height, params.tileMax, params.tileMax, overlap)
        tiler.forEachTile { tx, ty, tw, th, _, _ ->
            for (yy in 0 until th) {
                val wy = weight(yy, th, overlap)
                for (xx in 0 until tw) {
                    val wx = weight(xx, tw, overlap)
                    if (wx < 0.999f || wy < 0.999f) {
                        val gx = tx + xx
                        val gy = ty + yy
                        seamMask[gy * width + gx] = true
                    }
                }
            }
        }
        var seamSum = 0f
        var seamCount = 0
        var totalSum = 0f
        var totalCount = 0
        fun addDiff(a: Int, b: Int) {
            val diff = diff3(floats, a, b)
            totalSum += diff
            totalCount++
            if (seamMask[a] || seamMask[b]) {
                seamSum += diff
                seamCount++
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (x + 1 < width) addDiff(idx, y * width + x + 1)
                if (y + 1 < height) addDiff(idx, (y + 1) * width + x)
            }
        }
        assertTrue(seamCount > 0)
        val seamMean = seamSum / seamCount.toFloat()
        val totalMean = totalSum / totalCount.toFloat()
        assertTrue(
            seamMean <= totalMean * 1.5f + 1e-4f,
            "seamMean=$seamMean totalMean=$totalMean"
        )
    }

    @Test
    fun determinism() {
        val width = 320
        val height = 240
        val rgb = gradientRgb(width, height)
        val luminance = luminance(rgb, width, height)
        val source = ImageOps.packToF16(rgb, width, height)
        val build = BuildSpec.decide(width, height, luminance)
        val params = RunParams(tileMax = 96)
        val first = RunFull.run(source, build, params)
        val second = RunFull.run(source, build, params)
        val f1 = toFloat(first.out)
        val f2 = toFloat(second.out)
        for (i in f1.indices) {
            assertTrue(abs(f1[i] - f2[i]) <= 1e-6f, "idx=$i diff=${abs(f1[i] - f2[i])}")
        }
        val verify1 = first.verify
        val verify2 = second.verify
        assertEquals(verify1, verify2)
    }

    @Test
    fun verify_presence() {
        val width = 200
        val height = 150
        val rgb = gradientRgb(width, height)
        val luminance = luminance(rgb, width, height)
        val source = ImageOps.packToF16(rgb, width, height)
        val build = BuildSpec.decide(width, height, luminance)
        val result = RunFull.run(source, build)
        val verify = result.verify
        assertTrue(verify.ssimProxy in 0f..1f, "ssim=${verify.ssimProxy}")
        assertTrue(verify.edgeKeep in 0f..1f, "edgeKeep=${verify.edgeKeep}")
        assertTrue(verify.bandIdx in 0f..0.2f, "bandIdx=${verify.bandIdx}")
        assertTrue(verify.deltaE95Proxy >= 0f, "dE95=${verify.deltaE95Proxy}")
    }

    private fun gradientRgb(width: Int, height: Int): FloatArray {
        val data = FloatArray(width * height * 3)
        var i = 0
        for (y in 0 until height) {
            val fy = if (height <= 1) 0f else y.toFloat() / (height - 1).toFloat()
            for (x in 0 until width) {
                val fx = if (width <= 1) 0f else x.toFloat() / (width - 1).toFloat()
                data[i++] = fx
                data[i++] = fy
                data[i++] = (fx + fy) * 0.5f
            }
        }
        return data
    }

    private fun luminance(rgb: FloatArray, width: Int, height: Int): FloatArray {
        val L = FloatArray(width * height)
        var i = 0
        for (idx in 0 until width * height) {
            val r = rgb[i]
            val g = rgb[i + 1]
            val b = rgb[i + 2]
            L[idx] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            i += 3
        }
        return L
    }

    private fun toFloat(img: LinearImageF16): FloatArray {
        val out = FloatArray(img.width * img.height * img.planes)
        for (i in out.indices) {
            out[i] = HalfFloats.toFloat(img.data[i])
        }
        return out
    }

    private fun diff3(rgb: FloatArray, a: Int, b: Int): Float {
        val ia = a * 3
        val ib = b * 3
        val dr = abs(rgb[ia] - rgb[ib])
        val dg = abs(rgb[ia + 1] - rgb[ib + 1])
        val db = abs(rgb[ia + 2] - rgb[ib + 2])
        return (dr + dg + db) / 3f
    }

    private fun weight(p: Int, len: Int, overlap: Int): Float {
        if (overlap <= 0) return 1f
        val left = p.toFloat() / overlap.toFloat()
        val right = (len - 1 - p).toFloat() / overlap.toFloat()
        val wl = if (p < overlap) left.coerceIn(0f, 1f) else 1f
        val wr = if (len - 1 - p < overlap) right.coerceIn(0f, 1f) else 1f
        return kotlin.math.min(wl, wr)
    }
}
