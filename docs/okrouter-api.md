# OkRouter API 文档

> 本文档基于当前 `master` 分支源码编写，描述当前已经实现的 API 和行为。
> 设计文档中尚未落地的 API 不在本文档中视为可用 API。

## 1. 项目概览

OkRouter 是一个 Android 编译期路由框架：业务模块通过 `@Router` 和 `@Interceptor` 声明路由，Gradle 插件在构建每个应用变体时扫描 class/jar，生成 `OkRouterLoader` 和对象工厂；应用运行时只需加载生成结果，即可完成 URI 匹配、拦截器执行和目标分发。

### 1.1 模块职责

| 模块 | 职责 |
| --- | --- |
| `okrouter` | Android 运行时库，包含 `OkRouter`、请求/响应模型、注解、拦截器契约和分发逻辑 |
| `okrouter-plugin` | Gradle 插件源码，负责扫描注解、生成路由加载器和路由文档 |
| `okrouter-stub` | 编译期占位的 `OkRouterLoader`；运行时应由插件生成的同名类替换 |
| `buildSrc` | 当前源码仓库将 `okrouter-plugin` 的源码并入 `buildSrc`，供示例构建脚本直接使用 |
| `example/demo-app` | 示例宿主应用，负责初始化 OkRouter 并汇总业务模块路由 |
| `example/demo-base` | 示例公共依赖和工具 |
| `example/demo-login` | 全局/局部登录拦截器示例 |
| `example/demo-web` | `https?://...` 正则路由和 `RouterHandler` 示例 |
| `example/demo-biz1`、`example/demo-biz2` | Activity、Fragment、View、Service 及业务拦截器示例 |

### 1.2 运行时链路

```text
@Router / @Interceptor
        │
        ▼
构建期扫描 classpath → 生成 OkRouterLoader 和对象工厂
        │
        ▼
Application.onCreate → OkRouter.init(context)
        │
        ▼
OkRouter.build(uri) → RouterRequest.Builder.build/start
        │
        ▼
WareHouse：精确匹配 / 正则匹配 / 正则命中缓存
        │
        ▼
RouterDispatcher：合并全局与局部拦截器，按 priority 排序
        │
        ▼
RealRouterChain → 用户拦截器 → LaunchInterceptor
        │
        ▼
Activity | Fragment | View | Service | RouterHandler
```

## 2. 接入与初始化

### 2.1 环境基线

当前工程配置为：

- Android Gradle Plugin：8.13.0
- Gradle：8.13
- Kotlin：2.1.0
- JDK：17
- `okrouter` 最低 Android SDK：17

### 2.2 应用模块应用插件

源码仓库中的示例应用通过 `buildSrc` 暴露插件实现类：

```groovy
import com.anymore.okrouter.OkRouterTransformPlugin

plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

apply plugin: OkRouterTransformPlugin

okRouter {
    createOkRouterDoc = true
    okRouterDocFile = project.file("路由文档.md").canonicalPath
}
```

插件只能应用于 Android application 模块。应用插件后，它会为每个 application variant 注册：

- `okRouterRegister<Variant>`：扫描变体 classpath 并生成 Java 源码；
- `okRouterCompile<Variant>`：编译生成的 `OkRouterLoader` 和工厂；
- 对 `assemble`、dex、bundle、lint 等下游任务建立依赖，确保生成类进入最终产物。

发布后的插件元数据声明的插件 ID 是 `okrouter`，但当前源码示例优先使用上面的类方式。

### 2.3 Application 中初始化

