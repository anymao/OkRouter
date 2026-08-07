package com.anymore.okrouter

import android.app.Application
import android.content.Context
import com.anymore.okrouter.core.Logger
import com.anymore.okrouter.core.RouterMatch
import com.anymore.okrouter.core.RouterObserver
import com.anymore.okrouter.core.RouterOutcome
import com.anymore.okrouter.core.RouterLostHandler
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.RouterContext
import com.anymore.okrouter.core.internal.RouterDispatcher
import com.anymore.okrouter.core.internal.RouterResolver
import com.anymore.okrouter.warehouse.OkRouterLoader
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by anymore on 2023/6/5.
 */
object OkRouter {

    internal const val TAG = "OkRouter"

    /**
     * 应用上下文，仅在 [init] 成功调用后非空。
     * 未初始化时 [start] 会抛出 [IllegalStateException] 而非崩溃于属性未初始化。
     */
    @Volatile
    internal var application: Application? = null
        private set

    @Volatile
    private var initialized = false

    /**
     * 当没有找到目标路由时候，会执行此Handler，可以在这里进行埋点，或者重定向
     */
    @JvmStatic
    var routerLostHandler: RouterLostHandler = object : RouterLostHandler {}

    @JvmStatic
    var logger: Logger = Logger.Default

    private val observers = CopyOnWriteArraySet<RouterObserver>()

    /** 注册路由观察者。重复注册同一实例会被忽略。 */
    @JvmStatic
    fun addObserver(observer: RouterObserver) {
        observers += observer
    }

    /** 移除已注册的路由观察者。 */
    @JvmStatic
    fun removeObserver(observer: RouterObserver) {
        observers -= observer
    }

    internal fun notifyResolved(match: RouterMatch) {
        observers.forEach { observer ->
            runCatching { observer.onResolved(match) }
                .onFailure { error -> logger.e("RouterObserver.onResolved 执行失败", error) }
        }
    }

    internal fun notifyFinished(context: RouterContext?, outcome: RouterOutcome) {
        observers.forEach { observer ->
            runCatching { observer.onFinished(context, outcome) }
                .onFailure { error -> logger.e("RouterObserver.onFinished 执行失败", error) }
        }
    }

    @JvmStatic
    fun init(context: Context) {
        //幂等：重复调用直接跳过，避免重复加载路由表
        if (initialized) {
            logger.w("OkRouter 已经初始化，跳过重复 init() 调用")
            return
        }
        //先加载路由表，成功后才赋值 application，避免 load 失败时处于半初始化态
        //（application 非空但路由表未加载，start() 会误判为已初始化）
        OkRouterLoader.load()
        application = context.applicationContext as Application
        initialized = true
    }

    @JvmStatic
    fun isInitialized(): Boolean = initialized

    @JvmStatic
    fun build(uri: String) = RouterRequest.Builder().uri(uri)

    /**
     * 仅解析 URI 的目标和 query 参数，不初始化应用、不执行拦截器，也不发起跳转。
     *
     * 空白 URI 返回 [RouterMatch.Invalid]；URI 有效但未注册匹配目标时返回
     * [RouterMatch.NotFound]。
     */
    @JvmStatic
    fun resolve(uri: String): RouterMatch {
        if (uri.isBlank()) return RouterMatch.Invalid("URI 不能为空")
        return try {
            resolve(RouterRequest.Builder().uri(uri).build())
        } catch (error: IllegalStateException) {
            RouterMatch.Invalid(error.message ?: "URI 非法")
        }
    }

    /**
     * 仅解析已构造的请求，不初始化应用、不执行拦截器，也不发起跳转。
     * 未匹配任何已注册路由时返回 [RouterMatch.NotFound]。
     */
    @JvmStatic
    fun resolve(request: RouterRequest): RouterMatch = RouterResolver.resolve(request)

    @JvmOverloads
    @JvmStatic
    fun start(request: RouterRequest, context: Context? = null): RouterResponse {
        val ctx = context ?: application
            ?: throw IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")
        return RouterDispatcher.start(ctx, request)
    }

}
