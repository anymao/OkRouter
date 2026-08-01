package com.anymore.okrouter

import android.app.Application
import android.content.Context
import com.anymore.okrouter.core.Logger
import com.anymore.okrouter.core.RouterLostHandler
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.internal.RouterDispatcher
import com.anymore.okrouter.warehouse.OkRouterLoader

/**
 * Created by anymore on 2023/6/5.
 */
object OkRouter {

    internal const val TAG = "OkRouter"

    /**
     * 应用上下文，仅在 [init] 成功调用后非空。
     * 未初始化时 [start] 会抛出 [IllegalStateException] 而非崩溃于属性未初始化。
     */
    @Volatile
    internal var application: Application? = null
        private set

    @Volatile
    private var initialized = false

    /**
     * 当没有找到目标路由时候，会执行此Handler，可以在这里进行埋点，或者重定向
     */
    @JvmStatic
    var routerLostHandler: RouterLostHandler = object : RouterLostHandler {}

    @JvmStatic
    var logger: Logger = Logger.Default

    @JvmStatic
    fun init(context: Context) {
        //幂等：重复调用直接跳过，避免重复加载路由表
        if (initialized) {
            logger.w("OkRouter 已经初始化，跳过重复 init() 调用")
            return
        }
        //先加载路由表，成功后才赋值 application，避免 load 失败时处于半初始化态
        //（application 非空但路由表未加载，start() 会误判为已初始化）
        OkRouterLoader.load()
        application = context.applicationContext as Application
        initialized = true
    }

    @JvmStatic
    fun isInitialized(): Boolean = initialized

    @JvmStatic
    fun build(uri: String) = RouterRequest.Builder().uri(uri)

    @JvmOverloads
    @JvmStatic
    fun start(request: RouterRequest, context: Context? = null): RouterResponse {
        val ctx = context ?: application
            ?: throw IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")
        return RouterDispatcher.start(ctx, request)
    }

}