应在 `Application.onCreate()` 中配置未命中处理器后调用 `OkRouter.init(this)`：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        OkRouter.routerLostHandler = object : RouterLostHandler {
            override fun handle(context: Context, request: RouterRequest) {
                // 记录埋点、展示兜底页或执行重定向
            }
        }

        OkRouter.init(this)
    }
}
```

`init` 具备幂等性：重复调用会记录警告并直接返回。加载路由表成功后才会标记为已初始化；调用失败不会留下“已经初始化但路由未加载”的半初始化状态。

## 3. 核心 API

### 3.1 `OkRouter`

包名：`com.anymore.okrouter.OkRouter`

| API | 说明 |
| --- | --- |
| `var routerLostHandler: RouterLostHandler` | 路由未命中时执行的处理器，默认是空实现 |
| `var logger: Logger` | 运行时日志实现，默认使用 `Logger.Default` |
| `fun init(context: Context)` | 加载生成的路由表并保存 `Application` 上下文 |
| `fun isInitialized(): Boolean` | 返回是否已完成初始化 |
| `fun build(uri: String): RouterRequest.Builder` | 创建指定 URI 的请求构建器 |
| `fun start(request: RouterRequest, context: Context? = null): RouterResponse` | 启动已经构建好的请求；未传上下文时使用初始化时保存的 Application |

未初始化时，以下场景会抛出 `IllegalStateException`：

- `OkRouter.start(request)` 没有传入上下文；
- `OkRouter.build(uri).start()` 没有传入上下文。

推荐优先使用 `OkRouter.build(uri).start(context)`，这样请求参数和启动行为集中在同一个调用链中。

### 3.2 `RouterRequest`

包名：`com.anymore.okrouter.core.RouterRequest`

构建完成后的请求包含以下只读属性：

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `requestCode` | `Int` | 传统 `startActivityForResult` 请求码，默认 `-1` |
| `uri` | `String` | 原始请求 URI |
| `headers` | `Map<String, Any>` | 路由内部使用的请求头，不会自动写入 Intent/Fragment 参数 |
| `extras` | `Bundle` | 传递给目标的参数 |
| `routerType` | `RouterType` | 请求声明的类型，默认 `UNDEFINED`；通常由框架根据目标元数据返回类型 |
| `launcher` | `ActivityResultLauncher<Intent>?` | Activity Result API 启动器，可选 |

#### 创建请求

```kotlin
val response = OkRouter.build("okrouter://android/biz2")
    .header("source", "home")
    .putString("biz2Args", "hello")
    .start(context)
```

也可以直接使用公开的无参构造器：

```kotlin
val request = RouterRequest.Builder()
    .uri("okrouter://android/biz2")
    .routerType(RouterType.ACTIVITY)
    .build()

val response = OkRouter.start(request, context)
```

#### `RouterRequest.Builder` 方法

| 方法 | 说明 |
| --- | --- |
| `uri(uri: String)` | 设置路由 URI；`build()` 时不能为空 |
| `routerType(routerType: RouterType)` | 设置请求类型；通常无需手动指定 |
| `launcher(launcher: ActivityResultLauncher<Intent>)` | 指定 Activity Result 启动器 |
| `requestCode(requestCode: Int)` | 已废弃，改用 `launcher`；仅用于传统 Activity Result 启动 |
| `header(key, value)` / `putHeaders(map)` | 添加或批量添加 headers |
| `applyExtras { ... }` | 直接在 `Bundle` 上批量写入参数 |
| `putBoolean`、`putByte`、`putShort`、`putChar` | 写入基础类型参数 |
| `putInt`、`putLong`、`putFloat`、`putDouble` | 写入数值参数 |
| `putString`、`putCharSequence` | 写入文本参数 |
| `putIntegerArrayList`、`putStringArrayList`、`putCharSequenceArrayList` | 写入列表参数 |
| `putBooleanArray`、`putByteArray`、`putShortArray`、`putCharArray` | 写入基础类型数组 |
| `putIntArray`、`putLongArray`、`putFloatArray`、`putDoubleArray` | 写入数值数组 |
| `putStringArray`、`putCharSequenceArray` | 写入文本数组 |
| `putParcelable`、`putSerializable`、`putBundle` | 写入 Android 对象或序列化对象 |
| `putAll(Bundle)` / `putAll(PersistableBundle)` | 批量合并 Bundle 参数 |
| `build()` | 校验 URI、解析 query、生成 `RouterRequest` |
| `start(context, requestCode)` | 构建并启动请求；保留传统请求码入口 |
| `start(context, launcher)` | 构建并通过 Activity Result Launcher 启动 |
| `newBuilder()` | 从已有请求复制参数后继续修改 |

`build()` 的额外行为：

1. URI 的 query 参数会写入 `extras`；
2. 单值 query 写成 `String`，重复 key 写成 `ArrayList<String>`；
3. 原始 URI 会写入 `Extend.OKROUTER_RAW_URI`；
4. URI 为空时不会抛出到调用方，而是由 `start` 返回 `RouterResult.InvalidRequest`。

例如：

```kotlin
val response = OkRouter.build("okrouter://android/search?q=router&tag=a&tag=b")
    .start(context)

