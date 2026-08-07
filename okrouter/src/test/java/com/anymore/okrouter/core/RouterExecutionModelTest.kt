package com.anymore.okrouter.core

import com.anymore.okrouter.warehouse.RouterMeta
import com.anymore.okrouter.warehouse.RouterUri
import com.anymore.okrouter.warehouse.ContextFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RouterExecutionModelTest {

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
}
