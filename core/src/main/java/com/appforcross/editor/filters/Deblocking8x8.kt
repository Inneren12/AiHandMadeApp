package com.appforcross.editor.filters

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16

object Deblocking8x8 {
    fun apply(img: LinearImageF16, strength: Float = 0.35f): LinearImageF16 {
        Logger.i("FILTERS", "params", mapOf("fn" to "Deblocking8x8", "deblock.strength" to strength, "jpeg.blockiness" to 0.0))
        Logger.i("FILTERS", "done", mapOf("ms" to 0, "memMB" to 0))
        return img
    }
}
