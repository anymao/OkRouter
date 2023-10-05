package com.anymore.okrouter.warehouse

import com.anymore.okrouter.core.RouterInterceptor

/**
 * Created by anymore on 2023/6/7.
 */
abstract class RouterInterceptorFactory(private val singleton: Boolean = false) {

    @Volatile
    private var instance: RouterInterceptor? = null

    abstract fun newInstance(): RouterInterceptor

    fun create(): RouterInterceptor {
        return if (singleton) {
            instance ?: newInstance().also { instance = it }
        } else {
            newInstance()
        }
    }
}