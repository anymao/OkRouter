# OkRouter

一个 Android 路由组件，使用 **Gradle 插件编译期生成路由表**，并在运行时通过拦截器链进行分发。它避免了运行时大量扫描反射查找目标类的方式，核心能力为：

- 基于注解的静态路由声明（Activity / Fragment / Service / View / RouterHandler）
- 全局与局部拦截器链（按优先级执行）
- 稳定路由 / 正则路由自动匹配
- 路由文档自动生成
- 支持登录、鉴权、Web URL 兜底等场景

---

## 一、项目结构

```
OkRouter/
  okrouter/                 路由运行时库（核心 API 与分发逻辑）
  okrouter-stub/            仅包含编译时占位加载器（运行时会被插件生成代码替换）
  okrouter-plugin/          Gradle 插件源码（扫描注解并生成 OkRouterLoader）
  buildSrc/                 将 okrouter-plugin 作为构建脚本类库编译并提供插件实现类
  example/
    demo-app/              示例宿主应用（演示初始化与启动）
    demo-base/             公共依赖与工具
    demo-login/            登录模块（示例全局+局部拦截器）
    demo-web/              自定义 Handler 示例（https?://...）
    demo-biz1/ demo-biz2/  业务页面/视图/服务示例
```

## 发布到私有 Maven

当前发布版本由根目录 `gradle.properties` 中的 `VERSION` 统一控制，已设置为 `0.0.3`。本版本发布以下组件：

- `com.anymore:okrouter:0.0.3`：Android 路由运行时库
- `com.anymore:okrouter-plugin:0.0.3`：编译期路由表生成插件

请在 `~/.gradle/gradle.properties` 或 CI 环境变量中配置凭据。凭据不会保存在工程内：

```properties
maven_username=your_username
maven_password=your_password
# 可选：覆盖默认的阿里云私服地址
maven_release_url=https://your-maven/repository/releases/
maven_snapshot_url=https://your-maven/repository/snapshots/
```

也支持使用 `ALIYUN_USERNAME`、`ALIYUN_PASSWORD`、`MAVEN_RELEASE_URL` 和 `MAVEN_SNAPSHOT_URL` 环境变量。

发布正式版：

```bash
./gradlew :okrouter:publishReleasePublicationToPrivateMavenRepository \
  && (cd buildSrc && ../gradlew publishMavenPublicationToPrivateMavenRepository)
```

发布 `-SNAPSHOT` 版本时，将 `VERSION` 设为以 `-SNAPSHOT` 结尾的版本号，发布任务会自动选择快照仓库。

## 二、核心设计

### 1. 编译时：扫描注解并生成路由表

`OkRouterTransformPlugin` 在每个 application variant 下执行以下任务：

1. `OkRouterRegisterTask` 扫描 classpath 中所有 class/jar（Javassist）
2. 识别 `@Router`、`@Interceptor` 注解
3. 生成 `com.anymore.okrouter.warehouse.OkRouterLoader` 源码
4. 通过变体下游 Java 编译任务，把生成源码编译到变体 class 输出目录
5. 在 assemble 过程中确保生成类被打包

因此应用启动时不会再依赖“扫全量 class 查注解”；路由映射已在构建产物里落地。

### 2. 运行时：RouterDispatcher 执行流程

运行时主要数据结构：

- `WareHouse.stableRouters`：完全匹配路由（包含 scheme/host/path）
- `WareHouse.regexRouters`：正则路由候选
- `WareHouse.dynamicRouters`：命中正则后缓存，避免重复正则计算
- `WareHouse.globalInterceptors`：全局拦截器 Class 集合

请求链路：

1. 调用 `OkRouter.init(context)` 后，触发 `OkRouterLoader.load()`，完成注册
2. `OkRouter.build(uri).start(context)` 构建并发起路由请求
3. `RouterDispatcher.start()` 获取匹配路由元信息
4. 组装拦截器链：全局 + 页面级（`@Router` 指定）
5. 按优先级排序（`PriorityRouterInterceptorComparator`）
6. 最终追加 `LaunchInterceptor`
7. `RealRouterChain` 依次调用 `intercept`
8. `LaunchInterceptor` 根据类型进行最终分发
   - `ACTIVITY`：`startActivity` / `startActivityForResult` / `ActivityResultLauncher`
   - `FRAGMENT`：实例化 `Fragment`
   - `VIEW`：实例化 `View`
   - `SERVICE`：启动 Service
   - `HANDLER`：执行自定义 `RouterHandler`

如果匹配失败，触发 `RouterLostHandler`（默认空实现）并返回 `RouterResult.NotFound`。

## 三、支持的注解与 API

### 1. `@Router`

```kotlin
@Router(
    scheme = "okrouter",
    host = "android",
    path = "/login",
    interceptors = [LoginInterceptor::class],
    interceptorsByAlias = ["LoginCheckInterceptor"],
    priority = 0,
    desc = "登录页面"
)
```

参数说明：

- `scheme / host / path`：路由 URI 组装规则，支持正则表达式写法
- `interceptors`：直接绑定拦截器 class
- `interceptorsByAlias`：通过别名绑定拦截器（用于依赖隔离场景）
- `priority`：路由为正则时用于同类匹配优先级控制
- `desc`：生成路由文档使用

