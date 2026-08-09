# OkRouter 架构与生产化评审

> 评审日期：2026-08-02
> 范围：`okrouter` 运行时、`okrouter-plugin` 构建插件、`okrouter-stub` 及示例工程。
> 定位：架构解释与现状参考，不是已实施的改造方案。

## 结论

OkRouter 已具备一个清晰且有价值的路由内核：通过 Gradle 在构建期生成注册表，在运行时使用 URI 匹配、拦截器链和统一执行器完成导航。它适合中小型、单 App 的组件化项目作为内部路由基础。

要达到生产级，还需要优先解决构建兼容性、注册表一致性、确定性匹配、异步控制和测试保障。当前最紧急的问题是：插件依赖已经在 AGP 8 移除的 Transform API，且声明的增量处理尚未实现。生产化的重点不应是先增加跨进程等功能，而是先建立现代构建链和可验证的运行时契约。

## 1. 评审范围与证据

本评审基于以下源码和示例调用链：

- [运行时入口](../okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt)
- [路由仓库与匹配器](../okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt)
- [请求、响应与拦截器契约](../okrouter/src/main/java/com/anymore/okrouter/core)
- [分发器、责任链与终端执行器](../okrouter/src/main/java/com/anymore/okrouter/core/internal)
- [Transform 插件与路由表生成逻辑](../okrouter-plugin/src/main/kotlin/com/anymore/okrouter)
- [登录拦截和 HTTP 兜底示例](../example)

已尝试执行 `:demo-app:assembleDebug`。验证未能完成，原因是项目所需的 Gradle 6.7.1 分发包下载超时，而非已确认的源码编译错误。当前仓库未发现单元测试或设备测试源码。

## 2. 当前架构

```text
业务模块
  @Router / @Interceptor
        │
        ▼
构建期：OkRouterTransform + Javassist 扫描 class
        │
        ├── 生成 OkRouterLoader
        ├── 生成目标对象 Factory
        └── 生成拦截器 Factory
        │
        ▼
Application.onCreate → OkRouter.init
        │
        ▼
WareHouse
  ├── stableRouters：精确 URI
  ├── regexRouters：正则 URI
  ├── dynamicRouters：正则命中缓存
  └── interceptorMetas / globalInterceptors
        │
        ▼
OkRouter.build(uri).start()
        │
        ▼
RouterDispatcher
  匹配目标 → 合并全局/局部拦截器 → 按优先级排序
        │
        ▼
RealRouterChain
        │
        ▼
LaunchInterceptor
  Activity | Fragment | View | Service | Handler
```

### 2.1 构建期注册

`@Router` 和 `@Interceptor` 均采用 `BINARY` 保留策略。插件扫描 App 全量 classpath，读取字节码中的注解，将稳定路由、正则路由和拦截器写入生成的 `OkRouterLoader` 静态初始化块。

运行时 `OkRouter.init()` 调用该 Loader，因而不需要扫描 Dex，也不需要通过反射发现已注解的路由目标。对于 Fragment、View、Handler 与已注解拦截器，插件还会生成直接 `new` 对象的 Factory。

### 2.2 运行时分发

1. `RouterRequest.Builder` 接收 URI、headers、Bundle extras 和 `ActivityResultLauncher`。
2. `build()` 解析 URI query，并保存原始 URI 到 extras。
3. `RouterDispatcher` 从 `WareHouse` 获取 `RouterMeta`。
4. 找不到目标时，执行可替换的 `RouterLostHandler`，并返回 `NotFound`。
5. 找到目标时，合并全局拦截器和路由局部拦截器，排序后追加 `LaunchInterceptor`。
6. `RealRouterChain` 依次执行拦截器。任何拦截器不调用 `proceed()` 即可短路。
7. 终端执行器按目标类型执行 Android 跳转或创建对象。

### 2.3 匹配规则

`WareHouse.getMatchRouterMeta()` 会先去掉 query，再按以下顺序匹配：

