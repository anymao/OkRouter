package com.anymore.okrouter.core.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.fragment.app.Fragment
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.core.*
import com.anymore.okrouter.core.Extend.OKROUTER_NOTE
import com.anymore.okrouter.warehouse.RouterMeta

/**
 * 最终的执行拦截器，走到这里的路由会进行最终的分发
 * Created by anymore on 2023/6/6.
 */
@Suppress("UNCHECKED_CAST")
internal class LaunchInterceptor(private val meta: RouterMeta) : RouterInterceptorV2 {

    override fun intercept(chain: RouterChain): RouterOutcome = execute(chain.context)

    @Deprecated("由 RouterInterceptorV2.intercept(RouterChain) 替代")
    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
        val routerContext = RouterContext(
            context,
            chain.request(),
            RouterDestination(meta.uri.toString(), meta.routerType, meta.description),
            RouterOptions.DEFAULT
        )
        return execute(routerContext).toResponse(routerContext)
    }

    private fun execute(context: RouterContext): RouterOutcome {
        return try {
            when (meta.routerType) {
                RouterType.ACTIVITY -> RouterOutcomeMapper.fromLegacyResponse(
                    startActivity(context.appContext, context.request, meta),
                    context
                )
                RouterType.FRAGMENT -> RouterOutcomeMapper.fromLegacyResponse(
                    startFragment(context.appContext, context.request, meta),
                    context
                )
                RouterType.VIEW -> RouterOutcomeMapper.fromLegacyResponse(
                    startView(context.appContext, context.request, meta),
                    context
                )
                RouterType.SERVICE -> RouterOutcomeMapper.fromLegacyResponse(
                    startService(context.appContext, context.request, meta),
                    context
                )
                RouterType.HANDLER -> startHandler(context, meta)
                RouterType.UNDEFINED -> {
                    OkRouter.logger.e("LaunchInterceptor: routerType is UNDEFINED for ${meta.uri}")
                    RouterOutcome.Failed(IllegalStateException("routerType 不能为 UNDEFINED"))
                }
            }
        } catch (e: Exception) {
            OkRouter.logger.e("LaunchInterceptor: 目标启动失败 uri=${context.request.uri}, type=${meta.routerType}", e)
            RouterOutcome.Failed(e)
        }
    }

    private fun startHandler(
        context: RouterContext,
        meta: RouterMeta
    ): RouterOutcome {
        val factory = meta.factory
        var target: RouterHandler?
        target = factory?.create(context.appContext) as? RouterHandler
        if (target == null) {
            val clazz = meta.clazz as? Class<RouterHandler>
            target = clazz?.newInstance()
        }
        checkNotNull(target) {
            "target should not null!"
        }
        return if (target is RouterHandlerV2) {
            target.handle(context)
        } else {
            target.handle(context.appContext, context.request)
            RouterOutcomeMapper.fromLegacyResponse(
                RouterResponse.Builder()
                    .uri(context.request.uri)
                    .routerType(meta.routerType)
                    .routerResult(RouterResult.Ok)
                    .header(OKROUTER_NOTE, "OK")
                    .build(),
                context
            )
        }
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
        val launcher = request.launcher
        if (launcher != null) {
            launcher.launch(intent)
        } else if (request.requestCode > 0 && context is Activity) {
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
