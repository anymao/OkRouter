package com.anymore.okrouter.core

import android.content.Context
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.warehouse.RouterMeta
import com.anymore.okrouter.warehouse.RouterUri
import com.anymore.okrouter.warehouse.ContextFactory
import com.anymore.okrouter.warehouse.WareHouse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        assertEquals("42", found.parameters.getString("id"))
        assertEquals(0, RecordingHandler.callCount.get())
    }

    @Test
    fun `resolve blank uri is invalid instead of throwing`() {
        assertEquals(RouterMatch.Invalid("URI 不能为空"), OkRouter.resolve(""))
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

    private class RecordingHandler : RouterHandler {
        override fun handle(context: Context, request: RouterRequest) {
            callCount.incrementAndGet()
        }

        companion object {
            val callCount = AtomicInteger(0)
        }
    }
}
