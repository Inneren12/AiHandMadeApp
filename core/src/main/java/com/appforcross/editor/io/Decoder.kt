package com.appforcross.editor.io

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.ColorMeta
import com.appforcross.editor.types.HdrMode
import com.appforcross.editor.types.ImageSource
import com.appforcross.editor.types.LinearImageF16

object Decoder {
    /** Заглушка: декодирует в линейный sRGB F16 1×1 чёрный + метаданные. */
    fun decode(source: ImageSource, hdrMode: HdrMode = HdrMode.HDR_OFF): Pair<LinearImageF16, ColorMeta> {
        Logger.i("IO", "params", mapOf(
            "src.kind" to source::class.simpleName,
            "hdr.mode" to hdrMode.name,
            "colorspace.out" to "SRGB_LINEAR",
            "icc.space" to "UNKNOWN",
            "icc.confidence" to 0.0
        ))
        val img = LinearImageF16(1, 1, 3, ShortArray(3) { 0 })
        val meta = ColorMeta("UNKNOWN", 0f, hdrMode)
        Logger.i("IO", "done", mapOf("ms" to 0, "memMB" to 0))
        return img to meta
    }
}
