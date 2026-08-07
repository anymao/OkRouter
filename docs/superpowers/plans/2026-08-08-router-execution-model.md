# OkRouter 执行模型实施计划

> **供智能执行代理使用：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务执行；步骤使用复选框追踪。

**目标：** 在保持字符串路由、旧拦截器和旧 Handler API 兼容的前提下，增加纯匹配 API、不可变执行上下文、可表达重定向的结果模型和结构化观察器。

**架构：** 新增公共 `RouterMatch`、`RouterContext`、`RouterOutcome` 等值类型；内部以 `ResolvedRoute` 保存不公开的 `RouterMeta`。`RouterDispatcher` 先解析后执行，旧 `RouterInterceptor`/`RouterHandler` 由适配节点接入新的 outcome 链，最终仍转换成现有 `RouterResponse`。

**技术栈：** Kotlin 2.1、Android SDK、Robolectric 4.14.1、JUnit 4、Javassist、JavaPoet、Gradle 8.13、AGP 8.13.0。

## 全局约束

- 保持 `OkRouter.build(String).start(Context)`、`OkRouter.start(RouterRequest, Context?)`、`RouterInterceptor`、`RouterHandler` 的源码与二进制兼容。
- 不实现类型安全 `RouteSpec`、`Intent`/App Links、Provider 或动态模块注册。
- 公共新 API 必须提供 Java 可调用入口；不得暴露 `warehouse.RouterMeta`。
- `resolve` 不得启动 Android 组件、执行 Handler 或创建拦截器实例。
- 重定向最多执行 8 次；检测到重复规范化 URI 或超出上限时返回 `RouterResult.Failed`。
- 每次修改函数、类或方法前，执行 GitNexus `impact(target, direction="upstream")`；每次提交前执行 `detect_changes(scope="staged")`。

---

## 文件结构

| 文件 | 责任 |
|---|---|
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterMatch.kt` | 纯匹配的公开结果 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterDestination.kt` | 不泄漏 `RouterMeta` 的目标描述 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterContext.kt` | 单次执行的不可变上下文 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterOptions.kt` | 版本 1 的默认选项载体，版本 2 扩展导航选项 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterOutcome.kt` | 新执行链的完成、重定向、拦截和失败结果 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterInterceptorV2.kt` | 新版拦截器与链接口，继承旧接口保证注解兼容 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterHandlerV2.kt` | 新版 Handler，继承旧接口保证插件识别 |
| `okrouter/src/main/java/com/anymore/okrouter/core/RouterObserver.kt` | 解析和执行完成事件 |
| `okrouter/src/main/java/com/anymore/okrouter/core/internal/ResolvedRoute.kt` | 内部 `RouterMeta` 与公开匹配结果的桥接 |
| `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterResolver.kt` | 无副作用解析、参数构建和目标描述转换 |
| `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterExecutionChain.kt` | 混合旧/新拦截器的 outcome 链 |
| `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterOutcomeMapper.kt` | `RouterOutcome`、旧 `RouterResponse` 和最终响应的映射 |
| `okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt` | 支持 `RouterHandlerV2` 与 outcome 结果 |
| `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt` | 拆分解析、执行、重定向和观察器通知 |
| `okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt` | 公共 `resolve`、观察器注册和兼容 `start` 外观 |
| `okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterMeta.kt` | 加入带默认值的 `description`，保留旧 JVM 构造函数 |
| `okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterTaskAction.kt` | 将 `@Router.desc` 写入生成的 `RouterMeta` |
| `okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt` | 新模型、解析、观察器、旧 API 与重定向回归 |

## Task 1：建立公共执行模型与描述元数据

**文件：**
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterMatch.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterDestination.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterContext.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterOptions.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterOutcome.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterMeta.kt`
- 修改：`okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterTaskAction.kt`
- 测试：`okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt`

**消耗：** `RouterType`、`RouterRequest`、`RouterMeta`、插件 `RouterElement.desc`。

