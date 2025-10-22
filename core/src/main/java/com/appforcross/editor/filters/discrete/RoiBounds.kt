package com.appforcross.editor.filters.discrete

/**
 * Half-open rectangle describing the region of interest within a mask.
 * Coordinates are clamped to image bounds before use.
 */
internal data class RoiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left <= right) { "ROI left must be <= right" }
        require(top <= bottom) { "ROI top must be <= bottom" }
    }

    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun contains(x: Int, y: Int): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }

    fun clampTo(width: Int, height: Int): RoiBounds? {
        if (width <= 0 || height <= 0) return null
        val clampedLeft = left.coerceIn(0, width)
        val clampedTop = top.coerceIn(0, height)
        val clampedRight = right.coerceIn(clampedLeft, width)
        val clampedBottom = bottom.coerceIn(clampedTop, height)
        return if (clampedLeft >= clampedRight || clampedTop >= clampedBottom) {
            null
        } else {
            RoiBounds(clampedLeft, clampedTop, clampedRight, clampedBottom)
        }
    }

    companion object {
        fun full(width: Int, height: Int): RoiBounds = RoiBounds(0, 0, width, height)
    }
}
