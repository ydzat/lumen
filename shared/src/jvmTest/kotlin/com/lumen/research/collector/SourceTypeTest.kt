package com.lumen.research.collector

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceTypeTest {

    @Test
    fun fromString_uppercase_returnsCorrectType() {
        assertEquals(SourceType.RSS, SourceType.fromString("RSS"))
        assertEquals(SourceType.ARXIV_API, SourceType.fromString("ARXIV_API"))
        assertEquals(SourceType.SEMANTIC_SCHOLAR, SourceType.fromString("SEMANTIC_SCHOLAR"))
        assertEquals(SourceType.GITHUB_RELEASES, SourceType.fromString("GITHUB_RELEASES"))
    }

    @Test
    fun fromString_lowercase_returnsCorrectType() {
        assertEquals(SourceType.RSS, SourceType.fromString("rss"))
        assertEquals(SourceType.ARXIV_API, SourceType.fromString("arxiv_api"))
    }

    @Test
    fun fromString_mixedCase_returnsCorrectType() {
        assertEquals(SourceType.RSS, SourceType.fromString("Rss"))
        assertEquals(SourceType.GITHUB_RELEASES, SourceType.fromString("github_releases"))
    }

    @Test
    fun fromString_unknown_fallsBackToRss() {
        assertEquals(SourceType.RSS, SourceType.fromString("unknown"))
        assertEquals(SourceType.RSS, SourceType.fromString(""))
    }
}
