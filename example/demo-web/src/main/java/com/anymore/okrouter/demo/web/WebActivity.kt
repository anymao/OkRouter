package com.anymore.okrouter.demo.web

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common.host
import com.anymore.okrouter.demo.base.Common.scheme
import com.just.agentweb.AgentWeb


@Router(scheme = scheme, host = host, path = "/web", desc = "通用web容器")
class WebActivity: AppCompatActivity() {

    private lateinit var agentWeb: AgentWeb
    private lateinit var webContainer:FrameLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        val url = intent?.getStringExtra("url")
        if (url.isNullOrEmpty()){
            finish()
            return
        }
        webContainer = findViewById(R.id.web_parent)
        agentWeb = AgentWeb.with(this)
            .setAgentWebParent(webContainer, FrameLayout.LayoutParams(-1, -1))
            .useDefaultIndicator()
            .createAgentWeb()
            .ready()
            .go(url)
    }

    override fun onPause() {
        super.onPause()
        agentWeb.webLifeCycle.onPause()
    }

    override fun onResume() {
        super.onResume()
        agentWeb.webLifeCycle.onResume()
    }

    override fun onDestroy() {
        agentWeb.webLifeCycle.onDestroy()
        super.onDestroy()
    }

}