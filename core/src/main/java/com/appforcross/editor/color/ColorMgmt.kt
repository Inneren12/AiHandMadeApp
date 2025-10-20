package com.appforcross.editor.color

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.sqrt

object ColorMgmt {
    fun rgbToOKLab(img: LinearImageF16): FloatArray {
        Logger.i("COLOR", "params", mapOf("fn" to "rgbToOKLab", "w" to img.width, "h" to img.height))
        val out = FloatArray(img.width * img.height * 3) { 0f }
        Logger.i("COLOR", "done", mapOf("ms" to 0, "memMB" to 0))
        return out
    }
    fun okLabToRgb(lab: FloatArray, width: Int, height: Int): LinearImageF16 {
        Logger.i("COLOR", "params", mapOf("fn" to "okLabToRgb", "w" to width, "h" to height))
        val out = LinearImageF16(width, height, 3, ShortArray(width * height * 3))
        Logger.i("COLOR", "done", mapOf("ms" to 0, "memMB" to 0))
        return out
    }
    fun deltaE00(labA: FloatArray, labB: FloatArray): Double {
        require(labA.size >= 3 && labB.size >= 3) { "deltaE00 requires [L,a,b] for both operands" }
        val Ld = (labA[0] - labB[0]).toDouble()
        val ad = (labA[1] - labB[1]).toDouble()
        val bd = (labA[2] - labB[2]).toDouble()
        val de = sqrt(Ld * Ld + ad * ad + bd * bd)
        Logger.i("COLOR", "params", mapOf("fn" to "deltaE00"))
        Logger.i("COLOR", "done", mapOf("ms" to 0, "memMB" to 0))
        return de
    }
}
