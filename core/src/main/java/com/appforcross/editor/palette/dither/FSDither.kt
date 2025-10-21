package com.appforcross.editor.palette.dither

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Floyd–Steinberg style diffusion with orientation-aware cosine weighting. */
internal object FSDither {

    private data class Neighbor(val dx: Int, val dy: Int, val weight: Float)

    private val FORWARD = arrayOf(
        Neighbor(+1, 0, 7f / 16f),
        Neighbor(-1, +1, 3f / 16f),
        Neighbor(0, +1, 5f / 16f),
        Neighbor(+1, +1, 1f / 16f)
    )

    private val BACKWARD = arrayOf(
        Neighbor(-1, 0, 7f / 16f),
        Neighbor(+1, +1, 3f / 16f),
        Neighbor(0, +1, 5f / 16f),
        Neighbor(-1, +1, 1f / 16f)
    )

    fun apply(
        rgb: FloatArray,
        assign: IntArray,
        paletteRgb: FloatArray,
        W: Int,
        H: Int,
        params: DitherParams,
        context: DitherContext
    ): IntArray {
        require(params.mode == DitherParams.Mode.FS) {
            "FSDither supports only FS mode (got ${params.mode})."
        }
        val out = assign.clone()
        val errR = FloatArray(W * H)
        val errG = FloatArray(W * H)
        val errB = FloatArray(W * H)
        val gradX = FloatArray(W * H)
        val gradY = FloatArray(W * H)
        computeGradients(context.luma, W, H, gradX, gradY)

        val cap = params.diffusionCap.coerceIn(0f, 0.66f)
        for (y in 0 until H) {
            val forward = (y and 1) == 0
            var x = if (forward) 0 else W - 1
            while (x in 0 until W) {
                val idx = y * W + x
                val rgbIdx = idx * 3
                var r = rgb[rgbIdx] + errR[idx]
                var g = rgb[rgbIdx + 1] + errG[idx]
                var b = rgb[rgbIdx + 2] + errB[idx]
                r = r.coerceIn(0f, 1f)
                g = g.coerceIn(0f, 1f)
                b = b.coerceIn(0f, 1f)

                val best = nearest(r, g, b, paletteRgb, assign[idx])
                out[idx] = best
                val pk = best * 3
                var errValR = (r - paletteRgb[pk]) * context.amplitude[idx]
                var errValG = (g - paletteRgb[pk + 1]) * context.amplitude[idx]
                var errValB = (b - paletteRgb[pk + 2]) * context.amplitude[idx]

                val maxComp = max(max(abs(errValR), abs(errValG)), abs(errValB))
                if (maxComp > cap && maxComp > 1e-6f) {
                    val scale = cap / maxComp
                    errValR *= scale
                    errValG *= scale
                    errValB *= scale
                }

                errR[idx] = 0f
                errG[idx] = 0f
                errB[idx] = 0f

                val neighbors = if (forward) FORWARD else BACKWARD
                val weights = FloatArray(neighbors.size)
                var weightSum = 0f
                val tx = -gradY[idx]
                val ty = gradX[idx]
                val tangentNorm = sqrt(tx * tx + ty * ty)
                for (i in neighbors.indices) {
                    val n = neighbors[i]
                    val nx = x + n.dx
                    val ny = y + n.dy
                    if (nx !in 0 until W || ny !in 0 until H) {
                        weights[i] = 0f
                        continue
                    }
                    var w = n.weight
                    if (tangentNorm > 1e-6f) {
                        val dirX = n.dx.toFloat()
                        val dirY = n.dy.toFloat()
                        val dirNorm = sqrt(dirX * dirX + dirY * dirY)
                        if (dirNorm > 0f) {
                            val cos = ((tx * dirX) + (ty * dirY)) / (tangentNorm * dirNorm)
                            w *= (0.5f + 0.5f * abs(cos))
                        }
                    }
                    val neighborIdx = ny * W + nx
                    val edgeFalloff = (1f - params.ampEdgeFalloff * context.roi.edges[neighborIdx])
                        .coerceIn(0f, 1f)
                    w *= (0.5f + 0.5f * edgeFalloff)
                    weights[i] = w
                    weightSum += w
                }
                if (weightSum <= 1e-6f) {
                    x += if (forward) 1 else -1
                    continue
                }
                for (i in neighbors.indices) {
                    val w = weights[i]
                    if (w <= 0f) continue
                    val n = neighbors[i]
                    val nx = x + n.dx
                    val ny = y + n.dy
                    if (nx !in 0 until W || ny !in 0 until H) continue
                    val neighborIdx = ny * W + nx
                    val factor = w / weightSum
                    errR[neighborIdx] += errValR * factor
                    errG[neighborIdx] += errValG * factor
                    errB[neighborIdx] += errValB * factor
                }
                x += if (forward) 1 else -1
            }
        }
        return out
    }

    private fun nearest(r: Float, g: Float, b: Float, palette: FloatArray, prefer: Int): Int {
        var best = prefer
        var bestErr = error(r, g, b, palette, prefer)
        val colors = palette.size / 3
        for (k in 0 until colors) {
            val err = error(r, g, b, palette, k)
            if (err < bestErr - 1e-6f) {
                bestErr = err
                best = k
            } else if (abs(err - bestErr) <= 1e-6f) {
                if (k == prefer) {
                    best = prefer
                } else if (best != prefer && k < best) {
                    best = k
                }
            }
        }
        return best
    }

    private fun error(r: Float, g: Float, b: Float, palette: FloatArray, idx: Int): Float {
        val pk = idx * 3
        val dr = r - palette[pk]
        val dg = g - palette[pk + 1]
        val db = b - palette[pk + 2]
        return abs(dr) + abs(dg) + abs(db)
    }

    private fun computeGradients(
        luma: FloatArray,
        W: Int,
        H: Int,
        gradX: FloatArray,
        gradY: FloatArray
    ) {
        fun at(x: Int, y: Int) = luma[y * W + x]
        for (y in 1 until H - 1) {
            for (x in 1 until W - 1) {
                val gx = (-at(x - 1, y - 1) + at(x + 1, y - 1)) +
                    (-2f * at(x - 1, y) + 2f * at(x + 1, y)) +
                    (-at(x - 1, y + 1) + at(x + 1, y + 1))
                val gy = (-(at(x - 1, y - 1) + 2f * at(x, y - 1) + at(x + 1, y - 1))) +
                    (at(x - 1, y + 1) + 2f * at(x, y + 1) + at(x + 1, y + 1))
                val idx = y * W + x
                gradX[idx] = gx / 4f
                gradY[idx] = gy / 4f
            }
        }
    }
}