1. 精确路由表；
2. 正则路由命中缓存；
3. 逐项匹配 scheme、host、path 的正则表达式。

正则路由在插件侧会按 `priority` 升序写入，因此数值越小越优先。query 参数不参与路径匹配，而会写入 `Bundle`。

## 3. 现有设计的优点

### 3.1 运行时性能方向正确

预生成 Loader 和 Factory，使注册发生在初始化阶段，后续精确路由为 Map 查找。相比运行时 Dex 扫描，这更利于控制冷启动和包体行为。

### 3.2 责任链边界清楚

[`RealRouterChain`](../okrouter/src/main/java/com/anymore/okrouter/core/internal/RealRouterChain.kt) 的模型简洁。拦截器既能放行，也能中断；登录、埋点、权限、灰度等横切需求不必污染每个页面。

### 3.3 目标类型覆盖合理

[`LaunchInterceptor`](../okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt) 集中处理 Activity、Fragment、View、Android Service、Handler，避免各业务模块重复编写样板跳转逻辑。`ActivityResultLauncher` 已被纳入请求模型。

### 3.4 组件间解耦能力较好

业务模块只依赖注解和路由 API，最终 App 负责收集全量路由。示例中的 HTTP 正则 Handler 还能把外部网页链接统一转换到内部 Web 页面。

### 3.5 可降级、可审阅

`RouterLostHandler` 提供路由丢失回退；插件可以输出路由文档。这两个能力都是线上排障和组件治理的良好起点。

## 4. 缺口与风险

### P0：构建与正确性

#### 4.1 Transform API 已不可持续

