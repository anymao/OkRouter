# OkRouter：兼容式路由能力演进设计

> 设计日期：2026-08-08  
> 范围：在不破坏现有字符串路由 API 的前提下，分三个小版本演进执行模型、Android 入口与模块服务能力。  
> 非目标：类型安全 `RouteSpec` / 路由对象暂缓到后续独立立项。

## 1. 背景与目标

当前 OkRouter 已提供编译期生成的静态路由表、字符串 URI、拦截器链和 Activity/Fragment/View/Service/Handler 分发。它缺少可独立测试的匹配阶段、精确的执行结果、Android `Intent` 统一入口、导航选项，以及面向跨模块通信的 Provider 能力。

本设计的唯一兼容性承诺是：以下 1.x API 的源码和二进制行为继续有效。

```kotlin
OkRouter.init(context)
OkRouter.build(uri: String).start(context)
OkRouter.start(request, context)
@Router, @Interceptor, RouterInterceptor, RouterHandler, RouterLostHandler
RouterRequest, RouterResponse, RouterResult
```

所有新能力均通过新增类型、默认注解参数和适配器引入；旧 API 最终委托给新执行管线。

## 2. 发布顺序

| 小版本 | 主题 | 主要结果 |
|---|---|---|
| 版本 1 | 执行模型 | `resolve`、`RouterContext`、`RouterOutcome`、新版 Handler/拦截器与观察器 |
| 版本 2 | Android 入口 | `Intent` 处理、深链声明、导航选项、可选 manifest 生成 |
| 版本 3 | 模块能力 | Provider 发现、受控动态模块注册 |

每个版本都独立可发布、可回归验证，不依赖类型安全 `RouteSpec`。

## 3. 版本 1：执行模型

### 3.1 公共 API

```kotlin
fun OkRouter.resolve(uri: String): RouterMatch
fun OkRouter.resolve(request: RouterRequest): RouterMatch

sealed class RouterMatch {
    data class Found(
        val destination: RouterDestination,
        val request: RouterRequest,
        val parameters: Bundle
    ) : RouterMatch()

    data object NotFound : RouterMatch()
    data class Invalid(val reason: String) : RouterMatch()
}

data class RouterDestination(
    val uriPattern: String,
    val type: RouterType,
    val description: String
)

class RouterContext {
    val appContext: Context
    val request: RouterRequest
    val destination: RouterDestination
    val options: RouterOptions
}

sealed class RouterOutcome {
    data class Completed(val target: Any? = null) : RouterOutcome()
    data class Redirect(val uri: String, val options: RouterOptions = RouterOptions.DEFAULT) : RouterOutcome()
    data class Intercepted(val reason: String? = null) : RouterOutcome()
    data class Failed(val cause: Throwable) : RouterOutcome()
}

interface RouterHandlerV2 {
    fun handle(context: RouterContext): RouterOutcome
}

interface RouterInterceptorV2 {
    fun intercept(chain: RouterChain): RouterOutcome
}

interface RouterChain {
    val context: RouterContext
    fun proceed(): RouterOutcome
}

interface RouterObserver {
    fun onResolved(match: RouterMatch) {}
    fun onFinished(context: RouterContext?, outcome: RouterOutcome) {}
}
```

### 3.2 兼容适配

- 旧 `RouterRequest.Builder.start()` 和 `OkRouter.start()` 仍返回 `RouterResponse`，内部调用 `resolve` 与新版执行器，再将 `RouterOutcome` 映射为现有 `RouterResponse` / `RouterResult`。
- 旧 `RouterInterceptor` 由适配器包装为新版链节点；其既有 `RouterResponse` 映射为对应结果。
- 旧 `RouterHandler` 执行成功时映射为 `RouterOutcome.Completed`；`RouterHandlerV2` 可返回失败、拦截或重定向。
- `RouterRequest.routerType` 不再参与目标类型决策；实际类型由 `RouterDestination` 提供。为兼容旧响应，旧字段仅保留为请求附加信息。

### 3.3 调用链

```text
旧 Builder / OkRouter.start
          │
          ▼
       resolve
          │
          ▼
RouterMatch.Found + RouterContext
          │
          ▼
旧/新拦截器适配链 → HandlerV2 或现有目标启动器
          │
          ▼
RouterOutcome → RouterResponse
```

### 3.4 验收

- `resolve` 不触发 Activity、Service 或 Handler 副作用。
- 匹配失败、非法 URI、拦截、目标异常和重定向均有确定结果。
- 旧拦截器、旧 Handler 与旧字符串路由回归测试保持通过。
- 观察器异常不得影响路由主链。

