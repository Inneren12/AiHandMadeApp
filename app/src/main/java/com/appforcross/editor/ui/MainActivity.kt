package com.appforcross.editor.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.appforcross.editor.R
import com.appforcross.editor.logging.Logger
import com.appforcross.editor.prescale.BuildSpec
import com.appforcross.editor.prescale.ImageOps
import com.appforcross.editor.prescale.RunFull
import com.appforcross.editor.scene.SceneAnalyzer
import com.appforcross.editor.scene.SceneDecision
import com.appforcross.editor.scene.SceneKind
import com.appforcross.editor.scene.ScenePresetHook
import java.util.Locale

class MainActivity : Activity(), PreviewController.Listener {

    private lateinit var controller: PreviewController
    private lateinit var previewImage: ImageView
    private lateinit var infoText: TextView
    private lateinit var detectedText: TextView
    private lateinit var presetText: TextView
    private lateinit var preScaleText: TextView
    private lateinit var btnProcessAsPhoto: Button
    private lateinit var rbPhoto: RadioButton
    private lateinit var rbDiscrete: RadioButton
    private lateinit var overrideGroup: RadioGroup

    private var lastSceneDecision: SceneDecision? = null

    private var currentBitmap: Bitmap? = null

    private var lastPreviewRgb: FloatArray? = null
    private var lastPreviewWidth: Int = 0
    private var lastPreviewHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewImage = findViewById(R.id.previewImage)
        infoText = findViewById(R.id.tvInfo)
        detectedText = findViewById(R.id.detectedText)
        presetText = findViewById(R.id.tvPreset)
        preScaleText = findViewById(R.id.tvPreScale)
        btnProcessAsPhoto = findViewById(R.id.btnProcessAsPhoto)
        rbPhoto = findViewById(R.id.rbPhoto)
        rbDiscrete = findViewById(R.id.rbDiscrete)
        overrideGroup = findViewById(R.id.overrideGroup)

        controller = PreviewController()
        controller.setListener(this)

        findViewById<Button>(R.id.btnImport).setOnClickListener { pickImage() }

        val seekScale = findViewById<SeekBar>(R.id.seekScale)
        val tvScaleVal = findViewById<TextView>(R.id.tvScaleVal)
        seekScale.max = 290
        seekScale.progress = 90
        tvScaleVal.text = formatScaleLabel(100)
        seekScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = 10 + progress
                tvScaleVal.text = formatScaleLabel(pct)
                controller.setScalePercent(pct)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val seekK = findViewById<SeekBar>(R.id.seekK)
        val tvKVal = findViewById<TextView>(R.id.tvKVal)
        seekK.max = 64
        seekK.progress = 0
        tvKVal.text = formatKLabel(0)
        seekK.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvKVal.text = formatKLabel(progress)
                controller.setQuantK(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        overrideGroup.clearCheck()
        overrideGroup.setOnCheckedChangeListener { _, checkedId ->
            val overrideKind = when (checkedId) {
                R.id.rbPhoto -> SceneKind.PHOTO
                R.id.rbDiscrete -> SceneKind.DISCRETE
                else -> return@setOnCheckedChangeListener
            }
            Logger.i(
                "UI",
                "override",
                mapOf("checked" to overrideKind.name)
            )
            currentBitmap?.let { analyzeAndRender(it, via = "override") }
        }

        btnProcessAsPhoto.setOnClickListener {
            Logger.i("UI", "override", mapOf("to" to SceneKind.PHOTO.name))
            overrideGroup.check(R.id.rbPhoto)
            renderDetected(SceneKind.PHOTO, 1f, via = "override-button")
        }

        detectedText.text = getString(R.string.detected_placeholder)
        infoText.text = getString(R.string.info_placeholder)
        presetText.text = getString(R.string.preset_placeholder)
        preScaleText.text = getString(R.string.prescale_placeholder)
    }

