package com.anymore.okrouter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun `RouterResult Failed contains cause exception`() {
        val cause = RuntimeException("测试异常")
        val result = RouterResult.Failed(cause)
        assertEquals("Failed", result.value)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `RouterResult InvalidRequest contains reason`() {
        val result = RouterResult.InvalidRequest("URI 格式错误")
        assertEquals("InvalidRequest", result.value)
        assertEquals("URI 格式错误", result.reason)
    }

    @Test
    fun `RouterResult sealed class has all expected subtypes`() {
        // 验证新增子类型可用于 when 表达式
        val result: RouterResult = RouterResult.Failed(RuntimeException())
        val message = when (result) {
            is RouterResult.Ok -> "ok"
            is RouterResult.NotFound -> "not found"
            is RouterResult.Intercepted -> "intercepted"
            is RouterResult.Failed -> "failed: ${result.cause.message}"
            is RouterResult.InvalidRequest -> "invalid: ${result.reason}"
            is RouterResult.Custom -> "custom: ${result.value}"
        }
        assertTrue(message.startsWith("failed:"))
    }
}
