package com.anymore.okrouter.core

import android.content.Context

/**
 * 可返回结构化执行结果的新版本路由处理器。
 *
 * 继承旧接口，使现有注解处理和注册逻辑仍可识别该处理器。
 */
interface RouterHandlerV2 : RouterHandler {

    fun handle(context: RouterContext): RouterOutcome

    @Deprecated("由 RouterHandlerV2.handle(RouterContext) 替代")
    override fun handle(context: Context, request: RouterRequest) = Unit
}
