package com.anymore.okrouter.core

import android.content.Context

class RouterContext internal constructor(
    val appContext: Context,
    val request: RouterRequest,
    val destination: RouterDestination,
    val options: RouterOptions
)
