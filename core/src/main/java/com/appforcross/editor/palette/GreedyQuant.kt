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
            val lab = PaletteMath.rgbToOklab(r, g, b)
            labPixels[i * 3 + 0] = lab[0]
            labPixels[i * 3 + 1] = lab[1]
            labPixels[i * 3 + 2] = lab[2]
        }

        val roiMap = Roi.computeProxy(labPixels, W, H)
        var palette = Seeds.initialOKLab(labPixels, params.kStart)

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPalette = palette.copyOf()
        var bestAssign = IntArray(W * H) { 0 }
        var bestMetrics = Scores.DeltaEMetrics(0f, 0f, 0f, 0f)
        var bestGbi = 0f

        Kneedle.reset()

        while (palette.size / 3 <= params.kMax) {
            val assign = Assign.assignOKLab(labPixels, palette)
            val score = Scores.photoScoreStar(labPixels, palette, assign, roiMap)
            val deltaMetrics = Scores.deltaEMetrics(labPixels, palette, assign)
            val gbi = Scores.gbiProxy(assign, W, H, roiMap)

            Logger.i(
                "PALETTE",
                "iter",
                mapOf(
                    "K" to palette.size / 3,
                    "score" to "%.5f".format(score),
                    "de.avg" to "%.3f".format(deltaMetrics.avg),
                    "de.max" to "%.3f".format(deltaMetrics.max),
                    "de.min" to "%.3f".format(deltaMetrics.min),
                    "de.p95" to "%.3f".format(deltaMetrics.p95),
                    "gbi" to "%.4f".format(gbi)
                )
            )

            if (score > bestScore + 1e-6f) {
                bestScore = score
                bestPalette = palette.copyOf()
                bestAssign = assign.copyOf()
                bestMetrics = deltaMetrics
                bestGbi = gbi
            }

            if (!Kneedle.shouldGrow(score, palette.size / 3, params.kneedleTau)) {
                break
            }
            if (palette.size / 3 >= params.kMax) {
                break
            }

            val splitCandidate = Split.pickNext(labPixels, palette, assign, roiMap, params.roiWeights)
            palette = Refine.addAndRefine(palette, splitCandidate, labPixels, assign)
            palette = Spread.enforce(palette, params.dE00Min)
        }

        val finalMetrics = Scores.deltaEMetrics(labPixels, bestPalette, bestAssign)
        val photoScore = Scores.photoScoreStar(labPixels, bestPalette, bestAssign, roiMap)
        val gbiFinal = Scores.gbiProxy(bestAssign, W, H, roiMap)
        bestMetrics = finalMetrics
        bestGbi = gbiFinal

        Logger.i(
            "PALETTE",
            "done",
            mapOf(
                "K" to bestPalette.size / 3,
                "avgDE" to "%.3f".format(finalMetrics.avg),
                "maxDE" to "%.3f".format(finalMetrics.max)
            )
        )
        Logger.i(
            "PALETTE",
            "verify",
            mapOf(
                "de.min" to "%.3f".format(bestMetrics.min),
                "de.avg" to "%.3f".format(bestMetrics.avg),
                "de.max" to "%.3f".format(bestMetrics.max),
                "de.p95" to "%.3f".format(bestMetrics.p95),
                "gbi.proxy" to "%.4f".format(bestGbi),
                "photoScore" to "%.5f".format(photoScore)
            )
        )
        return QuantResult(
            bestPalette,
            bestAssign,
            QuantMetrics(
                bestPalette.size / 3,
                bestMetrics.avg,
                bestMetrics.max,
                bestMetrics.min,
                bestMetrics.p95,
                photoScore,
                bestGbi
            )
        )
    }
}
