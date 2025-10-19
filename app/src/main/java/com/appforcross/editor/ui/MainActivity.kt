package com.appforcross.editor.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.appforcross.editor.R
import com.appforcross.editor.logging.Logger
import com.appforcross.editor.scene.SceneAnalyzer
import com.appforcross.editor.scene.SceneDecision
import com.appforcross.editor.scene.SceneKind
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var detectedText: TextView
    private lateinit var overrideGroup: RadioGroup
    private lateinit var analyzeButton: Button

    private var suppressOverrideLog = false
    private var lastDecision: SceneDecision? = null
    private var detectedKind: SceneKind = SceneKind.PHOTO
    private var overrideKind: SceneKind = SceneKind.PHOTO
    private var demoToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        detectedText = findViewById(R.id.detectedText)
        overrideGroup = findViewById(R.id.overrideGroup)
        analyzeButton = findViewById(R.id.btnAnalyzeDemo)

        suppressOverrideLog = true
        overrideGroup.check(R.id.radioPhoto)
        suppressOverrideLog = false
        updateDetectedText(detectedKind, 0f, overrideKind, source = "init")

        overrideGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressOverrideLog) return@setOnCheckedChangeListener
            val kind = when (checkedId) {
                R.id.radioPhoto -> SceneKind.PHOTO
                R.id.radioDiscrete -> SceneKind.DISCRETE
                else -> return@setOnCheckedChangeListener
            }
            overrideKind = kind
            val confidence = lastDecision?.confidence ?: 0f
            Logger.i(
                "UI",
                "params",
                mapOf(
                    "action" to "override",
                    "kind" to overrideKind.name,
                    "confidence" to confidence
                )
            )
            updateDetectedText(detectedKind, confidence, overrideKind, source = "override")
        }

        analyzeButton.setOnClickListener {
            runDemoAnalyze()
        }
    }

    private fun runDemoAnalyze() {
        val pattern = if (demoToggle) "checker" else "gradient"
        demoToggle = !demoToggle
        val width = if (pattern == "checker") 64 else 96
        val height = 64
        val buffer = if (pattern == "checker") generateChecker(width, height) else generateGradient(width, height)
        Logger.i(
            "UI",
            "params",
            mapOf(
                "action" to "analyze_demo",
                "pattern" to pattern,
                "width" to width,
                "height" to height
            )
        )
        val decision = SceneAnalyzer.analyzePreview(buffer, width, height)
        lastDecision = decision
        detectedKind = decision.kind
        val currentConfidence = decision.confidence
        val selected = when (overrideGroup.checkedRadioButtonId) {
            R.id.radioPhoto -> SceneKind.PHOTO
            R.id.radioDiscrete -> SceneKind.DISCRETE
            else -> detectedKind
        }
        overrideKind = selected
        updateDetectedText(detectedKind, currentConfidence, overrideKind, source = "analyze")
        Logger.i(
            "UI",
            "done",
            mapOf(
                "action" to "analyze_demo",
                "pattern" to pattern,
                "detected" to detectedKind.name,
                "confidence" to currentConfidence,
                "override" to overrideKind.name
            )
        )
    }

    private fun updateDetectedText(
        detected: SceneKind,
        confidence: Float,
        overrideKind: SceneKind,
        source: String
    ) {
        val confidenceText = String.format(Locale.US, "%.2f", confidence)
        val overrideText = if (overrideKind == SceneKind.PHOTO) {
            "Process as PHOTO"
        } else {
            "Process as DISCRETE"
        }
        val summary = buildString {
            append("Detected: ")
            append(detected.name)
            append(" (confidence ")
            append(confidenceText)
            append(")")
            append('\n')
            append("Override: ")
            append(overrideText)
            append(" [source=")
            append(source)
            append(']')
        }
        detectedText.text = summary
        val targetId = if (overrideKind == SceneKind.PHOTO) R.id.radioPhoto else R.id.radioDiscrete
        if (overrideGroup.checkedRadioButtonId != targetId) {
            suppressOverrideLog = true
            overrideGroup.check(targetId)
            suppressOverrideLog = false
        }
    }

    private fun generateGradient(width: Int, height: Int): FloatArray {
        val buffer = FloatArray(width * height * 3)
        var idx = 0
        for (y in 0 until height) {
            val t = y.toFloat() / (height - 1).coerceAtLeast(1)
            for (x in 0 until width) {
                val value = t
                buffer[idx++] = value
                buffer[idx++] = value
                buffer[idx++] = value
            }
        }
        return buffer
    }

    private fun generateChecker(width: Int, height: Int): FloatArray {
        val buffer = FloatArray(width * height * 3)
        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if (((x / 2) + (y / 2)) % 2 == 0) 0f else 1f
                buffer[idx++] = value
                buffer[idx++] = value
                buffer[idx++] = value
            }
        }
        return buffer
    }
}
