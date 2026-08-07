package com.anymore.okrouter.core

/**
 * 新版拦截器使用的执行链。
 */
interface RouterChain {
    val context: RouterContext

    fun proceed(): RouterOutcome
}
