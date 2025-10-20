package com.appforcross.editor.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternLayoutTest {
    @Test
    fun paginate_basic_counts_and_coordinates() {
        val width = 25
        val height = 20
        val index = IntArray(width * height) { it % 7 }
        val bundle = PatternLayout.paginate(
            index,
            width,
            height,
            gridW = 10,
            gridH = 10,
            overlap = 2,
            boldEvery = 10
        )

        // 3×3 страницы (с учётом перекрытия) → 9 страниц
        assertEquals(9, bundle.pages.size)
        for (page in bundle.pages) {
            assertTrue(page.width > 0 && page.height > 0)
            assertEquals(page.width * page.height, page.data.size)
            assertTrue(page.x0 >= 0 && page.y0 >= 0)
            assertTrue(page.x0 + page.width <= width)
            assertTrue(page.y0 + page.height <= height)

            // проверяем совпадение выборки с исходной картой
            var idx = 0
            for (gy in page.y0 until page.y0 + page.height) {
                val base = gy * width
                for (gx in page.x0 until page.x0 + page.width) {
                    val expected = index[base + gx]
                    assertEquals(expected, page.data[idx++])
                }
            }
        }
    }
}

