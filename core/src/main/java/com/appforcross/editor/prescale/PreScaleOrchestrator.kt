package com.appforcross.editor.prescale

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.preset.PresetGateFOTO
import com.appforcross.editor.scene.SceneAnalyzer
import com.appforcross.editor.scene.SceneParams
import kotlin.math.max
import kotlin.math.roundToInt

data class PreScaleReport(
    val presetId: String,
    val presetConfidence: Float,
    val wst: Int,
    val hst: Int,
    val sigma: Float,
    val filter: String,
    val phaseDx: Int,
    val phaseDy: Int,
    val ssimProxy: Float,
    val edgeKeep: Float,
    val bandIdx: Float,
    val deltaE95: Float,
    val frPass: Boolean
)

object PreScaleOrchestrator {
    private const val TAG = "PRE"

    /**
     * Facade for PHOTO preview pipeline: analyze → preset gate → build → run → verify.
     * Input is RGB linear [0,1] buffer (width×height×3).
     */
    fun run(
        rgb: FloatArray,
        width: Int,
        height: Int,
        forceLargerWst: Boolean = false
    ): PreScaleReport {
        require(rgb.size == width * height * 3) { "rgb buffer mismatch" }
        Logger.i(
            TAG,
            "params",
            mapOf(
                "width" to width,
                "height" to height,
                "forceLargerWst" to forceLargerWst
            )
        )

        val scene = SceneAnalyzer.computeFeatures(rgb, width, height, SceneParams())
        val gate = PresetGateFOTO.decide(scene)
        Logger.i(
            TAG,
            "preset",
            mapOf(
                "preset.id" to gate.preset.id,
                "preset.filter" to gate.preset.filter,
                "preset.confidence" to gate.confidence,
                "forceLargerWst" to forceLargerWst
            )
        )

        val luminance = ImageOps.luminance(rgb, width, height)
        val baseParams = BuildParams(
            fabric = FabricSpec(ct = 14, corridorMin = 160, corridorMax = 260),
            kSigma = gate.preset.kSigma,
            filterPolicy = gate.preset.filter
        )
        val baseBuild = BuildSpec.decide(width, height, luminance, baseParams)

        val build = if (forceLargerWst) {
            val forcedWst = computeLargerWst(baseBuild.wst)
            if (forcedWst != baseBuild.wst) {
                val forcedParams = baseParams.copy(
                    fabric = FabricSpec(
                        ct = baseParams.fabric.ct,
                        corridorMin = forcedWst,
                        corridorMax = forcedWst
                    )
                )
                BuildSpec.decide(width, height, luminance, forcedParams)
            } else {
                baseBuild
            }
        } else {
            baseBuild
        }

        val srcF16 = ImageOps.packToF16(rgb, width, height)
        val run = RunFull.run(srcF16, build)
        val frPass = verifyPass(run.verify)

        Logger.i(
            TAG,
            "done",
            mapOf(
                "preset.id" to gate.preset.id,
                "build.wst" to build.wst,
                "build.hst" to build.hst,
                "build.sigma" to "%.3f".format(build.sigma),
                "verify.ssim" to "%.4f".format(run.verify.ssimProxy),
                "verify.edge" to "%.4f".format(run.verify.edgeKeep),
                "verify.band" to "%.4f".format(run.verify.bandIdx),
                "verify.dE95" to "%.2f".format(run.verify.deltaE95Proxy),
                "verify.pass" to frPass
            )
        )

        return PreScaleReport(
            presetId = gate.preset.id,
            presetConfidence = gate.confidence,
            wst = build.wst,
            hst = build.hst,
            sigma = build.sigma,
            filter = build.filter,
            phaseDx = build.phase.dx,
            phaseDy = build.phase.dy,
            ssimProxy = run.verify.ssimProxy,
            edgeKeep = run.verify.edgeKeep,
            bandIdx = run.verify.bandIdx,
            deltaE95 = run.verify.deltaE95Proxy,
            frPass = frPass
        )
    }

    private fun computeLargerWst(current: Int): Int {
        val scaled = (current * 1.12f).roundToInt()
        val minStep = current + 16
        return max(current, max(scaled, minStep))
    }

    private fun verifyPass(report: VerifyReport): Boolean {
        return report.ssimProxy >= 0.985f &&
            report.edgeKeep >= 0.98f &&
            report.bandIdx <= 0.003f &&
            report.deltaE95Proxy <= 3.0f
    }
}
