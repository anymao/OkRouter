package com.anymore.okrouter.core.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.fragment.app.Fragment
import com.anymore.okrouter.core.*
import com.anymore.okrouter.core.Extend.OKROUTER_NOTE
import com.anymore.okrouter.warehouse.RouterMeta

/**
 * 最终的执行拦截器，走到这里的路由会进行最终的分发
 * Created by anymore on 2023/6/6.
 */
@Suppress("UNCHECKED_CAST")
internal class LaunchInterceptor(private val meta: RouterMeta) : RouterInterceptor {

    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
        return when (meta.routerType) {
            RouterType.ACTIVITY -> startActivity(context, chain.request(), meta)
            RouterType.FRAGMENT -> startFragment(context, chain.request(), meta)
            RouterType.VIEW -> startView(context, chain.request(), meta)
            RouterType.SERVICE -> startService(context, chain.request(), meta)
            RouterType.HANDLER -> startHandler(context, chain.request(), meta)
            RouterType.UNDEFINED -> throw IllegalStateException("meta.routerType could not be RouterType.UNDEFINED")
        }
    }

    private fun startHandler(
        context: Context,
        request: RouterRequest,
        meta: RouterMeta
    ): RouterResponse {
        val factory = meta.factory
        var target: RouterHandler?
        target = factory?.create(context) as? RouterHandler
        if (target == null) {
            val clazz = meta.clazz as? Class<RouterHandler>
            target = clazz?.newInstance()
        }
        checkNotNull(target) {
            "target should not null!"
        }
        target.handle(context,request)
        return RouterResponse.Builder()
            .uri(request.uri)
            .routerType(meta.routerType)
            .routerResult(RouterResult.Ok)
            .header(OKROUTER_NOTE,"OK")
            .build()
    }

    private fun startService(
        context: Context,
        request: RouterRequest,
        meta: RouterMeta
    ): RouterResponse {
        val intent = Intent(context, meta.clazz)
        intent.putExtras(request.extras)
        context.startService(intent)
        return RouterResponse.Builder()
            .uri(request.uri)
            .routerType(meta.routerType)
            .routerResult(RouterResult.Ok)
            .header(OKROUTER_NOTE, "OK")
            .build()
    }

    private fun startView(
        context: Context,
        request: RouterRequest,
        meta: RouterMeta
    ): RouterResponse {
        val factory = meta.factory
        var target: View?
        target = factory?.create(context) as? View
        if (target == null) {
            val constructor = (meta.clazz as? Class<View>)?.getConstructor(Context::class.java)
            target = constructor?.newInstance(context)
        }
        checkNotNull(target) {
            "target should not null!"
        }
        val extras = request.extras
        target.setTag(Extend.OKROUTER_VIEW_EXTRAS, extras)
        return RouterResponse.Builder()
            .uri(request.uri)
            .routerType(meta.routerType)
            .routerResult(RouterResult.Ok)
            .header(OKROUTER_NOTE, "OK")
            .target(target)
            .build()
    }

    private fun startFragment(
        context: Context,
        request: RouterRequest,
        meta: RouterMeta
    ): RouterResponse {
        val factory = meta.factory
        var target: Fragment?
        target = factory?.create(context) as? Fragment
        if (target == null) {
            val clazz = meta.clazz as? Class<Fragment>
            target = clazz?.newInstance()
        }
        checkNotNull(target) {
            "target should not null!"
        }
        target.arguments = request.extras
        return RouterResponse.Builder()
            .uri(request.uri)
            .routerType(meta.routerType)
            .routerResult(RouterResult.Ok)
            .header(OKROUTER_NOTE, "OK")
            .target(target)
            .build()
    }

    private fun startActivity(
        context: Context,
        request: RouterRequest,
        meta: RouterMeta
    ): RouterResponse {
        val intent = Intent(context, meta.clazz)
        intent.putExtras(request.extras)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (request.requestCode > 0 && context is Activity) {
            context.startActivityForResult(intent, request.requestCode)
        } else {
            context.startActivity(intent)
        }
        return RouterResponse.Builder()
            .uri(request.uri)
            .routerType(meta.routerType)
            .routerResult(RouterResult.Ok)
            .header(OKROUTER_NOTE, "OK")
            .build()

    }
}