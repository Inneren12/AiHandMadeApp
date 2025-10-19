package com.appforcross.editor.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.appforcross.editor.R
import com.appforcross.editor.logging.Logger
import com.appforcross.editor.scene.SceneAnalyzer
import com.appforcross.editor.scene.SceneKind

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val detected = findViewById<TextView>(R.id.detectedText)
        val overrideGroup = findViewById<RadioGroup>(R.id.overrideGroup)
        val rbPhoto = findViewById<RadioButton>(R.id.rbPhoto)
        val btnAnalyzeGradient = findViewById<Button>(R.id.btnAnalyzeGradient)
        val btnAnalyzeChecker = findViewById<Button>(R.id.btnAnalyzeChecker)
        val btnProcessAsPhoto = findViewById<Button>(R.id.btnProcessAsPhoto)

        rbPhoto.isChecked = true

        fun render(kind: SceneKind, confidence: Float, via: String) {
            detected.text = "Detected: ${kind.name}  (confidence ${"%.2f".format(confidence)})"
            btnProcessAsPhoto.visibility = if (kind == SceneKind.DISCRETE) View.VISIBLE else View.GONE
            Logger.i(
                "UI",
                "analyze",
                mapOf(
                    "via" to via,
                    "kind" to kind.name,
                    "confidence" to confidence
                )
            )
        }

        fun manualOverride(): SceneKind? = when (overrideGroup.checkedRadioButtonId) {
            R.id.rbPhoto -> SceneKind.PHOTO
            R.id.rbDiscrete -> SceneKind.DISCRETE
            else -> null
        }

        btnAnalyzeGradient.setOnClickListener {
            val (rgb, w, h) = demoGradient(128, 64)
            val decision = SceneAnalyzer.analyzePreview(rgb, w, h)
            val forced = manualOverride()
            render(forced ?: decision.kind, if (forced == null) decision.confidence else 1f, "gradient")
        }

        btnAnalyzeChecker.setOnClickListener {
            val (rgb, w, h) = demoChecker(96, 96)
            val decision = SceneAnalyzer.analyzePreview(rgb, w, h)
            val forced = manualOverride()
            render(forced ?: decision.kind, if (forced == null) decision.confidence else 1f, "checker")
        }

        btnProcessAsPhoto.setOnClickListener {
            Logger.i("UI", "override", mapOf("to" to SceneKind.PHOTO.name))
            overrideGroup.check(R.id.rbPhoto)
            render(SceneKind.PHOTO, 1f, "override")
        }

        overrideGroup.setOnCheckedChangeListener { _, checkedId ->
            val kind = when (checkedId) {
                R.id.rbPhoto -> SceneKind.PHOTO
                R.id.rbDiscrete -> SceneKind.DISCRETE
                else -> return@setOnCheckedChangeListener
            }
            Logger.i(
                "UI",
                "override",
                mapOf(
                    "checked" to kind.name
                )
            )
        }
    }

    private fun demoGradient(w: Int, h: Int): Triple<FloatArray, Int, Int> {
        val rgb = FloatArray(w * h * 3)
        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = x.toFloat() / (w - 1).toFloat()
                rgb[p++] = v
                rgb[p++] = v
                rgb[p++] = v
            }
        }
        return Triple(rgb, w, h)
    }

    private fun demoChecker(w: Int, h: Int): Triple<FloatArray, Int, Int> {
        val rgb = FloatArray(w * h * 3)
        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = if (((x + y) and 1) == 0) 1f else 0f
                rgb[p++] = v
                rgb[p++] = v
                rgb[p++] = v
            }
        }
        return Triple(rgb, w, h)
    }
}
