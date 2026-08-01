package com.anymore.okrouter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterRequestTest {

    @Test
    fun `build from path with query should parse string query`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/main?foo=1&bar=hello")
            .build()

        assertEquals("okrouter://android/main?foo=1&bar=hello", request.uri)
        assertEquals("1", request.extras.getString("foo"))
        assertEquals("hello", request.extras.getString("bar"))
        assertEquals("okrouter://android/main?foo=1&bar=hello", request.extras.getString(Extend.OKROUTER_RAW_URI))
    }

    @Test
    fun `build from path with repeated query should parse list`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/main?foo=1&foo=2&foo=3")
            .build()

        assertEquals(listOf("1", "2", "3"), request.extras.getStringArrayList("foo"))
        assertNull(request.extras.getString("foo"))
    }

    @Test
    fun `newBuilder should copy all fields`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/main")
            .requestCode(12)
            .header("h1", "v1")
            .putInt("i", 10)
            .build()

        val copy = request.newBuilder()
            .putString("new", "ok")
            .build()

        assertEquals(12, copy.requestCode)
        assertEquals("v1", copy.headers["h1"])
        assertEquals(10, copy.extras.getInt("i"))
        assertEquals("ok", copy.extras.getString("new"))
    }

    @Test
    fun `start without uri should throw`() {
        try {
            RouterRequest.Builder().build()
            throw AssertionError("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("uri is null or empty!", e.message)
        }
    }

    @Test
    fun `request toString includes fields`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/main")
            .routerType(RouterType.ACTIVITY)
            .putBoolean("b", true)
            .build()

        val text = request.toString()
        assertNotNull(text)
        assertTrue(text.contains("okrouter://android/main"))
        assertTrue(!text.contains("UNDEFINED"))
    }

    @Test
    fun `build without query should keep raw uri and no arguments`() {
        val request = RouterRequest.Builder()
            .uri("okrouter://android/main")
            .build()

        assertEquals("okrouter://android/main", request.extras.getString(Extend.OKROUTER_RAW_URI))
        assertEquals(null, request.extras.getString("a"))
    }
}
