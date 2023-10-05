package com.anymore.okrouter.demo.login

import android.content.Context
import com.anymore.auto.AutoService
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.core.RouterRequest

@AutoService(value = [LoginCallback::class], priority = Int.MIN_VALUE, singleton = true)
class OkRouterResumeLoginCallback : LoginCallback {

    companion object {
        internal var suspendRequest: RouterRequest? = null
    }

    override fun success(context: Context) {
        val request = suspendRequest
        if (request != null){
            OkRouter.start(request,context)
        }
        suspendRequest = null
    }

    override fun failure(context: Context, message: String) {

    }
}