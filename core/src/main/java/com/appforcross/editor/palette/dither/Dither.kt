package com.appforcross.editor.palette.dither

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.palette.PaletteMath
import com.appforcross.editor.palette.Roi
import com.appforcross.editor.palette.RoiMap
import com.appforcross.editor.palette.Scores
import com.appforcross.editor.palette.Topology
import com.appforcross.editor.palette.TopologyMetrics
import com.appforcross.editor.palette.TopologyParams
import com.appforcross.editor.prescale.Verify
import com.appforcross.editor.prescale.VerifyReport
import kotlin.math.abs
import kotlin.math.max

/** Result bundle returned by [Dither.apply]. */
data class DitherResult(
    val index: IntArray,
    val topology: TopologyMetrics,
    val verify: VerifyReport
)

internal data class DitherContext(
    val luma: FloatArray,
    val amplitude: FloatArray,
    val roi: RoiMap
)

/** Palette dithering orchestrator with Ordered/FS modes and diagnostics. */
object Dither {

    fun apply(
        rgb: FloatArray,
        assign: IntArray,
        paletteRgb: FloatArray,
        W: Int,
        H: Int,
        params: DitherParams = DitherParams(),
        topologyParams: TopologyParams = TopologyParams()
    ): DitherResult {
        require(rgb.size == W * H * 3) { "RGB buffer size mismatch" }
        require(assign.size == W * H) { "Assignment size mismatch" }
        require(paletteRgb.size % 3 == 0) { "Palette RGB array must be multiple of 3" }

        val startNs = System.nanoTime()
        val lab = FloatArray(rgb.size)
        var p = 0
        for (i in 0 until W * H) {
            val labPix = PaletteMath.rgbToOklab(rgb[p], rgb[p + 1], rgb[p + 2])
            lab[p] = labPix[0]
            lab[p + 1] = labPix[1]
            lab[p + 2] = labPix[2]
            p += 3
        }
        val roi = Roi.computeProxy(lab, W, H)
        val luma = computeLuma(rgb, W, H)
        val amplitude = computeAmplitude(roi, luma, params)
        val context = DitherContext(luma, amplitude, roi)

        Logger.i(
            "DITHER",
            "params",
            mapOf(
                "dither.mode" to params.mode.name,
                "dither.amp.sky" to params.ampSky,
                "dither.amp.skin" to params.ampSkin,
                "dither.amp.hitex" to params.ampHiTex,
                "dither.amp.flat" to params.ampFlat,
                "dither.amp.edge_falloff" to params.ampEdgeFalloff,
                "dither.diffusion.cap" to params.diffusionCap,
                "edge_aware.mode" to when (params.mode) {
                    DitherParams.Mode.ORDERED -> "ordered_mask_amp"
                    DitherParams.Mode.FS -> "sobel_cosine"
                },
                "blue_noise.seed" to "0x${params.seed.toString(16)}",
                "image.width" to W,
                "image.height" to H,
                "palette.size" to (paletteRgb.size / 3)
            )
        )

        val dithered = when (params.mode) {
            DitherParams.Mode.ORDERED -> OrderedDither.apply(rgb, assign, paletteRgb, W, H, params, context)
            DitherParams.Mode.FS -> FSDither.apply(rgb, assign, paletteRgb, W, H, params, context)
        }

        val (cleanIndex, topoMetrics) = Topology.clean(dithered, W, H, topologyParams)
        val outRgb = synthesizeRgb(cleanIndex, paletteRgb)
        val maskSky = buildMask(roi.sky)
        val maskSkin = buildMask(roi.skin)
        val verify = Verify.computeDetailed(rgb, outRgb, W, H, maskSky, maskSkin)
        val gbiAll = Scores.gbiProxy(cleanIndex, W, H, roi)
        val gbiSky = gbiWithMask(cleanIndex, W, H, roi.edges, roi.sky)
        val avgEdge = roi.edges.sum() / max(1, roi.edges.size)

        Logger.i(
            "DITHER",
            "verify",
            mapOf(
                "gbi.proxy" to "%.4f".format(gbiAll),
                "gbi.sky" to "%.4f".format(gbiSky),
                "edge.proxy" to "%.4f".format(avgEdge),
                "ssimProxy" to "%.4f".format(verify.ssimProxy),
                "edgeKeep" to "%.4f".format(verify.edgeKeep),
                "bandAll" to "%.4f".format(verify.bandIdx),
                "bandSky" to "%.4f".format(verify.bandSky),
                "bandSkin" to "%.4f".format(verify.bandSkin),
                "dE95Proxy" to "%.2f".format(verify.deltaE95Proxy),
                "dE95Skin" to "%.2f".format(verify.deltaE95Skin)
            )
        )

        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000).toInt()
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_000_000.0
        Logger.i(
            "DITHER",
            "done",
            mapOf(
                "ms" to elapsedMs,
                "memMB" to "%.2f".format(usedMb),
                "mode" to params.mode.name
            )
        )

