package com.appforcross.editor.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.appforcross.editor.logging.Logger

/** Простой PDF-экспорт страниц и легенды. */
object PdfExporter {
    data class PdfParams(
        val pageWidthPt: Int = 595,
        val pageHeightPt: Int = 842,
        val marginPt: Int = 36,
        val cellPt: Int = 12
    )

    fun export(context: Context, uri: Uri, bundle: LayoutBundle, legend: Legend, params: PdfParams = PdfParams()) {
        Logger.i(
            "EXPORT",
            "params",
            mapOf(
                "stage" to "pdf",
                "pages" to bundle.pages.size,
                "cellPt" to params.cellPt,
                "pageWidthPt" to params.pageWidthPt,
                "pageHeightPt" to params.pageHeightPt,
                "tile.overlap" to bundle.overlap
            )
        )

        val cell = params.cellPt
        val left = params.marginPt
        val top = params.marginPt

        val paintGrid = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            isAntiAlias = false
        }
        val paintBold = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            isAntiAlias = false
        }
        val paintText = Paint().apply {
            style = Paint.Style.FILL
            textSize = 9f
            isAntiAlias = false
        }
        val paintTitle = Paint().apply {
            style = Paint.Style.FILL
            textSize = 12f
            isAntiAlias = true
        }

        val document = PdfDocument()
        try {
            for (page in bundle.pages) {
                val pageInfo = PdfDocument.PageInfo.Builder(params.pageWidthPt, params.pageHeightPt, page.pageIndex + 1).create()
                val pdfPage = document.startPage(pageInfo)
                val canvas: Canvas = pdfPage.canvas

                val gridWidthPt = page.width * cell
                val gridHeightPt = page.height * cell

                canvas.drawText("Page ${page.pageIndex + 1}", left.toFloat(), (top - 8).coerceAtLeast(8).toFloat(), paintTitle)

                val boldEvery = bundle.boldEvery.coerceAtLeast(1)
                for (iy in 0..page.height) {
                    val y = top + iy * cell
                    val globalRow = page.y0 + iy
                    val isBold = globalRow % boldEvery == 0
                    canvas.drawLine(
                        left.toFloat(),
                        y.toFloat(),
                        (left + gridWidthPt).toFloat(),
                        y.toFloat(),
                        if (isBold) paintBold else paintGrid
                    )
                }
                for (ix in 0..page.width) {
                    val x = left + ix * cell
                    val globalCol = page.x0 + ix
                    val isBold = globalCol % boldEvery == 0
                    canvas.drawLine(
                        x.toFloat(),
                        top.toFloat(),
                        x.toFloat(),
                        (top + gridHeightPt).toFloat(),
                        if (isBold) paintBold else paintGrid
                    )
                }

                for (yy in 0 until page.height) {
                    for (xx in 0 until page.width) {
                        val idx = yy * page.width + xx
                        val colorIndex = page.data[idx]
                        val symbol = legend.entries.getOrNull(colorIndex)?.symbol ?: '.'
                        val sx = left + xx * cell + cell * 0.35f
                        val sy = top + yy * cell + cell * 0.75f
                        canvas.drawText(symbol.toString(), sx, sy, paintText)
                    }
                }
                document.finishPage(pdfPage)
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
        }

        Logger.i(
            "EXPORT",
            "done",
            mapOf(
                "stage" to "pdf",
                "pages" to bundle.pages.size
            )
        )
    }
}

