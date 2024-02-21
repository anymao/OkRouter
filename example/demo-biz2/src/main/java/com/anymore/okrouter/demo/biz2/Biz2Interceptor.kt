package com.anymore.okrouter.demo.biz2

import android.content.Context
import com.anymore.okrouter.annotation.Interceptor
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.demo.base.toast

/**
 * Created by anymore on 2023/10/5.
 */
@Interceptor(desc = "Biz2Interceptor 测试")
class Biz2Interceptor : RouterInterceptor {
    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
        context.toast("欢迎来到Biz2页面")
        return chain.proceed(context, chain.request())
    }
}