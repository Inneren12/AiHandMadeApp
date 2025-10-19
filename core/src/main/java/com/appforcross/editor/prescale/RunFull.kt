package com.appforcross.editor.prescale

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Параметры запуска RunFull. */
data class RunParams(
    val tileMax: Int = 1024,
    val overlapPolicy: String = "AUTO"
)

/** Результат выполнения RunFull. */
data class RunResult(
    val out: LinearImageF16,
    val verify: VerifyReport
)

/**
 * Полноценная (v1) обработка превью с тайлинговой обвязкой и Verify отчётом.
 */
object RunFull {

    fun run(
        src: LinearImageF16,
        build: BuildDecision,
        params: RunParams = RunParams()
    ): RunResult {
        val radius = max(0f, 1.5f * build.sigma)
        val baseOverlap = max(2, ceil(2f * radius).toInt())
        val overlap = when (params.overlapPolicy) {
            "EXACT" -> max(2, (2f * radius).roundToInt())
            else -> baseOverlap
        }
        val tileStep = (params.tileMax - overlap).coerceAtLeast(1)
        val nrYRadius = (0.25f * 2f).toInt().coerceAtLeast(1)
        val nrCRadius = (0.20f * 2f).toInt().coerceAtLeast(1)

        Logger.i(
            "RUN",
            "params",
            mapOf(
                "src.w" to src.width,
                "src.h" to src.height,
                "seed" to 0,
                "colorspace.in" to "SRGB_LINEAR",
                "colorspace.out" to "SRGB_LINEAR",
                "icc.space" to "SRGB",
                "icc.confidence" to 1.0f,
                "hdr.mode" to "HDR_OFF",
                "device.profile" to "GENERIC",
                "threads" to 1,
                "neon.enabled" to true,
                "gpu.enabled" to false,
                "tile.max" to params.tileMax,
                "tile.step" to tileStep,
                "tile.overlap" to overlap,
                "overlapPolicy" to params.overlapPolicy,
                "sigma" to build.sigma,
                "filter" to build.filter,
                "phase.dx" to build.phase.dx,
                "phase.dy" to build.phase.dy,
                "wst" to build.wst,
                "hst" to build.hst,
                "verify.thresholds.ssim" to 0.985f,
                "verify.thresholds.edge" to 0.98f,
                "verify.thresholds.band" to 0.003f,
                "verify.thresholds.dE95" to 3.0f
            )
        )

        Logger.i(
            "RUN",
            "stage",
            mapOf(
                "name" to "tile_pipeline",
                "order" to "wb->nrY->nrC->antiSand->unify->halo->anisoAA->ewa",
                "nrY.radius" to nrYRadius,
                "nrC.radius" to nrCRadius,
                "anti_sand.enabled" to true,
                "skin.unify" to true,
                "sky.unify" to true,
                "halo.remove" to true,
                "sharp.edge" to "ANISO_AA",
                "post.dering" to false,
                "post.clahe" to false
            )
        )

        val tiler = Tiler(src.width, src.height, params.tileMax, params.tileMax, overlap)
        val outF = FloatArray(src.width * src.height * 3)
        val wsum = FloatArray(src.width * src.height)
        var tiles = 0

        tiler.forEachTile { tx, ty, tw, th, _, _ ->
            Logger.i(
                "RUN",
                "tile",
                mapOf(
                    "state" to "begin",
                    "x" to tx,
                    "y" to ty,
                    "w" to tw,
                    "h" to th,
                    "overlap" to overlap
                )
            )
            val tileRGB = ImageOps.extractFloatRGB(src, tx, ty, tw, th)
            val wb = ImageOps.whiteBalanceNeutral(tileRGB, tw, th)
            val nry = ImageOps.nrLumaGuided(wb, tw, th, strength = 0.25f)
            val nrc = ImageOps.nrChromaBox(nry, tw, th, strength = 0.20f)
            val anti = ImageOps.antiSandMedian3(nrc, tw, th)
            val unify = ImageOps.unifySoft(anti, tw, th)
            val halo = ImageOps.haloSuppress(unify, tw, th, k = 0.15f)
            val aa = ImageOps.anisoAA(unify = halo, w = tw, h = th, sigma = build.sigma)
            val resamp = ImageOps.ewaResample(aa, tw, th, filter = build.filter, phase = build.phase)
            Feather.blend(outF, wsum, resamp, tx, ty, tw, th, src.width, src.height, overlap)
            Logger.i(
                "RUN",
                "tile",
                mapOf(
                    "state" to "done",
                    "x" to tx,
                    "y" to ty,
                    "w" to tw,
                    "h" to th
                )
            )
            tiles++
        }

        val merged = ImageOps.normalizeByWeight(outF, wsum, src.width, src.height)
        val outL = ImageOps.luminance(merged, src.width, src.height)
        val verify = Verify.compute(outL, src.width, src.height)
        Logger.i(
            "RUN",
            "verify",
            mapOf(
                "ssimProxy" to "%.4f".format(verify.ssimProxy),
                "edgeKeep" to "%.4f".format(verify.edgeKeep),
                "bandIdx" to "%.4f".format(verify.bandIdx),
                "dE95Proxy" to "%.2f".format(verify.deltaE95Proxy)
            )
        )
        val out = ImageOps.packToF16(merged, src.width, src.height)
        Logger.i("RUN", "done", mapOf("ms" to 0, "memMB" to 0, "tiles" to tiles))
        return RunResult(out, verify)
    }
}
