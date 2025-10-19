package com.appforcross.editor.core

import org.junit.Test
import org.junit.Assert.*
import com.appforcross.editor.io.Decoder
import com.appforcross.editor.types.*
import com.appforcross.editor.color.ColorMgmt
import com.appforcross.editor.color.HdrTonemap
import com.appforcross.editor.filters.Deblocking8x8
import com.appforcross.editor.filters.HaloRemoval

class ApiPresenceTest {

    @Test
    fun api_presence_and_logging() {
        // minimal 1x1 F16 image
        val img = LinearImageF16(1, 1, 3, ShortArray(3))

        // IO
        val (decoded, meta) = Decoder.decode(ImageSource.Bytes(byteArrayOf()), HdrMode.HDR_OFF)
        assertNotNull(decoded)
        assertEquals(1, decoded.width)
        assertEquals(HdrMode.HDR_OFF, meta.hdrMode)

        // Color
        val lab = ColorMgmt.rgbToOKLab(img)
        assertEquals(3, lab.size)
        val back = ColorMgmt.okLabToRgb(lab, 1, 1)
        assertEquals(1, back.width)
        val de = ColorMgmt.deltaE00(FloatArray(3), FloatArray(3))+        assertEquals(0.0, de, 1e-9)
        // HDR
       val tonemapped = HdrTonemap.apply(img, HdrMode.HDR_OFF, null)
       assertNotNull(tonemapped)

        // Filters
        val halo = HaloRemoval.apply(img, null, 0.8f);  assertNotNull(halo)
        val deb  = Deblocking8x8.apply(img, 0.35f);     assertNotNull(deb)
    }
}