支持的目标类型：

- `Activity`
- `Fragment`
- `View`
- `Service`
- `RouterHandler`

### 2. `@Interceptor`

```kotlin
@Interceptor(
    alias = "LoginCheckInterceptor",
    priority = Int.MIN_VALUE,
    global = false,
    singleton = true,
    desc = "登录校验"
)
```

参数说明：

- `alias`：别名（可被 `@Router.interceptorsByAlias` 引用，需全局唯一）
- `priority`：越小优先级越高（会更早执行）
- `global`：是否全局生效
- `singleton`：是否使用单例实例（内部通过 factory 控制）
- `desc`：文档展示

### 3. 路由构建与启动

```kotlin
val response = OkRouter.build("okrouter://android/main")
    .putString("foo", "bar")
    .launcher(launcher) // 可选
    .start(context)
```

便捷示例（见 Demo）：

```kotlin
OkRouter.build("/biz1".toRoute())
    .start(this, launcher)
```

`toRoute()` 为项目内示例工具方法：

```kotlin
"/biz1".toRoute() -> "okrouter://android/biz1"
```

### 4. 回调与扩展

项目内提供 `Bundle` 与 `RouterRequest` 的兼容读取扩展：

- `getIntCompatibly` / `getStringCompatibly` 等
- `RouterResponse.getView()` / `requireView()`（目前 `requireFragment()` 返回类型存在拼写问题，仅用于本地兼容历史实现）

## 四、示例接入

### 1. App 模块接入插件

`example/demo-app/build.gradle` 的配置：

```kotlin
import com.anymore.okrouter.OkRouterTransformPlugin

plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'auto-service'
}

apply plugin: OkRouterTransformPlugin

okRouter {
    createOkRouterDoc = true
    okRouterDocFile = project.file("路由文档.md").canonicalPath
}
```

当前仓库内 `okrouter-plugin` 通过 `okrouter.properties` 声明了插件 id：`okrouter`，如果后续改为使用发布版插件可改为：

```kotlin
plugins {
    id("okrouter")
}
```

但在源码仓库场景，示例采用 `apply plugin: OkRouterTransformPlugin` 的方式以直接引用本地实现。

### 2. 依赖

- 主应用依赖公共/业务模块
- 业务/基础模块依赖 `project(":okrouter")`
- 示例中 `demo-base` 已统一集中对外输出 `okrouter` 及相关工具依赖

### 3. 初始化

在 `Application` 中执行：

```kotlin
override fun onCreate() {
    super.onCreate()
    OkRouter.routerLostHandler = object : RouterLostHandler {
        override fun handle(context: Context, request: RouterRequest) {
            OkRouter.build("/404".toRoute())
                .putString(Common.EXTRA_LOST_URI, request.uri)
                .start(context)
        }
    }
    OkRouter.init(this)
}
```

### 4. 典型场景

- 页面跳转：声明 `@Router(path = "/main")` 的 `Activity`
- 登录拦截：`@Interceptor(global = true)` + `@Router(interceptors = [...])`
- Web 兜底：`@Router(scheme="https?", host=".*", path=".*")` + 实现 `RouterHandler`
- 返回 VIEW：`@Router(path="/custom_text_view")` + `RouterResponse.target as View`

## 五、路由文档（README 同步）

插件可以自动生成文档，默认结构：

1. 固定路由表（`okrouter://...`）
2. 正则路由表（`https?://.*`）
3. 拦截器列表（class / alias / priority / global / singleton / desc）

输出文件默认在 `project.name + "-OkRouter-Doc.md"`；也可以通过 `okRouterDocFile` 指定路径。

## 六、版本与环境

- AGP：8.13.0
- Gradle：8.13
- Kotlin：2.1.0
- JDK：17
- minSdk（核心库）：17

## 七、常用命令

```bash
# 全量构建
./gradlew clean build

# 仅构建示例 App
./gradlew :demo-app:assembleDebug

# 安装示例（调试）
./gradlew :demo-app:installDebug

# 查看生成路由文档
cat example/demo-app/路由文档.md
```

## 八、draw.io 架构图

本仓库新增 `docs/okrouter-architecture.drawio`，包含：

1. **编译时生成链路图**（注解扫描 → 生成 `OkRouterLoader`）
2. **运行时分发链路图**（init → 匹配路由 → 拦截器链 → Launch）

可直接用 draw.io 打开 `.drawio` 文件进行编辑和导出（PNG / SVG）。

## 九、局限与注意点

1. 正则匹配时会先比较稳定路由，未匹配再执行正则匹配；匹配会清理 URI 的 query 用于路由 key 比对。
2. 目前 `@Router.path` 对于非正则写法要求以 `/` 开头。
3. 部分代码示例与历史实现混合了 Kotlin/JavaPoet 及历史兼容代码，若新增 `RouterType` 或拦截器生命周期，建议同步更新：
   - `okrouter-plugin` 的生成策略
   - 路由文档输出逻辑
   - Demo 验证清单
4. 代码仓库会在提交层保留一些兼容注释、历史验证内容（例如 AGP 迁移 notes），阅读时以 `README` 与 `CLAUDE.md` 为运行时入口文档。
