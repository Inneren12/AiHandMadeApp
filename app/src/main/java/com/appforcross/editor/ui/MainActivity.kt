package com.appforcross.editor.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import com.appforcross.editor.scene.SceneAnalyzer
import com.appforcross.editor.scene.SceneDecision
import com.appforcross.editor.scene.SceneKind
import com.appforcross.editor.palette.GreedyQuant
import com.appforcross.editor.palette.QuantParams
import com.appforcross.editor.palette.Topology
import com.appforcross.editor.palette.TopologyParams
import com.appforcross.editor.palette.dither.DitherParams
import com.appforcross.editor.palette.dither.OrderedDither
import java.util.Locale

class MainActivity : Activity(), PreviewController.Listener {

    private lateinit var controller: PreviewController
    private val preScaleWorker = PreScaleWorker()
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
    private var preScaleRequestSeq: Long = 0

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
        preScaleWorker.dispose()
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
            preScaleRequestSeq++
            return
        }
        if (kind != SceneKind.PHOTO) {
            presetText.text = getString(R.string.preset_placeholder)
            preScaleText.text = getString(R.string.prescale_placeholder)
            btnRecalcLarger.visibility = View.GONE
            forceLargerWst = false
            preScaleRequestSeq++
            return
        }
        val rgb = lastPreviewRgb
        val w = lastPreviewWidth
        val h = lastPreviewHeight
        if (rgb == null || w <= 0 || h <= 0) {
            preScaleText.text = getString(R.string.prescale_placeholder)
            btnRecalcLarger.visibility = View.GONE
            forceLargerWst = false
            preScaleRequestSeq++
            return
        }
        val requestId = ++preScaleRequestSeq
        val useLargerWst = forceLargerWst
        forceLargerWst = false
        presetText.text = getString(R.string.preset_placeholder)
        preScaleText.text = getString(R.string.prescale_placeholder)
        btnRecalcLarger.visibility = View.GONE
        preScaleWorker.run(rgb, w, h, useLargerWst) { report ->
            runOnUiThread {
                if (requestId != preScaleRequestSeq) return@runOnUiThread
                if (isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)) {
                    return@runOnUiThread
                }
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
                        report.hst,
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
                        "PreScale: Wst=%d Hst=%d σ=%.2f phase=%d,%d filter=%s SSIM=%.3f Edge=%.3f Band=%.3f ΔE95=%.2f",
                        report.wst,
                        report.hst,
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
        }
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
        val quantParams = QuantParams(
            kStart = colors.coerceAtLeast(2),
            kMax = colors.coerceAtLeast(2)
        )
        val quant = GreedyQuant.run(rgb, width, height, quantParams)
        val paletteRgb = okLabPaletteToLinearRgb(quant.colorsOKLab)
        val dithered = OrderedDither.apply(
            rgb,
            quant.assignments,
            paletteRgb,
            width,
            height,
            DitherParams()
        )
        val (index, topoMetrics) = Topology.clean(
            dithered,
            width,
            height,
            TopologyParams()
        )
        Logger.i(
            "EXPORT",
            "verify",
            mapOf(
                "stage" to "ui.export",
                "topology.changes_per100" to String.format(Locale.US, "%.2f", topoMetrics.changesPer100),
                "topology.islands_per1000" to String.format(Locale.US, "%.2f", topoMetrics.smallIslandsPer1000),
                "topology.runlen_p50" to String.format(Locale.US, "%.2f", topoMetrics.runLen50)
            )
        )
        val paletteCodes = generatePaletteCodes(paletteRgb)
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

    private fun okLabPaletteToLinearRgb(palette: FloatArray): FloatArray {
        val colors = palette.size / 3
        val out = FloatArray(colors * 3)
        for (i in 0 until colors) {
            val l = palette[i * 3 + 0]
            val a = palette[i * 3 + 1]
            val b = palette[i * 3 + 2]
            val l_ = l + 0.3963377774f * a + 0.2158037573f * b
            val m_ = l - 0.1055613458f * a - 0.0638541728f * b
            val s_ = l - 0.0894841775f * a - 1.2914855480f * b
            val l3 = l_ * l_ * l_
            val m3 = m_ * m_ * m_
            val s3 = s_ * s_ * s_
            val r = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
            val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
            val bCh = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
            out[i * 3 + 0] = r.coerceIn(0f, 1f)
            out[i * 3 + 1] = g.coerceIn(0f, 1f)
            out[i * 3 + 2] = bCh.coerceIn(0f, 1f)
        }
        return out
    }

    private fun generatePaletteCodes(paletteRgb: FloatArray): List<String> {
        val colors = paletteRgb.size / 3
        val codes = ArrayList<String>(colors)
        for (i in 0 until colors) {
            val r = linearToSrgbChannel(paletteRgb[i * 3 + 0])
            val g = linearToSrgbChannel(paletteRgb[i * 3 + 1])
            val b = linearToSrgbChannel(paletteRgb[i * 3 + 2])
            val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            codes += String.format(Locale.US, "#%02X%02X%02X", ri, gi, bi)
        }
        return codes
    }

    private fun linearToSrgbChannel(v: Float): Float {
        val clamped = v.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            12.92f * clamped
        } else {
            (1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat()) - 0.055f
        }
    }
}
