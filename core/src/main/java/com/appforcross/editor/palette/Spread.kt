package com.appforcross.editor.palette

import kotlin.math.sqrt

object Spread {
    fun enforce(palette: FloatArray, minDelta: Float): FloatArray {
        val colors = palette.size / 3
        if (colors <= 1) return palette
        val out = palette.copyOf()
        var iteration = 0
        var adjusted: Boolean
        do {
            adjusted = false
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
                        val shift = (minDelta - de) * 0.35f
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
            iteration++
        } while (adjusted && iteration < 4)

        var safety = 0
        var merged: Boolean
        do {
            val pair = closestPair(out)
            val distance = pair.second
            val keepMerging = distance < minDelta - 1e-3f && safety <= colors * 8
            if (keepMerging) {
                val i = pair.first.first
                val j = pair.first.second
                val baseI = i * 3
                val baseJ = j * 3
                val diffL = out[baseJ + 0] - out[baseI + 0]
                val diffA = out[baseJ + 1] - out[baseI + 1]
                val diffB = out[baseJ + 2] - out[baseI + 2]
                var norm = sqrt(diffL * diffL + diffA * diffA + diffB * diffB)
                val dirL: Float
                val dirA: Float
                val dirB: Float
                if (norm < 1e-5f) {
                    norm = 1f
                    dirL = 0.7071f
                    dirA = 0.0f
                    dirB = 0.7071f
                } else {
                    dirL = diffL / norm
                    dirA = diffA / norm
                    dirB = diffB / norm
                }
                val midL = (out[baseI + 0] + out[baseJ + 0]) * 0.5f
                val midA = (out[baseI + 1] + out[baseJ + 1]) * 0.5f
                val midB = (out[baseI + 2] + out[baseJ + 2]) * 0.5f
                val half = minDelta * 0.5f
                out[baseI + 0] = clampL(midL - dirL * half)
                out[baseI + 1] = clampAB(midA - dirA * half)
                out[baseI + 2] = clampAB(midB - dirB * half)
                out[baseJ + 0] = clampL(midL + dirL * half)
                out[baseJ + 1] = clampAB(midA + dirA * half)
                out[baseJ + 2] = clampAB(midB + dirB * half)
                safety++
            }
            merged = keepMerging
        } while (merged)
        return out
    }

    private fun clampL(v: Float): Float = v.coerceIn(0f, 1f)
    private fun clampAB(v: Float): Float = v.coerceIn(-1.5f, 1.5f)

    private fun closestPair(palette: FloatArray): Pair<Pair<Int, Int>, Float> {
        val colors = palette.size / 3
        var bestDistance = Float.POSITIVE_INFINITY
        var bestPair = 0 to 1
        for (i in 0 until colors) {
            for (j in i + 1 until colors) {
                val baseI = i * 3
                val baseJ = j * 3
                val de = PaletteMath.deltaE(
                    palette[baseI + 0], palette[baseI + 1], palette[baseI + 2],
                    palette[baseJ + 0], palette[baseJ + 1], palette[baseJ + 2]
                ).toFloat()
                if (de < bestDistance - 1e-5f) {
                    bestDistance = de
                    bestPair = i to j
                }
            }
        }
        return bestPair to bestDistance
    }
}
