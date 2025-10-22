package com.appforcross.editor.filters.discrete

/**
 * Public rectangular region-of-interest descriptor used by the discrete pipeline.
 *
 * The region is defined by its inclusive-exclusive bounds. All coordinates must
 * satisfy `0 <= left < right` and `0 <= top < bottom`. The [contains]
 * helper returns whether a pixel with integer coordinates lies inside the region.
 */
data class Roi(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0 && top >= 0) { "ROI coordinates must be non-negative" }
        require(right > left && bottom > top) { "ROI must have positive size" }
    }

    /** Width in pixels. */
    val width: Int get() = right - left

    /** Height in pixels. */
    val height: Int get() = bottom - top

    /** Returns `true` when the supplied pixel coordinate lies within the ROI. */
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    /** Ensures the ROI fits inside the provided image dimensions. */
    fun requireWithin(width: Int, height: Int) {
        require(right <= width && bottom <= height) { "ROI must fit within the image bounds" }
    }
}