        return DitherResult(cleanIndex, topoMetrics, verify)
    }

    private fun computeLuma(rgb: FloatArray, W: Int, H: Int): FloatArray {
        val out = FloatArray(W * H)
        var p = 0
        for (i in 0 until W * H) {
            val r = rgb[p]
            val g = rgb[p + 1]
            val b = rgb[p + 2]
            out[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            p += 3
        }
        return out
    }

    private fun computeAmplitude(roi: RoiMap, luma: FloatArray, params: DitherParams): FloatArray {
        val amp = FloatArray(roi.size)
        for (i in 0 until roi.size) {
            val sky = roi.sky[i]
            val skin = roi.skin[i]
            val hiTex = roi.hitex[i]
            val flat = roi.flat[i]
            val edge = roi.edges[i]
            val luminance = luma[i]
            val midtone = 1f - abs(2f * luminance - 1f)
            val varianceBoost = 0.5f + 0.5f * hiTex
            val base = params.ampSky * sky +
                params.ampSkin * skin +
                params.ampHiTex * hiTex +
                params.ampFlat * (1f - flat)
            val falloff = (1f - params.ampEdgeFalloff * edge).coerceIn(0.25f, 1f)
            val value = (base * varianceBoost * (0.35f + 0.65f * midtone)).coerceIn(0f, 1f)
            amp[i] = (value * falloff).coerceIn(0f, 1f)
        }
        return amp
    }

    private fun synthesizeRgb(index: IntArray, paletteRgb: FloatArray): FloatArray {
        val out = FloatArray(index.size * 3)
        var p = 0
        for (i in index.indices) {
            val k = index[i] * 3
            out[p] = paletteRgb[k]
            out[p + 1] = paletteRgb[k + 1]
            out[p + 2] = paletteRgb[k + 2]
            p += 3
        }
        return out
    }

    private fun buildMask(source: FloatArray): BooleanArray? {
        var positives = 0
        val mask = BooleanArray(source.size) { idx ->
            val flag = source[idx] > 0.55f
            if (flag) positives++
            flag
        }
        return if (positives == 0) null else mask
    }

    private fun gbiWithMask(
        index: IntArray,
        W: Int,
        H: Int,
        edges: FloatArray,
        mask: FloatArray
    ): Float {
        var transitions = 0.0
        var total = 0.0
        for (y in 0 until H) {
            for (x in 0 until W) {
                val idx = y * W + x
                val weight = edges[idx] * mask[idx]
                if (weight <= 1e-6f) continue
                if (x + 1 < W) {
                    val neighborIdx = idx + 1
                    val transition = if (index[idx] != index[neighborIdx]) 1.0 else 0.0
                    transitions += transition * weight
                    total += weight
                }
                if (y + 1 < H) {
                    val neighborIdx = idx + W
                    val transition = if (index[idx] != index[neighborIdx]) 1.0 else 0.0
                    transitions += transition * weight
                    total += weight
                }
            }
        }
        if (total <= 1e-6) return 0f
        return (transitions / total).toFloat().coerceIn(0f, 1f)
    }
}
