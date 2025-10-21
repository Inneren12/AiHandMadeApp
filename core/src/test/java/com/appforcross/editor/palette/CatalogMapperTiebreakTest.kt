package com.appforcross.editor.palette

import com.appforcross.editor.logging.Logger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMapperTiebreakTest {

    @Test
    fun tieBreakPrefersLowerIndex() {
        val base = ThreadCatalogLoader.loadMinimalDmc().first()
        val duplicateCatalog = listOf(
            ThreadColor("FIRST", "First", base.okLab.copyOf()),
            ThreadColor("SECOND", "Second", base.okLab.copyOf())
        )
        val palette = floatArrayOf(base.okLab[0], base.okLab[1], base.okLab[2])
        val fit = CatalogMapper.mapToCatalog(palette, duplicateCatalog)
        assertArrayEquals(intArrayOf(0), fit.mapping)
        assertEquals(0f, fit.avgDE, 1e-6f)
        assertEquals(0f, fit.maxDE, 1e-6f)
    }

    @Test
    fun thresholdsEnforceUnmappedAndLog() {
        val catalog = ThreadCatalogLoader.loadMinimalAnchor()
        val palette = floatArrayOf(3f, 0f, 0f)
        val fit = CatalogMapper.mapToCatalog(palette, catalog)
        assertEquals(-1, fit.mapping[0])
        val overThreshold =
            (fit.avgDE >= CatalogThresholds.AVG - 1e-6f) ||
            (fit.maxDE >= CatalogThresholds.MAX - 1e-6f)
        assertTrue(
            "expected avg or max deltaE above threshold " +
                "(avg=${'$'}{fit.avgDE}, max=${'$'}{fit.maxDE}, " +
                "thrAvg=${'$'}{CatalogThresholds.AVG}, thrMax=${'$'}{CatalogThresholds.MAX})",
            overThreshold
        )
        assertEquals("UNMAPPED", Logger.last()?.event)
        assertEquals("CATALOG", Logger.last()?.tag)
    }
}
