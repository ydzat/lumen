package com.lumen

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun platformName_returnsNonEmpty() {
        assertTrue(getPlatformName().isNotEmpty())
    }
}
