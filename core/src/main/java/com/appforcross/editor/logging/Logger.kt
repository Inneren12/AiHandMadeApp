package com.appforcross.editor.logging

import java.util.concurrent.atomic.AtomicReference

/**
 * Centralized logging utility that emits single-line JSON entries.
 */
object Logger {
    private val lastEntry = AtomicReference<Trip?>(null)

    data class Trip(
        val level: String,
        val tag: String,
        val event: String,
        val payload: Map<String, Any?>
    )

    /**
     * Records an informational log event.
     */
    fun i(tag: String, event: String, payload: Map<String, Any?> = emptyMap()) {
        log("INFO", tag, event, payload)
    }

    /**
     * Records an error log event.
     */
    fun e(tag: String, event: String, payload: Map<String, Any?> = emptyMap()) {
        log("ERROR", tag, event, payload)
    }

    /**
     * Returns the last emitted log entry for verification in tests.
     */
    fun last(): Trip? = lastEntry.get()

    private fun log(level: String, tag: String, event: String, payload: Map<String, Any?>) {
        val copiedPayload = payload.toMap()
        val trip = Trip(level, tag, event, copiedPayload)
        lastEntry.set(trip)
        System.out.println(toJson(trip))
    }

    private fun toJson(trip: Trip): String {
        val payloadJson = toJsonObject(trip.payload)
        return "{" +
            "\"level\":${jsonValue(trip.level)}," +
            "\"tag\":${jsonValue(trip.tag)}," +
            "\"event\":${jsonValue(trip.event)}," +
            "\"payload\":$payloadJson" +
            "}"
    }

    private fun toJsonObject(map: Map<String, Any?>): String {
        return map.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":${jsonValue(value)}"
        }
    }

    @Suppress("ComplexMethod")
    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is String -> "\"${escape(value)}\""
        is Map<*, *> -> {
            val stringKeyMap = value.entries.associate { (k, v) ->
                k?.toString() ?: "null" to v
            }
            toJsonObject(stringKeyMap)
        }
        else -> "\"${escape(value.toString())}\""
    }

    private fun escape(raw: String): String {
        val builder = StringBuilder(raw.length)
        raw.forEach { ch ->
            when (ch) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }
}