    override fun onDestroy() {
        previewImage.setImageDrawable(null)
        controller.dispose()
        currentBitmap?.takeIf { !it.isRecycled }?.recycle()
        currentBitmap = null
        super.onDestroy()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.import_image_title)), REQ_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.openInputStream(uri)?.use { stream -> controller.setSourceFromStream(stream) }
        }
    }

    override fun onSourceInfo(width: Int, height: Int) {
        infoText.text = getString(R.string.info_template, width, height)
    }

    override fun onPreviewReady(bitmap: Bitmap, width: Int, height: Int) {
        currentBitmap?.takeIf { it != bitmap && !it.isRecycled }?.recycle()
        currentBitmap = bitmap
        previewImage.setImageBitmap(bitmap)
        analyzeAndRender(bitmap, via = "preview")
    }

    private fun analyzeAndRender(bitmap: Bitmap, via: String) {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) {
            lastPreviewRgb = null
            lastPreviewWidth = 0
            lastPreviewHeight = 0
            return
        }
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgb = FloatArray(w * h * 3)
        var i = 0
        for (px in pixels) {
            val r = (px shr 16 and 0xFF) / 255f
            val g = (px shr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            rgb[i++] = r
            rgb[i++] = g
            rgb[i++] = b
        }
        lastPreviewRgb = rgb
        lastPreviewWidth = w
        lastPreviewHeight = h
        val decision = SceneAnalyzer.analyzePreview(rgb, w, h)
        lastSceneDecision = decision
        val forced = manualOverride()
        renderDetected(forced ?: decision.kind, if (forced == null) decision.confidence else 1f, via)
    }

    private fun manualOverride(): SceneKind? = when (overrideGroup.checkedRadioButtonId) {
        R.id.rbPhoto -> SceneKind.PHOTO
        R.id.rbDiscrete -> SceneKind.DISCRETE
        else -> null
    }

    private fun renderDetected(kind: SceneKind, confidence: Float, via: String) {
        detectedText.text = try {
            getString(R.string.detected_template, kind.name, confidence.toDouble())
        } catch (_: Throwable) {
            "Detected: ${kind.name}  (confidence ${String.format(Locale.US, "%.2f", confidence)})"
        }
        btnProcessAsPhoto.visibility = if (kind == SceneKind.DISCRETE) View.VISIBLE else View.GONE
        Logger.i(
            "UI",
            "analyze",
            mapOf(
                "via" to via,
                "kind" to kind.name,
                "confidence" to String.format(Locale.US, "%.3f", confidence)
            )
        )
        renderPreset(kind)
    }

    private fun formatScaleLabel(percent: Int): String = getString(R.string.scale_template, percent)

    private fun formatKLabel(k: Int): String =
        if (k <= 0) getString(R.string.quant_off) else getString(R.string.quant_template, k)

    private fun renderPreset(kind: SceneKind) {
        val decision = lastSceneDecision
        if (decision == null) {
            presetText.text = getString(R.string.preset_placeholder)
            preScaleText.text = getString(R.string.prescale_placeholder)
            return
        }
        if (kind != SceneKind.PHOTO) {
            presetText.text = getString(R.string.preset_placeholder)
            preScaleText.text = getString(R.string.prescale_placeholder)
            return
        }
        val presetDecision = ScenePresetHook.decideForFoto(decision.features)
        presetText.text = try {
            getString(
                R.string.preset_label,
                presetDecision.preset.id,
                presetDecision.confidence.toDouble()
            )
        } catch (_: Throwable) {
            "Preset: ${presetDecision.preset.id}  (conf ${String.format(Locale.US, "%.2f", presetDecision.confidence)})"
        }
        Logger.i(
            "UI",
            "preset",
            mapOf(
                "id" to presetDecision.preset.id,
                "confidence" to String.format(Locale.US, "%.3f", presetDecision.confidence)
            )
        )
        val rgb = lastPreviewRgb
        val w = lastPreviewWidth
        val h = lastPreviewHeight
        if (rgb == null || w <= 0 || h <= 0) {
            preScaleText.text = getString(R.string.prescale_placeholder)
            return
        }
        val luminance = FloatArray(w * h) { index ->
            val p = index * 3
            0.2126f * rgb[p] + 0.7152f * rgb[p + 1] + 0.0722f * rgb[p + 2]
        }
        val buildDecision = BuildSpec.decide(w, h, luminance)
        val verify = RunFull.run(ImageOps.packToF16(rgb, w, h), buildDecision).verify
        preScaleText.text = try {
            getString(
                R.string.prescale_label,
                buildDecision.wst,
                buildDecision.sigma.toDouble(),
                buildDecision.phase.dx,
                buildDecision.phase.dy,
                buildDecision.filter,
                verify.ssimProxy.toDouble(),
                verify.edgeKeep.toDouble(),
                verify.bandIdx.toDouble()
            )
        } catch (_: Throwable) {
            String.format(
                Locale.US,
                "PreScale: Wst=%d σ=%.2f phase=%d,%d filter=%s SSIM=%.3f Edge=%.3f Band=%.3f",
                buildDecision.wst,
                buildDecision.sigma,
                buildDecision.phase.dx,
                buildDecision.phase.dy,
                buildDecision.filter,
                verify.ssimProxy,
                verify.edgeKeep,
                verify.bandIdx
            )
        }
    }

    companion object {
        private const val REQ_PICK = 1001
    }
}
