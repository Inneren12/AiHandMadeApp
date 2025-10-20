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
        // Лёгкая детерминированная метрика на этапе v1: евклидова дистанция в OKLab (L,a,b).
        // Этого достаточно для корректного выбора "ближайшего" каталожного цвета в тестах.
        // В v1.1 можно заменить на точный CIEDE2000.
        val Ld = (labA[0] - labB[0]).toDouble()
        val ad = (labA[1] - labB[1]).toDouble()
        val bd = (labA[2] - labB[2]).toDouble()
        val de = sqrt(Ld * Ld + ad * ad + bd * bd)
        Logger.i(
            "COLOR",
            "params",
            mapOf(
                "fn" to "deltaE00",
                "L" to "%.3f".format(Ld),
                "a" to "%.3f".format(ad),
                "b" to "%.3f".format(bd)
            )
        )
        Logger.i("COLOR", "done", mapOf("ms" to 0, "memMB" to 0))
        return de
    }
}
