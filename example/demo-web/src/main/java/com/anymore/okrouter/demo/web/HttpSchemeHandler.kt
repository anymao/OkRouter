package com.anymore.okrouter.demo.web

import android.content.Context
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.core.RouterHandler
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.demo.base.Common.host
import com.anymore.okrouter.demo.base.Common.scheme

/**
 * Created by anymore on 2023/6/25.
 */
@Router(scheme = "https?", host = ".*", path = ".*")
class HttpSchemeHandler : RouterHandler {
    override fun handle(context: Context, request: RouterRequest) {
        val url = request.uri
        OkRouter.build("$scheme://$host/web")
            .putString("url", url)
            .start(context)
    }
}