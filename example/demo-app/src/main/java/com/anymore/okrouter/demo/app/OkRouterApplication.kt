package com.anymore.okrouter.demo.app

import android.app.Application
import android.content.Context
import com.anymore.auto.ServiceLoader
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.core.RouterLostHandler
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.demo.base.Common
import com.anymore.okrouter.demo.base.Common.toRoute
import com.anymore.okrouter.demo.login.LoginCallback
import com.anymore.okrouter.demo.login.LoginManager

class OkRouterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        OkRouter.routerLostHandler = object : RouterLostHandler {
            override fun handle(context: Context, request: RouterRequest) {
                val uri = request.uri
                OkRouter.build("/404".toRoute()).putString(Common.EXTRA_LOST_URI, uri)
                    .start(context)
            }
        }
        OkRouter.init(this)
        ServiceLoader.load(LoginCallback::class.java).forEach {
            LoginManager.register(it)
        }
    }
}