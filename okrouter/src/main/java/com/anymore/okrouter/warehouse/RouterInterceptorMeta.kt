package com.anymore.okrouter.warehouse

import com.anymore.okrouter.core.RouterInterceptor

/**
 *  拦截器元信息
 */
class RouterInterceptorMeta(
    val clazz: Class<out RouterInterceptor>,
    val className: String,
    val alias: String,
    val factory: RouterInterceptorFactory,
    val priority: Int,
    val global: Boolean,
    val singleton: Boolean
)