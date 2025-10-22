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

    fun run(input: LinearImageF16, roi: Roi? = config.roi): Output {
        val start = System.nanoTime()
        Logger.i(
            TAG,
            "params",
            mapOf(
                "fn" to "DiscretePipeline",
                "disc.enabled" to config.enabled,
                "disc.bin.enabled" to config.binarization.enabled,
                "disc.moire.enabled" to config.moire.enabled,
                "disc.morph.enabled" to config.morphology.enabled,
            ),
        )
        if (!config.enabled) {
            val emptyMask = ByteArray(input.width * input.height)
            val mask = U8Mask(input.width, input.height, emptyMask)
            Logger.i(TAG, "done", mapOf("fn" to "DiscretePipeline", "ms" to 0, "memMB" to 0))
            return Output(input, mask, false)
        }

        roi?.requireWithin(input.width, input.height)

        ensureCapacity(input.width, input.height, input.planes)

        val moireImage = moire.apply(input, floatScratch, floatScratch2, roi)
        val mask = binarizer.apply(moireImage, floatScratch, floatScratch3, byteScratch, roi)
        val morph = morphology.apply(mask, byteScratch, byteScratch2, roi)

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

    private fun ensureCapacity(width: Int, height: Int, planes: Int) {
        val pixels = width * height
        if (floatScratch.size < pixels) floatScratch = FloatArray(pixels)
        if (floatScratch2.size < pixels) floatScratch2 = FloatArray(pixels)
        val radius = maxOf(1, config.binarization.wBin / 2)
        val padded = (width + radius * 2) * (height + radius * 2)
        if (floatScratch3.size < padded) floatScratch3 = FloatArray(padded)
        if (byteScratch.size < pixels) byteScratch = ByteArray(pixels)
        if (byteScratch2.size < pixels) byteScratch2 = ByteArray(pixels)
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    companion object {
        private const val TAG = "FILTERS"
    }
}
