package com.appforcross.editor.filters.discrete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import com.appforcross.editor.filters.discrete.Roi
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16

class DiscretePipelineTest {

    @Test
    fun pipelineDisabledReturnsEmptyMask() {
        val width = 4
        val height = 4
        val data = ShortArray(width * height) { HalfFloats.fromFloat(0.5f) }
        val image = LinearImageF16(width, height, 1, data)
        val pipeline = DiscretePipeline(DiscreteConfig(enabled = false))

        val output = pipeline.run(image)

        assertEquals(false, output.roiAccepted)
        assertEquals(width * height, output.mask.data.size)
        assertTrue(output.mask.data.all { it.toInt() == 0 })
    }

    @Test
    fun binarizationAndMorphologyAcceptsRoi() {
        val width = 8
        val height = 8
        val data = ShortArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if (y in 2..5 && x in 1..6) 0.9f else 0.05f
                data[y * width + x] = HalfFloats.fromFloat(value)
            }
        }
        val image = LinearImageF16(width, height, 1, data)
        val config = DiscreteConfig(
            enabled = true,
            binarization = BinarizationConfig(wBin = 3, k = 0.25f, smoothing = false),
            moire = MoireConfig(enabled = false, mode = MoireSuppressor.Mode.OFF),
            morphology = MorphologyConfig(
                enabled = true,
                roiMinForeground = 0.1f,
                roiMaxForeground = 0.8f,
                roiTransitionDensity = 0.05f,
            ),
        )
        val pipeline = DiscretePipeline(config)
        val roi = Roi(left = 1, top = 2, right = 7, bottom = 6)

        val output = pipeline.run(image, roi)

        assertTrue(output.roiAccepted)
        val ones = output.mask.data.count { it.toInt() != 0 }
        assertTrue(ones > width)
        // ensure mask deterministic by spot checking corners
        assertEquals(0, output.mask.data[0].toInt())
        assertEquals(0, output.mask.data[width * 3 + 0].toInt())
        assertEquals(1, output.mask.data[width * 3 + 2].toInt())
        assertEquals(0, output.mask.data[width * (roi.top - 1) + roi.left].toInt())
        assertEquals(0, output.mask.data[width * roi.bottom + roi.left].toInt())
    }

    @Test
    fun paramsRoiIsRespectedWhenArgumentOmitted() {
        val width = 8
        val height = 8
        val data = ShortArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if (y in 2..5 && x in 1..6) 0.85f else 0.1f
                data[y * width + x] = HalfFloats.fromFloat(value)
            }
        }
        val image = LinearImageF16(width, height, 1, data)
        val config = DiscreteConfig(
            enabled = true,
            binarization = BinarizationConfig(wBin = 3, k = 0.25f, smoothing = false),
            moire = MoireConfig(enabled = false, mode = MoireSuppressor.Mode.OFF),
            morphology = MorphologyConfig(
                enabled = true,
                roiMinForeground = 0.1f,
                roiMaxForeground = 0.85f,
                roiTransitionDensity = 0.05f,
            ),
        )
        val pipeline = DiscretePipeline(config)
        val roi = Roi(left = 1, top = 2, right = 7, bottom = 6)

        val output = pipeline.run(image, params = DiscreteParams(roi = roi))

        assertTrue(output.roiAccepted)
        val ones = output.mask.data.count { it.toInt() != 0 }
        assertTrue(ones > width)
        assertEquals(1, output.mask.data[width * 3 + 2].toInt())
        assertEquals(0, output.mask.data[width * (roi.top - 1) + roi.left].toInt())
    }
}
