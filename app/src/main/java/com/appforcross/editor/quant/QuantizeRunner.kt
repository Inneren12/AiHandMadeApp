package com.appforcross.editor.quant

import com.appforcross.editor.logging.Logger
import kotlin.math.hypot

internal data class Anchor(val lab: FloatArray)

internal object QuantizeRunner {

    fun logAnchorDetection(
        black: Anchor,
        white: Anchor,
        neutral: Anchor,
        skinAnchor: Anchor?,
        skinCoverage: Float,
        skyAnchor: Anchor?,
        skyCoverage: Float,
        tauCover: Float
    ) {
        val skinInfo: Map<String, Any?> =
            if (skinAnchor != null) mapOf("ok" to true)
            else mapOf(
                "ok" to false,
                "reason" to if (skinCoverage >= tauCover) "no_candidate" else "fallback"
            )
        val skyInfo: Map<String, Any?> =
            if (skyAnchor != null) mapOf("ok" to true)
            else mapOf(
                "ok" to false,
                "reason" to if (skyCoverage >= tauCover) "no_candidate" else "fallback"
            )
        Logger.i(
            "PALETTE",
            "anchors.detect",
            mapOf<String, Any?>(
                "black" to mapOf("L" to black.lab[0], "C" to chroma(black.lab)),
                "white" to mapOf("L" to white.lab[0], "C" to chroma(white.lab)),
                "neutral_mid" to mapOf(
                    "L" to neutral.lab[0],
                    "a" to neutral.lab[1],
                    "b" to neutral.lab[2]
                ),
                "skin" to skinInfo,
                "sky" to skyInfo
            )
        )
    }

    private fun chroma(lab: FloatArray): Float {
        val a = lab.getOrElse(1) { 0f }.toDouble()
        val b = lab.getOrElse(2) { 0f }.toDouble()
        return hypot(a, b).toFloat()
    }
}
