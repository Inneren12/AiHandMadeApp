package com.appforcross.editor.palette

import kotlin.math.sqrt

object Spread {
    fun enforce(palette: FloatArray, minDelta: Float): FloatArray {
        val colors = palette.size / 3
        val out = palette.copyOf()
        repeat(4) {
            var adjusted = false
            for (i in 0 until colors) {
                for (j in i + 1 until colors) {
                    val baseI = i * 3
                    val baseJ = j * 3
                    val de = PaletteMath.deltaE(
                        out[baseI + 0], out[baseI + 1], out[baseI + 2],
                        out[baseJ + 0], out[baseJ + 1], out[baseJ + 2]
                    ).toFloat()
                    if (de < minDelta) {
                        val diffL = out[baseJ + 0] - out[baseI + 0]
                        val diffA = out[baseJ + 1] - out[baseI + 1]
                        val diffB = out[baseJ + 2] - out[baseI + 2]
                        var norm = sqrt(diffL * diffL + diffA * diffA + diffB * diffB)
                        if (norm < 1e-4f) {
                            norm = 1f
                        }
                        val shift = (minDelta - de) * 0.5f
                        val scale = shift / norm
                        out[baseI + 0] = clampL(out[baseI + 0] - diffL * scale)
                        out[baseI + 1] = clampAB(out[baseI + 1] - diffA * scale)
                        out[baseI + 2] = clampAB(out[baseI + 2] - diffB * scale)
                        out[baseJ + 0] = clampL(out[baseJ + 0] + diffL * scale)
                        out[baseJ + 1] = clampAB(out[baseJ + 1] + diffA * scale)
                        out[baseJ + 2] = clampAB(out[baseJ + 2] + diffB * scale)
                        adjusted = true
                    }
                }
            }
            if (!adjusted) return out
        }
        return out
    }

    private fun clampL(v: Float): Float = v.coerceIn(0f, 1f)
    private fun clampAB(v: Float): Float = v.coerceIn(-1.5f, 1.5f)
}