**产出：** `RouterMatch`、`RouterDestination`、`RouterContext`、`RouterOptions.DEFAULT`、`RouterOutcome`，以及保留六参数 JVM 构造函数的 `RouterMeta`。

- [ ] **步骤 1：写出模型与元数据的失败测试。**

```kotlin
@Test
fun `router destination retains generated description`() {
    val meta = RouterMeta(
        RouterUri("okrouter", "android", "/profile", 0),
        RouterType.ACTIVITY,
        "example.ProfileActivity",
        Any::class.java,
        emptyArray(),
        null,
        "用户资料"
    )

    val destination = RouterDestination(meta.uri.toString(), meta.routerType, meta.description)

    assertEquals("用户资料", destination.description)
}

@Test
fun `redirect outcome uses default options`() {
    val outcome = RouterOutcome.Redirect("okrouter://android/login")

    assertSame(RouterOptions.DEFAULT, outcome.options)
}
```

- [ ] **步骤 2：运行测试，确认缺少类型或构造函数。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest`

预期：编译失败，提示 `RouterDestination`、`RouterOutcome` 或七参数 `RouterMeta` 不存在。

- [ ] **步骤 3：实现最小公共模型与兼容元数据。**

```kotlin
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

class RouterOptions private constructor() {
    companion object {
        @JvmField
        val DEFAULT = RouterOptions()
    }
}

class RouterContext internal constructor(
    val appContext: Context,
    val request: RouterRequest,
    val destination: RouterDestination,
    val options: RouterOptions
)

sealed class RouterOutcome {
    data class Completed(val target: Any? = null) : RouterOutcome()
    data class Redirect(val uri: String, val options: RouterOptions = RouterOptions.DEFAULT) : RouterOutcome()
    data class Intercepted(val reason: String? = null) : RouterOutcome()
    data class Failed(val cause: Throwable) : RouterOutcome()
}
```

将 `RouterMeta` 改为以下完整构造函数，使旧生成 Loader 仍能链接六参数构造函数；在 `OkRouterTaskAction` 的稳定路由和正则路由两处 `builder.addStatement` 调用中，将 `element.desc` 作为最后一个参数传入生成的 `RouterMeta`。

```kotlin
class RouterMeta @JvmOverloads constructor(
    val uri: RouterUri,
    val routerType: RouterType,
    val clazzName: String,
    val clazz: Class<*>,
    val interceptors: Array<Class<out RouterInterceptor>>,
    val factory: ContextFactory? = null,
    val description: String = ""
)
```

- [ ] **步骤 4：运行模型测试和插件驱动的 Demo 构建。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest :demo-app:assembleDebug`

预期：测试通过，且生成 Loader 能以七参数构造函数编译。

- [ ] **步骤 5：提交可独立审查的公共模型。**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/core/RouterMatch.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/RouterDestination.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/RouterContext.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/RouterOptions.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/RouterOutcome.kt \
  okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterMeta.kt \
  okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterTaskAction.kt \
  okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt
git commit -m "feat: 增加路由执行模型"
```

## Task 2：拆分无副作用解析阶段

**文件：**
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/internal/ResolvedRoute.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterResolver.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt`
- 测试：`okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt`

**消耗：** Task 1 的 `RouterMatch`、`RouterDestination`，现有 `WareHouse.getMatchRouterMeta`。

**产出：** `OkRouter.resolve(String)`、`OkRouter.resolve(RouterRequest)` 与仅内部使用的 `ResolvedRoute`。

- [ ] **步骤 1：写出解析无副作用与参数保留的失败测试。**

