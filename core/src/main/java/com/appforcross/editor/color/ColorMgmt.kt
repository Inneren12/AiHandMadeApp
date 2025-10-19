package com.appforcross.editor.color

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16

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
        Logger.i("COLOR", "params", mapOf("fn" to "deltaE00", "n" to labA.size))
        Logger.i("COLOR", "done", mapOf("ms" to 0, "memMB" to 0))
        return 0.0
    }
}
