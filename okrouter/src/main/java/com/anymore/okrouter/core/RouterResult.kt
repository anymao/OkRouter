package com.anymore.okrouter.core

/**
 * 路由执行结果。
 *
 * **前向兼容提示**：本 sealed class 可能在未来版本中新增子类型。
 * 使用 `when` 表达式时请始终包含 `else` 分支（或 Kotlin 1.7+ 的非穷尽 `when`），
 * 以避免源码不兼容的编译错误。
 *
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
     * 执行失败（目标实例化异常、Service 启动被系统拒绝等）。
     * 调用方可从 [cause] 获取原始异常。
     */
    class Failed(val cause: Throwable) : RouterResult("Failed")

    /**
     * 请求参数非法（URI 格式错误、缺少必要参数等）。
     * 调用方可从 [reason] 获取具体原因。
     */
    class InvalidRequest(val reason: String) : RouterResult("InvalidRequest")

    /**
     * 自定义路由结果
     */
    class Custom(value: String) : RouterResult(value)
}