```kotlin
@Test
fun `resolve returns destination and query parameters without launching`() {
    WareHouse.registerStableRouter(
        "okrouter://android/profile",
        testMeta("/profile", RouterType.HANDLER, "用户资料")
    )

    val result = OkRouter.resolve("okrouter://android/profile?id=42")

    assertTrue(result is RouterMatch.Found)
    val found = result as RouterMatch.Found
    assertEquals(RouterType.HANDLER, found.destination.type)
    assertEquals("用户资料", found.destination.description)
    assertEquals("42", found.parameters.getString("id"))
    assertEquals(0, RecordingHandler.callCount.get())
}

private fun testMeta(path: String, type: RouterType, description: String): RouterMeta = RouterMeta(
    RouterUri("okrouter", "android", path, 0),
    type,
    RecordingHandler::class.java.name,
    RecordingHandler::class.java,
    emptyArray(),
    null,
    description
)

private class RecordingHandler : RouterHandler {
    override fun handle(context: Context, request: RouterRequest) {
        callCount.incrementAndGet()
    }

    companion object {
        val callCount = AtomicInteger(0)
    }
}

@Test
fun `resolve blank uri is invalid instead of throwing`() {
    assertEquals(RouterMatch.Invalid("URI 不能为空"), OkRouter.resolve(""))
}
```

- [ ] **步骤 2：运行测试，确认 `resolve` 尚不存在。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest`

预期：编译失败，提示 `OkRouter.resolve` 未定义。

- [ ] **步骤 3：实现解析器和公共外观。**

```kotlin
internal data class ResolvedRoute(
    val publicMatch: RouterMatch.Found,
    val meta: RouterMeta
)

internal object RouterResolver {
    fun resolve(request: RouterRequest): RouterMatch =
        resolveInternal(request)?.publicMatch ?: RouterMatch.NotFound

    fun resolveInternal(request: RouterRequest): ResolvedRoute? {
        val meta = WareHouse.getMatchRouterMeta(request.uri) ?: return null
        val destination = RouterDestination(meta.uri.toString(), meta.routerType, meta.description)
        return ResolvedRoute(RouterMatch.Found(destination, request, Bundle(request.extras)), meta)
    }
}

@JvmStatic
fun resolve(uri: String): RouterMatch {
    if (uri.isBlank()) return RouterMatch.Invalid("URI 不能为空")
    return try {
        resolve(RouterRequest.Builder().uri(uri).build())
    } catch (error: IllegalStateException) {
        RouterMatch.Invalid(error.message ?: "URI 非法")
    }
}
```

`RouterDispatcher.start` 暂时仍执行旧链，但匹配必须改为复用 `RouterResolver.resolveInternal`，避免产生两套 URI 规范化规则。

- [ ] **步骤 4：运行解析测试和现有仓储回归。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest --tests com.anymore.okrouter.warehouse.WareHouseTest`

预期：所有测试通过；`resolve` 不创建 `RecordingHandler`。

- [ ] **步骤 5：提交解析边界。**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/core/internal/ResolvedRoute.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterResolver.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt \
  okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt \
  okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt
git commit -m "feat: 增加无副作用路由解析"
```

## Task 3：接入新版 Handler、拦截器与 outcome 执行链

**文件：**
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterInterceptorV2.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterHandlerV2.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterChain.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterExecutionChain.kt`
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterOutcomeMapper.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt`
- 测试：`okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt`

**消耗：** Task 1 的 `RouterContext`/`RouterOutcome`，Task 2 的 `ResolvedRoute`。

**产出：** 旧与新拦截器可混排；`RouterHandlerV2` 可完成、拦截、失败或重定向；旧 `RouterHandler` 继续原样工作。

- [ ] **步骤 1：写出新版与旧版适配的失败测试。**

```kotlin
@Test
fun `v2 handler completed outcome becomes ok response`() {
    registerHandler("/v2", CompletedV2Handler::class.java)

    val response = OkRouter.build("okrouter://android/v2").start(appContext)

    assertEquals(RouterResult.Ok, response.routerResult)
    assertEquals("v2-target", response.target)
}

private val appContext: Context = RuntimeEnvironment.getApplication()

