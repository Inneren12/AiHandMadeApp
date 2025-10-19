package com.appforcross.editor.preset

/** Specification describing parameters for FOTO presets. */
data class PresetSpec(
    val id: String,
    val label: String,
    val kSigma: Float,
    val edgeMult: Float,
    val flatMult: Float,
    val filter: String,
    val addons: List<String> = emptyList()
)

/** Decision returned by the FOTO preset gate. */
data class PresetDecision(
    val preset: PresetSpec,
    val reason: Map<String, Any>,
    val confidence: Float
)
