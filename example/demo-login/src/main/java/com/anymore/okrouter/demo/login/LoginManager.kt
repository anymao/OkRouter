package com.anymore.okrouter.demo.login

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

object LoginManager {

    var username: String? = null

    var isLogin: Boolean = false

    private val callbacks = CopyOnWriteArrayList<LoginCallback>()

    fun register(callback: LoginCallback) {
        callbacks.add(callback)
    }



    internal fun doLogin(context: Context, username: String, password: String): Boolean {
        if (username.contentEquals("admin") && password.contentEquals("12345")) {
            this.username = username
            isLogin = true
            callbacks.forEach { it.success(context) }
        } else {
            this.username = null
            isLogin = false
            callbacks.forEach {
                it.failure(context, "密码错误")
            }
        }
        return isLogin
    }
}