package com.anymore.okrouter.core.internal

import android.net.Uri
import com.anymore.okrouter.core.RouterDestination
import com.anymore.okrouter.core.RouterMatch
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.warehouse.WareHouse
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 将路由请求解析为公开匹配结果，不创建目标实例，也不触发拦截器或跳转。
 */
internal object RouterResolver {

    fun resolve(request: RouterRequest): RouterMatch =
        resolveInternal(request)?.publicMatch ?: RouterMatch.NotFound

    fun resolveInternal(request: RouterRequest): ResolvedRoute? {
        val meta = WareHouse.findRouterMeta(request.uri) ?: return null
        val destination = RouterDestination(meta.uri.toString(), meta.routerType, meta.description)
        val match = RouterMatch.Found(destination, queryParameters(request.uri))
        return ResolvedRoute(match, request, meta)
    }

    private fun queryParameters(uri: String): Map<String, Any?> {
        val parsedUri = Uri.parse(uri)
        val parameters = LinkedHashMap<String, Any?>()
        parsedUri.queryParameterNames.orEmpty().forEach { key ->
            val values = parsedUri.getQueryParameters(key)
            if (values.size == 1) {
                parameters[key] = values.first()
            } else if (values.isNotEmpty()) {
                parameters[key] = Collections.unmodifiableList(ArrayList(values))
            }
        }
        return Collections.unmodifiableMap(parameters)
    }
}
