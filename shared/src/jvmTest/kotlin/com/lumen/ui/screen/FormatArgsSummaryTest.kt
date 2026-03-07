package com.lumen.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatArgsSummaryTest {

    @Test
    fun emptyString_returnsEmpty() {
        assertEquals("", formatArgsSummary(""))
    }

    @Test
    fun blankString_returnsEmpty() {
        assertEquals("", formatArgsSummary("   "))
    }

    @Test
    fun simpleJsonObject_parsesKeyValues() {
        val result = formatArgsSummary("""{"query":"transformer attention","limit":5}""")
        assertEquals("query: transformer attention, limit: 5", result)
    }

    @Test
    fun singleKeyValue_parsesCorrectly() {
        val result = formatArgsSummary("""{"projectId":42}""")
        assertEquals("projectId: 42", result)
    }

    @Test
    fun emptyValue_skipped() {
        val result = formatArgsSummary("""{"query":"test","empty":""}""")
        assertEquals("query: test", result)
    }

    @Test
    fun valueWithComma_parsedCorrectly() {
        val result = formatArgsSummary("""{"query":"attention, transformer","limit":5}""")
        assertEquals("query: attention, transformer, limit: 5", result)
    }

    @Test
    fun nestedJson_handledGracefully() {
        val result = formatArgsSummary("""{"query":"test","nested":{"a":1}}""")
        assert(result.contains("query: test"))
    }

    @Test
    fun malformedInput_doesNotCrash() {
        val result = formatArgsSummary("not json at all")
        assert(result.length >= 0)
    }
}
