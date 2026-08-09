package com.anymore.okrouter.core

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.warehouse.RouterMeta
import com.anymore.okrouter.warehouse.RouterUri
import com.anymore.okrouter.warehouse.ContextFactory
import com.anymore.okrouter.warehouse.WareHouse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class RouterExecutionModelTest {

    @Before
    fun clearWareHouse() {
        WareHouse.stableRouters.clear()
        WareHouse.dynamicRouters.clear()
        WareHouse.regexRouters.clear()
        RecordingHandler.callCount.set(0)
    }

    @Test
    fun `router destination retains generated description`() {
        val meta = RouterMeta(
            RouterUri("okrouter", "android", "/profile", 0),
            RouterType.ACTIVITY,
            "example.ProfileActivity",
            Any::class.java,
            emptyArray(),
            null,
            "用户资料"
        )

        val destination = RouterDestination(meta.uri.toString(), meta.routerType, meta.description)

        assertEquals("用户资料", destination.description)
    }

    @Test
    fun `redirect outcome uses default options`() {
        val outcome = RouterOutcome.Redirect("okrouter://android/login")

        assertSame(RouterOptions.DEFAULT, outcome.options)
    }

    @Test
    fun `legacy six argument JVM constructor keeps empty description`() {
        val constructor = RouterMeta::class.java.getConstructor(
            RouterUri::class.java,
            RouterType::class.java,
            String::class.java,
            Class::class.java,
            emptyArray<Class<out RouterInterceptor>>().javaClass,
            ContextFactory::class.java
        )

        val meta = constructor.newInstance(
            RouterUri("okrouter", "android", "/legacy", 0),
            RouterType.ACTIVITY,
            "example.LegacyActivity",
            Any::class.java,
            emptyArray<Class<out RouterInterceptor>>(),
            null
        ) as RouterMeta

        assertEquals("", meta.description)
    }

    @Test
    fun `resolve returns destination and query parameters without launching`() {
        WareHouse.registerStableRouter(
            "okrouter://android/profile",
            testMeta("/profile", RouterType.HANDLER, "用户资料")
        )

        val result = OkRouter.resolve("okrouter://android/profile?id=42")

        assertTrue(result is RouterMatch.Found)
        val found = result as RouterMatch.Found
        assertEquals(RouterType.HANDLER, found.destination.type)
        assertEquals("用户资料", found.destination.description)
        assertEquals("42", found.parameters["id"])
        assertEquals(0, RecordingHandler.callCount.get())
    }

    @Test
    fun `resolve blank uri is invalid instead of throwing`() {
        assertEquals(RouterMatch.Invalid("URI 不能为空"), OkRouter.resolve(""))
    }

    @Test
    fun `resolve valid uri without registered route returns not found`() {
        assertEquals(RouterMatch.NotFound, OkRouter.resolve("okrouter://android/missing"))
    }

    @Test
    fun `resolve regex route does not populate dynamic router cache`() {
        WareHouse.registerRegexRouter(
            RouterUri("okrouter", "android", "/profile/.*", 0),
            testMeta("/profile/.*", RouterType.HANDLER, "用户资料")
        )

        val result = OkRouter.resolve("okrouter://android/profile/42?tab=overview")

        assertTrue(result is RouterMatch.Found)
        assertEquals(
            "okrouter://android/profile/42?tab=overview",
            (result as RouterMatch.Found).uri
        )
        assertTrue(WareHouse.dynamicRouters.isEmpty())
    }

    @Test
    fun `start regex route populates dynamic router cache`() {
        WareHouse.registerRegexRouter(
            RouterUri("okrouter", "android", "/profile/.*", 0),
            RouterMeta(
                RouterUri("okrouter", "android", "/profile/.*", 0),
                RouterType.HANDLER,
                CompletedV2Handler::class.java.name,
                CompletedV2Handler::class.java,
                emptyArray()
            )
        )

        val response = OkRouter.build("okrouter://android/profile/42?tab=overview").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals(1, WareHouse.dynamicRouters.size)
        assertTrue(WareHouse.dynamicRouters.containsKey("okrouter://android/profile/42"))
    }

    @Test
    fun `resolve result parameters are immutable snapshots`() {
        WareHouse.registerStableRouter(
            "okrouter://android/profile",
            testMeta("/profile", RouterType.HANDLER, "用户资料")
        )
        val request = RouterRequest.Builder()
            .uri("okrouter://android/profile?id=42")
            .build()

        val result = OkRouter.resolve(request) as RouterMatch.Found
        @Suppress("UNCHECKED_CAST")
        val mutableParameters = result.parameters as MutableMap<String, Any?>

        try {
            mutableParameters["id"] = "changed"
            fail("解析结果的参数不应可被外部修改")
        } catch (_: UnsupportedOperationException) {
            // 预期：公开参数是只读快照。
        }
        request.extras.putString("id", "changed-from-request")

        assertEquals("42", result.parameters["id"])
    }

    @Test
    fun `v2 handler completed outcome becomes ok response`() {
        registerHandler("/v2", CompletedV2Handler::class.java)

        val response = OkRouter.build("okrouter://android/v2").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("v2-target", response.target)
    }

    @Test
    fun `legacy handler remains compatible`() {
        registerHandler("/legacy", RecordingHandler::class.java)

        val response = OkRouter.build("okrouter://android/legacy").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("OK", response.headers[Extend.OKROUTER_NOTE])
        assertEquals(1, RecordingHandler.callCount.get())
    }

    @Test
    fun `legacy builder start and dispatcher start return same result`() {
        registerHandler("/compat", RecordingHandler::class.java)
        val request = RouterRequest.Builder().uri("okrouter://android/compat").build()

        val viaBuilder = OkRouter.build("okrouter://android/compat").start(appContext)
        assertEquals(1, RecordingHandler.callCount.get())
        val viaDispatcher = OkRouter.start(request, appContext)

        assertEquals(viaBuilder.routerResult, viaDispatcher.routerResult)
        assertEquals(viaBuilder.routerType, viaDispatcher.routerType)
        assertEquals(viaBuilder.uri, viaDispatcher.uri)
        assertEquals(viaBuilder.target, viaDispatcher.target)
        assertEquals(viaBuilder.headers, viaDispatcher.headers)
        assertEquals(2, RecordingHandler.callCount.get())
    }

    @Test
    fun `v2 interceptor can intercept without calling next`() {
        registerHandler("/blocked", RecordingHandler::class.java, BlockingV2Interceptor::class.java)

        val response = OkRouter.build("okrouter://android/blocked").start(appContext)

        assertEquals(RouterResult.Intercepted, response.routerResult)
        assertEquals(0, RecordingHandler.callCount.get())
    }

    @Test
    fun `legacy interceptor remains compatible`() {
        registerHandler("/legacy-interceptor", RecordingHandler::class.java, PassingLegacyInterceptor::class.java)

        val response = OkRouter.build("okrouter://android/legacy-interceptor").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals(1, RecordingHandler.callCount.get())
    }

    @Test
    fun `legacy interceptor proceeds to v2 redirect without losing redirect outcome`() {
        registerHandler(
            "/legacy-redirect",
            RedirectHandler::class.java,
            PassingLegacyInterceptor::class.java
        )
        registerHandler("/login", CompletedV2Handler::class.java)

        val response = OkRouter.build("okrouter://android/legacy-redirect").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("okrouter://android/legacy-redirect", response.uri)
        assertEquals("okrouter://android/login", response.headers[Extend.OKROUTER_FINAL_URI])
    }

    @Test
    fun `missing handler becomes failed response`() {
        registerHandler("/missing-handler", String::class.java)

        val response = OkRouter.build("okrouter://android/missing-handler").start(appContext)

        assertTrue(response.routerResult is RouterResult.Failed)
    }

    @Test
    fun `legacy response remains lossless after v2 rewrites proceeded outcome`() {
        registerHandler(
            "/legacy-not-found",
            RecordingHandler::class.java,
            ARewritingV2Interceptor::class.java,
            ZNotFoundLegacyInterceptor::class.java
        )
        registerHandler(
            "/legacy-invalid",
            RecordingHandler::class.java,
            ARewritingV2Interceptor::class.java,
            ZInvalidLegacyInterceptor::class.java
        )
        registerHandler(
            "/legacy-custom",
            RecordingHandler::class.java,
            ARewritingV2Interceptor::class.java,
            ZCustomLegacyInterceptor::class.java
        )

        assertLegacyResponse(
            "/legacy-not-found",
            RouterResult.NotFound,
            "not-found-target"
        )
        assertLegacyResponse(
            "/legacy-invalid",
            RouterResult.InvalidRequest("legacy-invalid"),
            "invalid-target"
        )
        assertLegacyResponse(
            "/legacy-custom",
            RouterResult.Custom("legacy-custom"),
            "custom-target"
        )

        registerHandler("/after-legacy", CompletedV2Handler::class.java)
        val response = OkRouter.build("okrouter://android/after-legacy").start(appContext)
        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("v2-target", response.target)
    }

    @Test
    fun `redirect reaches terminal handler and preserves original response uri`() {
        registerHandler("/entry", RedirectHandler::class.java)
        registerHandler("/login", CompletedV2Handler::class.java)

        val response = OkRouter.build("okrouter://android/entry").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("okrouter://android/entry", response.uri)
        assertEquals("okrouter://android/login", response.headers[Extend.OKROUTER_FINAL_URI])
    }

    @Test
    fun `redirect preserves request configuration for terminal handler`() {
        val launcher = RecordingLauncher()
        registerHandler("/entry", RedirectHandler::class.java)
        registerHandler("/login", RequestCapturingHandler::class.java)
        val request = OkRouter.build("okrouter://android/entry")
            .requestCode(7)
            .routerType(RouterType.HANDLER)
            .header("trace-id", "trace-42")
            .putString("source", "home")
            .launcher(launcher)
            .build()

        val response = OkRouter.start(request, appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("home", RequestCapturingHandler.extras)
        assertEquals("trace-42", RequestCapturingHandler.header)
        assertEquals(7, RequestCapturingHandler.requestCode)
        assertEquals(RouterType.HANDLER, RequestCapturingHandler.routerType)
        assertSame(launcher, RequestCapturingHandler.launcher)
    }

    @Test
    fun `redirect query supplements but does not overwrite original extras`() {
        registerHandler("/redirect-query", RedirectWithQueryHandler::class.java)
        registerHandler("/query-target", QueryCapturingHandler::class.java)
        val request = OkRouter.build("okrouter://android/redirect-query")
            .putString("source", "original")
            .putString("carry", "keep")
            .build()

        val response = OkRouter.start(request, appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("original", QueryCapturingHandler.source)
        assertEquals("overview", QueryCapturingHandler.tab)
        assertEquals("keep", QueryCapturingHandler.carry)
    }

    @Test
    fun `redirect loop becomes failed response`() {
        registerHandler("/a", RedirectToBHandler::class.java)
        registerHandler("/b", RedirectToAHandler::class.java)

        val response = OkRouter.build("okrouter://android/a").start(appContext)

        assertTrue(response.routerResult is RouterResult.Failed)
    }

    @Test
    fun `redirect loop with changing query becomes failed response`() {
        registerHandler("/query-loop", QueryChangingLoopHandler::class.java)

        val response = OkRouter.build("okrouter://android/query-loop?step=0").start(appContext)

        assertTrue(response.routerResult is RouterResult.Failed)
        assertTrue((response.routerResult as RouterResult.Failed).cause.message!!.contains("循环"))
    }

    @Test
    fun `redirect exceeding maximum count becomes failed response`() {
        (0..8).forEach { number ->
            registerHandler("/$number", RedirectUntilLimitHandler::class.java)
        }

        val response = OkRouter.build("okrouter://android/0").start(appContext)

        assertTrue(response.routerResult is RouterResult.Failed)
    }

    @Test
    fun `eight redirects reach the terminal route`() {
        (0..8).forEach { number ->
            registerHandler("/$number", RedirectAtLimitHandler::class.java)
        }

        val response = OkRouter.build("okrouter://android/0").start(appContext)

        assertEquals(RouterResult.Ok, response.routerResult)
        assertEquals("redirect-target-8", response.target)
        assertEquals("okrouter://android/0", response.uri)
        assertEquals("okrouter://android/8", response.headers[Extend.OKROUTER_FINAL_URI])
    }

    @Test
    fun `ninth redirect is rejected before terminal route`() {
        (0..9).forEach { number ->
            registerHandler("/$number", RedirectAtNineHandler::class.java)
        }

        val response = OkRouter.build("okrouter://android/0").start(appContext)

        assertTrue(response.routerResult is RouterResult.Failed)
        assertEquals("okrouter://android/0", response.uri)
        assertEquals("okrouter://android/9", response.headers[Extend.OKROUTER_FINAL_URI])
    }

    @Test
    fun `observers receive resolved before finished and failures are isolated`() {
        val events = mutableListOf<String>()
        val recordingObserver = object : RouterObserver {
            override fun onResolved(match: RouterMatch) {
                val found = match as RouterMatch.Found
                events += "resolved:${found.uri}"
            }

            override fun onFinished(context: RouterContext?, outcome: RouterOutcome) {
                events += "finished:${context?.request?.uri}:${outcome::class.simpleName}"
            }
        }
        val throwingObserver = object : RouterObserver {
            override fun onFinished(context: RouterContext?, outcome: RouterOutcome) {
                throw IllegalStateException("metrics unavailable")
            }
        }
        OkRouter.addObserver(recordingObserver)
        OkRouter.addObserver(throwingObserver)
        try {
            registerHandler("/observe", CompletedV2Handler::class.java)

            val response = OkRouter.build("okrouter://android/observe").start(appContext)

            assertEquals(RouterResult.Ok, response.routerResult)
            assertEquals(
                listOf(
                    "resolved:okrouter://android/observe",
                    "finished:okrouter://android/observe:Completed"
                ),
                events
            )
        } finally {
            OkRouter.removeObserver(recordingObserver)
            OkRouter.removeObserver(throwingObserver)
        }
    }

    @Test
    fun `throwing observer logger keeps later observer and response intact`() {
        val events = mutableListOf<String>()
        val originalLogger = OkRouter.logger
        val throwingObserver = object : RouterObserver {
            override fun onFinished(context: RouterContext?, outcome: RouterOutcome) {
                throw IllegalStateException("metrics unavailable")
            }
        }
        val laterObserver = object : RouterObserver {
            override fun onFinished(context: RouterContext?, outcome: RouterOutcome) {
                events += "finished:${context?.request?.uri}:${outcome::class.simpleName}"
            }
        }
        OkRouter.logger = object : Logger {
            override fun v(tag: String, message: String) = Unit
            override fun d(tag: String, message: String) = Unit
            override fun i(tag: String, message: String) = Unit
            override fun w(tag: String, message: String, throwable: Throwable?) = Unit
            override fun e(tag: String, message: String, throwable: Throwable?) {
                throw IllegalStateException("logger unavailable")
            }
        }
        OkRouter.addObserver(throwingObserver)
        OkRouter.addObserver(laterObserver)
        try {
            registerHandler("/observe-logger", CompletedV2Handler::class.java)

            val response = OkRouter.build("okrouter://android/observe-logger").start(appContext)

            assertEquals(RouterResult.Ok, response.routerResult)
            assertEquals("v2-target", response.target)
            assertEquals(
                listOf("finished:okrouter://android/observe-logger:Completed"),
                events
            )
        } finally {
            OkRouter.removeObserver(throwingObserver)
            OkRouter.removeObserver(laterObserver)
            OkRouter.logger = originalLogger
        }
    }

    private fun assertLegacyResponse(path: String, result: RouterResult, target: String) {
        val response = OkRouter.build("okrouter://android$path").start(appContext)

        assertEquals(result::class, response.routerResult::class)
        assertEquals(result.value, response.routerResult.value)
        assertEquals("legacy-$target", response.headers["legacy-header"])
        assertEquals(target, response.target)
    }

    private fun testMeta(path: String, type: RouterType, description: String): RouterMeta = RouterMeta(
        RouterUri("okrouter", "android", path, 0),
        type,
        RecordingHandler::class.java.name,
        RecordingHandler::class.java,
        emptyArray(),
        null,
        description
    )

    private fun registerHandler(
        path: String,
        clazz: Class<*>,
        vararg interceptors: Class<out RouterInterceptor>
    ) {
        WareHouse.registerStableRouter(
            "okrouter://android$path",
            RouterMeta(
                RouterUri("okrouter", "android", path, 0),
                RouterType.HANDLER,
                clazz.name,
                clazz,
                arrayOf(*interceptors)
            )
        )
    }

    private val appContext: Context = RuntimeEnvironment.getApplication()

    class CompletedV2Handler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome = RouterOutcome.Completed("v2-target")
    }

    class RedirectHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome =
            RouterOutcome.Redirect("okrouter://android/login")
    }

    class RedirectWithQueryHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome =
            RouterOutcome.Redirect("okrouter://android/query-target?source=redirect&tab=overview")
    }

    class RedirectToBHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome =
            RouterOutcome.Redirect("okrouter://android/b")
    }

    class RedirectToAHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome =
            RouterOutcome.Redirect("okrouter://android/a")
    }

    class QueryChangingLoopHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome {
            val step = context.request.uri.substringAfter("step=").toInt()
            return RouterOutcome.Redirect("okrouter://android/query-loop?step=${step + 1}")
        }
    }

    class RequestCapturingHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome {
            extras = context.request.extras.getString("source")
            header = context.request.headers["trace-id"] as String
            requestCode = context.request.requestCode
            routerType = context.request.routerType
            launcher = context.request.launcher
            return RouterOutcome.Completed()
        }

        companion object {
            var extras: String? = null
            var header: String? = null
            var requestCode: Int = -1
            var routerType: RouterType = RouterType.UNDEFINED
            var launcher: ActivityResultLauncher<Intent>? = null
        }
    }

    class QueryCapturingHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome {
            source = context.request.extras.getString("source")
            tab = context.request.extras.getString("tab")
            carry = context.request.extras.getString("carry")
            return RouterOutcome.Completed()
        }

        companion object {
            var source: String? = null
            var tab: String? = null
            var carry: String? = null
        }
    }

    class RecordingLauncher : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) = Unit

        override fun unregister() = Unit

        override fun getContract(): ActivityResultContract<Intent, *> =
            ActivityResultContracts.StartActivityForResult()
    }

    class RedirectUntilLimitHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome {
            val current = context.request.uri.substringAfterLast('/').toInt()
            return RouterOutcome.Redirect("okrouter://android/${current + 1}")
        }
    }

    class RedirectAtLimitHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome {
            val current = context.request.uri.substringAfterLast('/').toInt()
            return if (current == 8) {
                RouterOutcome.Completed("redirect-target-$current")
            } else {
                RouterOutcome.Redirect("okrouter://android/${current + 1}")
            }
        }
    }

    class RedirectAtNineHandler : RouterHandlerV2 {
        override fun handle(context: RouterContext): RouterOutcome {
            val current = context.request.uri.substringAfterLast('/').toInt()
            return if (current == 9) {
                RouterOutcome.Completed("redirect-target-9")
            } else {
                RouterOutcome.Redirect("okrouter://android/${current + 1}")
            }
        }
    }

    class BlockingV2Interceptor : RouterInterceptorV2 {
        override fun intercept(chain: RouterChain): RouterOutcome = RouterOutcome.Intercepted("blocked")
    }

    class PassingLegacyInterceptor : RouterInterceptor {
        override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
            return chain.proceed(context, chain.request())
        }
    }

    class ARewritingV2Interceptor : RouterInterceptorV2 {
        override fun intercept(chain: RouterChain): RouterOutcome {
            chain.proceed()
            return RouterOutcome.Completed("rewritten-target")
        }
    }

    class ZNotFoundLegacyInterceptor : FixedLegacyResponseInterceptor(RouterResult.NotFound, "not-found-target")

    class ZInvalidLegacyInterceptor :
        FixedLegacyResponseInterceptor(RouterResult.InvalidRequest("legacy-invalid"), "invalid-target")

    class ZCustomLegacyInterceptor :
        FixedLegacyResponseInterceptor(RouterResult.Custom("legacy-custom"), "custom-target")

    abstract class FixedLegacyResponseInterceptor(
        private val result: RouterResult,
        private val target: String
    ) : RouterInterceptor {
        override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
            return RouterResponse.Builder()
                .uri(chain.request().uri)
                .routerType(RouterType.HANDLER)
                .routerResult(result)
                .header("legacy-header", "legacy-$target")
                .target(target)
                .build()
        }
    }

    class RecordingHandler : RouterHandler {
        override fun handle(context: Context, request: RouterRequest) {
            callCount.incrementAndGet()
        }

        companion object {
            val callCount = AtomicInteger(0)
        }
    }
}
