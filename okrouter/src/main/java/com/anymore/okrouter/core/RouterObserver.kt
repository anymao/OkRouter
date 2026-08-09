package com.anymore.okrouter.core

/**
 * 路由解析与执行完成的观察接口。
 *
 * 观察者仅用于日志、指标等旁路能力；其异常会被路由框架隔离，不会影响业务路由结果。
 */
interface RouterObserver {

    fun onResolved(match: RouterMatch) = Unit

    fun onFinished(context: RouterContext?, outcome: RouterOutcome) = Unit
}