// 目标可以读取：
// extras["q"] == "router"
// extras["tag"] == ArrayList("a", "b")
```

### 3.3 `RouterResponse`

包名：`com.anymore.okrouter.core.RouterResponse`

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `uri` | `String` | 本次响应关联的 URI |
| `headers` | `Map<String, Any>` | 响应头；终端成功执行会包含 `Extend.OKROUTER_NOTE = "OK"` |
| `routerType` | `RouterType` | 目标类型 |
| `routerResult` | `RouterResult` | 执行结果 |
| `target` | `Any?` | Fragment 或 View 路由返回的对象；其他类型通常为空 |

响应也支持 `newBuilder()`、`toString()` 和 `RouterResponse.Builder`。Builder 需要设置 `uri`、`routerType`、`routerResult`；成功结果不能与 `RouterType.UNDEFINED` 组合。

不同目标类型的结果：

| 目标类型 | 终端行为 | `target` |
| --- | --- | --- |
| `ACTIVITY` | 创建 Intent，传入 extras 后启动 Activity；非 Activity 上下文会自动增加 `FLAG_ACTIVITY_NEW_TASK` | 通常为空 |
| `FRAGMENT` | 创建 Fragment，并将 extras 设置为 `arguments` | Fragment 实例 |
| `VIEW` | 使用 Context 创建 View，并将 extras 设置到 `Extend.OKROUTER_VIEW_EXTRAS` 对应的 View tag | View 实例 |
| `SERVICE` | 创建 Intent，传入 extras 后调用 `Context.startService` | 通常为空 |
| `HANDLER` | 创建 `RouterHandler` 并调用 `handle(context, request)` | 通常为空 |

Activity 启动优先级如下：

1. 如果设置了 `launcher`，调用 `launcher.launch(intent)`；
2. 否则如果 `requestCode > 0` 且上下文是 `Activity`，调用 `startActivityForResult`；
3. 否则调用 `startActivity`。

### 3.4 `RouterResult`

包名：`com.anymore.okrouter.core.RouterResult`

`RouterResult` 是 sealed class，当前子类型如下：

| 类型 | 含义 | 额外数据 |
| --- | --- | --- |
| `RouterResult.Ok` | 目标已成功执行 | 无 |
| `RouterResult.NotFound` | 没有匹配到目标路由 | 无 |
| `RouterResult.Intercepted` | 被拦截器短路 | 无 |
| `RouterResult.Failed(cause)` | 目标实例化或启动失败 | 原始 `Throwable` |
| `RouterResult.InvalidRequest(reason)` | `RouterRequest.Builder` 的 URI 为空时的构建失败 | 错误原因字符串 |
| `RouterResult.Custom(value)` | 自定义结果 | 自定义字符串值 |

调用方使用 `when` 处理时应保留 `else` 分支，因为未来版本可能增加新的子类型。

```kotlin
when (val result = response.routerResult) {
    RouterResult.Ok -> Unit
    RouterResult.NotFound -> showNotFound()
    RouterResult.Intercepted -> Unit
    is RouterResult.Failed -> report(result.cause)
    is RouterResult.InvalidRequest -> showError(result.reason)
    is RouterResult.Custom -> handleCustom(result.value)
    else -> Unit
}
```

### 3.5 `RouterType`

包名：`com.anymore.okrouter.core.RouterType`

当前枚举值：`ACTIVITY`、`FRAGMENT`、`VIEW`、`SERVICE`、`HANDLER`、`UNDEFINED`。

### 3.6 `RouterHandler`

包名：`com.anymore.okrouter.core.RouterHandler`

用于将路由映射到自定义代码：

```kotlin
@Router(scheme = "https?", host = ".*", path = ".*")
class HttpSchemeHandler : RouterHandler {
    override fun handle(context: Context, request: RouterRequest) {
        val url = request.uri
        // 根据 URL 继续导航到应用内 Web 容器
    }
}
```

`handle` 返回 `Unit`。`LaunchInterceptor` 调用完成后会构造 `RouterResult.Ok` 响应；如果 `handle` 抛出异常，则会由终端执行器转换为 `RouterResult.Failed`。

### 3.7 `RouterInterceptor`

包名：`com.anymore.okrouter.core.RouterInterceptor`

拦截器契约：

```kotlin
@Interceptor(priority = 0)
class LoginInterceptor : RouterInterceptor {
    override fun intercept(
        context: Context,
        chain: RouterInterceptor.Chain
    ): RouterResponse {
        return if (isLoggedIn()) {
            chain.proceed(context, chain.request())
        } else {
            interceptRequest(chain.request())
        }
    }
}
```

`RouterInterceptor.Chain` 提供：

- `request(): RouterRequest`：读取当前请求；
- `proceed(context, request): RouterResponse`：继续执行下一个拦截器。

拦截器不调用 `proceed` 即可短路。`RouterInterceptor.interceptRequest(request)` 是一个辅助方法，会返回 `RouterResult.Intercepted`，并写入 `Extend.OKROUTER_NOTE` 说明被哪个拦截器拦截。

执行顺序：

1. 收集全局拦截器；
2. 收集 `@Router` 上声明的局部拦截器；
3. 通过集合去重；
4. 按 `@Interceptor.priority` 升序排序，数值越小越早执行；
5. 追加内部的 `LaunchInterceptor` 作为终端。

拦截器实例默认每次创建新对象；标记 `singleton = true` 时由工厂缓存实例。

### 3.8 `RouterLostHandler`

包名：`com.anymore.okrouter.core.RouterLostHandler`

当前 API 继承 `RouterHandler`，方法签名是：

```kotlin
interface RouterLostHandler : RouterHandler {
    override fun handle(context: Context, request: RouterRequest)
}
```

当 URI 没有匹配路由时，框架先调用 `OkRouter.routerLostHandler.handle`，然后返回 `RouterResult.NotFound`。默认处理器不执行任何操作。

示例：

```kotlin
OkRouter.routerLostHandler = object : RouterLostHandler {
    override fun handle(context: Context, request: RouterRequest) {
        analytics.record("router_not_found", request.uri)
        OkRouter.build("okrouter://android/404")
            .putString("lost_uri", request.uri)
            .start(context)
    }
}
```

注意：当前处理器的返回值是 `Unit`，重定向需要在处理器内部再次发起路由请求；它不会改变当前未命中响应的 `RouterResult.NotFound`。

## 4. 注解 API

### 4.1 `@Router`

包名：`com.anymore.okrouter.annotation.Router`

`@Router` 只能标注类，保留策略为 `BINARY`。插件会根据目标类的继承关系推导 `RouterType`。

```kotlin
@Router(
    scheme = "okrouter",
    host = "android",
    path = "/detail",
    interceptors = [LoginInterceptor::class],
    interceptorsByAlias = ["LoginCheck"],
    priority = 0,
    desc = "详情页"
)
class DetailActivity : AppCompatActivity()
```

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `scheme` | `String` | `""` | URI scheme；支持正则表达式 |
| `host` | `String` | `""` | URI host；支持正则表达式 |
| `path` | `String` | 无，必填 | URI path；支持正则表达式 |
| `interceptors` | `Array<KClass<out RouterInterceptor>>` | `[]` | 通过 Class 直接绑定局部拦截器 |
| `interceptorsByAlias` | `Array<String>` | `[]` | 通过 `@Interceptor.alias` 绑定拦截器 |
| `priority` | `Int` | `0` | 正则路由的匹配优先级；越小越优先 |
| `desc` | `String` | `""` | 用于生成路由文档的描述 |

允许的路由目标类：

- `Activity` 子类；
- `Fragment` 子类；
- `View` 子类；
- `Service` 子类；
- `RouterHandler` 实现类。

插件校验规则：

- `path` 不能为空；
- 如果 `path` 不包含正则字符，必须以 `/` 开头；
- 未实现上述目标类型的类不能标注 `@Router`；
- 稳定路由 URI 或完全相同的正则路由重复注册时，构建失败。

### 4.2 `@Interceptor`

包名：`com.anymore.okrouter.annotation.Interceptor`

```kotlin
@Interceptor(
    alias = "LoginCheck",
    priority = Int.MIN_VALUE,
    global = true,
    singleton = true,
    desc = "登录校验"
)
class GlobalLoginInterceptor : RouterInterceptor
```

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `alias` | `String` | `""` | 供 `interceptorsByAlias` 引用；非空 alias 必须全局唯一 |
| `priority` | `Int` | `0` | 拦截器执行优先级；越小越早执行 |
| `global` | `Boolean` | `false` | 是否加入所有路由的全局拦截器集合 |
| `singleton` | `Boolean` | `false` | 是否由工厂复用同一个实例 |
| `desc` | `String` | `""` | 用于生成路由文档的描述 |

标注 `@Interceptor` 的类必须实现 `RouterInterceptor`。未标注 `@Interceptor` 但被 `@Router.interceptors` 直接引用的类，会在运行时通过反射实例化为非单例拦截器。

## 5. URI 匹配规则

### 5.1 固定路由

当 `scheme`、`host`、`path` 都只包含字母、数字、下划线或 `/` 时，插件将其写入稳定路由表。例如：

```kotlin
@Router(scheme = "okrouter", host = "android", path = "/main")
class MainActivity : AppCompatActivity()
```

对应 URI 为 `okrouter://android/main`。

