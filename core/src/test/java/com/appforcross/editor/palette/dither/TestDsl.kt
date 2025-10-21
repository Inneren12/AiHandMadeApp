package com.appforcross.editor.palette.dither

/**
 * Общие утилиты для тестов дизеринга:
 *  - ASCII DSL:  mask { + "...."; + ".##."; ... }
 *  - Константы W/H (дефолтные размеры, если тест не задаёт свои)
 *  - rgb() сборщик 0xRRGGBB
 */

internal class AsciiMaskBuilder {
    val lines: MutableList<String> = mutableListOf()
    /** Позволяет писать в тестах:  + "....##.."  */
    operator fun String.unaryPlus() { lines += this }
}

/** Собрать список строк ASCII-маски: */
internal inline fun mask(build: AsciiMaskBuilder.() -> Unit): List<String> =
    AsciiMaskBuilder().apply(build).lines

/** Базовые размеры (если в конкретном тесте не переопределены своими): */
internal const val W: Int = 64
internal const val H: Int = 64

/** Утилита сборки 0xRRGGBB без альфы: */
internal fun rgb(r: Int, g: Int, b: Int): Int =
    ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
