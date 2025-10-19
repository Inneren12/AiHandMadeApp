package com.appforcross.editor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.appforcross.editor.logging.Logger
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Background preview pipeline: scaling + optional uniform quantization. */
class PreviewController {
    interface Listener {
        fun onPreviewReady(bitmap: Bitmap, width: Int, height: Int)
        fun onSourceInfo(width: Int, height: Int)
    }

    private val ui = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val version = AtomicInteger(0)
    private var listener: Listener? = null
    private var source: Bitmap? = null
    private var scalePercent: Int = 100
    private var quantK: Int = 0

    fun setListener(l: Listener?) {
        listener = l
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        source?.takeIf { it != bitmap && !it.isRecycled }?.recycle()
        source = bitmap
        listener?.onSourceInfo(bitmap.width, bitmap.height)
        requestRender()
    }

    fun setSourceFromStream(stream: InputStream) {
        BitmapFactory.decodeStream(stream)?.let { setSourceBitmap(it) }
    }

    fun setScalePercent(percent: Int) {
        scalePercent = percent.coerceIn(10, 300)
        requestRender()
    }

    fun setQuantK(k: Int) {
        quantK = k.coerceIn(0, 64)
        requestRender()
    }

    /** Release controller resources. Call from the host Activity's onDestroy(). */
    fun dispose() {
        listener = null
        ui.removeCallbacksAndMessages(null)
        try {
            exec.shutdownNow()
        } catch (_: Throwable) {
        }
        source?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        source = null
        Logger.i("PREVIEW", "disposed", emptyMap())
    }

    private fun requestRender() {
        val ticket = version.incrementAndGet()
        val src = source ?: return
        val currentScale = scalePercent
        val currentK = quantK
        Logger.i(
            "PREVIEW",
            "params",
            mapOf(
                "src.w" to src.width,
                "src.h" to src.height,
                "scale.pct" to currentScale,
                "quant.K" to currentK
            )
        )
        val start = System.nanoTime()
        exec.execute {
            val targetW = max(1, src.width * currentScale / 100)
            val targetH = max(1, src.height * currentScale / 100)
            val scaled = if (targetW != src.width || targetH != src.height) {
                Bitmap.createScaledBitmap(src, targetW, targetH, true)
            } else {
                src.copy(Bitmap.Config.ARGB_8888, true)
            }

            val output = if (currentK > 0) {
                val quantized = quantizeUniformApproxK(scaled, currentK)
                if (quantized !== scaled && !scaled.isRecycled) {
                    scaled.recycle()
                }
                quantized
            } else {
                scaled
            }
            val durationMs = ((System.nanoTime() - start) / 1_000_000).toInt()
            if (ticket == version.get()) {
                Logger.i(
                    "PREVIEW",
                    "done",
                    mapOf(
                        "ms" to durationMs,
                        "out.w" to output.width,
                        "out.h" to output.height
                    )
                )
                ui.post { listener?.onPreviewReady(output, output.width, output.height) }
            } else if (output !== src && !output.isRecycled) {
                output.recycle()
            }
        }
    }

    private fun quantizeUniformApproxK(bitmap: Bitmap, targetK: Int): Bitmap {
        val levels = max(2, ceil(targetK.toDouble().pow(1.0 / 3.0)).toInt())
        val step = 255f / (levels - 1)
        val w = bitmap.width
        val h = bitmap.height
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val buf = IntArray(w * h)
        out.getPixels(buf, 0, w, 0, 0, w, h)
        var index = 0
        while (index < buf.size) {
            val color = buf[index]
            val a = (color ushr 24) and 0xFF
            val r = (color ushr 16) and 0xFF
            val g = (color ushr 8) and 0xFF
            val b = color and 0xFF
            val rq = quantChannel(r, step)
            val gq = quantChannel(g, step)
            val bq = quantChannel(b, step)
            buf[index] = (a shl 24) or (rq shl 16) or (gq shl 8) or bq
            index++
        }
        out.setPixels(buf, 0, w, 0, 0, w, h)
        return out
    }

    private fun quantChannel(value: Int, step: Float): Int {
        val q = (value / step).roundToInt()
        return min(255, max(0, (q * step).roundToInt()))
    }
}