### 5.2 正则路由

只要 `scheme`、`host`、`path` 任一字段包含正则字符，就会进入正则路由表：

```kotlin
@Router(scheme = "https?", host = ".*", path = ".*")
class HttpSchemeHandler : RouterHandler
```

正则路由按以下规则选择：

1. 先尝试固定路由；
2. 再查询已经命中的正则 URI 缓存；
3. 按 `priority` 升序、URI 字典序遍历正则路由；
4. `scheme`、`host`、`path` 三个正则都匹配才算命中；
5. 命中结果按去除 query 和 fragment 后的 URI 写入缓存。

### 5.3 query 和 fragment

query 和 fragment 不参与路由表匹配。比如下面两个请求会命中同一个固定路由：

```text
okrouter://android/detail
okrouter://android/detail?id=42#top
```

但 query 参数仍会进入 `RouterRequest.extras`，供 Activity、Fragment、View 或 Handler 读取。

## 6. 构建插件扩展配置

扩展名：`okRouter`

| 配置 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `isIncremental` | `Boolean` | `false` | 增量配置字段；当前 API 暴露但默认关闭 |
| `sourceCompatibility` | `String` | `"1.7"` | 扩展字段，当前生成任务使用 Java 17 编译 |
| `createOkRouterDoc` | `Boolean` | `true` | 是否生成路由文档 |
| `okRouterDocFile` | `String` | `""` | 路由文档输出绝对路径；为空时输出到 `<project.name>-OkRouter-Doc.md` |
| `setLogLevel(level)` | 方法 | 无 | 接受 `VERBOSE`、`DEBUG`、`INFO`、`WARN`、`ERROR` |

