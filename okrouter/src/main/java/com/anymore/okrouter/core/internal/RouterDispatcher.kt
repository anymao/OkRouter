package com.anymore.okrouter.core.internal

import android.content.Context
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.OkRouter.routerLostHandler
import com.anymore.okrouter.core.*
import com.anymore.okrouter.warehouse.WareHouse
import java.util.*

/**
 * Created by anymore on 2023/6/5.
 */
internal object RouterDispatcher {

    fun start(context: Context, request: RouterRequest): RouterResponse {
        logger.v("start for $request")
        val resolved = RouterResolver.resolveInternal(request)
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
        resolved.meta.interceptors.forEach {
            ics += it
        }
        val interceptors = mutableListOf<RouterInterceptor>()
        ics.forEach {
            val instance = try {
                WareHouse.getInterceptorInstance(it)
            } catch (e: Exception) {
                OkRouter.logger.e("RouterDispatcher: 拦截器实例化失败 ${it.name}", e)
                null
            }
            if (instance != null) {
                interceptors += instance
            }
        }
        interceptors.sortWith(PriorityRouterInterceptorComparator)
        //调用拦截器放在最后执行
        interceptors += LaunchInterceptor(resolved.meta)

        val routerContext = RouterContext(
            context,
            resolved.request,
            resolved.publicMatch.destination,
            RouterOptions.DEFAULT
        )
        val chain = RouterExecutionChain(routerContext, Collections.unmodifiableList(interceptors))
        return chain.proceed().toResponse(routerContext)
    }
}