private fun registerHandler(
    path: String,
    clazz: Class<out RouterHandler>,
    vararg interceptors: Class<out RouterInterceptor>
) {
    WareHouse.registerStableRouter(
        "okrouter://android$path",
        RouterMeta(
            RouterUri("okrouter", "android", path, 0),
            RouterType.HANDLER,
            clazz.name,
            clazz,
            arrayOf(*interceptors)
        )
    )
}

@Test
fun `legacy handler remains compatible`() {
    registerHandler("/legacy", RecordingHandler::class.java)

    val response = OkRouter.build("okrouter://android/legacy").start(appContext)

    assertEquals(RouterResult.Ok, response.routerResult)
    assertEquals(1, RecordingHandler.callCount.get())
}

@Test
fun `v2 interceptor can intercept without calling next`() {
    registerHandler("/blocked", RecordingHandler::class.java, BlockingV2Interceptor::class.java)

    val response = OkRouter.build("okrouter://android/blocked").start(appContext)

    assertEquals(RouterResult.Intercepted, response.routerResult)
    assertEquals(0, RecordingHandler.callCount.get())
}
```

- [ ] **步骤 2：运行测试，确认新版接口未定义。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest`

预期：编译失败，提示 `RouterHandlerV2`、`RouterInterceptorV2` 或测试处理器不存在。

- [ ] **步骤 3：实现兼容接口和执行链。**

```kotlin
interface RouterHandlerV2 : RouterHandler {
    fun handle(context: RouterContext): RouterOutcome

    @Deprecated("由 RouterHandlerV2.handle(RouterContext) 替代")
    override fun handle(context: Context, request: RouterRequest) = Unit
}

interface RouterInterceptorV2 : RouterInterceptor {
    fun intercept(chain: RouterChain): RouterOutcome

    @Deprecated("由 RouterInterceptorV2.intercept(RouterChain) 替代")
    override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse =
        chain.proceed(context, chain.request())
}

interface RouterChain {
    val context: RouterContext
    fun proceed(): RouterOutcome
}
```

`RouterExecutionChain` 按已排序的拦截器列表递归推进：遇到 `RouterInterceptorV2` 调用其新方法；否则调用旧接口，并由 `RouterOutcomeMapper.fromLegacyResponse` 保留旧响应的 `headers`、`target` 和 `RouterResult`。`LaunchInterceptor` 发现目标是 `RouterHandlerV2` 时调用 `handle(RouterContext)`，其他目标维持现有启动逻辑。

- [ ] **步骤 4：实现 outcome 到旧响应的固定映射并运行测试。**

```kotlin
internal fun RouterOutcome.toResponse(context: RouterContext): RouterResponse = when (this) {
    is RouterOutcome.Completed -> RouterResponse.Builder()
        .uri(context.request.uri)
        .routerType(context.destination.type)
        .routerResult(RouterResult.Ok)
        .target(target)
        .build()
    is RouterOutcome.Intercepted -> RouterResponse.Builder()
        .uri(context.request.uri)
        .routerType(context.destination.type)
        .routerResult(RouterResult.Intercepted)
        .build()
    is RouterOutcome.Failed -> RouterResponse.Builder()
        .uri(context.request.uri)
        .routerType(context.destination.type)
        .routerResult(RouterResult.Failed(cause))
        .build()
    is RouterOutcome.Redirect -> error("Redirect 必须由 RouterDispatcher 处理")
}
```

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest --tests com.anymore.okrouter.core.RouterResponseTest`

预期：新版 Handler、旧 Handler、旧拦截器与新版拦截器的测试均通过。

- [ ] **步骤 5：提交执行链。**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/core/RouterInterceptorV2.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/RouterHandlerV2.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/RouterChain.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterExecutionChain.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterOutcomeMapper.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt \
  okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt
git commit -m "feat: 支持新版路由执行链"
```

## Task 4：实现重定向和结构化观察器

