package com.appforcross.editor.prescale

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import kotlin.math.ceil
import kotlin.math.max

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
        val overlap = max(2, ceil(2f * radius).toInt())
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
                "nrY.radius" to nrYRadius,
                "nrC.radius" to nrCRadius,
                "anti_sand.enabled" to true,
                "skin.unify" to true,
                "sky.unify" to true,
                "halo.remove" to true,
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
                "TILE",
                "begin",
                mapOf("x" to tx, "y" to ty, "w" to tw, "h" to th)
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
            Logger.i("TILE", "done", mapOf("x" to tx, "y" to ty))
            tiles++
        }

        val merged = ImageOps.normalizeByWeight(outF, wsum, src.width, src.height)
        val outL = ImageOps.luminance(merged, src.width, src.height)
        val verify = Verify.compute(outL, src.width, src.height)
        val out = ImageOps.packToF16(merged, src.width, src.height)
        Logger.i("RUN", "done", mapOf("ms" to 0, "memMB" to 0, "tiles" to tiles))
        return RunResult(out, verify)
    }
}
