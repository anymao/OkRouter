package com.anymore.okrouter.warehouse

import com.anymore.okrouter.core.RouterInterceptor

/**
 * 反射实例化拦截器，如果没有使用@Interceptor注解，则会使用反射生成拦截器，且此拦截器非单例
 * @see com.anymore.okrouter.annotation.Interceptor
 * Created by anymore on 2023/6/8.
 */
class ReflectRouterInterceptorFactory(private val clazz: Class<*>) :
    RouterInterceptorFactory(singleton = false) {

    @Suppress("UNCHECKED_CAST")
    override fun newInstance(): RouterInterceptor {
        val instance = clazz.newInstance()
        check(instance != null && instance is RouterInterceptor) {
            "${clazz.canonicalName} is not RouterInterceptor"
        }
        return instance
    }
}