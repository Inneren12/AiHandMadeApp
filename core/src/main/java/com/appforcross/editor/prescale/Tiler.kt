package com.appforcross.editor.prescale

/** Простая реализация разбиения изображения на тайлы с overlap. */
class Tiler(
    private val W: Int,
    private val H: Int,
    private val tileW: Int,
    private val tileH: Int,
    private val overlap: Int
) {
    inline fun forEachTile(visit: (x: Int, y: Int, w: Int, h: Int, ox: Int, oy: Int) -> Unit) {
        var y = 0
        while (y < H) {
            val th = minOf(tileH, H - y)
            var x = 0
            while (x < W) {
                val tw = minOf(tileW, W - x)
                visit(x, y, tw, th, overlap, overlap)
                x += (tileW - overlap).coerceAtLeast(1)
            }
            y += (tileH - overlap).coerceAtLeast(1)
        }
    }
}
