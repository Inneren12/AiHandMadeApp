package com.appforcross.editor.scene

import com.appforcross.editor.logging.Logger
import com.appforcross.editor.preset.PresetDecision
import com.appforcross.editor.preset.PresetGateFOTO

object ScenePresetHook {
    /** Удобная фасадная функция: features -> PresetDecision (FOTO). */
    fun decideForFoto(features: SceneFeatures): PresetDecision {
        Logger.i("HOOK", "params", mapOf("stage" to "scene->preset"))
        return PresetGateFOTO.decide(features)
    }
}
