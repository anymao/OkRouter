package com.anymore.okrouter.annotation

import androidx.annotation.Keep
import com.anymore.okrouter.core.RouterInterceptor
import kotlin.reflect.KClass

/**
 * Created by anymore on 2023/6/5.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Keep
annotation class Router(
    val scheme: String = "",
    val host: String = "",
    val path: String,
    /**
     * 声明此路由需要经历的非全局拦截器，注意全局拦截器和非全局拦截器是放在一起排序的
     */
    val interceptors: Array<KClass<out RouterInterceptor>> = [],
    /**
     * 如果无法获取到拦截器的Class对象，则可以通过拦截器别名来声明
     */
    val interceptorsByAlias: Array<String> = [],
    /**
     * 路由的优先级，如果路由的 scheme/host/path使用了正则，那么推荐设置priority
     * 这样会提高正则路由的优先级，如果一个路由
     * val uri = "app://host/test2"
     *
     * 而应用中存在两个路由
     * @Router(scheme=".*",host="host",path="test2")
     * @Router(scheme=".*",host="host",path="test.*")
     *
     * 那么此uri理论上会命中两个路由，但是我们只选取靠前的那个，priority越小越靠前
     *
     */
    val priority: Int = 0,
    /**
     * 页面描述，用于生成路由表
     */
    val desc: String = ""
)