**文件：**
- 创建：`okrouter/src/main/java/com/anymore/okrouter/core/RouterObserver.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt`
- 修改：`okrouter/src/main/java/com/anymore/okrouter/core/Extend.kt`
- 测试：`okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt`

**消耗：** Task 2 的解析结果、Task 3 的 outcome 链和 `toResponse` 映射。

**产出：** `OkRouter.addObserver`、`removeObserver`、无影响观察器失败隔离、最多八跳且无环的重定向。

- [ ] **步骤 1：写出重定向、循环保护和观察器的失败测试。**

```kotlin
@Test
fun `redirect reaches terminal handler and preserves original response uri`() {
    registerHandler("/entry", RedirectHandler::class.java)
    registerHandler("/login", CompletedV2Handler::class.java)

    val response = OkRouter.build("okrouter://android/entry").start(appContext)

    assertEquals(RouterResult.Ok, response.routerResult)
    assertEquals("okrouter://android/entry", response.uri)
    assertEquals("okrouter://android/login", response.headers[Extend.OKROUTER_FINAL_URI])
}

@Test
fun `redirect loop becomes failed response`() {
    registerHandler("/a", RedirectToBHandler::class.java)
    registerHandler("/b", RedirectToAHandler::class.java)

    val response = OkRouter.build("okrouter://android/a").start(appContext)

    assertTrue(response.routerResult is RouterResult.Failed)
}

@Test
fun `throwing observer does not interrupt route`() {
    OkRouter.addObserver(object : RouterObserver {
        override fun onFinished(context: RouterContext?, outcome: RouterOutcome) {
            throw IllegalStateException("metrics unavailable")
        }
    })
    registerHandler("/observe", CompletedV2Handler::class.java)

    val response = OkRouter.build("okrouter://android/observe").start(appContext)

    assertEquals(RouterResult.Ok, response.routerResult)
}
```

- [ ] **步骤 2：运行测试，确认重定向、观察器和最终 URI 常量不存在。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest`

预期：编译失败，提示 `RouterObserver`、`addObserver` 或 `OKROUTER_FINAL_URI` 不存在。

- [ ] **步骤 3：实现观察器注册和重定向防护。**

```kotlin
private const val MAX_REDIRECT_COUNT = 8
private val observers = CopyOnWriteArraySet<RouterObserver>()

@JvmStatic
fun addObserver(observer: RouterObserver) {
    observers += observer
}

@JvmStatic
fun removeObserver(observer: RouterObserver) {
    observers -= observer
}

private fun execute(
    context: Context,
    request: RouterRequest,
    originalUri: String,
    visitedUris: LinkedHashSet<String>
): RouterResponse {
    if (visitedUris.size >= MAX_REDIRECT_COUNT) return failedResponse(request, IllegalStateException("路由重定向超过 8 次"))
    val resolved = RouterResolver.resolveInternal(request) ?: return notFoundResponse(context, request)
    if (!visitedUris.add(resolved.publicMatch.destination.uriPattern + "|" + request.uri)) {
        return failedResponse(request, IllegalStateException("检测到路由重定向循环"))
    }
    return when (val outcome = executeResolved(context, resolved)) {
        is RouterOutcome.Redirect -> execute(context, RouterRequest.Builder().uri(outcome.uri).build(), originalUri, visitedUris)
        else -> outcome.toResponse(resolved.publicMatch.toContext(context)).newBuilder()
            .uri(originalUri)
            .header(Extend.OKROUTER_FINAL_URI, request.uri)
            .build()
    }
}

private fun executeResolved(context: Context, resolved: ResolvedRoute): RouterOutcome =
    RouterExecutionChain(resolved.publicMatch.toContext(context), resolved.meta).proceed()

