package com.appforcross.editor.color

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object ColorMgmt {
    // --------------------------
    // OKLab / OKLCh (Björn Ottosson)
    // --------------------------
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

    /** OKLab ← sRGB(8-бит). */
    fun sRgb8ToOKLab(r8: Int, g8: Int, b8: Int): FloatArray {
        fun srgbToLinear(u: Double): Double = if (u <= 0.04045) u / 12.92 else ((u + 0.055) / 1.055).pow(2.4)
        val r = srgbToLinear(r8 / 255.0)
        val g = srgbToLinear(g8 / 255.0)
        val b = srgbToLinear(b8 / 255.0)
        // LMS
        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
        val l_ = cbrt(l)
        val m_ = cbrt(m)
        val s_ = cbrt(s)
        val L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
        val A = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        val B = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
        return floatArrayOf(L.toFloat(), A.toFloat(), B.toFloat())
    }

    /** OKLab → OKLCh. */
    fun okLabToOKLCh(L: Float, a: Float, b: Float): FloatArray {
        val C = sqrt((a * a + b * b).toDouble()).toFloat()
        val h = atan2(b.toDouble(), a.toDouble()).toFloat()
        return floatArrayOf(L, C, h)
    }

    // --------------------------
    // CIE L*a*b* (D65) и ΔE2000
    // --------------------------
    private const val Xn = 0.95047
    private const val Yn = 1.0
    private const val Zn = 1.08883

    private fun srgbToLinear(u: Double): Double = if (u <= 0.04045) u / 12.92 else ((u + 0.055) / 1.055).pow(2.4)

    private fun linearToXyz(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val X = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val Y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val Z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        return Triple(X, Y, Z)
    }

    private fun xyzToLab(X: Double, Y: Double, Z: Double): FloatArray {
        fun f(t: Double): Double {
            val e = 216.0 / 24389.0
            val k = 24389.0 / 27.0
            return if (t > e) cbrt(t) else (k * t + 16.0) / 116.0
        }
        val fx = f(X / Xn)
        val fy = f(Y / Yn)
        val fz = f(Z / Zn)
        val L = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val b = 200.0 * (fy - fz)
        return floatArrayOf(L.toFloat(), a.toFloat(), b.toFloat())
    }

    /** sRGB (float 0..1, gamma-coded) → Lab(D65). */
    fun sRgbToLab(r: Float, g: Float, b: Float): FloatArray {
        val rl = srgbToLinear(r.toDouble())
        val gl = srgbToLinear(g.toDouble())
        val bl = srgbToLinear(b.toDouble())
        val (X, Y, Z) = linearToXyz(rl, gl, bl)
        return xyzToLab(X, Y, Z)
    }

    /** Linear sRGB (float 0..1, linear) → Lab(D65). */
    fun linearRgbToLab(r: Float, g: Float, b: Float): FloatArray {
        val (X, Y, Z) = linearToXyz(r.toDouble(), g.toDouble(), b.toDouble())
        return xyzToLab(X, Y, Z)
    }

    /**
     * Реализация CIEDE2000 (Sharma 2005). Принимает два Lab-вектора [L,a,b].
     * Возвращает ΔE00 в double.
     */
    fun deltaE00(labA: FloatArray, labB: FloatArray): Double {
        require(labA.size >= 3 && labB.size >= 3) { "deltaE00 requires [L,a,b] for both operands" }
        val L1 = labA[0].toDouble()
        val a1 = labA[1].toDouble()
        val b1 = labA[2].toDouble()
        val L2 = labB[0].toDouble()
        val a2 = labB[1].toDouble()
        val b2 = labB[2].toDouble()

        val kL = 1.0
        val kC = 1.0
        val kH = 1.0
        val C1 = sqrt(a1 * a1 + b1 * b1)
        val C2 = sqrt(a2 * a2 + b2 * b2)
        val Cbar = (C1 + C2) * 0.5
        val Cbar7 = Cbar.pow(7.0)
        val G = 0.5 * (1.0 - sqrt(Cbar7 / (Cbar7 + 25.0.pow(7.0))))
        val a1p = (1.0 + G) * a1
        val a2p = (1.0 + G) * a2
        val C1p = sqrt(a1p * a1p + b1 * b1)
        val C2p = sqrt(a2p * a2p + b2 * b2)

        fun hp(x: Double, y: Double): Double {
            if (x == 0.0 && y == 0.0) return 0.0
            var h = Math.toDegrees(atan2(y, x))
            if (h < 0) h += 360.0
            return h
        }

        val h1p = hp(a1p, b1)
        val h2p = hp(a2p, b2)
        val dLp = L2 - L1
        val dCp = C2p - C1p
        val dhp = when {
            C1p * C2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180.0 -> h2p - h1p
            h2p <= h1p -> h2p - h1p + 360.0
            else -> h2p - h1p - 360.0
        }
        val dHp = 2.0 * sqrt(C1p * C2p) * sin(Math.toRadians(dhp / 2.0))
        val Lbarp = (L1 + L2) * 0.5
        val Cbarp = (C1p + C2p) * 0.5
        val hbarp = when {
            C1p * C2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) * 0.5
            (h1p + h2p) < 360.0 -> (h1p + h2p + 360.0) * 0.5
            else -> (h1p + h2p - 360.0) * 0.5
        }
        val T = 1.0 -
            0.17 * cos(Math.toRadians(hbarp - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hbarp)) +
            0.32 * cos(Math.toRadians(3.0 * hbarp + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hbarp - 63.0))
        val Sl = 1.0 + (0.015 * (Lbarp - 50.0).pow(2.0)) / sqrt(20.0 + (Lbarp - 50.0).pow(2.0))
        val Sc = 1.0 + 0.045 * Cbarp
        val Sh = 1.0 + 0.015 * Cbarp * T
        val dTheta = 30.0 * kotlin.math.exp(-((hbarp - 275.0) / 25.0).pow(2.0))
        val Rc = 2.0 * sqrt(Cbarp.pow(7.0) / (Cbarp.pow(7.0) + 25.0.pow(7.0)))
        val Rt = -sin(Math.toRadians(2.0 * dTheta)) * Rc
        val dE = sqrt(
            (dLp / (kL * Sl)).pow(2.0) +
            (dCp / (kC * Sc)).pow(2.0) +
            (dHp / (kH * Sh)).pow(2.0) +
            Rt * (dCp / (kC * Sc)) * (dHp / (kH * Sh))
        )
        Logger.i("COLOR", "params", mapOf("fn" to "deltaE00"))
        Logger.i("COLOR", "done", mapOf("ms" to 0, "memMB" to 0))
        return dE
    }
}
