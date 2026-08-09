package com.anymore.okrouter.core.internal

import com.anymore.okrouter.core.RouterContext
import com.anymore.okrouter.core.RouterOutcome
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.RouterResult

/**
 * 在旧响应模型与新执行结果模型之间转换。
 */
internal object RouterOutcomeMapper {

    fun fromLegacyResponse(
        response: RouterResponse,
        context: RouterContext
    ): RouterOutcome {
        context.executionScope.recordLegacyResponse(response)
        return when (val result = response.routerResult) {
            RouterResult.Ok -> RouterOutcome.Completed(response.target)
            RouterResult.Intercepted -> RouterOutcome.Intercepted()
            is RouterResult.Failed -> RouterOutcome.Failed(result.cause)
            else -> RouterOutcome.Failed(
                IllegalStateException("旧拦截器返回了无法映射的路由结果：${result.value}")
            )
        }
    }

    fun toResponse(
        outcome: RouterOutcome,
        context: RouterContext
    ): RouterResponse = context.executionScope.legacyResponse ?: outcome.toNewResponse(context)
}

/**
 * 单次路由执行的兼容性状态。
 *
 * 旧拦截器可返回包含自定义结果、headers 和 target 的完整响应；新版 Outcome 无法表达全部
 * 旧字段。因此该状态跟随执行链显式传递，确保它穿过新版拦截器后仍可作为最终响应返回。
 */
internal class RouterExecutionScope {
    var legacyResponse: RouterResponse? = null
        private set

    fun recordLegacyResponse(response: RouterResponse) {
        legacyResponse = response
    }
}

internal fun RouterOutcome.toResponse(context: RouterContext): RouterResponse =
    RouterOutcomeMapper.toResponse(this, context)

private fun RouterOutcome.toNewResponse(context: RouterContext): RouterResponse = when (this) {
    is RouterOutcome.Completed -> RouterResponse.Builder()
        .uri(context.request.uri)
        .routerType(context.destination.type)
        .routerResult(RouterResult.Ok)
        .target(target)
        .build()
    is RouterOutcome.Intercepted -> RouterResponse.Builder()
        .uri(context.request.uri)
        .routerType(context.destination.type)
        .routerResult(RouterResult.Intercepted)
        .build()
    is RouterOutcome.Failed -> RouterResponse.Builder()
        .uri(context.request.uri)
        .routerType(context.destination.type)
        .routerResult(RouterResult.Failed(cause))
        .build()
    is RouterOutcome.Redirect -> error("Redirect 必须由 RouterDispatcher 处理")
}