private fun RouterMatch.Found.toContext(context: Context): RouterContext = RouterContext(
    context.applicationContext,
    request,
    destination,
    RouterOptions.DEFAULT
)
```

观察器通知必须逐个 `try/catch`，记录错误日志但不改变 `RouterOutcome`。重定向循环的 `visitedUris` 使用规范化匹配 URI 与原始请求 URI 的组合，防止 `/a` 与 `/b` 往返。

- [ ] **步骤 4：运行聚焦测试和完整核心测试。**

运行：`./gradlew :okrouter:test --rerun-tasks`

预期：`RouterExecutionModelTest` 的重定向、观察器、旧 API 测试和现有 32 项测试全部通过。

- [ ] **步骤 5：提交可观测性与重定向。**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/core/RouterObserver.kt \
  okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt \
  okrouter/src/main/java/com/anymore/okrouter/core/Extend.kt \
  okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt
git commit -m "feat: 增加路由重定向与观察器"
```

## Task 5：完成兼容性、构建与文档门禁

**文件：**
- 修改：`README.md`
- 修改：`example/demo-app/路由文档.md`（仅当插件生成字段发生预期变化）
- 测试：`okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt`

**消耗：** Task 1 至 Task 4 的完整执行模型。

**产出：** 可发布的版本 1 兼容性证明、更新后的调用示例和干净工作树。

- [ ] **步骤 1：补充旧入口与新入口等价的回归测试。**

```kotlin
@Test
fun `legacy builder start and dispatcher start return same result`() {
    registerHandler("/compat", CompletedV2Handler::class.java)
    val request = RouterRequest.Builder().uri("okrouter://android/compat").build()

    val viaBuilder = OkRouter.build("okrouter://android/compat").start(appContext)
    val viaDispatcher = OkRouter.start(request, appContext)

    assertEquals(viaBuilder.routerResult, viaDispatcher.routerResult)
    assertEquals(viaBuilder.routerType, viaDispatcher.routerType)
}
```

- [ ] **步骤 2：运行测试，确认当前兼容行为后再更新文档。**

运行：`./gradlew :okrouter:test --tests com.anymore.okrouter.core.RouterExecutionModelTest`

预期：测试通过；若失败，先修复兼容适配，不更新 README。

- [ ] **步骤 3：更新 README 的 API 示例与边界。**

新增以下示例，并明确 `resolve` 无副作用、`build(String)` 未弃用：

```kotlin
when (val match = OkRouter.resolve("okrouter://android/profile?id=42")) {
    is RouterMatch.Found -> OkRouter.build(match.request.uri).start(context)
    RouterMatch.NotFound -> Unit
    is RouterMatch.Invalid -> error(match.reason)
}
```

补充 `RouterHandlerV2` 返回 `RouterOutcome.Redirect` 的示例，并说明旧 `RouterHandler` 仍然可用。

- [ ] **步骤 4：执行发布前验证。**

运行：`./gradlew :okrouter:test :demo-app:assembleDebug --rerun-tasks`

预期：所有测试通过，Debug APK 生成，路由文档只出现与新增 description 传递有关的预期变化。

- [ ] **步骤 5：核查影响范围并提交。**

```bash
git diff --check
git add README.md okrouter/src/test/java/com/anymore/okrouter/core/RouterExecutionModelTest.kt
git add example/demo-app/路由文档.md
git status --short
git commit -m "docs: 说明新版路由执行模型"
```

提交前执行 GitNexus `detect_changes(scope="staged", repo="OkRouter")`。预期影响仅包括路由解析、分发、Handler、拦截器和插件生成 Loader 流程；若出现无关流程，停止提交并审查差异。

## 覆盖检查

| 设计要求 | 对应任务 |
|---|---|
| 纯 `resolve` 与不泄漏元信息 | Task 1、Task 2 |
| 不可变上下文和 outcome | Task 1、Task 3 |
| 旧 Handler / 拦截器兼容 | Task 3、Task 5 |
| 重定向、循环保护与最终 URI | Task 4 |
| 观察器隔离 | Task 4 |
| 插件 description 元数据 | Task 1、Task 5 |
| 核心单测、Demo 构建与变更核查 | 每个任务的验证步骤，Task 5 汇总 |
