package com.anymore.okrouter.demo.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common.host
import com.anymore.okrouter.demo.base.Common.scheme
import com.anymore.okrouter.demo.base.Common.toRoute

/**
 * Created by anymore on 2023/6/25.
 */
@Router(scheme = scheme, host = host, path = "/main", desc = "应用主界面")
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btn1).setOnClickListener {
            OkRouter.build("/biz1".toRoute()).start(this)
        }
    }
}