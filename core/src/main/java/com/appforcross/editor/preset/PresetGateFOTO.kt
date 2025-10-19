package com.appforcross.editor.preset

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.scene.SceneFeatures
import java.util.Locale
import kotlin.math.max

object PresetGateFOTO {

    data class GateParams(
        val kSigmaAuto: Float = 0.9f,
        val edgeMultAuto: Float = 0.8f,
        val flatMultAuto: Float = 1.1f
    )

    /** Мини-библиотека пресетов (дефолт). Можно расширять JSON-ом ниже. */
    val defaults: Map<String, PresetSpec> = mapOf(
        "AUTO_BALANCED" to PresetSpec("AUTO_BALANCED", "Auto balanced", 0.9f, 0.9f, 1.0f, "EWA_Mitchell"),
        "PORTRAIT_SOFT" to PresetSpec(
            "PORTRAIT_SOFT",
            "Portrait soft",
            0.8f,
            0.8f,
            1.1f,
            "EWA_Mitchell",
            listOf("ADD_SKIN_PROTECT")
        ),
        "LANDSCAPE_GRADIENTS" to PresetSpec(
            "LANDSCAPE_GRADIENTS",
            "Landscape gradients",
            1.0f,
            0.9f,
            1.2f,
            "EWA_Lanczos3",
            listOf("ADD_SKY_BANDING_SHIELD")
        ),
        "ARCHITECTURE_EDGES" to PresetSpec(
            "ARCHITECTURE_EDGES",
            "Architecture edges",
            0.7f,
            0.7f,
            1.0f,
            "EWA_Lanczos3",
            listOf("ADD_EDGE_GUARD")
        )
    )

    /**
     * Правило выбора пресета на основе SceneFeatures.
     * Вход: признаки от SceneAnalyzer (checker2x2, edgeDensity, top8Coverage, entropy ...).
     */
    fun decide(features: SceneFeatures, params: GateParams = GateParams()): PresetDecision {
        Logger.i(
            "PGATE",
            "params",
            mapOf(
                "kSigmaAuto" to params.kSigmaAuto,
                "edgeMultAuto" to params.edgeMultAuto,
                "flatMultAuto" to params.flatMultAuto
            )
        )
        val f = features
        val choice: String
        val why = linkedMapOf<String, Any>()

        when {
            f.edgeDensity >= 0.14f -> {
                choice = "ARCHITECTURE_EDGES"
                why["edgeDensity"] = f.edgeDensity
            }
            f.edgeDensity < 0.06f && f.entropy >= 5.0f -> {
                choice = "LANDSCAPE_GRADIENTS"
                why["entropy"] = f.entropy
            }
            f.entropy < 4.3f && f.edgeDensity in 0.06f..0.14f -> {
                choice = "PORTRAIT_SOFT"
                why["entropy"] = f.entropy
            }
            else -> {
                choice = "AUTO_BALANCED"
                why["fallback"] = true
            }
        }

        val base = defaults[choice] ?: defaults.getValue("AUTO_BALANCED")
        val adapt = base.copy(
            kSigma = params.kSigmaAuto * base.kSigma,
            edgeMult = params.edgeMultAuto * base.edgeMult,
            flatMult = params.flatMultAuto * base.flatMult
        )

        val confidence = when (choice) {
            "ARCHITECTURE_EDGES" -> clamp01((f.edgeDensity - 0.12f) / 0.10f)
            "LANDSCAPE_GRADIENTS" -> clamp01((f.entropy - 4.5f) / 2.0f)
            "PORTRAIT_SOFT" -> clamp01((4.5f - f.entropy) / 1.5f)
            else -> 0.55f
        }

        Logger.i(
            "PGATE",
            "decision",
            mapOf(
                "preset.id" to adapt.id,
                "preset.filter" to adapt.filter,
                "preset.addons" to adapt.addons.joinToString(separator = ","),
                "k_sigma" to adapt.kSigma,
                "edge_mult" to adapt.edgeMult,
                "flat_mult" to adapt.flatMult,
                "confidence" to String.format(Locale.US, "%.3f", confidence),
                "why" to why
            )
        )
        Logger.i("PGATE", "done", mapOf("ms" to 0))
        return PresetDecision(adapt, why, confidence)
    }

    private fun clamp01(v: Float) = max(0f, kotlin.math.min(1f, v))
}
