# OkRouter 阶段 A：正确性与稳定性 — 设计文档

> 设计日期：2026-08-02
> 范围：`okrouter` 运行时 + `okrouter-plugin` 构建插件
> 目标：修复已确认的正确性 bug 和线程安全隐患，使运行时行为可预测、多线程安全、错误可处理
> 依据：[OkRouter 架构与生产化评审](../OkRouter-架构与生产化评审.md)

## 概述

阶段 A 是 OkRouter 生产化路线图的第一阶段，聚焦于**无需破坏 API 兼容性的 bug 修复和安全隐患消除**。全部 7 项改动集中在 `okrouter` 运行时和 `okrouter-plugin` 插件侧，每个改动独立可测、可单独提交。

## 第一组：运行时正确性修复（P0）

### 1.1 正则缓存键不一致

**文件**：`okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt`

**现状**：

```kotlin
fun getMatchRouterMeta(uri: String): RouterMeta? {
    val clearedUri = Uri.parse(uri).buildUpon().clearQuery().build()
    // 第 81 行：用去掉 query 的 URI 读缓存 ✓
    result = dynamicRouters[clearedUri.toString()]
    // ...
    regexRouters.forEach {
        if (/* 正则匹配成功 */) {
            // 第 92 行：用原始 URI（含 query）写缓存 ✗
            dynamicRouters[uri] = it.value
        }
    }
}
```

读缓存用 `clearedUri`（不含 query），写缓存用原始 `uri`（含 query）。导致同一个路由 `/song/123?a=1` 和 `/song/123?a=2` 各自写入不同的缓存键，下一次请求 `/song/123?a=3` 读取 `clearedUri` 时缓存永远无法命中，白白重复正则匹配。

**修复**：将第 92 行改为 `dynamicRouters[clearedUri.toString()] = it.value`，读写统一使用规范化后的 URI。同时规范化 URI 时同时清除 query 和 fragment（`clearQuery().clearFragment()`），避免 fragment 也污染缓存键。

**测试**：修改现有 `match regex router and cache dynamic result` 测试用例，断言缓存键为不含 query 和 fragment 的规范化 URI；新增「同一路由不同 query 参数第二次命中缓存」「同一路由不同 fragment 第二次命中缓存」测试。

**已知限制**：`dynamicRouters` 缓存无容量上限，每个首次命中的规范化 URI 永久保留。这可能导致长运行进程的内存持续增长。阶段 C 将使用带容量上限的线程安全 LRU 缓存替代。

### 1.2 路由冲突构建期检测

**文件**：
- `okrouter-plugin/src/main/kotlin/com/anymore/okrouter/` — 代码生成逻辑
- `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt` — `registerStableRouter` / `registerRegexRouter`

**现状**：`registerStableRouter` 和 `registerRegexRouter` 在重复注册时静默忽略，保留第一个值。全量 classpath 的遍历顺序成为决定路由生效的隐式规则，冲突永远不会被发现。

**修复**：
1. 插件侧：在汇总阶段检测冲突，范围限定为：
   - 稳定路由重复 URI → 构建错误，指明冲突 URI 和冲突类名
   - 逐字相同的正则路由 → 构建错误（`Set` 相等即可检测）
2. 运行时 `registerStableRouter` 增加日志警告作为兜底。
3. 重叠正则检测（不同表达式匹配相同 URI）和稳定/正则相互遮蔽检测推迟到阶段 C，届时配合 KSP 模块索引和静态分析做完整的路由冲突分析。

**设计决策**：构建期以错误方式失败，而非警告。路由冲突是配置错误，与代码编译错误同级别对待。

**实现提示**：插件侧在 `AbsOkRouterAction` 或 `OkRouterRegisterTask` 中，汇总所有模块注解信息后、调用 JavaPoet 生成 `OkRouterLoader` 之前，做去重检测。注意 javassist 扫描未记录模块来源，错误信息用冲突类名定位而非模块名。

---

## 第二组：线程安全与确定性（P1）

### 2.1 WareHouse 线程安全

**文件**：`okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt`

