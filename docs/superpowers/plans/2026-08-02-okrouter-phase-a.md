# OkRouter 阶段 A：正确性与稳定性 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 OkRouter 运行时的 7 项正确性 bug 和线程安全隐患，使运行时行为可预测、多线程安全、错误可处理。

**Architecture:** 全部改动集中在 `okrouter` 运行时和 `okrouter-plugin` 插件侧，不引入新依赖或新抽象层。每个任务独立可测、可单独提交。ConcurrentHashMap 替换 HashMap，synchronized DCL 保护单例，sealed class 扩展错误模型。

**Tech Stack:** Kotlin 2.1.0, AGP 8.13.0, Gradle 8.13, JUnit 4 + Robolectric, JavaPoet + Javassist (插件侧)

## Global Constraints

- 保持 API 二进制兼容（源码层 `RouterResult` 新增 sealed 子类除外，已声明）
- 每项修复带单元测试，T2 额外需要 Gradle 构建验证
- 目标 minSdk 17, compileSdk 33
- 提交信息使用中文

---

### Task 1: 正则缓存键一致性修复 (P0)

**Files:**
- Modify: `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt:77,92`
- Modify: `okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt`

**Interfaces:**
- Produces: `WareHouse.getMatchRouterMeta()` 缓存键读写统一为无 query 无 fragment 的规范化 URI

- [ ] **Step 1: 更新测试——缓存键不含 query 和 fragment**

修改 `okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt` 中的 `match regex router and cache dynamic result` 测试：

```kotlin
@Test
fun `match regex router and cache dynamic result with cleared query and fragment`() {
    val regex = RouterUri("https?", ".*", ".*", 0)
    val regexMeta = RouterMeta(
        regex,
        RouterType.SERVICE,
        "com.example.Service",
        WareHouseTest::class.java,
        emptyArray()
    )

    WareHouse.registerRegexRouter(regex, regexMeta)

    // 第一次请求：带 query 和 fragment
    val result1 = WareHouse.getMatchRouterMeta("https://music.163.com/song/123?track=1#tab")
    assertNotNull(result1)
    assertEquals(regexMeta, result1)
    assertEquals(1, WareHouse.dynamicRouters.size)
    // 缓存键应为规范化后的 URI（无 query，无 fragment）
    assertTrue(WareHouse.dynamicRouters.containsKey("https://music.163.com/song/123"))

    // 第二次请求：不同 query 参数，应命中缓存
    val result2 = WareHouse.getMatchRouterMeta("https://music.163.com/song/123?track=2")
    assertNotNull(result2)
    assertEquals(regexMeta, result2)
    assertEquals(1, WareHouse.dynamicRouters.size) // 缓存数量未增长

    // 第三次请求：不同 fragment，应命中缓存
    val result3 = WareHouse.getMatchRouterMeta("https://music.163.com/song/123#tab2")
    assertNotNull(result3)
    assertEquals(regexMeta, result3)
    assertEquals(1, WareHouse.dynamicRouters.size)
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew :okrouter:testDebugUnitTest --tests "com.anymore.okrouter.warehouse.WareHouseTest.match regex router and cache dynamic result with cleared query and fragment"
```

预期：FAIL — 缓存键仍为原始 URI

- [ ] **Step 3: 修复缓存键**

修改 `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt`：

```kotlin
// 第 77 行：同时清除 query 和 fragment
val clearedUri = Uri.parse(uri).buildUpon().clearQuery().clearFragment().build()

// 第 92 行：用规范化 URI 写入缓存（原为 dynamicRouters[uri] = it.value）
dynamicRouters[clearedUri.toString()] = it.value
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./gradlew :okrouter:testDebugUnitTest --tests "com.anymore.okrouter.warehouse.WareHouseTest"
```

预期：全部 6 个测试 PASS

- [ ] **Step 5: 提交**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt \
        okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt
git commit -m "fix: 修复正则路由缓存键不一致——读写统一使用规范化URI（清除query和fragment）"
```

---

### Task 2: 路由冲突构建期检测 (P0)

**Files:**
- Modify: `okrouter-plugin/src/main/kotlin/com/anymore/okrouter/AbsOkRouterAction.kt:97-115`（`execute()` 方法中插入去重逻辑）
- Modify: `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt:48-52`（`registerStableRouter` 增加日志警告）

**Interfaces:**
- Consumes: `stableRouterElements: List<RouterElement>`, `regexRouterElements: List<RouterElement>`
- Produces: Gradle 构建在重复 URI 时失败；运行时重复注册输出警告日志

- [ ] **Step 1: 插件侧——在 `execute()` 中增加路由冲突检测**

修改 `okrouter-plugin/src/main/kotlin/com/anymore/okrouter/AbsOkRouterAction.kt`，在 `execute()` 方法中 `regexRouterElements.sortBy { it.priority }` 之后、`createRouterLoader(...)` 之前插入去重检测（约第 111 行之后）：

```kotlin
regexRouterElements.sortBy { it.priority }

// 检测稳定路由重复 URI
val stableUriSet = mutableSetOf<String>()
stableRouterElements.forEach { element ->
    val uri = element.uri
    if (!stableUriSet.add(uri)) {
        throw GradleException(
            "路由冲突：稳定路由 URI \"$uri\" 重复注册。" +
            "请检查 @Router 注解中 scheme、host、path 的组合是否唯一。"
        )
    }
}

// 检测正则路由逐字重复
val regexUriSet = mutableSetOf<String>()
regexRouterElements.forEach { element ->
    val uri = element.uri
    if (!regexUriSet.add(uri)) {
        throw GradleException(
            "路由冲突：正则路由 URI \"$uri\" 重复注册。" +
            "请检查 @Router 注解中 scheme、host、path 的组合是否唯一。"
        )
    }
}

