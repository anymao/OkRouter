package com.anymore.okrouter.demo.biz2

import android.content.Context
import android.util.Log
import com.anymore.okrouter.annotation.Interceptor
import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterResponse

/**
 * Created by anymore on 2023/10/5.
 */
@Interceptor(desc = "Biz2Interceptor 测试")
class Biz2Interceptor : RouterInterceptor {
    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
        Log.d("Biz2Interceptor", "test intercept")
        return chain.proceed(context, chain.request())
    }
}