package com.anymore.okrouter.core.internal

import android.content.Context
import android.net.Uri
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.OkRouter.routerLostHandler
import com.anymore.okrouter.core.*
import com.anymore.okrouter.warehouse.WareHouse
import java.util.*

/**
 * Created by anymore on 2023/6/5.
 */
internal object RouterDispatcher {

    private const val MAX_REDIRECT_COUNT = 8

    fun start(context: Context, request: RouterRequest): RouterResponse {
        logger.v("start for $request")
        return execute(context, request, request.uri, LinkedHashSet(), false)
    }

    private fun execute(
        context: Context,
        request: RouterRequest,
        originalUri: String,
        visitedUris: LinkedHashSet<String>,
        redirected: Boolean
    ): RouterResponse {
        // 初始路由不属于重定向次数；已访问路由数超过上限时才拒绝下一跳。
        if (visitedUris.size > MAX_REDIRECT_COUNT) {
            return finishWithoutContext(
                request,
                originalUri,
                redirected,
                IllegalStateException("路由重定向超过 $MAX_REDIRECT_COUNT 次")
            )
        }
        val resolved = RouterResolver.resolveForExecution(request)
            ?: return notFoundResponse(context, request, originalUri, redirected)
        OkRouter.notifyResolved(resolved.publicMatch)
        val visitKey = normalizedUri(request.uri)
        if (!visitedUris.add(visitKey)) {
            val routerContext = resolved.publicMatch.toContext(context, resolved.request)
            return finish(
                RouterOutcome.Failed(IllegalStateException("检测到路由重定向循环")),
                routerContext,
                originalUri,
                redirected
            )
        }

        val routerContext = resolved.publicMatch.toContext(context, resolved.request)
        val outcome = executeResolved(routerContext, resolved)
        if (outcome is RouterOutcome.Redirect) {
            val redirectRequest = try {
                request.newBuilder().uri(outcome.uri).build()
            } catch (error: IllegalStateException) {
                return finish(RouterOutcome.Failed(error), routerContext, originalUri, true)
            }
            return execute(context, redirectRequest, originalUri, visitedUris, true)
        }
        return finish(outcome, routerContext, originalUri, redirected)
    }

    private fun executeResolved(
        routerContext: RouterContext,
        resolved: ResolvedRoute
    ): RouterOutcome {
        val ics = mutableSetOf<Class<out RouterInterceptor>>()
        //加载全局拦截器实例
        WareHouse.globalInterceptors.forEach {
            ics += it
        }
        //加载非全局拦截器
        resolved.meta.interceptors.forEach {
            ics += it
        }
        val interceptors = mutableListOf<RouterInterceptor>()
        ics.forEach {
            val instance = try {
                WareHouse.getInterceptorInstance(it)
            } catch (e: Exception) {
                OkRouter.logger.e("RouterDispatcher: 拦截器实例化失败 ${it.name}", e)
                null
            }
            if (instance != null) {
                interceptors += instance
            }
        }
        interceptors.sortWith(PriorityRouterInterceptorComparator)
        //调用拦截器放在最后执行
        interceptors += LaunchInterceptor(resolved.meta)

        val chain = RouterExecutionChain(routerContext, Collections.unmodifiableList(interceptors))
        return chain.proceed()
    }

    private fun notFoundResponse(
        context: Context,
        request: RouterRequest,
        originalUri: String,
        redirected: Boolean
    ): RouterResponse {
        logger.d("router[${request.uri}] match no target")
        logger.d("not found uri will be handled by ${routerLostHandler.javaClass.name}")
        routerLostHandler.handle(context, request)
        OkRouter.notifyResolved(RouterMatch.NotFound)
        val outcome = RouterOutcome.Failed(NoSuchElementException("未找到路由：${request.uri}"))
        OkRouter.notifyFinished(null, outcome)
        val response = RouterResponse.Builder()
            .uri(request.uri)
            .routerType(request.routerType)
            .routerResult(RouterResult.NotFound)
            .build()
        return decorateRedirectResponse(response, originalUri, request.uri, redirected)
    }

    private fun finishWithoutContext(
        request: RouterRequest,
        originalUri: String,
        redirected: Boolean,
        cause: Throwable
    ): RouterResponse {
        val outcome = RouterOutcome.Failed(cause)
        OkRouter.notifyFinished(null, outcome)
        val response = RouterResponse.Builder()
            .uri(request.uri)
            .routerType(request.routerType)
            .routerResult(RouterResult.Failed(cause))
            .build()
        return decorateRedirectResponse(response, originalUri, request.uri, redirected)
    }

    private fun finish(
        outcome: RouterOutcome,
        routerContext: RouterContext,
        originalUri: String,
        redirected: Boolean
    ): RouterResponse {
        OkRouter.notifyFinished(routerContext, outcome)
        return decorateRedirectResponse(
            outcome.toResponse(routerContext),
            originalUri,
            routerContext.request.uri,
            redirected
        )
    }

    private fun decorateRedirectResponse(
        response: RouterResponse,
        originalUri: String,
        finalUri: String,
        redirected: Boolean
    ): RouterResponse = if (redirected) {
        response.newBuilder()
            .uri(originalUri)
            .header(Extend.OKROUTER_FINAL_URI, finalUri)
            .build()
    } else {
        response
    }

    private fun normalizedUri(uri: String): String = Uri.parse(uri)
        .buildUpon()
        .clearQuery()
        .fragment(null)
        .build()
        .toString()

    private fun RouterMatch.Found.toContext(
        context: Context,
        request: RouterRequest
    ): RouterContext = RouterContext(
        context,
        request,
        destination,
        RouterOptions.DEFAULT
    )
}
