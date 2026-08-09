package com.anymore.okrouter.core

import android.content.Context

/**
 * 可返回结构化执行结果的新版本路由拦截器。
 */
interface RouterInterceptorV2 : RouterInterceptor {

    fun intercept(chain: RouterChain): RouterOutcome

    @Deprecated("由 RouterInterceptorV2.intercept(RouterChain) 替代")
    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse =
        chain.proceed(context, chain.request())
}
