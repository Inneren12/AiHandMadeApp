package com.appforcross.editor.palette

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMapperTest {
    @Test
    fun mapping_selects_closest_catalog_color() {
        val palette = floatArrayOf(
            0.52f, 0.12f, 0.04f,
            0.68f, -0.18f, 0.12f,
            0.32f, 0.02f, -0.18f
        )
        val catalog = listOf(
            ThreadColor("A1", "Warm", floatArrayOf(0.5f, 0.1f, 0.05f)),
            ThreadColor("B2", "Cool", floatArrayOf(0.7f, -0.2f, 0.1f)),
            ThreadColor("C3", "Deep", floatArrayOf(0.3f, 0.0f, -0.2f))
        )
        val fit = CatalogMapper.mapToCatalog(palette, catalog)
        assertArrayEquals(intArrayOf(0, 1, 2), fit.mapping)
        assertTrue(fit.avgDE < 1.0f)
        assertTrue(fit.maxDE < 2.0f)
    }
}
