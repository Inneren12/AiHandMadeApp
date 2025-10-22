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
    val enabled: Boolean = false,
    val binarization: BinarizationConfig = BinarizationConfig(),
    val moire: MoireConfig = MoireConfig(),
    val morphology: MorphologyConfig = MorphologyConfig(),
    /** Optional ROI restricting processing to the specified rectangle. */
    val roi: Roi? = null,
)

/** Parameters for Sauvola/Wolf adaptive thresholding. */
data class BinarizationConfig(
    val enabled: Boolean = true,
    /** Window size (must be odd) used for mean/variance evaluation. */
    val wBin: Int = 21,
    /** Aggressiveness of the Wolf threshold adjustment. */
    val k: Float = 0.34f,
    /** Optional 3x3 smoothing applied to the binary mask. */
    val smoothing: Boolean = true,
)

/** Configuration for the moiré suppression stage. */
data class MoireConfig(
    val enabled: Boolean = true,
    val mode: Mode = Mode.AUTO,
    /** Maximum lag (in pixels) inspected during autocorrelation. */
    val maxLag: Int = 12,
    /** Downscale factor used when the stage selects the downscale strategy. */
    val downscaleFactor: Int = 2,
    /** Correlation threshold that indicates a comb frequency. */
    val detectionThreshold: Float = 0.45f,
)

/** Role-spread morphology toggles and heuristics. */
data class MorphologyConfig(
    val enabled: Boolean = true,
    val closing: Boolean = true,
    val selectiveDilation: Boolean = true,
    val majority: Boolean = true,
    /** Lower bound on foreground ratio required to run text heuristics. */
    val roiMinForeground: Float = 0.02f,
    /** Upper bound on foreground ratio accepted for text detection. */
    val roiMaxForeground: Float = 0.65f,
    /** Transition density threshold required to treat the ROI as text. */
    val roiTransitionDensity: Float = 0.12f,
)
