package com.anymore.okrouter.demo.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common

/**
 * Created by anymore on 2024/2/21.
 */
@Router(scheme = Common.scheme, host = Common.host, path = "/404", desc = "没有找到匹配路由的落地页面")
class RouterNotFoundActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_router_not_found)
        val uri = intent?.getStringExtra(Common.EXTRA_LOST_URI)
        findViewById<TextView>(R.id.tv_hint).text = "路由<$uri>未找到，请检查路由是否正确，或者引导升级至最新版本"
        findViewById<View>(R.id.btn1).setOnClickListener {
            finish()
        }
    }
}