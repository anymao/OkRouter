package com.anymore.okrouter.demo.biz2

import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common.host
import com.anymore.okrouter.demo.base.Common.scheme
import com.anymore.okrouter.demo.base.toast

/**
 * Created by anymore on 2023/7/30.
 */
@Router(scheme = scheme, host = host, path = "/biz2/biz2Service")
class Biz2Service : IntentService("biz2") {

    override fun onHandleIntent(intent: Intent?) {
        val args = intent?.getStringExtra("biz2Args")
        Log.d("Biz2Service", args.orEmpty())
        toast(args ?: "传入参数为空")
        Log.d("Biz2Service", "thread:${Thread.currentThread().name}")
    }

}