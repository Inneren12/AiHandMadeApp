package com.appforcross.editor.palette

import com.appforcross.editor.logging.Logger

object GreedyQuant {

    /** Вход: линейный RGB [0..1], W×H; выход: палитра OKLab и присвоения. */
    fun run(rgb: FloatArray, W: Int, H: Int, params: QuantParams = QuantParams()): QuantResult {
        require(rgb.size == W * H * 3) { "RGB array size mismatch" }
        Logger.i(
            "PALETTE",
            "params",
            mapOf(
                "w" to W,
                "h" to H,
                "quant.k_start" to params.kStart,
                "quant.k_max" to params.kMax,
                "spread.de00_min" to params.dE00Min,
                "kneedle.tau" to params.kneedleTau,
                "roi.quota.edges" to params.roiWeights.edges,
                "roi.quota.skin" to params.roiWeights.skin,
                "roi.quota.sky" to params.roiWeights.sky,
                "roi.quota.hitex" to params.roiWeights.hitex,
                "roi.quota.flat" to params.roiWeights.flat
            )
        )

        val labPixels = FloatArray(W * H * 3)
        for (i in 0 until W * H) {
            val r = rgb[i * 3 + 0]
            val g = rgb[i * 3 + 1]
            val b = rgb[i * 3 + 2]
            val lab = rgbToOklab(r, g, b)
            labPixels[i * 3 + 0] = lab[0]
            labPixels[i * 3 + 1] = lab[1]
            labPixels[i * 3 + 2] = lab[2]
        }

        val roiMap = Roi.computeProxy(rgb, W, H)
        var palette = Seeds.initialOKLab(labPixels, params.kStart)

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPalette = palette.copyOf()
        var bestAssign = IntArray(W * H) { 0 }

        Kneedle.reset()

        while (palette.size / 3 <= params.kMax) {
            val assign = Assign.assignOKLab(labPixels, palette)
            val score = Scores.photoScoreStar(labPixels, palette, assign, roiMap)

            Logger.i(
                "PALETTE",
                "iter",
                mapOf(
                    "K" to palette.size / 3,
                    "score" to "%.5f".format(score)
                )
            )

            if (score > bestScore + 1e-6f) {
                bestScore = score
                bestPalette = palette.copyOf()
                bestAssign = assign.copyOf()
            }

            if (!Kneedle.shouldGrow(score, palette.size / 3, params.kneedleTau)) {
                break
            }
            if (palette.size / 3 >= params.kMax) {
                break
            }

            val splitLab = Split.pickNext(labPixels, palette, assign, roiMap, params.roiWeights)
            palette = Refine.addAndRefine(palette, splitLab, labPixels)
            palette = Spread.enforce(palette, params.dE00Min)
        }

        val (avg, maxDE) = Scores.deltaEStats(labPixels, bestPalette, bestAssign)
        Logger.i(
            "PALETTE",
            "done",
            mapOf(
                "K" to bestPalette.size / 3,
                "avgDE" to "%.3f".format(avg),
                "maxDE" to "%.3f".format(maxDE)
            )
        )
        return QuantResult(bestPalette, bestAssign, QuantMetrics(bestPalette.size / 3, avg, maxDE, bestScore))
    }

    private fun rgbToOklab(r: Float, g: Float, b: Float): FloatArray {
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

    private fun cbrt(value: Float): Float = Math.cbrt(value.toDouble()).toFloat()
}
