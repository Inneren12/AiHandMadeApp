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
import com.appforcross.editor.export.LegendBuilder
import com.appforcross.editor.export.PatternLayout
import com.appforcross.editor.export.PdfExporter
import com.appforcross.editor.prescale.PreScaleOrchestrator
import com.appforcross.editor.scene.SceneAnalyzer
import com.appforcross.editor.scene.SceneDecision
import com.appforcross.editor.scene.SceneKind
import java.util.Locale

class MainActivity : Activity(), PreviewController.Listener {

    private lateinit var controller: PreviewController
    private lateinit var previewImage: ImageView
    private lateinit var infoText: TextView
    private lateinit var detectedText: TextView
    private lateinit var presetText: TextView
    private lateinit var preScaleText: TextView
    private lateinit var btnRecalcLarger: Button
    private lateinit var btnProcessAsPhoto: Button
    private lateinit var btnExportPdf: Button
    private lateinit var rbPhoto: RadioButton
    private lateinit var rbDiscrete: RadioButton
    private lateinit var overrideGroup: RadioGroup

    private var lastSceneDecision: SceneDecision? = null
    private var forceLargerWst: Boolean = false

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
        btnRecalcLarger = findViewById(R.id.btnRecalcLarger)
        btnProcessAsPhoto = findViewById(R.id.btnProcessAsPhoto)
        btnExportPdf = findViewById(R.id.btnExportPdf)
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

        btnExportPdf.isEnabled = false
        btnExportPdf.setOnClickListener { exportPdfFlow() }

        btnRecalcLarger.visibility = View.GONE
        btnRecalcLarger.setOnClickListener {
            forceLargerWst = true
            renderPreset(SceneKind.PHOTO)
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
        } else if (requestCode == REQ_EXPORT && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            performExport(uri)
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
            btnExportPdf.isEnabled = false
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
        btnExportPdf.isEnabled = true
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
            btnRecalcLarger.visibility = View.GONE
            forceLargerWst = false
            return
        }
        if (kind != SceneKind.PHOTO) {
            presetText.text = getString(R.string.preset_placeholder)
            preScaleText.text = getString(R.string.prescale_placeholder)
            btnRecalcLarger.visibility = View.GONE
            forceLargerWst = false
            return
        }
        val rgb = lastPreviewRgb
        val w = lastPreviewWidth
        val h = lastPreviewHeight
        if (rgb == null || w <= 0 || h <= 0) {
            preScaleText.text = getString(R.string.prescale_placeholder)
            btnRecalcLarger.visibility = View.GONE
            forceLargerWst = false
            return
        }
        val report = PreScaleOrchestrator.run(rgb, w, h, forceLargerWst)
        forceLargerWst = false
        presetText.text = try {
            getString(R.string.preset_label, report.presetId, report.presetConfidence.toDouble())
        } catch (_: Throwable) {
            "Preset: ${report.presetId}  (conf ${String.format(Locale.US, "%.2f", report.presetConfidence)})"
        }
        Logger.i(
            "UI",
            "preset",
            mapOf(
                "id" to report.presetId,
                "confidence" to String.format(Locale.US, "%.3f", report.presetConfidence),
                "frPass" to report.frPass
            )
        )
        preScaleText.text = try {
            getString(
                R.string.prescale_label,
                report.wst,
                report.sigma.toDouble(),
                report.phaseDx,
                report.phaseDy,
                report.filter,
                report.ssimProxy.toDouble(),
                report.edgeKeep.toDouble(),
                report.bandIdx.toDouble(),
                report.deltaE95.toDouble()
            )
        } catch (_: Throwable) {
            String.format(
                Locale.US,
                "PreScale: Wst=%d σ=%.2f phase=%d,%d filter=%s SSIM=%.3f Edge=%.3f Band=%.3f ΔE95=%.2f",
                report.wst,
                report.sigma,
                report.phaseDx,
                report.phaseDy,
                report.filter,
                report.ssimProxy,
                report.edgeKeep,
                report.bandIdx,
                report.deltaE95
            )
        }
        btnRecalcLarger.visibility = if (report.frPass) View.GONE else View.VISIBLE
    }

    companion object {
        private const val REQ_PICK = 1001
        private const val REQ_EXPORT = 1002
        private const val EXPORT_COLORS = 12
        private const val EXPORT_GRID = 50
        private const val EXPORT_OVERLAP = 3
    }

    private fun exportPdfFlow() {
        if (lastPreviewRgb == null || lastPreviewWidth <= 0 || lastPreviewHeight <= 0) {
            Logger.e("EXPORT", "fatal", mapOf("stage" to "ui.export", "reason" to "preview_missing"))
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "pattern.pdf")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun performExport(uri: Uri) {
        val rgb = lastPreviewRgb ?: return
        val width = lastPreviewWidth
        val height = lastPreviewHeight
        if (width <= 0 || height <= 0) {
            Logger.e("EXPORT", "fatal", mapOf("stage" to "ui.export", "reason" to "invalid_dimensions"))
            return
        }
        val colors = EXPORT_COLORS.coerceAtLeast(1)
        Logger.i(
            "EXPORT",
            "params",
            mapOf(
                "stage" to "ui.export",
                "width" to width,
                "height" to height,
                "colors" to colors,
                "gridW" to EXPORT_GRID,
                "gridH" to EXPORT_GRID,
                "tile.overlap" to EXPORT_OVERLAP
            )
        )
        val index = naiveAssign(rgb, width, height, colors)
        val paletteCodes = generatePaletteCodes(colors)
        val legend = LegendBuilder.build(paletteCodes)
        val bundle = PatternLayout.paginate(
            index,
            width,
            height,
            gridW = EXPORT_GRID,
            gridH = EXPORT_GRID,
            overlap = EXPORT_OVERLAP,
            boldEvery = 10
        )
        PdfExporter.export(this, uri, bundle, legend)
        Logger.i(
            "EXPORT",
            "done",
            mapOf(
                "stage" to "ui.export",
                "pages" to bundle.pages.size
            )
        )
    }

    /** Простейшая квантовка: делим диапазон яркости на K уровней. */
    private fun naiveAssign(rgb: FloatArray, width: Int, height: Int, colors: Int): IntArray {
        val total = width * height
        val result = IntArray(total)
        val maxIndex = (colors - 1).coerceAtLeast(0)
        var p = 0
        for (i in 0 until total) {
            val l = 0.2126f * rgb[p] + 0.7152f * rgb[p + 1] + 0.0722f * rgb[p + 2]
            val scaled = (l.coerceIn(0f, 1f) * maxIndex + 0.5f).toInt()
            result[i] = scaled.coerceIn(0, maxIndex)
            p += 3
        }
        return result
    }

    private fun generatePaletteCodes(colors: Int): List<String> {
        if (colors <= 1) return listOf("#000000")
        val codes = ArrayList<String>(colors)
        for (i in 0 until colors) {
            val shade = (255f * i / (colors - 1)).toInt().coerceIn(0, 255)
            codes += String.format(Locale.US, "#%02X%02X%02X", shade, shade, shade)
        }
        return codes
    }
}
