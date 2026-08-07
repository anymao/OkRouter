package com.anymore.okrouter.core

import com.anymore.okrouter.warehouse.RouterMeta
import com.anymore.okrouter.warehouse.RouterUri
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
}
