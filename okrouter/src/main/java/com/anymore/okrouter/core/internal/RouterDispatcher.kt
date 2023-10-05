package com.anymore.okrouter.core.internal

import android.content.Context
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.OkRouter.routerLostHandler
import com.anymore.okrouter.core.*
import com.anymore.okrouter.warehouse.RouterMeta
import com.anymore.okrouter.warehouse.WareHouse
import java.util.*

/**
 * Created by anymore on 2023/6/5.
 */
internal object RouterDispatcher {

    fun start(context: Context, request: RouterRequest): RouterResponse {
        logger.v("start for $request")
        val meta = getRouteMeta(request)
            ?: return kotlin.run {
                logger.d("router[${request.uri}] match no target")
                logger.d("not found uri will be handled by ${routerLostHandler.javaClass.name}")
                routerLostHandler.handle(context,request)
                RouterResponse.Builder()
                    .uri(request.uri)
                    .routerType(request.routerType)
                    .routerResult(RouterResult.NotFound)
                    .build()
            }
        val ics = mutableSetOf<Class<out RouterInterceptor>>()
        //加载全局拦截器实例
        WareHouse.globalInterceptors.forEach {
            ics += it
        }
        //加载非全局拦截器
        meta.interceptors.forEach {
            ics += it
        }
        val interceptors = mutableListOf<RouterInterceptor>()
        ics.forEach {
            val instance = WareHouse.getInterceptorInstance(it)
            if (instance != null) {
                interceptors += instance
            }
        }
        interceptors.sortWith(PriorityRouterInterceptorComparator)
        //调用拦截器放在最后执行
        interceptors += LaunchInterceptor(meta)

        val chain = RealRouterChain(request, Collections.unmodifiableList(interceptors), 0)
        return chain.proceed(context, request)
    }

    private fun getRouteMeta(request: RouterRequest): RouterMeta? {
        return WareHouse.getMatchRouterMeta(request.uri)
    }
}