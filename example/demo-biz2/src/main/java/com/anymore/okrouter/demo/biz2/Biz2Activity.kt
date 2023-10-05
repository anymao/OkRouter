package com.anymore.okrouter.demo.biz2

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common
import com.anymore.okrouter.demo.base.Common.LOGIN_CHECKER
import com.anymore.okrouter.demo.base.Common.toRoute

/**
 * Created by anymore on 2023/7/30.
 */
@Router(
    scheme = Common.scheme,
    host = Common.host,
    path = "/biz2",
    interceptors = [Biz2Interceptor::class],
    interceptorsByAlias = [LOGIN_CHECKER],
    desc = "Biz2主页面"
)
class Biz2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biz2)
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
            OkRouter.build("/web".toRoute())
                .putString("url", "https://www.jd.com/")
                .start(this)
        }
        findViewById<View>(R.id.btn_4).setOnClickListener {
            OkRouter.build("https://www.baidu.com/")
                .start(this)
        }
    }
}