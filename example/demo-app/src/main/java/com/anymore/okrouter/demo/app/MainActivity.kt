package com.anymore.okrouter.demo.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.core.RouterType
import com.anymore.okrouter.demo.base.Common.host
import com.anymore.okrouter.demo.base.Common.scheme
import com.anymore.okrouter.demo.base.Common.toRoute

/**
 * Created by anymore on 2023/6/25.
 */
@Router(scheme = scheme, host = host, path = "/main", desc = "应用主界面")
class MainActivity : AppCompatActivity() {

    val launcher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val resultCode = it.resultCode
        val data = it.data?.getStringExtra("data")
        Log.d("MainActivity", "resultCode: $resultCode,data: $data")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btn1).setOnClickListener {
            OkRouter.build("/biz1".toRoute()).start(this, launcher)
        }
        val container = findViewById<FrameLayout>(R.id.container)
        val response = OkRouter.build("/custom_text_view".toRoute()).start(this)
        if (response.routerType == RouterType.VIEW) {
            val view = response.target as? View
            if (view != null) {
                container.addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
        }
    }
}