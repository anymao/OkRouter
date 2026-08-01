package com.anymore.okrouter.warehouse

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.core.RouterType
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.RouterResult
import com.anymore.okrouter.core.internal.PriorityRouterInterceptorComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `match regex router and cache dynamic result with cleared query and fragment`() {
        val regex = RouterUri("https?", ".*", ".*", 0)
        val regexMeta = RouterMeta(
            regex,
            RouterType.SERVICE,
            "com.example.Service",
            WareHouseTest::class.java,
            emptyArray()
        )

        WareHouse.registerRegexRouter(regex, regexMeta)

        // 第一次请求：带 query 和 fragment
        val result1 = WareHouse.getMatchRouterMeta("https://music.163.com/song/123?track=1#tab")
        assertNotNull(result1)
        assertEquals(regexMeta, result1)
        assertEquals(1, WareHouse.dynamicRouters.size)
        // 缓存键应为规范化后的 URI（无 query，无 fragment）
        assertTrue(WareHouse.dynamicRouters.containsKey("https://music.163.com/song/123"))

        // 第二次请求：不同 query 参数，应命中缓存
        val result2 = WareHouse.getMatchRouterMeta("https://music.163.com/song/123?track=2")
        assertNotNull(result2)
        assertEquals(regexMeta, result2)
        assertEquals(1, WareHouse.dynamicRouters.size) // 缓存数量未增长

        // 第三次请求：不同 fragment，应命中缓存
        val result3 = WareHouse.getMatchRouterMeta("https://music.163.com/song/123#tab2")
        assertNotNull(result3)
        assertEquals(regexMeta, result3)
        assertEquals(1, WareHouse.dynamicRouters.size)
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
    fun `regex routers iterate in stable priority order`() {
        // 注册两条同优先级正则路由（priority 相同）
        val regex1 = RouterUri("https?", ".*", "/user/.*", 0)
        val regex2 = RouterUri("https?", ".*", "/song/.*", 0)
        val meta1 = RouterMeta(regex1, RouterType.ACTIVITY, "com.example.UserActivity",
            WareHouseTest::class.java, emptyArray())
        val meta2 = RouterMeta(regex2, RouterType.ACTIVITY, "com.example.SongActivity",
            WareHouseTest::class.java, emptyArray())

        WareHouse.registerRegexRouter(regex1, meta1)
        WareHouse.registerRegexRouter(regex2, meta2)

        // /song/.* 在 toString 字典序中排在 /user/.* 之前
        // 所以 /song/test 应命中 regex2
        val result = WareHouse.getMatchRouterMeta("https://example.com/song/test")
        assertNotNull(result)
        assertEquals("com.example.SongActivity", result!!.clazzName)
    }

    @Test
    fun `regex routers iterate in deterministic sorted order when both match`() {
        // 两个正则都能匹配同一路径，且注册顺序与字典序相反
        val regexSpecific = RouterUri("https?", ".*", "/song/test", 0)
        val regexBroad = RouterUri("https?", ".*", "/song/.*", 0)
        val metaSpecific = RouterMeta(regexSpecific, RouterType.ACTIVITY,
            "com.example.SpecificActivity", WareHouseTest::class.java, emptyArray())
        val metaBroad = RouterMeta(regexBroad, RouterType.ACTIVITY,
            "com.example.BroadActivity", WareHouseTest::class.java, emptyArray())

        // 先注册字典序较大的 /song/test（若按插入顺序遍历会先命中它）
        WareHouse.registerRegexRouter(regexSpecific, metaSpecific)
        WareHouse.registerRegexRouter(regexBroad, metaBroad)

        // 排序后 /song/.* 字典序更小，应优先命中 BroadActivity
        val result = WareHouse.getMatchRouterMeta("https://example.com/song/test")
        assertNotNull(result)
        assertEquals("com.example.BroadActivity", result!!.clazzName)
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
    fun `interceptors with same priority ordered by class name`() {
        // TestInterceptorA 和 TestInterceptorB 已在文件中定义
        val clazzA = TestInterceptorA::class.java
        val clazzB = TestInterceptorB::class.java

        // 注册同优先级
        WareHouse.registerInterceptor(clazzA,
            RouterInterceptorMeta(clazzA, clazzA.name, "",
                ReflectRouterInterceptorFactory(clazzA), 5, false, false))
        WareHouse.registerInterceptor(clazzB,
            RouterInterceptorMeta(clazzB, clazzB.name, "",
                ReflectRouterInterceptorFactory(clazzB), 5, false, false))

        val sorted = listOf(TestInterceptorB(), TestInterceptorA())
            .sortedWith(PriorityRouterInterceptorComparator)

        // TestInterceptorA 字典序在 TestInterceptorB 之前
        assertEquals(TestInterceptorA::class.java, sorted[0]::class.java)
        assertEquals(TestInterceptorB::class.java, sorted[1]::class.java)
    }

    @Test
    fun `singleton factory creates only one instance under concurrent access`() {
        // 统计 newInstance 实际被调用的次数，验证并发下只创建一次
        val createCount = java.util.concurrent.atomic.AtomicInteger(0)
        val factory = object : RouterInterceptorFactory(singleton = true) {
            override fun newInstance(): RouterInterceptor {
                createCount.incrementAndGet()
                // 放大竞态窗口，让并发竞争真实发生（否则首个线程可能先完成创建）
                Thread.sleep(5)
                return TestInterceptorA()
            }
        }
        val executor = java.util.concurrent.Executors.newFixedThreadPool(100)
        val startGate = java.util.concurrent.CountDownLatch(1)
        val latch = java.util.concurrent.CountDownLatch(100)
        val instances = java.util.concurrent.ConcurrentHashMap.newKeySet<RouterInterceptor>()

        repeat(100) {
            executor.submit {
                startGate.await() // 所有线程就绪后同时开跑
                instances.add(factory.create())
                latch.countDown()
            }
        }
        startGate.countDown()
        latch.await()
        executor.shutdown()

        assertEquals(1, instances.size)
        assertEquals(1, createCount.get())
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

    // ===== OkRouter 初始化生命周期（Task 6） =====
    // 说明：测试环境中 okrouter-stub 的 OkRouterLoader.load() 会直接抛出
    // UnsupportedOperationException，因此本测试类中没有任何测试能成功完成 init()，
    // OkRouter 在整个测试运行期间保持未初始化状态。若未来测试环境能成功初始化，
    // 上述"未初始化"类测试会在 isInitialized() 为 true 时自动跳过失败断言。

    @Test
    fun `start before init throws IllegalStateException`() {
        try {
            OkRouter.start(RouterRequest.Builder().uri("okrouter://test/main").build())
            // 走到这里说明没有抛出异常：若此时仍未初始化，则测试失败
            if (!OkRouter.isInitialized()) {
                org.junit.Assert.fail("Expected IllegalStateException")
            }
        } catch (e: IllegalStateException) {
            assertTrue("异常消息应提示未初始化", e.message!!.contains("未初始化"))
        }
    }

    @Test
    fun `builder start without context before init throws IllegalStateException`() {
        try {
            RouterRequest.Builder().uri("okrouter://test/main").start()
            if (!OkRouter.isInitialized()) {
                org.junit.Assert.fail("Expected IllegalStateException")
            }
        } catch (e: IllegalStateException) {
            assertTrue("异常消息应提示未初始化", e.message!!.contains("未初始化"))
        }
    }

    @Test
    fun `isInitialized returns false when not initialized`() {
        // 测试环境中 init() 无法成功完成（stub load() 抛异常），状态应保持未初始化
        assertFalse(OkRouter.isInitialized())
    }

    @Test
    fun `start with explicit context works before init`() {
        // 显式传入 context 时不受初始化状态限制，应返回 NotFound 而非抛异常
        val response = OkRouter.start(
            RouterRequest.Builder().uri("okrouter://test/main").build(),
            RuntimeEnvironment.getApplication()
        )
        assertNotNull(response)
        assertEquals(RouterResult.NotFound, response.routerResult)
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
