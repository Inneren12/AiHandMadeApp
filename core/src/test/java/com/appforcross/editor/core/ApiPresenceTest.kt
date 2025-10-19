package com.appforcross.editor.core

import com.appforcross.editor.color.ColorMgmt
import com.appforcross.editor.color.HdrTonemap
import com.appforcross.editor.filters.Deblocking8x8
import com.appforcross.editor.filters.HaloRemoval
import com.appforcross.editor.io.Decoder
import com.appforcross.editor.types.HdrMode
import com.appforcross.editor.types.ImageSource
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiPresenceTest {
    @Test
    fun api_presence_and_logging() {
        val img = LinearImageF16(1, 1, 3, ShortArray(3))

        // IO
        val (decoded, meta) = Decoder.decode(ImageSource.Bytes(byteArrayOf()), HdrMode.HDR_OFF)
        assertEquals(1, decoded.width)
        assertEquals(HdrMode.HDR_OFF, meta.hdrMode)

        // Color
        val lab = ColorMgmt.rgbToOKLab(img)
        assertEquals(3, lab.size)
        val back = ColorMgmt.okLabToRgb(lab, 1, 1)
        assertEquals(1, back.width)
        val delta = ColorMgmt.deltaE00(FloatArray(3), FloatArray(3))
        assertTrue(abs(delta - 0.0) <= 1e-9, "deltaE00 expected 0.0, was $delta")

        // HDR
        val tonemapped = HdrTonemap.apply(img, HdrMode.HDR_OFF, null)
        assertEquals(1, tonemapped.width)

        // Filters
        val halo = HaloRemoval.apply(img, null, 0.8f)
        val deb = Deblocking8x8.apply(img, 0.35f)
        assertEquals(1, halo.width)
        assertEquals(1, deb.width)
    }
}