示例：

```groovy
okRouter {
    createOkRouterDoc = true
    okRouterDocFile = project.file("路由文档.md").canonicalPath
    setLogLevel "INFO"
}
```

生成的路由文档包含固定路由、正则路由和拦截器三部分，字段包括 URI、目标类、目标类型、拦截器、alias、priority、global、singleton 和描述。

## 7. 扩展函数与内部字段

包名：`com.anymore.okrouter.ktx`

### 7.1 兼容读取 Bundle 参数

提供 `Bundle` 和 `RouterRequest` 的兼容读取扩展：

- `getBooleanCompatibly`
- `getByteCompatibly`
- `getShortCompatibly`
- `getIntCompatibly`
- `getLongCompatibly`
- `getFloatCompatibly`
- `getDoubleCompatibly`
- `getStringCompatibly`

这些方法会在类型不一致时尝试从 `Number` 或 `String` 转换，转换失败则返回调用方传入的默认值。

```kotlin
val needLogin = request.getBooleanCompatibly("need_login", false)
val userId = request.getLongCompatibly("user_id", -1L)
```

### 7.2 从响应中读取目标对象

| 扩展 | 说明 |
| --- | --- |
| `RouterResponse.getView()` | 当 `routerType == VIEW` 时返回 `View?`，否则记录警告并返回 null |
| `RouterResponse.requireView()` | 获取 View；类型不符时抛出异常 |
| `RouterResponse.getFragment()` | 当 `routerType == FRAGMENT` 时返回 `Fragment?`，否则记录警告并返回 null |
| `RouterResponse.requireFragment()` | 当前源码返回类型声明为 `View`，与 Fragment 语义不一致；使用前应注意这一现状 |

