package com.appforcross.editor.palette

object Kneedle {
    private val history = ArrayList<Pair<Int, Float>>()

    fun reset() {
        history.clear()
    }

    fun shouldGrow(score: Float, k: Int, tau: Float): Boolean {
        history.add(k to score)
        if (history.size <= 2) return true
        val last = history.last()
        val prev = history[history.size - 2]
        val prevPrev = history.getOrNull(history.size - 3) ?: prev
        val slope = derivative(last, prev)
        val prevSlope = derivative(prev, prevPrev)
        val twoPoint = derivative(last, prevPrev)
        val derivativeOk = slope >= tau
        val twoPointOk = twoPoint >= tau
        val slopeDrop = prevSlope - slope
        if (derivativeOk || twoPointOk) {
            return true
        }
        return slopeDrop <= tau * 0.5f
    }

    private fun derivative(cur: Pair<Int, Float>, prev: Pair<Int, Float>): Float {
        val deltaK = (cur.first - prev.first).coerceAtLeast(1)
        return (cur.second - prev.second) / deltaK
    }
}
