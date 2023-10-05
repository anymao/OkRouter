package com.anymore.okrouter.demo.app

import android.app.Application
import com.anymore.auto.ServiceLoader
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.demo.login.LoginCallback
import com.anymore.okrouter.demo.login.LoginManager

class OkRouterApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        OkRouter.init(this)
        ServiceLoader.load(LoginCallback::class.java).forEach {
            LoginManager.register(it)
        }
    }
}