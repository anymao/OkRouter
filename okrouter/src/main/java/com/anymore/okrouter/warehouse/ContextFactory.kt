package com.anymore.okrouter.warehouse

import android.content.Context

/**
 * Created by anymore on 2023/6/7.
 */
interface ContextFactory {
    fun create(context:Context): Any?
}