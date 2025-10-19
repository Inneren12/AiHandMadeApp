package com.appforcross.editor.filters

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.types.LinearImageF16
import com.appforcross.editor.types.U8Mask

object HaloRemoval {
    fun apply(img: LinearImageF16, edgeMask: U8Mask? = null, strength: Float = 0.8f): LinearImageF16 {
        Logger.i("FILTERS", "params", mapOf(
            "fn" to "HaloRemoval", "halo.strength" to strength, "edgeMask.present" to (edgeMask != null)
        ))
        Logger.i("FILTERS", "done", mapOf("ms" to 0, "memMB" to 0))
        return img
    }
}
