package com.anymore.okrouter.core.internal

import android.content.Context
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.core.RouterResponse

/**
 * Created by anymore on 2023/6/6.
 */
internal class RealRouterChain(
    private val request: RouterRequest,
    private val interceptors: List<RouterInterceptor>,
    private val index: Int
) : RouterInterceptor.Chain {

    override fun request(): RouterRequest = request

    override fun proceed(context: Context, request: RouterRequest): RouterResponse {
        check(interceptors.isNotEmpty()) {
            "interceptors should not empty"
        }
        check(index in interceptors.indices) {
            "the index($index) should in interceptors.indices(${interceptors.indices})"
        }
        val interceptor = interceptors[index]
        val next = RealRouterChain(request, interceptors, index + 1)
        logger.d("call ${interceptor::class.simpleName} for uri:${request.uri}")
        return interceptor.intercept(context, next)
    }
}