## 4. 版本 2：Android Intent 与导航选项

### 4.1 公共 API

```kotlin
fun OkRouter.handle(
    intent: Intent,
    context: Context? = null,
    options: RouterOptions = RouterOptions.DEFAULT
): RouterResponse

fun OkRouter.canHandle(intent: Intent): Boolean

fun OkRouter.createDeepLinkIntent(
    uri: Uri,
    options: RouterOptions = RouterOptions.DEFAULT
): Intent

class RouterOptions private constructor(
    val flags: Int,
    val singleTop: Boolean,
    val clearTop: Boolean,
    val newTask: Boolean,
    val activityOptions: Bundle?,
    val enterAnimRes: Int?,
    val exitAnimRes: Int?
) {
    companion object {
        val DEFAULT: RouterOptions = Builder().build()
    }

    class Builder {
        fun addFlags(flags: Int): Builder
        fun singleTop(): Builder
        fun clearTop(): Builder
        fun newTask(): Builder
        fun activityOptions(options: Bundle?): Builder
        fun transition(enterAnim: Int, exitAnim: Int): Builder
        fun build(): RouterOptions
    }
}
```

现有 `@Router` 仅新增拥有默认值的字段：

```kotlin
annotation class Router(
    // 保留全部既有字段
    val external: Boolean = false,
    val actions: Array<String> = [],
    val mimeTypes: Array<String> = []
)
```

插件新增可选配置：

```kotlin
okRouter {
    generateIntentFilters = true
    verifyAppLinks = false
}
```

### 4.2 行为

- `handle(intent)` 提取 URI、action、MIME type，并复用版本 1 的 `resolve` 与执行链。
- `external = true` 才允许生成外部 `intent-filter`；默认不暴露，避免内部路由被意外导出。
- `RouterOptions` 仅由 Activity 启动器消费；对 Fragment、View、Handler 是无副作用字段。
- 现有字符串路由不会自动拥有外部暴露资格。

### 4.3 验收

- URI、action、MIME 均参与匹配；不满足声明即返回未命中。
- 外部 Deep Link、内部字符串 URI 与 `Intent` 走同一拦截器顺序。
- 验证 `singleTop`、`clearTop`、`newTask`、flags、转场和 Activity Result 场景。
- manifest 生成必须由集成测试校验，并默认关闭。

## 5. 版本 3：Provider 与受控动态注册

### 5.1 公共 API

```kotlin
interface RouterProvider {
    fun onCreate(context: Context) {}
}

@Provider(key = "payment", singleton = true)
class PaymentProvider : RouterProvider, PaymentApi

inline fun <reified T : RouterProvider> OkRouter.provider(): T?
fun <T : RouterProvider> OkRouter.provider(type: Class<T>): T?
fun OkRouter.provider(key: String): RouterProvider?

interface RouterModule {
    val id: String
    fun register(registry: RouterRegistry)
}

interface RouterRegistry {
    fun registerRoute(definition: DynamicRoute)
    fun registerProvider(definition: DynamicProvider)
}

fun OkRouter.install(module: RouterModule): RouterInstallResult
fun OkRouter.isInstalled(moduleId: String): Boolean
```

### 5.2 约束

- `RouterProvider` 是业务接口发现能力，和现有“启动 Android Service”的 `RouterType.SERVICE` 完全分离。
- 注册冲突默认失败；只有显式策略才能替换现有条目。
- 初版仅支持安装后注册，不提供 `uninstall()`；动态特性模块的物理卸载和运行中路由移除另行设计。
- Provider 按注解的单例策略创建，并保证初始化与并发访问安全。

### 5.3 验收

- 可按接口和 key 获取 Provider；单例只初始化一次。
- 模块重复安装和路由/Provider 冲突有确定错误结果。
- 动态注册不影响已生成的静态路由表与正在执行的请求。

## 6. 暂缓事项

类型安全 `RouteSpec`、模板参数对象、插件生成的参数编解码 API 不属于上述三个版本。它将作为后续独立设计：内部调用优先类型化入口，`build(String)` 和 `handle(Intent)` 保持兼容并承接外部输入。

## 7. 非目标

- 不重造 Jetpack Navigation 的 Compose back stack 或 UI 图导航。
- 不修改现有字符串路由的调用方式。
- 不在版本 3 首次实现动态模块卸载、跨进程路由或任意插件热替换。
