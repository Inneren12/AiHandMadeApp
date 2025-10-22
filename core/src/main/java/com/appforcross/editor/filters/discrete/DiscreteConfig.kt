package com.appforcross.editor.filters.discrete

import com.appforcross.editor.filters.discrete.MoireSuppressor.Mode

/**
 * Configuration block for the discrete document filter pipeline.
 *
 * Defaults deliberately keep the pipeline disabled until the controller
 * enables it via the exposed `disc.enabled` flag.
 */
data class DiscreteConfig(
    /** Enables the complete discrete pipeline. */
    val enabled: Boolean = true,
    val binarization: BinarizationConfig = BinarizationConfig(),
    val moire: MoireConfig = MoireConfig(),
    val morphology: MorphologyConfig = MorphologyConfig(),
)

/** Parameters for Sauvola/Wolf adaptive thresholding. */
data class BinarizationConfig(
    val enabled: Boolean = true,
    /** Algorithm variant applied to local windows. */
    val algorithm: Algorithm = Algorithm.SAUVOLA,
    /** Window size (must be odd) used for mean/variance evaluation. */
    val wBin: Int = 21,
    /** Aggressiveness of the Wolf/Sauvola threshold adjustment. */
    val k: Float = 0.34f,
    /** Optional 3x3 post-smoothing applied to the binary mask. */
    val smoothing: Smoothing = Smoothing.BOX3,
)

enum class Algorithm { SAUVOLA, WOLF }

enum class Smoothing { NONE, BOX3, MEDIAN3 }

/** Configuration for the moiré suppression stage. */
data class MoireConfig(
    val enabled: Boolean = true,
    val mode: Mode = Mode.AUTO,
    /** Maximum lag (in pixels) inspected during autocorrelation. */
    val maxLag: Int = 24,
    /** Downscale factor used when the stage selects the downscale strategy. */
    val downscaleFactor: Float = 0.5f,
    /** Correlation threshold that indicates a comb frequency. */
    val detectionThreshold: Float = 0.45f,
    /** Width of the notch filter when selected. */
    val notchWidth: Int = 2,
    /** Median window size applied in the median strategy. */
    val medianSize: Int = 3,
)

/** Role-spread morphology toggles and heuristics. */
data class MorphologyConfig(
    val enabled: Boolean = true,
    val closing: Boolean = true,
    val selectiveKernel: SelectiveKernel = SelectiveKernel.ADAPTIVE,
    val majority: Boolean = true,
    /** Lower bound on foreground ratio required to run text heuristics. */
    val roiMinForeground: Float = 0.02f,
    /** Upper bound on foreground ratio accepted for text detection. */
    val roiMaxForeground: Float = 0.65f,
    /** Transition density threshold required to treat the ROI as text. */
    val roiTransitionDensity: Float = 0.12f,
)

enum class SelectiveKernel { OFF, HORIZONTAL, VERTICAL, ADAPTIVE }
