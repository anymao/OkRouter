package com.anymore.okrouter.warehouse

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.anymore.okrouter.core.RouterType
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.internal.PriorityRouterInterceptorComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class WareHouseTest {

    @Before
    fun clear() {
        WareHouse.stableRouters.clear()
        WareHouse.dynamicRouters.clear()
        WareHouse.regexRouters.clear()
        WareHouse.globalInterceptors.clear()
        WareHouse.interceptorMetas.clear()
    }

    @Test
    fun `match stable router by exact uri`() {
        val uri = RouterUri("okrouter", "android", "/main", 0)
        val meta = RouterMeta(
            uri,
            RouterType.ACTIVITY,
            "com.example.MainActivity",
            RouterMeta::class.java,
            emptyArray()
        )

        WareHouse.registerStableRouter("okrouter://android/main", meta)

        val result = WareHouse.getMatchRouterMeta("okrouter://android/main")
        assertNotNull(result)
        assertEquals(meta, result)
    }

    @Test
    fun `match regex router and cache dynamic result`() {
        val regex = RouterUri("https?", ".*", ".*", 0)
        val regexMeta = RouterMeta(
            regex,
            RouterType.SERVICE,
            "com.example.Service",
            WareHouseTest::class.java,
            emptyArray()
        )

        WareHouse.registerRegexRouter(regex, regexMeta)

        val result = WareHouse.getMatchRouterMeta("https://music.163.com/song/123?track=1")
        assertNotNull(result)
        assertEquals(regexMeta, result)
        assertEquals(1, WareHouse.dynamicRouters.size)
        assertTrue(WareHouse.dynamicRouters.containsKey("https://music.163.com/song/123?track=1"))
    }

    @Test
    fun `match stable router should ignore query params`() {
        val uri = RouterUri("okrouter", "android", "/main", 0)
        val meta = RouterMeta(
            uri,
            RouterType.ACTIVITY,
            "com.example.MainActivity",
            RouterMeta::class.java,
            emptyArray()
        )

        WareHouse.registerStableRouter("okrouter://android/main", meta)
        val result = WareHouse.getMatchRouterMeta("okrouter://android/main?a=1&b=2")
        assertEquals(meta, result)
    }

    @Test
    fun `register stable router should ignore duplicate uri`() {
        val uri = RouterUri("okrouter", "android", "/main", 0)
        val oldMeta = RouterMeta(
            uri,
            RouterType.ACTIVITY,
            "com.example.MainActivity",
            RouterMeta::class.java,
            emptyArray()
        )
        val newMeta = RouterMeta(
            uri,
            RouterType.SERVICE,
            "com.example.Service",
            WareHouseTest::class.java,
            emptyArray()
        )

        WareHouse.registerStableRouter("okrouter://android/main", oldMeta)
        WareHouse.registerStableRouter("okrouter://android/main", newMeta)

        assertEquals(oldMeta, WareHouse.getMatchRouterMeta("okrouter://android/main"))
    }

    @Test
    fun `interceptor priority comparator order`() {
        val clazzA = TestInterceptorA::class.java
        val clazzB = TestInterceptorB::class.java

        WareHouse.registerInterceptor(
            clazzA,
            RouterInterceptorMeta(
                clazzA,
                clazzA.name,
                "",
                object : RouterInterceptorFactory(singleton = true) {
                    override fun newInstance(): RouterInterceptor = TestInterceptorA()
                },
                5,
            false,
                false
            )
        )
        WareHouse.registerInterceptor(
            clazzB,
            RouterInterceptorMeta(
                clazzB,
                clazzB.name,
                "",
                ReflectRouterInterceptorFactory(clazzB),
                1,
                false,
                false
            )
        )

        val sorted = listOf(TestInterceptorA(), TestInterceptorB()).sortedWith(PriorityRouterInterceptorComparator)

        assertEquals(TestInterceptorB::class.java, sorted[0]::class.java)
        assertEquals(TestInterceptorA::class.java, sorted[1]::class.java)
    }

    @Test
    fun `get interceptor instance should use single factory instance when singleton`() {
        val clazzA = TestInterceptorA::class.java

        WareHouse.registerInterceptor(
            clazzA,
            RouterInterceptorMeta(
                clazzA,
                clazzA.name,
                "",
                object : RouterInterceptorFactory(singleton = true) {
                    override fun newInstance(): RouterInterceptor = TestInterceptorA()
                },
                1,
                false,
                true
            )
        )

        val instance = WareHouse.getInterceptorInstance(clazzA)
        assertNotNull(instance)
        assertTrue(instance is TestInterceptorA)

        val cached = WareHouse.getInterceptorInstance(clazzA)
        assertSame(instance, cached)
    }

    private class TestInterceptorA : RouterInterceptor {
        override fun intercept(context: android.content.Context, chain: RouterInterceptor.Chain): RouterResponse {
            return chain.proceed(context, chain.request())
        }
    }

    private class TestInterceptorB : RouterInterceptor {
        override fun intercept(context: android.content.Context, chain: RouterInterceptor.Chain): RouterResponse {
            return chain.proceed(context, chain.request())
        }
    }
}
