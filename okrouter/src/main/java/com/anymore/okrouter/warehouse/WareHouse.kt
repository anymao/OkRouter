package com.anymore.okrouter.warehouse

import android.net.Uri
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.core.RouterInterceptor
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 路由仓储层，统一从此处获取路由信息以及拦截器信息
 * Created by anymore on 2023/6/7.
 */
internal object WareHouse {
    /**
     * 稳定路由，如果路由不包含正则，则会存到此Map中
     * 此Map应在编译时填充
     */
    @JvmStatic
    val stableRouters: MutableMap<String, RouterMeta> = ConcurrentHashMap()

    /**
     * 如果路由命中正则路由，将会将此映射加入到此Map中
     * 在下一次映射的时候将会之间从这里面取，无需再次正则匹配
     */
    @JvmStatic
    val dynamicRouters: MutableMap<String, RouterMeta> = ConcurrentHashMap()

    /**
     * 正则路由，如果路由包含正则，则会存放在这里。
     */
    @JvmStatic
    val regexRouters: MutableMap<RouterUri, RouterMeta> = ConcurrentHashMap()

    /**
     * 全局拦截器集合列表
     */
    @JvmStatic
    val globalInterceptors: MutableSet<Class<out RouterInterceptor>> =
        java.util.Collections.newSetFromMap(
            ConcurrentHashMap<Class<out RouterInterceptor>, Boolean>()
        )

    /**
     * 拦截器元信息映射，可以通过拦截器的Class获取其他信息，例如factory和priority
     */
    @JvmStatic
    val interceptorMetas: MutableMap<Class<out RouterInterceptor>, RouterInterceptorMeta> =
        ConcurrentHashMap()

    @JvmStatic
    fun registerStableRouter(uri: String, meta: RouterMeta) {
        if (!stableRouters.containsKey(uri)) {
            stableRouters[uri] = meta
        } else {
            logger.w("稳定路由 URI \"$uri\" 重复注册，已保留首次注册的映射。请检查是否存在冲突的 @Router 注解。")
        }
    }

    @JvmStatic
    fun registerRegexRouter(uri: RouterUri, meta: RouterMeta) {
        if (!regexRouters.containsKey(uri)) {
            regexRouters[uri] = meta
        }
    }

    @JvmStatic
    fun registerInterceptor(
        clazz: Class<out RouterInterceptor>,
        meta: RouterInterceptorMeta,
        isGlobal: Boolean = false
    ) {
        if (!interceptorMetas.containsKey(clazz)) {
            interceptorMetas[clazz] = meta
        }
        if (isGlobal) {
            globalInterceptors += clazz
        }
    }

    fun getMatchRouterMeta(uri: String): RouterMeta? {
        return matchRouterMeta(uri, cacheDynamicResult = true)
    }

    /**
     * 只读取当前路由表进行匹配，不写入动态路由缓存。
     */
    internal fun findRouterMeta(uri: String): RouterMeta? {
        return matchRouterMeta(uri, cacheDynamicResult = false)
    }

    private fun matchRouterMeta(uri: String, cacheDynamicResult: Boolean): RouterMeta? {
        logger.v("getMatchRouterMeta for full uri:$uri")
        val clearedUri = Uri.parse(uri).buildUpon().clearQuery().fragment(null).build()
        logger.v("build cleared uri $clearedUri")
        var result = stableRouters[clearedUri.toString()]
        if (result != null) return result
        result = dynamicRouters[clearedUri.toString()]
        if (result != null) return result
        val (scheme, host, path) = clearedUri.run {
            Triple(scheme.orEmpty(), host.orEmpty(), path.orEmpty())
        }
        // ConcurrentHashMap 遍历无序，按 priority + URI 字典序排序后遍历，保证命中确定性
        regexRouters.entries
            .sortedWith(compareBy<Map.Entry<RouterUri, RouterMeta>> { it.key.priority }
                .thenBy { it.key.toString() })
            .forEach { (key, value) ->
                if (key.scheme.toRegex().matches(scheme) && key.host.toRegex()
                        .matches(host) && key.path.toRegex().matches(path)
                ) {
                    logger.d("match regex router:${value.uri}")
                    if (cacheDynamicResult) {
                        dynamicRouters[clearedUri.toString()] = value
                    }
                    return value
                }
            }
        return null
    }


    fun getInterceptorInstance(clazz: Class<out RouterInterceptor>): RouterInterceptor {
        val meta = interceptorMetas[clazz]
        var instance: RouterInterceptor? = null
        if (meta != null) {
            instance = meta.factory.create()
        }
        if (instance == null) {
            val newFactory = ReflectRouterInterceptorFactory(clazz)
            instance = newFactory.newInstance()
            registerInterceptor(
                clazz,
                RouterInterceptorMeta(clazz, clazz.name, "", newFactory, 0,
                    global = false,
                    singleton = false
                )
            )
        }
        return instance
    }

    /**
     * 获取一个拦截器的优先级
     */
    fun getInterceptorInstancePriority(clazz: Class<RouterInterceptor>): Int {
        return interceptorMetas[clazz]?.priority ?: 0
    }
}