**现状**：`stableRouters`、`dynamicRouters`、`regexRouters`、`globalInterceptors`、`interceptorMetas` 全部使用普通 `HashMap`/`HashSet`/`LinkedHashMap`。`dynamicRouters` 在运行时被正则匹配写入，无任何同步保护。

**修复**：

| 集合 | 当前类型 | 替换为 |
|------|----------|--------|
| `stableRouters` | `HashMap` | `ConcurrentHashMap` |
| `dynamicRouters` | `HashMap` | `ConcurrentHashMap` |
| `regexRouters` | `LinkedHashMap` | `ConcurrentHashMap`（改用构造时传入的 `regexRouters` 列表迭代替代 LinkedHashMap 的顺序语义） |
| `globalInterceptors` | `HashSet` | `ConcurrentHashMap.newKeySet()` |
| `interceptorMetas` | `HashMap` | `ConcurrentHashMap` |

**设计决策**：使用 `ConcurrentHashMap` 而非 `synchronized` 包装或不可变快照，原因是：(1) 改动最小化，集合操作语义不变；(2) 读写比例高（大部分集合只在 init 时写入、运行时只读），`ConcurrentHashMap` 读操作无锁；(3) 不可变快照模式更适合阶段 C 引入的路由表重构。

`regexRouters` 原先使用 `LinkedHashMap` 以保证正则路由按 `priority` 顺序写入后的遍历顺序。修复时改为在 `getMatchRouterMeta` 中先将 `regexRouters` entrySet 按 `priority` 排序再遍历，以消除对插入顺序的依赖。同优先级时以 `RouterUri.toString()` 作为平局键，保证迭代顺序确定且可复现，与 2.2 的确定性原则一致。

### 2.2 拦截器稳定排序

**文件**：`okrouter/src/main/java/com/anymore/okrouter/core/internal/PriorityRouterInterceptorComparator.kt`

**现状**：Comparator 仅比较 `priority`，同优先级的拦截器顺序取决于 `HashSet` 迭代顺序——不可预测。

**修复**：增加 `className` 作为二级排序键。排序规则：
1. `priority` 升序（数值越小越优先）
2. `className` 字典序（稳定平局裁决）

**影响**：同优先级的拦截器执行顺序变为确定的（按类名字典序），这是可文档化、可测试的行为。

### 2.3 单例 Factory 线程安全

**文件**：`okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterInterceptorFactory.kt`

**现状**：

```kotlin
fun create(): RouterInterceptor {
    return if (singleton) {
        instance ?: newInstance().also { instance = it }  // 非原子操作
    } else {
        newInstance()
    }
}
```

并发首访时，两个线程可能同时看到 `instance == null`，各自调用 `newInstance()`，创建两个实例。

**修复**：使用 `synchronized` double-check locking：

```kotlin
fun create(): RouterInterceptor {
    return if (singleton) {
        instance ?: synchronized(this) {
            instance ?: newInstance().also { instance = it }
        }
    } else {
        newInstance()
    }
}
```

### 2.4 初始化生命周期

**文件**：`okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt`

**现状**：
- `init()` 可重复调用，无提示
- `application` 使用 `lateinit var`，在未初始化时调用 `start()` 会抛出 `UninitializedPropertyAccessException`，信息不明确

**关键问题**：当前 `start()` 签名使用了 `@JvmOverloads` + 默认参数 `context: Context = application`。Kotlin 的 `@JvmOverloads` 会生成 1 参的 synthetic 重载，该重载在进入方法体**之前**先求值默认参数 `application`。若未调用 `init()`，`UninitializedPropertyAccessException` 在方法体执行前就抛出，任何方法体内的检查都无法生效。同样的问题也存在于 `RouterRequest.Builder.start()`。

**修复**：
1. 将 `internal lateinit var application: Application` 改为 `internal var application: Application? = null`
2. `init()` 中赋值 `application = context.applicationContext as Application`，并设置 `initialized = true`；重复调用时记录警告并跳过
3. `start()` 内部检查 `application` 是否为 null，为 null 时抛出 `IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")`
4. 同样修复 `RouterRequest.Builder.start()` 中的默认参数问题
5. 暴露 `fun isInitialized(): Boolean` 供调用方检查

