package com.anymore.okrouter.core

import android.content.Context
import com.anymore.okrouter.core.Extend.OKROUTER_NOTE

/**
 * @see com.anymore.okrouter.annotation.Interceptor
 * Created by anymore on 2023/6/5.
 */
interface RouterInterceptor {

    companion object {
        @JvmStatic
        fun <T : RouterInterceptor> T.interceptRequest(request: RouterRequest): RouterResponse {
            return RouterResponse.Builder()
                .uri(request.uri)
                .routerType(request.routerType)
                .routerResult(RouterResult.Intercepted)
                .header(
                    OKROUTER_NOTE,
                    "the router request was intercepted by ${this::class.simpleName}"
                )
                .build()
        }
    }

    fun intercept(context: Context, chain: Chain): RouterResponse

    interface Chain {

        fun request(): RouterRequest

        fun proceed(context: Context, request: RouterRequest): RouterResponse
    }
}