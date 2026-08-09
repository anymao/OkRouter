# OkRouter API 参考

本文描述 `okrouter` 运行时库的公开使用面，以及应用模块需要使用的 Gradle 插件。内部的 `warehouse` 与 `core.internal` 类型不是稳定扩展点。

## 初始化与入口：`OkRouter`

| API | 说明 |
|---|---|
| `init(context: Context)` | 加载编译期生成的 `OkRouterLoader`；重复调用会被忽略并记录警告。 |
| `isInitialized(): Boolean` | 返回是否已成功初始化。 |
| `build(uri: String): RouterRequest.Builder` | 构建可配置的路由请求。现有字符串路由入口保持兼容。 |
| `resolve(uri: String): RouterMatch` | 无副作用预检。空白 URI 返回 `Invalid`，无匹配返回 `NotFound`。 |
| `resolve(request: RouterRequest): RouterMatch` | 对已构造请求做无副作用预检。 |
| `start(request: RouterRequest, context: Context? = null): RouterResponse` | 执行路由；未传 context 时要求已初始化。 |
| `routerLostHandler: RouterLostHandler` | 未匹配目标时调用的 Handler，默认空实现。 |
| `logger: Logger` | 日志实现，默认为 `Logger.Default`。 |
| `addObserver(observer)` / `removeObserver(observer)` | 注册或移除旁路 `RouterObserver`；同一实例重复注册会被忽略。 |

## 注解

### `@Router`

| 参数 | 类型 / 默认值 | 含义 |
|---|---|---|
| `scheme` | `String = ""` | URI scheme，可使用正则。 |
| `host` | `String = ""` | URI host，可使用正则。 |
| `path` | `String` | URI path，可使用正则。 |
| `interceptors` | `Array<KClass<out RouterInterceptor>> = []` | 路由局部拦截器。 |
| `interceptorsByAlias` | `Array<String> = []` | 通过 `@Interceptor.alias` 引用的局部拦截器。 |
| `priority` | `Int = 0` | 正则规则优先级，越小越先匹配。 |
| `desc` | `String = ""` | 路由描述，进入生成文档与 `RouterDestination.description`。 |

可标记的目标类型：`Activity`、`Fragment`、`View`、`Service`、`RouterHandler` / `RouterHandlerV2`。

### `@Interceptor`

| 参数 | 类型 / 默认值 | 含义 |
|---|---|---|
| `alias` | `String = ""` | 可被 `interceptorsByAlias` 引用的全局唯一别名。 |
| `priority` | `Int = 0` | 执行顺序，越小越靠前。 |
| `global` | `Boolean = false` | 为 true 时进入每条路由的拦截器链。 |
| `singleton` | `Boolean = false` | 为 true 时工厂在并发下只创建一个实例。 |
| `desc` | `String = ""` | 生成路由文档使用的描述。 |

## 请求：`RouterRequest` 与 `Builder`

`RouterRequest` 的只读字段为 `requestCode`、`uri`、`headers`、`extras`、`routerType` 与 `launcher`。`newBuilder()` 会复制 headers 与 extras。

| Builder API | 作用 |
|---|---|
| `uri(String)` | 设置必填 URI；空值或空字符串时 `build()` 抛 `IllegalStateException`。 |
| `requestCode(Int)` | 已弃用。调用 `start(context, requestCode)` 会设置它；仅 Activity 场景生效。 |
| `routerType(RouterType)` | 设置类型提示；默认 `UNDEFINED`。 |
| `launcher(ActivityResultLauncher<Intent>)` | 使用 Activity Result API 启动 Activity。 |
| `header(key, value)` / `putHeaders(map)` | 添加请求 headers。 |
| `applyExtras { ... }` / `putAll(Bundle)` | 直接操作或合并 extras。 |
| `putBoolean`、`putByte`、`putChar`、`putShort`、`putInt`、`putLong`、`putFloat`、`putDouble`、`putString`、`putCharSequence`、`putSerializable`、`putParcelable`、`putBundle` | 写入对应标量或对象。 |
| `put*Array`、`putIntegerArrayList`、`putStringArrayList`、`putCharSequenceArrayList` | 写入 Android `Bundle` 支持的数组或列表。 |
| `build()` | 校验 URI，解析 query 到 extras，并生成 `RouterRequest`。 |
| `start(context: Context? = null, requestCode: Int = -1)` | 构造后执行；未初始化且未给 context 时抛异常。 |
| `start(context: Context, launcher)` | 构造后通过 Activity Result launcher 执行。 |

