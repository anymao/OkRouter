package com.anymore.okrouter.annotation

import androidx.annotation.Keep

/**
 * Created by anymore on 2023/6/6.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Keep
annotation class Interceptor(
    /**
     * 拦截器别名，如果使用方没有依赖到此类，可通过alias设置拦截器
     * 需要保证全局唯一
     */
    val alias: String = "",
    /**
     * 多个拦截器之间的优先级，从小到大排列
     * <b>如果两个拦截器的优先级相同，则认为他们谁先谁后执行，对结果不产生影响</b>
     */
    val priority: Int = 0,
    /**
     * 是否是全局拦截器，默认false
     */
    val global: Boolean = false,
    /**
     * 是否是单例，默认false，单例拦截器在全局是同一个对象
     */
    val singleton: Boolean = false,
    /**
     * 拦截器描述，用于生成路由表
     */
    val desc: String = ""
)
