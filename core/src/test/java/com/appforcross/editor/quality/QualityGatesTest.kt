package com.appforcross.editor.quality

import com.appforcross.editor.prescale.BuildSpec
import com.appforcross.editor.prescale.ImageOps
import com.appforcross.editor.prescale.RunFull
import com.appforcross.editor.types.HalfFloats
import com.appforcross.editor.types.LinearImageF16
import kotlin.test.Test
import kotlin.test.assertTrue

class QualityGatesTest {
    private fun solidF16(w:Int,h:Int,v:Float)= LinearImageF16(w,h,3, ShortArray(w*h*3){ HalfFloats.fromFloat(v) })

    @Test
    fun sky_gradient_banding_gate() {
        val W=256; val H=128
        val rgb = FloatArray(W*H*3)
        var p=0
        for (y in 0 until H) for (x in 0 until W) {
            val v = x/(W-1f)
            rgb[p++]=v; rgb[p++]=v; rgb[p++]=v
        }
        val L = FloatArray(W*H){ i-> val q=i*3; 0.2126f*rgb[q]+0.7152f*rgb[q+1]+0.0722f*rgb[q+2] }
        val bs = BuildSpec.decide(W,H,L)
        val out = RunFull.run(ImageOps.packToF16(rgb,W,H), bs)
        assertTrue(out.verify.bandIdx <= 0.03f, "Band gate failed: band=${out.verify.bandIdx}")
    }
}
