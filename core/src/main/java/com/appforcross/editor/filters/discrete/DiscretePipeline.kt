package com.appforcross.editor.filters.discrete

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.U8Mask

/**
 * Sequential discrete document pipeline composed of moiré suppression,
 * Sauvola/Wolf binarization and role-spread morphology.
 */
class DiscretePipeline(private val config: DiscreteConfig = DiscreteConfig()) {

    data class Output(val image: LinearImageF16, val mask: U8Mask, val roiAccepted: Boolean)

    private val binarizer = SauvolaWolfBinarizer(config.binarization)
    private val moire = MoireSuppressor(config.moire)
    private val morphology = RoleSpreadMorphology(config.morphology)

    private var floatScratch = FloatArray(0)
    private var floatScratch2 = FloatArray(0)
    private var floatScratch3 = FloatArray(0)
    private var byteScratch = ByteArray(0)
    private var byteScratch2 = ByteArray(0)

    fun run(input: LinearImageF16): Output {
        val start = System.nanoTime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "DiscretePipeline",
                "disc.enabled" to config.enabled,
                "disc.bin.enabled" to config.binarization.enabled,
                "disc.bin.algorithm" to config.binarization.algorithm.name.lowercase(),
                "disc.bin.window" to config.binarization.wBin,
                "disc.bin.k" to "%.3f".format(config.binarization.k),
                "disc.bin.smooth" to config.binarization.smoothing.name.lowercase(),
                "disc.moire.enabled" to config.moire.enabled,
                "disc.moire.mode" to config.moire.mode.name.lowercase(),
                "disc.moire.maxLag" to config.moire.maxLag,
                "disc.moire.threshold" to "%.3f".format(config.moire.detectionThreshold),
                "disc.moire.notch.width" to config.moire.notchWidth,
                "disc.moire.downscale.factor" to "%.2f".format(config.moire.downscaleFactor),
                "disc.moire.median" to config.moire.medianSize,
                "disc.morph.enabled" to config.morphology.enabled,
                "disc.morph.closing" to config.morphology.closing,
                "disc.morph.sel_kernel" to config.morphology.selectiveKernel.name.lowercase(),
                "disc.morph.majority" to config.morphology.majority,
                "disc.connectivity" to 4,
                "disc.deterministic" to true,
            ),
        )
        if (!config.enabled) {
            val emptyMask = ByteArray(input.width * input.height)
            val mask = U8Mask(input.width, input.height, emptyMask)
            Logger.i(TAG, "done", mapOf("fn" to "DiscretePipeline", "ms" to 0, "memMB" to 0))
            return Output(input, mask, false)
        }

        ensureCapacity(input.width, input.height)

        val moireImage = moire.apply(input, floatScratch, floatScratch2, floatScratch3)
        val mask = binarizer.apply(moireImage, floatScratch, byteScratch)
        val morph = morphology.apply(mask, byteScratch, byteScratch2)

        Logger.i(
            TAG,
            "done",
            mapOf(
                "fn" to "DiscretePipeline",
                "roi" to morph.roiAccepted,
                "ms" to elapsedMs(start),
                "memMB" to ((floatScratch.size + floatScratch2.size + floatScratch3.size) * 4 + (byteScratch.size + byteScratch2.size)) / 1_048_576,
            ),
        )
        return Output(moireImage, morph.mask, morph.roiAccepted)
    }

    private fun ensureCapacity(width: Int, height: Int) {
        val pixels = width * height
        if (floatScratch.size < pixels) floatScratch = FloatArray(pixels)
        if (floatScratch2.size < pixels) floatScratch2 = FloatArray(pixels)
        if (floatScratch3.size < pixels) floatScratch3 = FloatArray(pixels)
        if (byteScratch.size < pixels) byteScratch = ByteArray(pixels)
        if (byteScratch2.size < pixels) byteScratch2 = ByteArray(pixels)
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    companion object {
        private const val TAG = "FILTERS"
    }
}
