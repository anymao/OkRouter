package com.anymore.okrouter.core

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class RouterRequestEdgeCaseTest {

    @Test
    fun `query should decode percent encoded value and preserve encoded plus`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/search?q=hello%20world%2Brouter")
            .build()

        assertEquals("hello world+router", request.extras.getString("q"))
    }

    @Test
    fun `empty query value should be preserved`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/main?empty=")
            .build()

        assertEquals("", request.extras.getString("empty"))
    }

    @Test
    fun `fragment should stay in raw uri while not becoming an extra`() {
        val uri = "okrouter://android/main?tab=detail#comments"
        val request = RouterRequest.Builder().uri(uri).build()

        assertEquals(uri, request.uri)
        assertEquals(uri, request.extras.getString(Extend.OKROUTER_RAW_URI))
        assertEquals("detail", request.extras.getString("tab"))
        assertNull(request.extras.getString("comments"))
    }

    @Test
    fun `new builder should isolate mutable headers and extras`() {
        val original = RouterRequest.Builder()
            .uri("okrouter://android/main")
            .header("source", "original")
            .putString("key", "original")
            .build()

        val copied = original.newBuilder()
            .header("source", "copied")
            .putString("key", "copied")
            .build()

        assertEquals("original", original.headers["source"])
        assertEquals("original", original.extras.getString("key"))
        assertEquals("copied", copied.headers["source"])
        assertEquals("copied", copied.extras.getString("key"))
        assertTrue(original !== copied)
    }
}
