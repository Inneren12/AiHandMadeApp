package com.appforcross.editor.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LoggerContractTest {
    @Test
    fun writesJsonAndExposesLast() {
        val oldOut = System.out
        val baos = ByteArrayOutputStream()
        // без явного Charset: во всех раннерах по умолчанию UTF-8
        System.setOut(PrintStream(baos))
        try {
            Logger.i("X", "params", mapOf("a" to 1))

            val last = Logger.last()
            assertNotNull(last, "Logger.last() must not be null after a log call")
            assertEquals('I', last!!.level)
            assertEquals("X", last.tag)
            assertEquals("params", last.event)

            val out = baos.toString()
            assertTrue(out.contains("\"lvl\":\"I\""))
            assertTrue(out.contains("\"tag\":\"X\""))
            assertTrue(out.contains("\"evt\":\"params\""))
        } finally {
            System.setOut(oldOut)
        }
    }
}