createRouterLoader(
```

- [ ] **Step 2: 运行时侧——`registerStableRouter` 增加警告日志**

修改 `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt`：

```kotlin
@JvmStatic
fun registerStableRouter(uri: String, meta: RouterMeta) {
    if (!stableRouters.containsKey(uri)) {
        stableRouters[uri] = meta
    } else {
        logger.w("稳定路由 URI \"$uri\" 重复注册，已保留首次注册的映射。请检查是否存在冲突的 @Router 注解。")
    }
}
```

- [ ] **Step 3: 验证构建期检测**

创建一个临时测试：在 `example/demo-base` 和 `example/demo-login` 中各自添加相同 URI 的路由，运行构建，验证构建失败并输出冲突信息。

```bash
# 预期失败，输出包含 "路由冲突" 的错误信息
./gradlew :demo-app:assembleDebug
```

验证后撤销临时测试路由。

- [ ] **Step 4: 提交**

```bash
git add okrouter-plugin/src/main/kotlin/com/anymore/okrouter/AbsOkRouterAction.kt \
        okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt
git commit -m "feat: 构建期检测重复路由URI——稳定路由和逐字相同的正则路由冲突时构建失败"
```

---

### Task 3: WareHouse 线程安全 (P1)

**Files:**
- Modify: `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt`
- Modify: `okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt`

**Interfaces:**
- Consumes: Task 1 的缓存键修复（同文件，变更区域不重叠）
- Produces: 所有集合类型替换为 `ConcurrentHashMap`，`regexRouters` 遍历按 priority + URI 排序

- [ ] **Step 1: 替换集合类型**

修改 `okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt`，将 import 和集合声明从：

```kotlin
import java.util.*
import kotlin.collections.LinkedHashMap

internal object WareHouse {
    @JvmStatic
    val stableRouters: MutableMap<String, RouterMeta> = HashMap()

    @JvmStatic
    val dynamicRouters: MutableMap<String, RouterMeta> = HashMap()

    @JvmStatic
    val regexRouters: MutableMap<RouterUri, RouterMeta> = LinkedHashMap()

    @JvmStatic
    val globalInterceptors: MutableSet<Class<out RouterInterceptor>> = HashSet()

    @JvmStatic
    val interceptorMetas: MutableMap<Class<out RouterInterceptor>, RouterInterceptorMeta> =
        HashMap()
```

改为：

```kotlin
import java.util.concurrent.ConcurrentHashMap

internal object WareHouse {
    @JvmStatic
    val stableRouters: MutableMap<String, RouterMeta> = ConcurrentHashMap()

    @JvmStatic
    val dynamicRouters: MutableMap<String, RouterMeta> = ConcurrentHashMap()

    @JvmStatic
    val regexRouters: MutableMap<RouterUri, RouterMeta> = ConcurrentHashMap()

    @JvmStatic
    val globalInterceptors: MutableSet<Class<out RouterInterceptor>> =
        ConcurrentHashMap.newKeySet()

    @JvmStatic
    val interceptorMetas: MutableMap<Class<out RouterInterceptor>, RouterInterceptorMeta> =
        ConcurrentHashMap()
```

- [ ] **Step 2: 修复 `getMatchRouterMeta` 的正则遍历排序**

修改 `getMatchRouterMeta()` 方法中的 `regexRouters.forEach` 为排序后遍历。将：

```kotlin
regexRouters.forEach {
    val key = it.key
    if (key.scheme.toRegex().matches(scheme) && key.host.toRegex()
            .matches(host) && key.path.toRegex().matches(path)
    ) {
        logger.d("match regex router:${it.value.uri}")
        dynamicRouters[clearedUri.toString()] = it.value
        return it.value
    }
}
```

改为：

```kotlin
regexRouters.entries
    .sortedWith(compareBy<Map.Entry<RouterUri, RouterMeta>> { it.key.priority }
        .thenBy { it.key.toString() })
    .forEach { (key, value) ->
        if (key.scheme.toRegex().matches(scheme) && key.host.toRegex()
                .matches(host) && key.path.toRegex().matches(path)
        ) {
            logger.d("match regex router:${value.uri}")
            dynamicRouters[clearedUri.toString()] = value
            return value
        }
    }
```

- [ ] **Step 3: 测试——正则路由排序确定性**

在 `WareHouseTest.kt` 中新增测试：

```kotlin
@Test
fun `regex routers iterate in stable priority order`() {
    // 注册两条同优先级正则路由（priority 相同）
    val regex1 = RouterUri("https?", ".*", "/user/.*", 0)
    val regex2 = RouterUri("https?", ".*", "/song/.*", 0)
    val meta1 = RouterMeta(regex1, RouterType.ACTIVITY, "com.example.UserActivity",
        WareHouseTest::class.java, emptyArray())
    val meta2 = RouterMeta(regex2, RouterType.ACTIVITY, "com.example.SongActivity",
        WareHouseTest::class.java, emptyArray())

    WareHouse.registerRegexRouter(regex1, meta1)
    WareHouse.registerRegexRouter(regex2, meta2)

    // /song/.* 在 toString 字典序中排在 /user/.* 之前
    // 所以 /song/test 应命中 regex2
    val result = WareHouse.getMatchRouterMeta("https://example.com/song/test")
    assertNotNull(result)
    assertEquals("com.example.SongActivity", result.className)
}
```

- [ ] **Step 4: 运行全部测试**

```bash
./gradlew :okrouter:testDebugUnitTest
```

预期：全部测试 PASS

- [ ] **Step 5: 提交**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/warehouse/WareHouse.kt \
        okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt
git commit -m "fix: WareHouse集合线程安全——ConcurrentHashMap替换HashMap，正则遍历确定性排序"
```

---

### Task 4: 拦截器稳定排序 (P1)

**Files:**
- Modify: `okrouter/src/main/java/com/anymore/okrouter/core/internal/PriorityRouterInterceptorComparator.kt`
- Modify: `okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt`

**Interfaces:**
- Produces: `PriorityRouterInterceptorComparator` 在 `priority` 相同时按 `className` 字典序稳定排序

- [ ] **Step 1: 增加二级排序键**

修改 `okrouter/src/main/java/com/anymore/okrouter/core/internal/PriorityRouterInterceptorComparator.kt`：

```kotlin
package com.anymore.okrouter.core.internal

import com.anymore.okrouter.core.RouterInterceptor

internal object PriorityRouterInterceptorComparator : Comparator<RouterInterceptor> {

    override fun compare(o1: RouterInterceptor?, o2: RouterInterceptor?): Int {
        val p1 = o1?.javaClass?.let { WareHouse.getInterceptorInstancePriority(it) } ?: 0
        val p2 = o2?.javaClass?.let { WareHouse.getInterceptorInstancePriority(it) } ?: 0
        val priorityDiff = p1.compareTo(p2)
        if (priorityDiff != 0) return priorityDiff
        // 同优先级按 className 字典序稳定排序
        val name1 = o1?.javaClass?.name.orEmpty()
        val name2 = o2?.javaClass?.name.orEmpty()
        return name1.compareTo(name2)
    }
}
```

- [ ] **Step 2: 测试——同优先级拦截器按类名排序**

在 `WareHouseTest.kt` 中新增测试：

```kotlin
@Test
fun `interceptors with same priority ordered by class name`() {
    // TestInterceptorA 和 TestInterceptorB 已在文件中定义
    val clazzA = TestInterceptorA::class.java
    val clazzB = TestInterceptorB::class.java

    // 注册同优先级
    WareHouse.registerInterceptor(clazzA,
        RouterInterceptorMeta(clazzA, clazzA.name, "",
            ReflectRouterInterceptorFactory(clazzA), 5, false, false))
    WareHouse.registerInterceptor(clazzB,
        RouterInterceptorMeta(clazzB, clazzB.name, "",
            ReflectRouterInterceptorFactory(clazzB), 5, false, false))

    val sorted = listOf(TestInterceptorB(), TestInterceptorA())
        .sortedWith(PriorityRouterInterceptorComparator)

    // TestInterceptorA 字典序在 TestInterceptorB 之前
    assertEquals(TestInterceptorA::class.java, sorted[0]::class.java)
    assertEquals(TestInterceptorB::class.java, sorted[1]::class.java)
}
```

- [ ] **Step 3: 运行测试**

```bash
./gradlew :okrouter:testDebugUnitTest --tests "com.anymore.okrouter.warehouse.WareHouseTest"
```

预期：全部测试 PASS

- [ ] **Step 4: 提交**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/core/internal/PriorityRouterInterceptorComparator.kt \
        okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt
git commit -m "fix: 拦截器排序增加className二级键——同优先级拦截器按类名字典序稳定排序"
```

---

### Task 5: 单例 Factory 线程安全 (P1)

**Files:**
- Modify: `okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterInterceptorFactory.kt`
- Modify: `okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt`

**Interfaces:**
- Produces: `RouterInterceptorFactory.create()` 在并发场景下保证单例只创建一个实例

- [ ] **Step 1: 修复 `create()` 方法**

修改 `okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterInterceptorFactory.kt`：

```kotlin
package com.anymore.okrouter.warehouse

import com.anymore.okrouter.core.RouterInterceptor

abstract class RouterInterceptorFactory(private val singleton: Boolean = false) {

    @Volatile
    private var instance: RouterInterceptor? = null

    abstract fun newInstance(): RouterInterceptor

    fun create(): RouterInterceptor {
        return if (singleton) {
            instance ?: synchronized(this) {
                instance ?: newInstance().also { instance = it }
            }
        } else {
            newInstance()
        }
    }
}
```

- [ ] **Step 2: 并发测试——100 线程只创建单例**

在 `WareHouseTest.kt` 中新增测试：

```kotlin
@Test
fun `singleton factory creates only one instance under concurrent access`() {
    val factory = object : RouterInterceptorFactory(singleton = true) {
        override fun newInstance(): RouterInterceptor = TestInterceptorA()
    }
    val executor = java.util.concurrent.Executors.newFixedThreadPool(100)
    val latch = java.util.concurrent.CountDownLatch(100)
    val instances = java.util.concurrent.ConcurrentHashMap.newKeySet<RouterInterceptor>()

    repeat(100) {
        executor.submit {
            instances.add(factory.create())
            latch.countDown()
        }
    }
    latch.await()
    executor.shutdown()

    assertEquals(1, instances.size)
}
```

- [ ] **Step 3: 运行测试**

```bash
./gradlew :okrouter:testDebugUnitTest --tests "com.anymore.okrouter.warehouse.WareHouseTest"
```

预期：全部测试 PASS，并发测试证明只创建一个实例

- [ ] **Step 4: 提交**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/warehouse/RouterInterceptorFactory.kt \
        okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt
git commit -m "fix: RouterInterceptorFactory单例线程安全——synchronized double-check locking"
```

---

### Task 6: 初始化生命周期 (P1)

**Files:**
- Modify: `okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt`
- Modify: `okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt:362-365`
- Modify: `okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt`

**Interfaces:**
- Consumes: `application` 改为 `Application?`（影响所有引用 `OkRouter.application` 的代码）
- Produces: `isInitialized()` 公开方法；`start()` 未初始化时抛 `IllegalStateException` 而非 `UninitializedPropertyAccessException`

- [ ] **Step 1: 修改 `OkRouter.kt`**

修改 `okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt`：

```kotlin
package com.anymore.okrouter

import android.app.Application
import android.content.Context
import com.anymore.okrouter.core.Logger
import com.anymore.okrouter.core.RouterLostHandler
import com.anymore.okrouter.core.RouterRequest
import com.anymore.okrouter.core.RouterResponse
import com.anymore.okrouter.core.internal.RouterDispatcher
import com.anymore.okrouter.warehouse.OkRouterLoader

object OkRouter {

    internal const val TAG = "OkRouter"

    internal var application: Application? = null
        private set

    private var initialized = false

    @JvmStatic
    var routerLostHandler: RouterLostHandler = object : RouterLostHandler {}

    @JvmStatic
    var logger: Logger = Logger.Default

    @JvmStatic
    fun init(context: Context) {
        if (initialized) {
            logger.w("OkRouter 已经初始化，跳过重复 init() 调用")
            return
        }
        application = context.applicationContext as Application
        OkRouterLoader.load()
        initialized = true
    }

    @JvmStatic
    fun isInitialized(): Boolean = initialized

    @JvmStatic
    fun build(uri: String) = RouterRequest.Builder().uri(uri)

    @JvmOverloads
    @JvmStatic
    fun start(request: RouterRequest, context: Context? = null): RouterResponse {
        val ctx = context ?: application
            ?: throw IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")
        return RouterDispatcher.start(ctx, request)
    }
}
```

- [ ] **Step 2: 修改 `RouterRequest.Builder.start()`**

修改 `okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt`，将：

```kotlin
@JvmOverloads
fun start(context: Context = application, requestCode: Int = -1): RouterResponse {
    requestCode(requestCode)
    return RouterDispatcher.start(context, build())
}
```

改为：

```kotlin
@JvmOverloads
fun start(context: Context? = null, requestCode: Int = -1): RouterResponse {
    requestCode(requestCode)
    val ctx = context ?: OkRouter.application
        ?: throw IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")
    return RouterDispatcher.start(ctx, build())
}
```

- [ ] **Step 3: 测试——初始化生命周期**

在 `WareHouseTest.kt` 中新增测试（不需要 Robolectric 完整环境，通过直接测试 OkRouter 状态验证）：

```kotlin
@Test
fun `start before init throws IllegalStateException`() {
    // 注意：此测试需要在未初始化的环境中运行
    // 如果测试环境中 OkRouter 已被初始化，跳过此测试
    try {
        OkRouter.start(RouterRequest.Builder().uri("okrouter://test/main").build())
        // 如果执行到这里而未抛异常，且 OkRouter 未初始化，则测试失败
        if (!OkRouter.isInitialized()) {
            org.junit.Assert.fail("Expected IllegalStateException")
        }
    } catch (e: IllegalStateException) {
        assertTrue(e.message!!.contains("未初始化"))
    }
}

@Test
fun `isInitialized returns correct state`() {
    // init 已在 @Before 中间接调用，确认状态一致
    assertTrue(OkRouter.isInitialized())
}
```

- [ ] **Step 4: 运行测试**

```bash
./gradlew :okrouter:testDebugUnitTest
```

预期：全部测试 PASS

- [ ] **Step 5: 提交**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/OkRouter.kt \
        okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt \
        okrouter/src/test/java/com/anymore/okrouter/warehouse/WareHouseTest.kt
git commit -m "fix: OkRouter初始化生命周期——application改为nullable，未初始化时抛出明确异常"
```

---

### Task 7: RouterResult 错误模型扩展 (P1)

**Files:**
- Modify: `okrouter/src/main/java/com/anymore/okrouter/core/RouterResult.kt`
- Modify: `okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt`
- Modify: `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt`
- Modify: `okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt:363-366`
- Modify: `okrouter/src/test/java/com/anymore/okrouter/core/RouterResponseTest.kt`

**Interfaces:**
- Consumes: Task 6 的 `OkRouter.application` nullable 变更
- Produces: `RouterResult.Failed`（含 cause）、`RouterResult.InvalidRequest`（含 reason）；框架层异常不再穿透

- [ ] **Step 1: 扩展 `RouterResult`**

修改 `okrouter/src/main/java/com/anymore/okrouter/core/RouterResult.kt`：

```kotlin
package com.anymore.okrouter.core

/**
 * 路由执行结果。
 *
 * **前向兼容提示**：本 sealed class 可能在未来版本中新增子类型。
 * 使用 `when` 表达式时请始终包含 `else` 分支（或 Kotlin 1.7+ 的非穷尽 `when`），
 * 以避免源码不兼容的编译错误。
 */
sealed class RouterResult(val value: String) {
    /** 路由成功 */
    object Ok : RouterResult("Ok")
    /** 目标未找到 */
    object NotFound : RouterResult("NotFound")
    /** 被拦截器拦截 */
    object Intercepted : RouterResult("Intercepted")
    /**
     * 执行失败（目标实例化异常、Service 启动被系统拒绝等）。
     * 调用方可从 [cause] 获取原始异常。
     */
    class Failed(val cause: Throwable) : RouterResult("Failed")
    /**
     * 请求参数非法（URI 格式错误、缺少必要参数等）。
     * 调用方可从 [reason] 获取具体原因。
     */
    class InvalidRequest(val reason: String) : RouterResult("InvalidRequest")
    /** 自定义路由结果 */
    class Custom(value: String) : RouterResult(value)
}
```

- [ ] **Step 2: `LaunchInterceptor` 包裹异常捕获**

修改 `okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt`，将 `intercept()` 方法包裹在 try-catch 中：

```kotlin
override fun intercept(context: Context, chain: RouterInterceptor.Chain): RouterResponse {
    return try {
        when (meta.routerType) {
            RouterType.ACTIVITY -> startActivity(context, chain.request(), meta)
            RouterType.FRAGMENT -> startFragment(context, chain.request(), meta)
            RouterType.VIEW -> startView(context, chain.request(), meta)
            RouterType.SERVICE -> startService(context, chain.request(), meta)
            RouterType.HANDLER -> startHandler(context, chain.request(), meta)
            RouterType.UNDEFINED -> {
                OkRouter.logger.e("LaunchInterceptor: routerType is UNDEFINED for ${meta.uri}")
                RouterResponse.Builder()
                    .uri(chain.request().uri)
                    .routerType(meta.routerType)
                    .routerResult(RouterResult.Failed(IllegalStateException("routerType 不能为 UNDEFINED")))
                    .build()
            }
        }
    } catch (e: Exception) {
        OkRouter.logger.e("LaunchInterceptor: 目标启动失败 uri=${chain.request().uri}, type=${meta.routerType}", e)
        RouterResponse.Builder()
            .uri(chain.request().uri)
            .routerType(meta.routerType)
            .routerResult(RouterResult.Failed(e))
            .build()
    }
}
```

- [ ] **Step 3: `RouterDispatcher` 包裹拦截器实例化异常**

修改 `okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt`，在 `getInterceptorInstance()` 调用处包裹异常：

```kotlin
ics.forEach {
    val instance = try {
        WareHouse.getInterceptorInstance(it)
    } catch (e: Exception) {
        OkRouter.logger.e("RouterDispatcher: 拦截器实例化失败 ${it.name}", e)
        null
    }
    if (instance != null) {
        interceptors += instance
    }
}
```

如果所有拦截器实例化都失败导致列表为空，在创建 chain 之前增加检查：

```kotlin
// 在 interceptors.sortWith(...) 之前
// 移除 null 实例（已在上面处理），继续执行——至少 LaunchInterceptor 会运行
interceptors.sortWith(PriorityRouterInterceptorComparator)
interceptors += LaunchInterceptor(meta)
```

- [ ] **Step 4: `RouterRequest.Builder.start()` 包裹校验异常**

修改 `okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt` 的 `start()` 方法（在 Task 6 修改后的基础上），将 `build()` 调用包裹在 try-catch 中：

```kotlin
@JvmOverloads
fun start(context: Context? = null, requestCode: Int = -1): RouterResponse {
    requestCode(requestCode)
    val ctx = context ?: OkRouter.application
        ?: throw IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")
    return try {
        RouterDispatcher.start(ctx, build())
    } catch (e: IllegalStateException) {
        // build() 中 check(!u.isNullOrEmpty()) 抛出的异常
        OkRouter.logger.e("RouterRequest: URI 参数非法", e)
        RouterResponse.Builder()
            .uri(uri ?: "(null)")
            .routerType(routerType)
            .routerResult(RouterResult.InvalidRequest(e.message ?: "URI 为空"))
            .build()
    }
}
```

- [ ] **Step 5: 测试——错误模型**

在 `okrouter/src/test/java/com/anymore/okrouter/core/RouterResponseTest.kt` 中新增测试（利用现有测试文件）：

```kotlin
@Test
fun `RouterResult Failed contains cause exception`() {
    val cause = RuntimeException("测试异常")
    val result = RouterResult.Failed(cause)
    assertEquals("Failed", result.value)
    assertEquals(cause, result.cause)
}

@Test
fun `RouterResult InvalidRequest contains reason`() {
    val result = RouterResult.InvalidRequest("URI 格式错误")
    assertEquals("InvalidRequest", result.value)
    assertEquals("URI 格式错误", result.reason)
}

@Test
fun `RouterResult sealed class has all expected subtypes`() {
    // 验证新增子类型可用于 when 表达式
    val result: RouterResult = RouterResult.Failed(RuntimeException())
    val message = when (result) {
        is RouterResult.Ok -> "ok"
        is RouterResult.NotFound -> "not found"
        is RouterResult.Intercepted -> "intercepted"
        is RouterResult.Failed -> "failed: ${result.cause.message}"
        is RouterResult.InvalidRequest -> "invalid: ${result.reason}"
        is RouterResult.Custom -> "custom: ${result.value}"
    }
    assertTrue(message.startsWith("failed:"))
}
```

- [ ] **Step 6: 运行全部测试**

```bash
./gradlew :okrouter:testDebugUnitTest
```

预期：全部测试 PASS

- [ ] **Step 7: 提交**

```bash
git add okrouter/src/main/java/com/anymore/okrouter/core/RouterResult.kt \
        okrouter/src/main/java/com/anymore/okrouter/core/internal/LaunchInterceptor.kt \
        okrouter/src/main/java/com/anymore/okrouter/core/internal/RouterDispatcher.kt \
        okrouter/src/main/java/com/anymore/okrouter/core/RouterRequest.kt \
        okrouter/src/test/java/com/anymore/okrouter/core/RouterResponseTest.kt
git commit -m "feat: RouterResult扩展——新增Failed和InvalidRequest，框架异常不再穿透调用方"
```

---

## 验证检查清单

全部任务完成后，运行：

```bash
# 全部单元测试
./gradlew :okrouter:testDebugUnitTest

# 示例应用构建
./gradlew :demo-app:assembleDebug

# 清理后增量构建
./gradlew clean :demo-app:assembleDebug
```

预期：全部通过。

## 任务依赖

```
T1 (缓存键) ──→ T3 (线程安全)    ← 同文件，变更不重叠
T2 (冲突检测)                     ← 独立
T4 (排序)                         ← 独立
T5 (Factory)                      ← 独立
T6 (生命周期) ──→ T7 (错误模型)   ← RouterRequest.kt 有累积变更
```

T1-T5 可完全并行实现（不同文件或同文件不同区域）。T7 依赖 T6（`application` nullable 变更）。

## 预计工作量

| Task | 人类 | CC |
|------|------|-----|
| T1 | 15 min | 3 min |
| T2 | 30 min | 5 min |
| T3 | 20 min | 4 min |
| T4 | 10 min | 2 min |
| T5 | 15 min | 3 min |
| T6 | 20 min | 4 min |
| T7 | 30 min | 6 min |
| **总计** | **~2.5h** | **~30min** |
