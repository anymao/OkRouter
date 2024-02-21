package com.anymore.okrouter.demo.biz1

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common
import com.anymore.okrouter.demo.base.Common.toRoute

/**
 * Created by anymore on 2023/7/30.
 */
@Router(scheme = Common.scheme, host = Common.host, path = "/biz1", desc = "Biz1主页面")
class Biz1Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biz1)
        findViewById<View>(R.id.btn_1).setOnClickListener {
            OkRouter.build("/biz2".toRoute())
                .start(this)
        }
        findViewById<View>(R.id.btn_2).setOnClickListener {
            OkRouter.build("/biz2/biz2Service".toRoute())
                .putString("biz2Args", "hello OkRouter!")
                .start(this)
        }
        findViewById<View>(R.id.btn_3).setOnClickListener {
            OkRouter.build("https://music.163.com/")
                .putBoolean(Common.EXTRA_CHECK_LOGIN, true)
                .start(this)
        }
        findViewById<View>(R.id.btn_4).setOnClickListener {
            OkRouter.build("/not_existed_route".toRoute())
                .putBoolean(Common.EXTRA_CHECK_LOGIN, true)
                .start(this)
        }
    }
}