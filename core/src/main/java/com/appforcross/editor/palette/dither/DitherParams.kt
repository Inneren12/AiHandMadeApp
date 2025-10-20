package com.appforcross.editor.palette.dither

/** Parameter bag for palette dithering. */
data class DitherParams(
    val mode: Mode = Mode.ORDERED,   // ORDERED | FS
    val ampSky: Float = 0.30f,
    val ampSkin: Float = 0.20f,
    val ampHiTex: Float = 0.70f,
    val ampFlat: Float = 0.25f,
    val ampEdgeFalloff: Float = 0.05f,  // снижение амплитуды вблизи кромок
    val diffusionCap: Float = 0.66f,    // лимит для FS (доля шага)
    val seed: Long = 0xA1F00D01L        // детерминизм
) {
    enum class Mode { ORDERED, FS }
}
