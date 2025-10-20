package com.appforcross.editor.palette

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal object PaletteMath {
    fun rgbToOklab(r: Float, g: Float, b: Float): FloatArray {
        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b

        val lRoot = cbrt(l)
        val mRoot = cbrt(m)
        val sRoot = cbrt(s)

        val okL = 0.2104542553f * lRoot + 0.7936177850f * mRoot - 0.0040720468f * sRoot
        val okA = 1.9779984951f * lRoot - 2.4285922050f * mRoot + 0.4505937099f * sRoot
        val okB = 0.0259040371f * lRoot + 0.7827717662f * mRoot - 0.8086757660f * sRoot
        return floatArrayOf(okL, okA, okB)
    }

    fun deltaE(l1: Float, a1: Float, b1: Float, l2: Float, a2: Float, b2: Float): Double {
        val L1 = l1 * 100.0
        val A1 = a1 * 100.0
        val B1 = b1 * 100.0
        val L2 = l2 * 100.0
        val A2 = a2 * 100.0
        val B2 = b2 * 100.0

        val avgLp = (L1 + L2) * 0.5
        val c1 = sqrt(A1 * A1 + B1 * B1)
        val c2 = sqrt(A2 * A2 + B2 * B2)
        val avgC = (c1 + c2) * 0.5
        val g = 0.5 * (1.0 - sqrt(avgC.pow(7.0) / (avgC.pow(7.0) + 6103515625.0)))
        val a1p = (1.0 + g) * A1
        val a2p = (1.0 + g) * A2
        val c1p = sqrt(a1p * a1p + B1 * B1)
        val c2p = sqrt(a2p * a2p + B2 * B2)
        val avgCp = (c1p + c2p) * 0.5
        val h1p = angle(B1.toFloat(), a1p)
        val h2p = angle(B2.toFloat(), a2p)
        val deltahp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= PI -> h2p - h1p
            h2p <= h1p -> h2p - h1p + 2.0 * PI
            else -> h2p - h1p - 2.0 * PI
        }
        val deltaLp = L2 - L1
        val deltaCp = c2p - c1p
        val deltaHp = 2.0 * sqrt(c1p * c2p) * sin(deltahp / 2.0)
        val avgHp = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= PI -> (h1p + h2p) * 0.5
            h1p + h2p < 2.0 * PI -> (h1p + h2p + 2.0 * PI) * 0.5
            else -> (h1p + h2p - 2.0 * PI) * 0.5
        }
        val t = 1.0 - 0.17 * cos(avgHp - PI / 6.0) + 0.24 * cos(2.0 * avgHp) +
            0.32 * cos(3.0 * avgHp + PI / 30.0) - 0.20 * cos(4.0 * avgHp - 63.0 * PI / 180.0)
        val deltaTheta = 30.0 * PI / 180.0 * exp(-((avgHp * 180.0 / PI - 275.0) / 25.0).pow(2.0))
        val rc = 2.0 * sqrt(avgCp.pow(7.0) / (avgCp.pow(7.0) + 6103515625.0))
        val sl = 1.0 + 0.015 * (avgLp - 50.0).pow(2.0) / sqrt(20.0 + (avgLp - 50.0).pow(2.0))
        val sc = 1.0 + 0.045 * avgCp
        val sh = 1.0 + 0.015 * avgCp * t
        val rt = -sin(2.0 * deltaTheta) * rc
        val kl = 1.0
        val kc = 1.0
        val kh = 1.0
        val termL = deltaLp / (sl * kl)
        val termC = deltaCp / (sc * kc)
        val termH = deltaHp / (sh * kh)
        return sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH)
    }

    private fun angle(b: Float, a: Double): Double {
        val angle = atan2(b.toDouble(), a)
        return if (angle >= 0) angle else angle + 2.0 * PI
    }

    private fun cbrt(value: Float): Float = Math.cbrt(value.toDouble()).toFloat()
}
