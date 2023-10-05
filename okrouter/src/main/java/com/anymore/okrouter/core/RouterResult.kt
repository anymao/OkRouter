package com.anymore.okrouter.core

/**
 * Created by anymore on 2023/6/5.
 */
sealed class RouterResult(val value: String) {
    /**
     * 路由成功
     */
    object Ok : RouterResult("Ok")

    /**
     * 路由没找到
     */
    object NotFound : RouterResult("NotFound")

    /**
     * 路由被拦截
     */
    object Intercepted : RouterResult("Intercepted")

    /**
     * 自定义路由结果
     */
    class Custom(value: String) : RouterResult(value)
}