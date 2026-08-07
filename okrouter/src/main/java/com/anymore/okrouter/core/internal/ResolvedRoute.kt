package com.anymore.okrouter.core.internal

import com.anymore.okrouter.core.RouterMatch
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.warehouse.RouterMeta

/**
 * 解析阶段的内部结果，同时保留公开匹配信息和后续执行所需的路由元数据。
 */
internal data class ResolvedRoute(
    val publicMatch: RouterMatch.Found,
    val request: RouterRequest,
    val meta: RouterMeta
)
