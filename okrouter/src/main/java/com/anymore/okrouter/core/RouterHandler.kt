package com.anymore.okrouter.core

import android.content.Context

/**
 * Created by anymore on 2023/6/5.
 */
interface RouterHandler {
    fun handle(context: Context,request: RouterRequest)
}