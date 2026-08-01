package com.anymore.okrouter.core

import com.anymore.okrouter.warehouse.RouterUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterUriTest {

    @Test
    fun `stable uri should not be regex`() {
        val uri = RouterUri("okrouter", "android", "/main", 0)
        assertFalse(uri.isRegex)
        assertEquals("okrouter://android/main", uri.toString())
    }

    @Test
    fun `regex uri should be regex`() {
        val uri = RouterUri("https?", ".*", ".*", 1)
        assertTrue(uri.isRegex)
        assertEquals("https?://.*.*", uri.toString())
    }

    @Test
    fun `compare by priority`() {
        val low = RouterUri("okrouter", "android", "/a", 10)
        val high = RouterUri("okrouter", "android", "/a", 1)
        assertTrue(low > high)
        assertTrue(high < low)
    }

    @Test
    fun `toString with empty scheme`() {
        val uri = RouterUri("", "okrouter", "/main", 0)
        assertEquals("okrouter/main", uri.toString())
    }
}
