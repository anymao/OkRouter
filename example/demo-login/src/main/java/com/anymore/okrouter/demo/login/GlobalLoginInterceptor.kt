package com.anymore.okrouter.demo.login

import android.content.Context
import com.anymore.okrouter.annotation.Interceptor
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterInterceptor.Companion.interceptRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.demo.base.Common
import com.anymore.okrouter.ktx.getBooleanCompatibly

@Interceptor(global = true, singleton = true, desc = "全局登录拦截器，通过‘extra_check_login’来设置是否检查登录状态")
class GlobalLoginInterceptor : RouterInterceptor {

    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
        val request = chain.request()
        val needLogin = request.getBooleanCompatibly(Common.EXTRA_CHECK_LOGIN, false)
        return if (needLogin && !LoginManager.isLogin) {
            OkRouterResumeLoginCallback.suspendRequest = request.newBuilder().build()
            interceptRequest(request)
        } else {
            chain.proceed(context, request)
        }
    }
}