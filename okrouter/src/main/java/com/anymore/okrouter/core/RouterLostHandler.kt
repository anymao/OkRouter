package com.anymore.okrouter.core

import android.content.Context

/**
 * 当目标路由没有找到时候的处理，可以设置自定义的RouterLostHandler，完成未找到该路由时候的逻辑
 */
interface RouterLostHandler : RouterHandler {
    override fun handle(context: Context,request: RouterRequest){}
}