package com.anymore.okrouter.demo.login

import android.content.Context
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Interceptor
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterInterceptor.Companion.interceptRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.demo.base.Common
import com.anymore.okrouter.demo.base.Common.toRoute

@Interceptor(
    alias = Common.LOGIN_CHECKER,
    singleton = true,
    desc = "局部登录拦截器，通过注解设置到路由目标上面",
    priority = Int.MIN_VALUE
)
class LoginInterceptor : RouterInterceptor {

    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
        val request = chain.request()
        return if (LoginManager.isLogin) {
            chain.proceed(context, request)
        } else {
            OkRouterResumeLoginCallback.suspendRequest = request.newBuilder().build()
            interceptRequest(request)
            OkRouter.build("/login".toRoute()).start(context)
        }
    }

}