插件通过 `android.registerTransform()` 注册 [`OkRouterTransform`](../okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterTransform.kt)。AGP 8 已移除 Transform API；官方建议根据场景迁移到 Android Components、Artifacts 或 Instrumentation API。[Android Gradle Plugin API 更新](https://developer.android.com/build/releases/gradle-plugin-api-updates)

同时，插件的增量分支仍为 `TODO`，但暴露了 `isIncremental` 开关。若未来开启该开关，目录和 Jar 的增量输入不会被复制或重新收集，结果可能不完整。

#### 4.2 正则缓存键不一致

匹配时读取缓存的键为清除 query 后的 URI；首次正则命中时写入缓存的键却是原始 URI。带 query 的正则路由将反复执行正则匹配，缓存无法生效。修复原则是只保留一个 `normalizedUri`，读取和写入都使用它。

#### 4.3 冲突未在构建期失败

稳定路由重复注册时仅保留第一个；没有错误信息。正则路由也未检测重叠关系。全量 classpath 的遍历顺序不应成为决定目标页面的隐式规则。

构建期应直接失败，或要求冲突方显式声明唯一且可审计的覆盖策略。

### P1：并发、确定性与生命周期

#### 4.4 可变全局仓库缺乏线程安全

`stableRouters`、`dynamicRouters`、`regexRouters`、`globalInterceptors` 都是普通可变集合。运行中正则匹配会写入 `dynamicRouters`。若导航来自并发线程，存在数据竞争风险。

#### 4.5 同优先级拦截器无稳定顺序

分发器先将拦截器装入 `HashSet`，再按优先级排序。同优先级元素的原始顺序不可保证，因此登录、埋点等同优先级逻辑可能在不同构建或设备上顺序不同。

排序应固定为：`priority → scope（全局/局部）→ moduleName → className`，并将该规则写入文档和测试。

#### 4.6 单例 Factory 不是严格单例

[`RouterInterceptorFactory`](../okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterInterceptorFactory.kt) 使用 `volatile` 字段，但“检查后创建”没有同步保护。并发首访可能创建多个实例。应使用 `lazy(LazyThreadSafetyMode.SYNCHRONIZED)`、锁或原子 CAS。

#### 4.7 初始化与多进程语义不明确

`OkRouter.init()` 不可重复诊断，且默认 `start()` 依赖已经初始化的全局 Application。应定义：未初始化、重复初始化、不同进程初始化和动态模块注册各自的明确行为。

### P1：执行模型与错误处理

#### 4.8 仅同步拦截器，无法表达等待与取消

当前 `RouterInterceptor` 只能同步返回 `RouterResponse`。登录、权限、动态模块安装等异步流程只能由业务自行保存请求。示例中的 `suspendRequest` 是单个静态变量，多个并发登录请求会相互覆盖。

生产 API 应支持协程或等价回调模型，并明确超时、取消、恢复和单次 `proceed()` 约束。

#### 4.9 错误结果表达不足

`RouterResult` 仅有 `Ok`、`NotFound`、`Intercepted` 和字符串型 `Custom`。目标实例化、拦截器异常、参数非法、系统拒绝启动 Service 都会直接抛出，调用方不能可靠处理。

#### 4.10 Service 路由缺少系统限制策略

终端执行器直接调用 `context.startService()`。Android 8 起后台 Service 有启动限制；新系统还会对后台启动前台服务施加限制。[Android Services 概览](https://developer.android.com/develop/background-work/services)

Service 路由应由单独的 `ServiceLauncher` 承担，显式区分普通、前台、绑定和拒绝启动的结果。

### P2：能力和治理

- 字符串 URI 与 `Bundle` 缺少类型安全、参数 schema、占位符提取和 IDE 重构保护。
- `RouterRequest.routerType` 不参与成功路径匹配与执行，却会影响部分错误响应，语义容易混淆。
- 正则表达式在每次未缓存命中时重新编译，复杂路由较多时会形成线性扫描和额外分配。
- 缺少访问控制、URL 白名单、参数大小限制和敏感参数脱敏策略。
- 缺少路由耗时、命中来源、拦截器耗时、错误分类等可观测性数据。
- 没有针对匹配、并发、R8、增量构建和系统组件的自动化测试。

## 5. 外部方案的可借鉴点

| 方案 | 借鉴点 | 对 OkRouter 的建议 |
|---|---|---|
| ARouter | 分组与按需初始化、路由降级、动态元数据、编译期生成、文档与 IDE 辅助 | 借鉴模块化索引和构建期校验，不照搬其历史构建实现。[ARouter](https://github.com/alibaba/ARouter) |
| DRouter | URI 路由、拦截器、异步 hold、Service SPI、动态注册、跨进程能力 | 借鉴其“路由、服务、页面、进程”分层。该项目已归档，不宜作为新的运行时依赖。[DRouter](https://github.com/didi/DRouter) |
| Jetpack Navigation | Back Stack、Deep Link、类型安全参数、转场、测试支持 | 将其作为 UI 导航后端或协作组件，不试图用它替代跨模块服务发现。[Jetpack Navigation](https://developer.android.com/guide/navigation) |

## 6. 生产级目标架构

```text
okrouter-api
  注解、RouteSpec、RouteOutcome、Interceptor 契约
        │
okrouter-ksp
  每个业务模块生成 ModuleRouteRegistry + 索引清单
        │
okrouter-gradle
  按变体汇总索引、检测冲突、生成 AppRouteRegistry
        │
okrouter-runtime
  ├── Registry：不可变快照与规范化匹配
  ├── Dispatcher：解析、策略、拦截器、错误映射
  ├── Navigator：Activity / Fragment / View / Handler / Service
  ├── Provider：跨模块服务发现
  └── Observability：追踪、指标、调试检查器
```

### 6.1 构建期

1. 每个模块用 KSP 生成本模块 `ModuleRouteRegistry` 和机器可读索引。
2. App 变体任务收集所有索引，检查 URI 重复、正则重叠、缺失拦截器和无效目标类型。
3. 生成 `AppRouteRegistry`，以直接方法调用加载模块 Registry，而非扫描全量 class/jar。
4. 使用 Android Components 与 Artifacts API 接入 AGP；为每个变体声明明确的输入、输出和缓存行为。
5. Dynamic Feature 安装完成后，通过显式 `register(moduleRegistry)` 加载模块路由。

### 6.2 匹配与注册表

- 用 `RouteKey(scheme, host, path)` 表示已规范化的精确路径。
- 正则在注册时预编译，并按“显式优先级、具体度、稳定 ID”排序。
- 运行时以不可变快照读取注册表；更新时构建新快照后原子替换。
- 使用带容量上限的线程安全 LRU 缓存正则命中。
- 所有 URI 只规范化一次；query 参数只在参数解析阶段使用。

### 6.3 执行与 API

建议将核心 API 定义为：

```kotlin
suspend fun navigate(request: RouteRequest): RouteOutcome
```

其中 `RouteOutcome` 至少包含：

- `Success`：目标、最终路由类型、追踪 ID；
- `NotFound`：规范化 URI 和可选降级结果；
- `Intercepted` / `Rejected`：拦截器及原因；
- `InvalidRequest`：参数或 URI 校验失败；
- `Timeout`：超时阶段；
- `Failed`：已分类的执行异常。

拦截器需要支持超时、取消、一次性 `proceed()` 与稳定顺序。UI Navigator 应切换至主线程；Handler 可以声明执行器；Android Service 由独立策略执行。

### 6.4 类型安全与安全边界

新增类型安全路由，例如：

```kotlin
@Destination("app://product/{id}")
data class ProductRoute(val id: Long)
```

程序内调用优先使用 `navigate(ProductRoute(id))`；外部 Deep Link 才走字符串 URI，并执行 scheme/host 白名单、参数 schema、长度限制、解码规范化和敏感字段脱敏。

## 7. 分阶段路线图

### 阶段 A：正确性与稳定性

- 修复正则缓存键。
- 引入稳定的拦截器二级排序。
- 重复精确路由构建失败；歧义正则要求显式策略。
- 使用不可变路由快照与线程安全缓存。
- 为未初始化、执行异常和 Service 拒绝启动定义结果模型。
- 为这些行为补齐 JVM 单元测试。

### 阶段 B：构建链迁移

- 用 Android Components / Artifacts API 替换 Transform API。
- 先实现正确的全量变体任务，再实现可验证的增量任务。
- 引入 KSP 模块索引和最终汇总 Registry。
- 使用 Gradle TestKit 覆盖 clean、incremental、Configuration Cache、AGP 8+ 与 R8。

### 阶段 C：异步与类型安全

- 引入协程导航、超时、取消和恢复机制。
- [TODO，延后立项] 推出类型安全 RouteSpec：内部模块调用优先使用类型化路由对象，由插件生成 URI 模板、参数编解码与校验；`build(String)` / 字符串 URI 路由继续保留为兼容层和外部 Deep Link 入口。
- 拆分各类 Navigator 与 Provider SPI。
- 增加 Deep Link 安全策略。

### 阶段 D：平台能力

- 动态模块注册与卸载。
- Service Provider 的别名、筛选和生命周期。
- 有明确业务需求后，再评估跨进程和插件化。

## 8. 验收标准

框架可被视为生产就绪的最低标准：

1. 能在目标 AGP 版本上完成 clean 与 incremental 构建，且生成的路由清单一致。
2. 路由冲突和正则歧义在 CI 构建期失败，而非在线上静默选择。
3. 多线程导航下，注册表与缓存没有数据竞争，拦截器顺序完全可预测。
4. 外部 URI、权限拒绝、超时、目标异常和 Service 限制均返回可处理的结果。
5. Debug 可查看路由表和完整追踪；Release 有采样指标和敏感字段脱敏。
6. 单元、Gradle 功能、R8、设备与性能测试进入 CI 门禁。

## 9. 推荐的下一步

先完成阶段 A 的小范围修复和测试，再单独立项迁移阶段 B。不要在 Transform API 的基础上叠加新功能。完成现代构建链、确定性注册表和异步执行契约后，Service SPI、动态模块和跨进程能力才有稳定的承载基础。
