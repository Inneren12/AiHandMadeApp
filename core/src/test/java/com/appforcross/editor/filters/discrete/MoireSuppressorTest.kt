package com.appforcross.editor.filters.discrete

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16

class MoireSuppressorTest {

    @Test
    fun notchFilterReducesVarianceOnStripedPattern() {
        val width = 16
        val height = 16
        val data = ShortArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if ((x % 2) == 0) 0.9f else 0.1f
                data[y * width + x] = HalfFloats.fromFloat(value)
            }
        }
        val image = LinearImageF16(width, height, 1, data)
        val suppressor = MoireSuppressor(
            MoireConfig(enabled = true, mode = MoireMode.NOTCH, maxLag = 4, detectionThreshold = 0.3f),
        )
        val scratch = FloatArray(width * height)
        val scratch2 = FloatArray(width * height)
        val scratch3 = FloatArray(width * height)

        val processed = suppressor.apply(image, scratch, scratch2, scratch3)

        val originalVariance = variance(image.data, width * height)
        val processedVariance = variance(processed.data, width * height)

        assertTrue(processedVariance < originalVariance)
    }

    private fun variance(data: ShortArray, size: Int): Double {
        var mean = 0.0
        for (i in 0 until size) {
            mean += HalfFloats.toFloat(data[i])
        }
        mean /= size.toDouble()
        var acc = 0.0
        for (i in 0 until size) {
            val d = HalfFloats.toFloat(data[i]) - mean
            acc += d.toDouble().pow(2.0)
        }
        return acc / size.toDouble()
    }
}
