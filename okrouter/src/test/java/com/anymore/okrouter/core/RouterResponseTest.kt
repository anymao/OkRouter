package com.anymore.okrouter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RouterResponseTest {

    @Test
    fun `build success response`() {
        val response = RouterResponse.Builder()
            .uri("okrouter://android/main")
            .routerType(RouterType.ACTIVITY)
            .routerResult(RouterResult.Ok)
            .header("k", "v")
            .build()

        assertEquals("okrouter://android/main", response.uri)
        assertEquals(RouterType.ACTIVITY, response.routerType)
        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("v", response.headers["k"])
        assertEquals(null, response.target)
    }

    @Test
    fun `build intercept response should keep result`() {
        val response = RouterResponse.Builder()
            .uri("okrouter://android/main")
            .routerType(RouterType.ACTIVITY)
            .routerResult(RouterResult.Intercepted)
            .build()

        assertEquals(RouterResult.Intercepted, response.routerResult)
        assertNotNull(response)
    }

    @Test
    fun `build without routerResult should throw`() {
        assertThrows(IllegalStateException::class.java) {
            RouterResponse.Builder()
                .uri("okrouter://android/main")
                .routerType(RouterType.ACTIVITY)
                .build()
        }
    }

    @Test
    fun `ok result with undefined type should throw`() {
        assertThrows(IllegalStateException::class.java) {
            RouterResponse.Builder()
                .uri("okrouter://android/main")
                .routerType(RouterType.UNDEFINED)
                .routerResult(RouterResult.Ok)
                .build()
        }
    }

    @Test
    fun `newBuilder should copy headers and allow override`() {
        val response = RouterResponse.Builder()
            .uri("okrouter://android/main")
            .routerType(RouterType.ACTIVITY)
            .routerResult(RouterResult.Ok)
            .header("k", "v")
            .build()

        val copied = response.newBuilder()
            .header("v2", "2")
            .build()

        assertEquals("v", copied.headers["k"])
        assertEquals("2", copied.headers["v2"])
    }
}