---

## 第三组：错误模型（P1）

### 3.1 RouterResult 扩展

**文件**：
- `okrouter/src/main/java/com/anymore/okrouter/core/RouterResult.kt`
- `okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt`
- `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt`

**现状**：`RouterResult` 仅涵盖成功路径（`Ok`、`NotFound`、`Intercepted`、`Custom`）。目标实例化失败、参数非法、系统拒绝启动 Service 等情况直接抛出异常，调用方无法通过返回值处理。

**修复**：

```kotlin
sealed class RouterResult(val value: String) {
    /** 路由成功 */
    object Ok : RouterResult("Ok")
    /** 目标未找到 */
    object NotFound : RouterResult("NotFound")
    /** 被拦截器拦截 */
    object Intercepted : RouterResult("Intercepted")
    /** 执行失败（实例化异常、系统拒绝等） */
    class Failed(val cause: Throwable) : RouterResult("Failed")
    /** 请求参数非法（URI 格式错误等） */
    class InvalidRequest(val reason: String) : RouterResult("InvalidRequest")
    /** 自定义结果 */
    class Custom(value: String) : RouterResult(value)
}
```

对应改动：
- `LaunchInterceptor`：catch 目标创建和启动异常，返回 `Failed(cause)`
- `RouterRequest.Builder.start()`：参数校验失败时（如 URI 为空），catch 并返回 `InvalidRequest(reason)`。注意校验发生在 `Builder` 侧，不在 `RouterDispatcher`——到达 dispatcher 的请求已经是 build 完成的合法对象
- `RouterDispatcher`：`getInterceptorInstance()` 如果拦截器实例化抛异常（如构造函数依赖注入未就绪），catch 并返回 `Failed(cause)`。当前异常会未经处理穿透到调用方
- 所有 `Failed` 和 `InvalidRequest` 结果在生成时通过 `OkRouter.logger` 输出 error 级别日志，包含请求 URI 和异常/原因，确保线上可排查

**兼容性说明**：`RouterResult` 给 sealed class 新增子类型是**源码不兼容**变更。消费者的穷尽 `when`（不带 `else` 分支）在重新编译时会失败。二进制兼容（已编译 class）不受影响。发布说明应标注此变更，并建议消费者使用 `when` 时始终包含 `else` 分支以保持前向兼容。

**错误边界声明**：3.1 的异常捕获范围限定为 `LaunchInterceptor`（目标创建和启动）和拦截器实例化（`getInterceptorInstance()`）。用户自定义 `RouterInterceptor.intercept()` 方法内部抛出的异常不在捕获范围内——这些异常由业务方自行处理，框架不介入。

---

## 验收标准

每项修复通过以下标准视为完成：

| 修复项 | 验收标准 |
|--------|----------|
| 1.1 缓存键 | 带不同 query 参数的同一路由第二次请求命中缓存，不再执行正则匹配 |
| 1.2 路由冲突 | 重复 URI 注册触发 Gradle 构建失败，错误信息指明冲突 URI |
| 2.1 线程安全 | 多线程并发导航 1000 次无数据竞争（压力测试 + 单例计数验证） |
| 2.2 稳定排序 | 同优先级拦截器顺序在不同构建、不同设备上一致 |
| 2.3 Factory | 并发 100 线程获取单例拦截器，只创建一个实例 |
| 2.4 初始化 | 未 init 调用 start 抛出明确 `IllegalStateException`；重复 init 输出警告 |
| 3.1 错误模型 | 目标 Activity 不存在时返回 `Failed` 而非 crash；参数非法返回 `InvalidRequest`；拦截器实例化异常返回 `Failed` |

---

## 不在范围内

- 协程/异步 API（阶段 C）
- 类型安全路由声明（阶段 C）
- 可观测性埋点和追踪（阶段 D）
- 动态模块支持（阶段 D）
- R8/增量构建测试（阶段 B 补充）
