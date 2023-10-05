package com.anymore.okrouter.demo.login

import android.content.Context

interface LoginCallback {
    fun success(context: Context)
    fun failure(context: Context, message: String)
}