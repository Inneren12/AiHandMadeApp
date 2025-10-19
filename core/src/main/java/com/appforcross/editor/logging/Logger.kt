package com.appforcross.editor.logging

/** Минимальный JSON-логгер + last() для тестов. Без Android-зависимостей. */
object Logger {
    interface Sink { fun log(level: Char, tag: String, event: String, payload: Map<String, Any?>) }
    data class Trip(val level: Char, val tag: String, val event: String, val payload: Map<String, Any?>)
    @Volatile private var sink: Sink = StdoutSink()
    @Volatile private var lastTrip: Trip? = null

    private class StdoutSink : Sink {
        override fun log(level: Char, tag: String, event: String, payload: Map<String, Any?>) {
            val json = buildString {
                append('{')
                append("\"lvl\":\"").append(level).append("\",")
                append("\"tag\":\"").append(tag).append("\",")
                append("\"evt\":\"").append(event).append("\",")
                append("\"data\":{")
                var first = true
                for ((k, v) in payload) {
                    if (!first) append(',') else first = false
                    append('"').append(k).append('"').append(':')
                    append('"').append(v?.toString()?.replace("\"", "\\\"") ?: "null").append('"')
                }
                append("}}")
            }
            println(json)
            lastTrip = Trip(level, tag, event, payload)
        }
    }

    fun installSink(s: Sink) { sink = s }
    fun last(): Trip? = lastTrip
    fun i(tag: String, event: String, payload: Map<String, Any?> = emptyMap()) = sink.log('I', tag, event, payload)
    fun e(tag: String, event: String, payload: Map<String, Any?> = emptyMap()) = sink.log('E', tag, event, payload)
}