`build()` 会把 query 参数写入 extras：单值为 `String`，多值为 `ArrayList<String>`，并写入 `Extend.OKROUTER_RAW_URI`。

## 响应与结果

### `RouterResponse`

字段为 `uri`、`headers`、`routerType`、`routerResult`、`target`。使用 `RouterResponse.Builder` 创建，必须设置 URI、类型和结果；当结果为 `Ok` 时类型不能是 `UNDEFINED`。`newBuilder()` 可在保留现有字段的前提下修改响应。

### `RouterResult`

| 类型 | 含义 |
|---|---|
| `Ok` | 成功分发。 |
| `NotFound` | 没有找到路由目标。 |
| `Intercepted` | 被旧版拦截器中断。 |
| `Failed(cause)` | 目标创建、系统启动或 V2 执行失败。 |
| `InvalidRequest(reason)` | URI 或请求参数非法。 |
| `Custom(value)` | 业务自定义结果。 |

`RouterResult` 是可扩展的 sealed class；`when` 请保留 `else` 分支以兼容未来新增类型。

### `RouterType`

`ACTIVITY`、`FRAGMENT`、`VIEW`、`SERVICE`、`HANDLER`、`UNDEFINED`。

## 纯解析模型

| 类型 | 字段 / 语义 |
|---|---|
| `RouterMatch.Found` | `uri` 为完整输入 URI；`destination` 是规则、类型、描述；`parameters` 是只读 query 快照。 |
| `RouterMatch.NotFound` | URI 有效，但当前路由表没有匹配目标。 |
| `RouterMatch.Invalid(reason)` | URI 为空或无法构造请求。 |
| `RouterDestination` | `uriPattern`、`type`、`description`。 |

`resolve` 不调用 `RouterLostHandler`，也不创建 Android 目标或写入正则命中缓存。

## V2 执行模型

| 类型 | 合约 |
|---|---|
| `RouterContext` | 提供 `appContext`、`request`、`destination`、`options`。仅由框架创建。 |
| `RouterOptions.DEFAULT` | 当前唯一可用的默认选项实例。 |
| `RouterOutcome.Completed(target?)` | 正常完成，可携带 target。 |
| `RouterOutcome.Redirect(uri, options)` | 请求框架重新解析并执行另一 URI。 |
| `RouterOutcome.Intercepted(reason?)` | 短路并返回拦截结果。 |
| `RouterOutcome.Failed(cause)` | 终止并转为失败响应。 |
| `RouterHandlerV2` | 实现 `handle(context: RouterContext): RouterOutcome`。 |
| `RouterInterceptorV2` | 实现 `intercept(chain: RouterChain): RouterOutcome`；调用 `chain.proceed()` 进入下游。 |
| `RouterChain` | 公开 `context` 和 `proceed()`。 |
| `RouterObserver` | `onResolved(match)` 与 `onFinished(context, outcome)`；异常被隔离。 |

旧 `RouterHandler` 和 `RouterInterceptor` 仍是稳定接口。V2 类型分别继承它们，因此可在同一注解注册与执行链中混用。

## 辅助扩展与常量

- `Bundle` / `RouterRequest`：`getBooleanCompatibly`、`getByteCompatibly`、`getShortCompatibly`、`getIntCompatibly`、`getLongCompatibly`、`getFloatCompatibly`、`getDoubleCompatibly`、`getStringCompatibly`。它们接受数值或字符串形式并提供默认值。
- `RouterResponse.getView()`、`requireView()`、`getFragment()`：按类型读取 target。
- `Extend.OKROUTER_RAW_URI`：原始请求 URI。
- `Extend.OKROUTER_FINAL_URI`：发生重定向后最终执行 URI。
- `Extend.OKROUTER_VIEW_EXTRAS`：View target 上保存 extras 的 tag resource id。
- `Extend.OKROUTER_NOTE`：框架使用的响应说明 header。

## Gradle 插件

`OkRouterTransformPlugin` 只能应用于 application 模块。它创建扩展 `okRouter`，主要配置：

| 配置 | 说明 |
|---|---|
| `createOkRouterDoc` | 是否生成路由文档。 |
| `okRouterDocFile` | 生成文档的目标路径。 |

每个 variant 会有 `okRouterRegister<Variant>` 和 `okRouterCompile<Variant>` 任务。前者扫描注解并生成 `OkRouterLoader`，后者编译生成源码。
