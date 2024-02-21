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

    internal lateinit var application: Application

    /**
     * 当没有找到目标路由时候，会执行此Handler，可以在这里进行埋点，或者重定向
     */
    @JvmStatic
    var routerLostHandler: RouterLostHandler = object : RouterLostHandler {}

    @JvmStatic
    var logger: Logger = Logger.Default

    @JvmStatic
    fun init(context: Context) {
        application = context.applicationContext as Application
        //加载路由表
        OkRouterLoader.load()
    }


    @JvmStatic
    fun build(uri: String) = RouterRequest.Builder().uri(uri)

    @JvmOverloads
    @JvmStatic
    fun start(request: RouterRequest, context: Context = application): RouterResponse {
        return RouterDispatcher.start(context, request)
    }

}