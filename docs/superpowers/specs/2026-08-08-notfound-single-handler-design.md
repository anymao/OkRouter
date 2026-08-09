# OkRouter：未命中恢复模型设计

> 状态：已确认  
> 发布策略：下一个主版本  
> 范围：以单一、可返回结果的未命中处理器替换旧的副作用回调。

## 1. 目标与边界

路由未命中是可预期的业务结果，不应被观察系统记录为
`Failed(NoSuchElementException)`，也不应要求业务在回调中再次调用
`OkRouter.start()` 才能跳转到兜底页。

本设计只改造未命中恢复与其观测模型：

- 未命中处理器可明确返回未恢复、重定向恢复或失败。
- 兜底重定向纳入当前 Dispatcher 调用链。
- 所有观察事件拥有 URI、请求与调用链标识。
- 字符串 URI 路由、`build(uri).start()`、解析器和正常命中执行链继续存在。

不在本次范围内：Intent/App Link、Provider、动态模块、类型安全路由对象。

## 2. 破坏性变更

旧 API 被删除，不提供适配器或并行 V2：

```kotlin
interface RouterLostHandler {
    fun handle(context: Context, request: RouterRequest)
}
```

替换为唯一的新 API。`OkRouter.routerLostHandler` 保留原字段名，但类型的方法契约变化；已有实现必须迁移并重新编译。因此本变更随主版本发布。

```kotlin
interface RouterLostHandler {
    fun handle(context: RouterLostContext): RouterLostOutcome
}

sealed class RouterLostOutcome {
    data object NotHandled : RouterLostOutcome()

    data class Redirect(
        val uri: String,
        val options: RouterOptions = RouterOptions.DEFAULT
    ) : RouterLostOutcome()

    data class Failed(val cause: Throwable) : RouterLostOutcome()
}
```

框架默认安装一个返回 `NotHandled` 的 Handler；业务无需为“没有兜底策略”配置空实现。

`Redirect` 是唯一成功恢复方式。Handler 不直接启动 Activity，也不在内部调用
`OkRouter.start()`；它只声明下一跳 URI，统一由 Dispatcher 执行。

## 3. 上下文与观测 API

未命中不存在 `RouterDestination`，故不能复用需要 destination 的
`RouterContext`。

```kotlin
class RouterLostContext internal constructor(
    val appContext: Context,
    val request: RouterRequest,
    val originalUri: String,
    val attemptedUris: List<String>,
    val traceId: String
)
```

观察器收敛为一个事件入口，删除“只给 Match”与“nullable Context + Outcome”的分裂表达：

```kotlin
interface RouterObserver {
    fun onEvent(event: RouterEvent) = Unit
}

sealed class RouterEvent {
    data class NotFoundDetected(val context: RouterLostContext) : RouterEvent()

    data class Finished(
        val traceId: String,
        val originalUri: String,
        val finalUri: String,
        val result: RouterResult,
        val cause: Throwable? = null
    ) : RouterEvent()
}
```

同一条路由调用只生成一个 `traceId`。观察者先收到 `NotFoundDetected`，再收到一次终态 `Finished`，不会再把未命中强行归类为异常。

## 4. 调度与返回语义

```text
resolve 未命中
  → onEvent(NotFoundDetected)
  → RouterLostHandler.handle(lostContext)
  → NotHandled / Redirect / Failed
  → onEvent(Finished)
  → RouterResponse
```

| Handler 返回 | Dispatcher 行为 | 调用方收到的结果 |
|---|---|---|
| `NotHandled` | 不执行额外跳转 | `RouterResult.NotFound` |
| `Redirect(uri)` | 复用当前执行链执行目标 URI | 成功时 `RouterResult.Ok` |
| `Failed(cause)` | 停止当前执行 | `RouterResult.Failed(cause)` |
| Handler 抛异常 | 捕获并等价处理为 `Failed` | `RouterResult.Failed(cause)` |

重定向成功后，响应的 `uri` 保持最初请求 URI，最终实际 URI 写入既有
`Extend.OKROUTER_FINAL_URI`。这保持现有重定向响应的读取方式。

## 5. 执行状态与环路保护

Dispatcher 新增仅内部使用的单次执行状态：原始 URI、traceId、重定向深度与已尝试的规范化 URI 集合。

所有 URI 在解析前登记到该集合，包括未命中的 URI。这样当 `/404` 未注册且 Handler 再次返回 `Redirect("/404")` 时，框架会识别重复尝试并以 `RouterResult.Failed` 结束，而不是无限回调 Handler。

初始 URI 的重定向深度为 0；最多允许 8 次重定向。第 9 次重定向在执行前失败。正常 Handler/Interceptor 返回的 `RouterOutcome.Redirect` 与未命中 Handler 返回的 `RouterLostOutcome.Redirect` 共用这套限制。

## 6. 图示

- [优化流程图](../../../diagrams/notfound-v2-flow.svg)
- [组件设计图](../../../diagrams/notfound-v2-design.svg)
- [NotFound 时序图](../../../diagrams/notfound-v2-sequence.svg)

流程图和组件图同时提供 `.mmd`、`.svg`、`.png`、`.excalidraw`；时序图提供 `.mmd`、`.svg`、`.png`。

## 7. 迁移示例

旧写法：

```kotlin
OkRouter.routerLostHandler = object : RouterLostHandler {
    override fun handle(context: Context, request: RouterRequest) {
        OkRouter.start(OkRouter.build("okrouter://android/not-found").build(), context)
    }
}
```

新写法：

```kotlin
OkRouter.routerLostHandler = object : RouterLostHandler {
    override fun handle(context: RouterLostContext): RouterLostOutcome {
        return RouterLostOutcome.Redirect("okrouter://android/not-found")
    }
}
```

无兜底页时直接返回 `RouterLostOutcome.NotHandled`；兜底策略自身不能继续时返回 `RouterLostOutcome.Failed(cause)`。

## 8. 验收与测试

1. 未配置业务 Handler 时，未知 URI 返回 `NotFound`，并发出检测与终态事件。
2. `NotHandled` 返回 `NotFound`，事件包含原始 URI、请求和同一 traceId。
3. `Redirect` 成功执行兜底路由后返回 `Ok`，并保留原始 URI 与最终 URI 标记。
4. Handler 抛异常或返回 `Failed` 时返回 `Failed`，不向调用栈抛出 Handler 异常。
5. 未注册的兜底 URI、重复 URI 与超过八次重定向都会确定地失败，不产生递归。
6. 现有普通命中、拦截器重定向、Handler 重定向、Activity/Fragment/View/Service/Handler 路由回归通过。
7. 编译期测试验证旧 Handler 方法已不可用；使用新 Handler 的 Kotlin 与 Java 示例均可编译。

## 9. 实施顺序

1. 替换 `RouterLostHandler` 契约，新增 `RouterLostContext`、`RouterLostOutcome` 与事件模型。
2. 改造 Dispatcher 的未命中分支及统一执行状态，接入重定向与异常隔离。
3. 调整 Observer 注册与通知实现，删除旧观察回调。
4. 更新单元测试、Java/Kotlin 示例、API 文档和迁移说明。
5. 以主版本发布，并在 release note 中突出迁移步骤。