### 7.3 `Extend` 常量

包名：`com.anymore.okrouter.core.Extend`

| 常量 | 用途 |
| --- | --- |
| `OKROUTER_RAW_URI` | 请求构建时保存原始 URI |
| `OKROUTER_VIEW_EXTRAS` | View 目标保存请求 extras 的 View tag ID |
| `OKROUTER_NOTE` | 拦截或终端执行附加的响应说明 |

## 8. 推荐接入流程

1. 在所有目标类和拦截器上声明 `@Router` / `@Interceptor`。
2. 在 application 模块应用 `OkRouterTransformPlugin`。
3. 配置并运行对应变体的 `okRouterRegister<Variant>` 和 `okRouterCompile<Variant>`。
4. 在 `Application.onCreate()` 配置 `routerLostHandler`，然后调用 `OkRouter.init(this)`。
5. 使用完整 URI 调用 `OkRouter.build(uri).start(context)`。
6. 根据 `RouterResponse.routerResult` 处理成功、未命中、拦截、参数非法和执行失败。
7. Fragment/View 路由从 `response.target` 或对应 KTX 扩展取出对象。

## 9. 当前行为注意事项

- 未命中处理器当前只返回 `Unit`；内部重定向需要再次调用路由 API，当前响应仍是 `NotFound`。
- query 参数不会影响匹配，但会写入 extras；原始 URI 另存于 `OKROUTER_RAW_URI`。
- Service 目标直接调用 `Context.startService`，仍受 Android 系统后台 Service 启动限制约束。
- 默认 `OkRouter.start` 依赖 `OkRouter.init` 保存的 Application；在库或测试环境中建议显式传入 Context。
- `@Router` 的路由目标类型由插件扫描时判定，不是运行时根据 `RouterRequest.routerType` 推导。
- `RouterResult` 是 sealed class，调用方不应假设当前子类型集合永久不变。
- 当前文档只描述源码中已经实现的旧版 `RouterLostHandler` 契约；`RouterLostOutcome`、事件观察器等设计稿 API 尚未纳入实现。

## 10. 相关源码

- [运行时入口](../okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt)
- [请求模型](../okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt)
- [响应模型](../okrouter/src/main/java/com/anymore/okrouter/core/RouterResponse.kt)
- [结果模型](../okrouter/src/main/java/com/anymore/okrouter/core/RouterResult.kt)
- [注解定义](../okrouter/src/main/java/com/anymore/okrouter/annotation)
- [拦截器契约](../okrouter/src/main/java/com/anymore/okrouter/core/RouterInterceptor.kt)
- [仓库与匹配](../okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt)
- [运行时分发](../okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt)
- [终端执行器](../okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt)
- [Gradle 插件](../okrouter-plugin/src/main/kotlin/com/anymore/okrouter)
- [示例应用](../example/demo-app)
