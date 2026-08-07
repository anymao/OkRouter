package com.anymore.okrouter.core.internal

import android.os.Bundle
import com.anymore.okrouter.core.RouterDestination
import com.anymore.okrouter.core.RouterMatch
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.warehouse.WareHouse

/**
 * 将路由请求解析为公开匹配结果，不创建目标实例，也不触发拦截器或跳转。
 */
internal object RouterResolver {

    fun resolve(request: RouterRequest): RouterMatch =
        resolveInternal(request)?.publicMatch ?: RouterMatch.NotFound

    fun resolveInternal(request: RouterRequest): ResolvedRoute? {
        val meta = WareHouse.getMatchRouterMeta(request.uri) ?: return null
        val destination = RouterDestination(meta.uri.toString(), meta.routerType, meta.description)
        return ResolvedRoute(RouterMatch.Found(destination, request, Bundle(request.extras)), meta)
    }
}
