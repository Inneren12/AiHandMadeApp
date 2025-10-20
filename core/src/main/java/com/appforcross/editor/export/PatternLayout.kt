package com.appforcross.editor.export

import com.appforcross.editor.logging.Logger
import kotlin.math.max
import kotlin.math.min

/** Описание страницы схемы (фрагмент индексной карты). */
data class PatternPage(
    val pageIndex: Int,
    val x0: Int,
    val y0: Int,
    val width: Int,
    val height: Int,
    val data: IntArray
)

/** Набор страниц и параметры разбиения. */
data class LayoutBundle(
    val pages: List<PatternPage>,
    val boldEvery: Int,
    val overlap: Int,
    val gridW: Int,
    val gridH: Int
)

/** Разбиение индексной карты на страницы с перекрытием. */
object PatternLayout {
    fun paginate(
        index: IntArray,
        width: Int,
        height: Int,
        gridW: Int,
        gridH: Int,
        overlap: Int,
        boldEvery: Int = 10
    ): LayoutBundle {
        require(width > 0 && height > 0) { "image must be non-empty" }
        require(gridW > 0 && gridH > 0) { "grid must be positive" }
        require(overlap >= 0) { "overlap must be non-negative" }
        require(index.size == width * height) { "index length mismatch" }

        Logger.i(
            "EXPORT",
            "params",
            mapOf(
                "stage" to "paginate",
                "width" to width,
                "height" to height,
                "gridW" to gridW,
                "gridH" to gridH,
                "tile.overlap" to overlap,
                "boldEvery" to boldEvery
            )
        )

        val pages = ArrayList<PatternPage>()
        val stepX = (gridW - overlap).coerceAtLeast(1)
        val stepY = (gridH - overlap).coerceAtLeast(1)
        var pageIndex = 0
        var startY = 0
        while (true) {
            val endY = min(startY + gridH, height)
            val y0 = max(0, startY - overlap)
            val y1 = min(height, endY + overlap)
            val pageHeight = y1 - y0
            var startX = 0
            while (true) {
                val endX = min(startX + gridW, width)
                val x0 = max(0, startX - overlap)
                val x1 = min(width, endX + overlap)
                val pageWidth = x1 - x0

                val data = IntArray(pageWidth * pageHeight)
                var dst = 0
                for (gy in y0 until y1) {
                    val rowBase = gy * width
                    for (gx in x0 until x1) {
                        data[dst++] = index[rowBase + gx]
                    }
                }
                pages += PatternPage(pageIndex++, x0, y0, pageWidth, pageHeight, data)

                if (endX == width) {
                    break
                }
                startX += stepX
            }
            if (endY == height) {
                break
            }
            startY += stepY
        }

        Logger.i(
            "EXPORT",
            "done",
            mapOf(
                "stage" to "paginate",
                "pages" to pages.size
            )
        )

        return LayoutBundle(pages, boldEvery, overlap, gridW, gridH)
    }
}

