package com.anymore.okrouter.core.internal

import com.anymore.okrouter.core.RouterContext
import com.anymore.okrouter.core.RouterOutcome
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.RouterResult
import java.util.IdentityHashMap

/**
 * 在旧响应模型与新执行结果模型之间转换。
 */
internal object RouterOutcomeMapper {

    private val legacyResponses = ThreadLocal.withInitial { IdentityHashMap<RouterOutcome, RouterResponse>() }

    fun fromLegacyResponse(response: RouterResponse): RouterOutcome {
        val outcome = when (val result = response.routerResult) {
            RouterResult.Ok -> RouterOutcome.Completed(response.target)
            RouterResult.Intercepted -> RouterOutcome.Intercepted()
            is RouterResult.Failed -> RouterOutcome.Failed(result.cause)
            else -> RouterOutcome.Failed(
                IllegalStateException("旧拦截器返回了无法映射的路由结果：${result.value}")
            )
        }
        legacyResponseMap()[outcome] = response
        return outcome
    }

    fun toResponse(outcome: RouterOutcome, context: RouterContext): RouterResponse =
        legacyResponseMap().remove(outcome) ?: outcome.toNewResponse(context)

    private fun legacyResponseMap(): IdentityHashMap<RouterOutcome, RouterResponse> =
        legacyResponses.get() ?: IdentityHashMap<RouterOutcome, RouterResponse>().also {
            legacyResponses.set(it)
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
