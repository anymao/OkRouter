package com.anymore.okrouter.core.internal

import android.content.Context
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.core.RouterChain
import com.anymore.okrouter.core.RouterContext
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterInterceptorV2
import com.anymore.okrouter.core.RouterOutcome
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.RouterResult

/**
 * 同时执行新旧拦截器的内部链路。旧接口通过适配器接入，但仍保留其传递 Context 和请求的语义。
 */
internal class RouterExecutionChain(
    override val context: RouterContext,
    private val interceptors: List<RouterInterceptor>,
    private val index: Int = 0
) : RouterChain {

    override fun proceed(): RouterOutcome {
        check(interceptors.isNotEmpty()) {
            "interceptors should not empty"
        }
        check(index in interceptors.indices) {
            "the index($index) should in interceptors.indices(${interceptors.indices})"
        }
        val interceptor = interceptors[index]
        val next = RouterExecutionChain(context, interceptors, index + 1)
        logger.d("call ${interceptor::class.simpleName} for uri:${context.request.uri}")
        return if (interceptor is RouterInterceptorV2) {
            interceptor.intercept(next)
        } else {
            val legacyChain = LegacyChainAdapter(context, next)
            val legacyResponse = interceptor.intercept(context.appContext, legacyChain)
            legacyChain.redirectOutcome?.let { return it }
            RouterOutcomeMapper.fromLegacyResponse(
                legacyResponse,
                context
            )
        }
    }

    private fun withContext(context: RouterContext): RouterExecutionChain =
        RouterExecutionChain(context, interceptors, index)

    fun toResponse(outcome: RouterOutcome): RouterResponse = outcome.toResponse(context)

    private class LegacyChainAdapter(
        private val context: RouterContext,
        private val next: RouterExecutionChain
    ) : RouterInterceptor.Chain {

        var redirectOutcome: RouterOutcome.Redirect? = null
            private set

        override fun request(): RouterRequest = context.request

        override fun proceed(context: Context, request: RouterRequest): RouterResponse {
            val nextContext = RouterContext(
                context,
                request,
                this.context.destination,
                this.context.options,
                this.context.executionScope
            )
            val nextChain = next.withContext(nextContext)
            val outcome = nextChain.proceed()
            if (outcome is RouterOutcome.Redirect) {
                redirectOutcome = outcome
                return RouterResponse.Builder()
                    .uri(request.uri)
                    .routerType(nextContext.destination.type)
                    .routerResult(RouterResult.Ok)
                    .build()
            }
            return nextChain.toResponse(outcome)
        }
    }
}
