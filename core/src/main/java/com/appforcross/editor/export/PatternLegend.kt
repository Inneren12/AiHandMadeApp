package com.appforcross.editor.export

import com.appforcross.editor.logging.Logger

/** Одна строка легенды (цвет палитры). */
data class LegendEntry(
    val index: Int,
    val code: String,
    val name: String,
    val symbol: Char
)

/** Полная легенда для схемы. */
data class Legend(
    val entries: List<LegendEntry>
)

/** Генератор набора символов и легенды под заданные коды палитры. */
object LegendBuilder {
    private val SYMBOLS: CharArray = (
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "0123456789" +
        "@#$%&*+=/\\<>^~" +
        "abcdefghijklmno"
    ).toCharArray()

    /** Строит легенду: каждому цвету сопоставляет уникальный символ. */
    fun build(paletteCodes: List<String>): Legend {
        Logger.i(
            "EXPORT",
            "params",
            mapOf(
                "stage" to "legend",
                "colors" to paletteCodes.size
            )
        )
        val entries = ArrayList<LegendEntry>(paletteCodes.size)
        for (i in paletteCodes.indices) {
            val symbol = SYMBOLS[i % SYMBOLS.size]
            val code = paletteCodes[i]
            entries += LegendEntry(i, code, "Color $code", symbol)
        }
        Logger.i(
            "EXPORT",
            "done",
            mapOf(
                "stage" to "legend",
                "colors" to entries.size
            )
        )
        return Legend(entries)
    }
}

