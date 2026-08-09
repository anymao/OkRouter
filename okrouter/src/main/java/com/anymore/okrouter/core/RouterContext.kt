package com.anymore.okrouter.core

import android.content.Context
import com.anymore.okrouter.core.internal.RouterExecutionScope

class RouterContext internal constructor(
    val appContext: Context,
    val request: RouterRequest,
    val destination: RouterDestination,
    val options: RouterOptions,
    internal val executionScope: RouterExecutionScope = RouterExecutionScope()
)
