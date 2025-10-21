package com.appforcross.editor.pattern

import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Deterministic symbol assignment for merged topology labels. */
class Symbolizer(
    private val symbolPool: CharArray = DEFAULT_SYMBOLS
) {
    private val assignments = LinkedHashMap<Int, Char>()

    fun symbolize(labels: IntArray): CharArray {
        val used = LinkedHashSet<Int>()
        for (label in labels) used.add(label)

        val iterator = assignments.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!used.contains(entry.key)) {
                iterator.remove()
            }
        }

        for (label in used) {
            if (!assignments.containsKey(label)) {
                assignments[label] = acquireSymbol()
            }
        }

        val out = CharArray(labels.size)
        for (i in labels.indices) {
            out[i] = assignments.getValue(labels[i])
        }
        return out
    }

    private fun acquireSymbol(): Char {
        for (symbol in symbolPool) {
            if (!assignments.containsValue(symbol)) return symbol
        }
        throw IllegalStateException("Symbol pool exhausted")
    }

    companion object {
        private val DEFAULT_SYMBOLS = (
            ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('#', '@', '&', '%', '*', '+', '=')
        ).toCharArray()
    }
}
