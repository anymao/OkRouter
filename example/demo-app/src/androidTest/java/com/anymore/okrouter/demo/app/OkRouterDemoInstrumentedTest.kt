package com.anymore.okrouter.demo.app

import android.app.Instrumentation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.demo.biz1.Biz1Activity
import com.anymore.okrouter.demo.biz1.R as Biz1R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OkRouterDemoInstrumentedTest {

    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun mainPageShouldInitializeRouterAndRenderViewRoute() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            assertTrue("应用启动时 OkRouter 应已初始化", OkRouter.isInitialized())
            val container = activity.findViewById<FrameLayout>(R.id.container)
            assertEquals(1, container.childCount)
            val routedView = container.getChildAt(0) as TextView
            assertEquals("自定义TextView", routedView.text.toString())
        }

        scenario.close()
    }

    @Test
    fun mainPageButtonShouldOpenBiz1Activity() {
        val monitor = instrumentation.addMonitor(Biz1Activity::class.java.name, null, false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            activity.findViewById<android.view.View>(R.id.btn1).performClick()
        }

        val launched = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull("主页面按钮应启动 Biz1Activity", launched)
        assertEquals("这是Biz1页面", launched?.findViewById<TextView>(Biz1R.id.tv_1)?.text)
        launched?.finish()
        instrumentation.removeMonitor(monitor)
        scenario.close()
    }

    @Test
    fun missingRouteShouldReachApplicationNotFoundPage() {
        val missingUri = "okrouter://android/instrumented_missing_route"
        val monitor = instrumentation.addMonitor(RouterNotFoundActivity::class.java.name, null, false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            val response = OkRouter.build(missingUri).start(activity)
            assertEquals("NotFound", response.routerResult.value)
        }

        val launched = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull("未命中路由应进入 RouterNotFoundActivity", launched)
        val hint = launched?.findViewById<TextView>(R.id.tv_hint)?.text?.toString().orEmpty()
        assertTrue(hint.contains(missingUri))
        launched?.finish()
        instrumentation.removeMonitor(monitor)
        scenario.close()
    }
}
