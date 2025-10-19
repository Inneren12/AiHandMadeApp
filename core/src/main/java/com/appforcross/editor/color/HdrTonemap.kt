package com.appforcross.editor.color

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.HdrMode
import com.appforcross.editor.types.LinearImageF16

object HdrTonemap {
    fun apply(img: LinearImageF16, mode: HdrMode, gainMap: Any? = null): LinearImageF16 {
        Logger.i("HDR", "params", mapOf("mode" to mode.name, "gainmap.present" to (gainMap != null)))
        Logger.i("HDR", "done", mapOf("ms" to 0, "memMB" to 0))
        return img
    }
}
