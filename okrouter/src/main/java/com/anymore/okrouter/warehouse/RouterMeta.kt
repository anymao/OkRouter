package com.anymore.okrouter.warehouse

import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.core.RouterType

/**
 * 路由表元信息
 * Created by anymore on 2023/6/6.
 */
class RouterMeta(
    val uri: RouterUri,
    val routerType: RouterType,
    val clazzName: String,
    val clazz: Class<*>,
    val interceptors: Array<Class<out RouterInterceptor>>,
    val factory: ContextFactory? = null
)