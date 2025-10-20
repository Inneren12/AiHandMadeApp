package com.appforcross.editor.palette

import kotlin.math.abs

object Kneedle {
    private val history = ArrayList<Pair<Int, Float>>()

    fun reset() {
        history.clear()
    }

    fun shouldGrow(score: Float, k: Int, tau: Float): Boolean {
        history.add(k to score)
        if (history.size == 1) return true
        val prev = history[history.size - 2]
        val deltaScore = score - prev.second
        val deltaK = (k - prev.first).coerceAtLeast(1)
        val slope = deltaScore / deltaK
        if (history.size == 2) {
            return slope >= tau
        }
        val prevPrev = history[history.size - 3]
        val prevSlope = (prev.second - prevPrev.second) / (prev.first - prevPrev.first).coerceAtLeast(1)
        val slopeDrop = prevSlope - slope
        return slope >= tau && abs(slopeDrop) <= tau * 2f
    }
}
