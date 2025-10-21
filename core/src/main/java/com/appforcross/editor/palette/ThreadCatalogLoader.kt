package com.appforcross.editor.palette

import com.appforcross.editor.logging.Logger
import kotlin.text.RegexOption

object ThreadCatalogLoader {
    private val ENTRY_REGEX = Regex(
        "\\{\\s*\"code\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"rgb\"\\s*:\\s*\"([0-9A-Fa-f]{6})\"\\s*}\\s*,?",
        setOf(RegexOption.MULTILINE)
    )

    fun load(resource: String): List<ThreadColor> {
        val path = if (resource.startsWith("catalogs/")) resource else "catalogs/$resource"
        Logger.i("CATALOG", "params", mapOf("resource" to path))
        val stream = ThreadCatalogLoader::class.java.classLoader?.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Catalog resource missing: $path")
        val text = stream.bufferedReader().use { it.readText() }
        val colors = mutableListOf<ThreadColor>()
        for (match in ENTRY_REGEX.findAll(text)) {
            val code = match.groupValues[1]
            val name = match.groupValues[2]
            val rgb = match.groupValues[3]
            val okLab = hexToOklab(rgb)
            colors += ThreadColor(code, name, okLab)
        }
        require(colors.isNotEmpty()) { "Catalog $path produced no entries" }
        Logger.i(
            "CATALOG",
            "done",
            mapOf(
                "resource" to path,
                "count" to colors.size
            )
        )
        return colors
    }

    fun loadMinimalDmc(): List<ThreadColor> = load("catalogs/dmc_min.json")

    fun loadMinimalAnchor(): List<ThreadColor> = load("catalogs/anchor_min.json")

    private fun hexToOklab(hex: String): FloatArray {
        require(hex.length == 6) { "RGB hex must be 6 characters" }
        val r = srgbToLinear(hex.substring(0, 2).toInt(16))
        val g = srgbToLinear(hex.substring(2, 4).toInt(16))
        val b = srgbToLinear(hex.substring(4, 6).toInt(16))
        return PaletteMath.rgbToOklab(r, g, b)
    }

    private fun srgbToLinear(component: Int): Float {
        val normalized = component / 255.0
        val linear = if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return linear.toFloat()
    }
}
