package com.anymore.okrouter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouterResponseEdgeCaseTest {

    @Test
    fun `not found response should keep undefined router type`() {
        val response = RouterResponse.Builder()
            .uri("okrouter://android/missing")
            .routerType(RouterType.UNDEFINED)
            .routerResult(RouterResult.NotFound)
            .build()

        assertEquals(RouterType.UNDEFINED, response.routerType)
        assertEquals(RouterResult.NotFound, response.routerResult)
        assertNull(response.target)
    }

    @Test
    fun `new builder should preserve target and allow header extension`() {
        val target = Any()
        val response = RouterResponse.Builder()
            .uri("okrouter://android/view")
            .routerType(RouterType.VIEW)
            .routerResult(RouterResult.Ok)
            .target(target)
            .build()

        val copied = response.newBuilder()
            .header("traceId", "trace-1")
            .build()

        assertEquals(target, copied.target)
        assertEquals("trace-1", copied.headers["traceId"])
        assertEquals(emptyMap<String, Any>(), response.headers)
    }
}
