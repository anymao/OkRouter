## Context

当前 OkRouter 项目使用 Android Gradle Plugin (AGP) 4.2.2，该版本于 2021 年发布。项目使用传统的 Transform API (`com.android.build.api.transform.Transform`) 来实现编译时代码注入。AGP 从 7.0 开始废弃了旧的 Transform API，引入了新的 Transform API 和 AsmClassVisitorFactory 作为替代方案。

AGP 7.4.0 需要 Gradle 7.5 或更高版本，并且推荐 Kotlin 1.8.0 以获得最佳兼容性。

当前项目的 Transform 实现位于 `OkRouterTransform.kt`，通过扫描字节码生成路由表代码。

## Goals / Non-Goals

**Goals:**
- 升级 AGP 到 7.4.0 以获得更好的构建性能和现代 API 支持
- 更新 Transform API 使用方式以适配新的 AGP 版本
- 升级 Kotlin 到 1.8.0
- 更新 Java 版本配置以符合 AGP 7.4.0 的要求
- 保持现有功能完整性，确保示例应用能够正常构建和运行

**Non-Goals:**
- 重构路由核心逻辑（保持现有 OkRouter 库功能不变）
- 更新示例应用的依赖版本（除非为了兼容性）
- 实现增量编译支持（当前标记为 TODO，继续跳过）

## 版本对比

```
┌─────────────────┬────────────────┬────────────────┐
│ 组件             │ 当前版本       │ 目标版本       │
├─────────────────┼────────────────┼────────────────┤
│ Gradle          │ 6.7.1          │ 7.5.2+         │
│ AGP             │ 4.2.2          │ 7.4.0          │
│ Kotlin          │ 1.5.31         │ 1.8.0          │
│ Java            │ 8              │ 17             │
│ Transform API   │ Legacy         │ Legacy/Asm     │
└─────────────────┴────────────────┴────────────────┘
```

## AGP 7.4.0 关键 API 变更

### Transform API 状态

AGP 7.4.0 中，旧的 Transform API (`com.android.build.api.transform.Transform`) 处于**兼容模式**状态：

```
AGP 版本演进：
─────────────────────────────────────────────
AGP 4.x: Transform API 是主要方式
AGP 7.0-7.3: Transform API 废弃，引入 AsmClassVisitorFactory
AGP 7.4+: Transform API 仍可以通过兼容层使用
AGP 8.0+: Transform API 完全移除（需迁移到 Asm）
─────────────────────────────────────────────
```

**对项目的具体影响：**
- 当前使用 `OkRouterTransform` 继承自 `Transform` 类
- 在 AGP 7.4.0 中**仍可工作**，但需要配置兼容性
- AGP 7.4.0 提供 `registerTransform()` 的兼容实现
- 建议：短期使用兼容层，长期规划迁移到 AsmClassVisitorFactory

### 主要配置变更

```gradle
// ❌ AGP 4.2.2 旧方式（已废弃）
defaultConfig {
    minSdkVersion 24        // 已废弃
    targetSdkVersion 33      // 已废弃
}

// ✅ AGP 7.4.0 新方式
defaultConfig {
    minSdk = 24              // 新方式
    targetSdk = 33           // 新方式
}
```

### buildscript vs plugins DSL

```gradle
// ❌ AGP 7.4.0 不推荐使用 buildscript
buildscript {
    dependencies {
        classpath "com.android.tools.build:gradle:4.2.2"
    }
}

// ✅ AGP 7.4.0 推荐使用 plugins DSL
plugins {
    id 'com.android.application' version '7.4.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.8.0' apply false
}
```

## 自定义插件开发方案

### 方案 A：Transform API 兼容层（推荐快速升级）

```
┌──────────────────────────────────────────────────────┐
│              Transform API 兼容层                    │
├──────────────────────────────────────────────────────┤
│                                                      │
│  1. 保持现有 Transform 实现                           │
│  2. 更新依赖版本                                     │
│  3. 配置编译兼容性                                   │
│  4. 测试验证功能                                     │
│                                                      │
│  优点：代码改动最小，风险低                           │
│  缺点：未来需要重构                                   │
│                                                      │
└──────────────────────────────────────────────────────┘
```

**实现步骤：**

```kotlin
// buildSrc/build.gradle
dependencies {
    // AGP 7.4.0 需要明确的依赖
    api("com.android.tools.build:gradle:7.4.0")
    implementation("org.javassist:javassist:3.28.0-GA")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
}
```

```kotlin
// OkRouterTransformPlugin.kt - 无需改动
// AGP 7.4.0 提供 registerTransform 的兼容实现
class OkRouterTransformPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // 现有代码继续工作
        val android = target.extensions.getByType(AppExtension::class.java)
        val okRouterTransform = OkRouterTransform(target)
        android.registerTransform(okRouterTransform)
    }
}
```

### 方案 B：AsmClassVisitorFactory（现代化方案）

```
┌──────────────────────────────────────────────────────┐
│           AsmClassVisitorFactory 迁移路径            │
├──────────────────────────────────────────────────────┤
│                                                      │
│  1. 创建 AsmClassVisitorFactory 子类                │
│  2. 实现类访问逻辑                                  │
│  3. 使用 ASM 替代 Javassist                        │
│  4. 通过 ComponentRegistrar 注册                     │
│                                                      │
│  优点：现代化、高性能、未来兼容                       │
│  缺点：需要重写所有字节码处理逻辑                    │
│                                                      │
└──────────────────────────────────────────────────────┘
```

**新 API 架构示例：**

```kotlin
// 1. 创建 Factory
class OkRouterClassVisitorFactory(
    private val project: Project,
    private val extension: OkRouterExtension
) : AsmClassVisitorFactory<OkRouterParameters> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return OkRouterClassVisitor(
            nextClassVisitor,
            classContext,
            extension
        )
    }

    override fun bindParameters(paramBuilder: ParametersBuilder<OkRouterParameters>) {
        // 绑定参数
    }
}

// 2. 创建 Visitor
class OkRouterClassVisitor(
    private val nextVisitor: ClassVisitor,
    private val classContext: ClassContext,
    private val extension: OkRouterExtension
) : ClassVisitor(nextVisitor) {

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        // 处理类信息，替代现有的 Javassist 逻辑
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
        // 处理注解，替代现有的 getAnnotation 逻辑
        return super.visitAnnotation(desc, visible)
    }
}

// 3. 通过 ComponentRegistrar 注册
class OkRouterComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(
        projectContext: ProjectContext,
        registrar: ProjectComponentRegistrar
    ) {
        registrar.registerTransformTaskWithInputType(
            AndroidPluginType.ANDROID_APP,
            AndroidPluginType.ANDROID_LIBRARY
        ) { inputType ->
            registrar.registerAsmClassVisitorFactory(
                inputType,
                OkRouterClassVisitorFactory::class.java
            )
        }
    }
}
```

## 方案对比

| 方面 | Transform 兼容层 | AsmClassVisitorFactory |
|------|------------------|----------------------|
| **代码改动量** | 小（仅依赖升级） | 大（需要重写） |
| **学习曲线** | 低 | 高 |
| **性能** | 中等 | 高 |
| **未来兼容性** | AGP 8.0+ 需要再次迁移 | 长期兼容 |
| **风险** | 低 | 中等 |
| **推荐场景** | 快速升级、时间紧张 | 长期维护、性能关键 |

## Decisions

### 1. Transform API 迁移策略

**决策：** 保留旧版 Transform API，使用 `android.registerTransform()` 的兼容层

**理由：**
- AGP 7.4.0 仍然提供了对旧版 Transform API 的兼容支持
- 迁移到新的 AsmClassVisitorFactory API 需要大量重构现有代码
- 当前实现稳定，升级成本较低
- 项目使用 Javassist 进行字节码操作，迁移到 ASM 需要重写所有逻辑

**备选方案：**
- 迁移到 AsmClassVisitorFactory：更现代化，但需要重写所有字节码处理逻辑
- 迁移到新版 Transform API：仍然保持 Transform 接口，但需要适配新的注册方式

### 2. Gradle 版本选择

**决策：** 升级到 Gradle 7.5.2（AGP 7.4.0 推荐版本）

**理由：**
- AGP 7.4.0 明确要求 Gradle 7.5+
- 7.5.2 是稳定版本，修复了多个已知问题
- 与 Kotlin 1.8.0 有最佳兼容性

### 3. Kotlin 版本升级

**决策：** 升级到 Kotlin 1.8.0

**理由：**
- AGP 7.4.0 与 Kotlin 1.8.0 有最佳兼容性
- 支持 JDK 17 特性
- 提供更好的性能和稳定性

### 4. Java 版本配置

**决策：** 使用新的 `JavaVersion.VERSION_17` 和现代 DSL 配置

**理由：**
- AGP 7.4.0 默认使用 JDK 17
- 使用新的 DSL 配置方式更加现代化
- 移除废弃的 `minSdkVersion()` 等配置方式

## Risks / Trade-offs

### 风险 1：旧版 Transform API 在未来 AGP 版本中可能完全移除
**缓解措施：**
- 在设计文档中记录技术债务
- 建议在下一个主要版本迁移到 AsmClassVisitorFactory
- 关注 AGP 8.0 的迁移指南发布

### 风险 2：Kotlin 版本升级可能导致现有代码不兼容
**缓解措施：**
- 先升级 Kotlin 编译器版本，测试构建
- 保留 Kotlin 1.5.31 作为回退选项
- 检查 Kotlin 标准库的变更
- 特别注意 JavaPoet 和 Javassist 与 Kotlin 1.8.0 的兼容性

### 风险 3：buildSrc 配置可能需要调整
**缓解措施：**
- 先升级 buildSrc 的依赖版本
- 测试 Gradle 插件的构建和发布
- 验证 Javassist 依赖在新版本上的兼容性

### 风险 4：Javassist 在 AGP 7.4.0 上可能存在兼容性问题
**缓解措施：**
- 测试 Javassist 的字节码生成功能
- 如果出现问题，评估迁移到 ASM 的成本
- Javassist 3.28.0-GA 在 AGP 7.4.0 上应该可以正常工作

### 权衡：快速升级 vs 完全重构
选择快速升级以最小化风险，但增加了未来维护成本。如果需要长期支持，应在后续迭代中完成 Transform API 的重构。

## Migration Plan

### 阶段 1：基础依赖升级
1. 更新 Gradle Wrapper 到 7.5.2
   - 修改 `gradle/wrapper/gradle-wrapper.properties`
   - 验证 Gradle 版本正确性
2. 更新根 build.gradle 中的 AGP 版本到 7.4.0
   - 将 `classpath "com.android.tools.build:gradle:4.2.2"` 更新为 7.4.0
   - 验证 AGP 版本正确性
3. 更新 Kotlin 版本到 1.8.0
   - 更新 `ext.kotlin_version = "1.8.0"`
   - 验证 Kotlin 版本正确性

### 阶段 2：buildSrc 适配
1. 更新 buildSrc/build.gradle 中的 AGP 依赖版本
   - 将 `api("com.android.tools.build:gradle:4.2.2")` 更新为 7.4.0
   - 更新 Kotlin 插件配置
   - 更新 Kotlin 依赖版本到 1.8.0
2. 测试 buildSrc 构建
   - 运行 `./gradlew build` 验证 buildSrc
   - 检查编译错误和警告

### 阶段 3：Transform 插件适配
1. 验证现有 Transform API 兼容性
   - 检查 `OkRouterTransform.kt` 中的 API 使用
   - 确认 `registerTransform()` 调用正常
2. 添加必要的编译依赖
   - 更新 Javassist 版本（如需要）
   - 更新 commons-io 版本
3. 测试 Transform 插件注册和执行
   - 运行 `./gradlew :demo-app:assembleDebug`
   - 验证路由表生成功能

### 阶段 4：模块配置更新
1. 更新所有模块的 build.gradle 配置
   - 将 `minSdkVersion()` 改为 `minSdk`
   - 将 `targetSdkVersion()` 改为 `targetSdk`
   - 将 `compileSdkVersion` 改为 `compileSdk`
   - 移除废弃的配置方式
2. 使用新的 DSL 配置方式
   - 更新 `compileOptions` 配置
   - 更新 `kotlinOptions` 配置
3. 更新 Java 版本到 17
   - 将 `JavaVersion.VERSION_1_8` 更新为 `JavaVersion.VERSION_17`
   - 更新 `jvmTarget = '1.8'` 为 `jvmTarget = '17'`

### 阶段 5：验证测试
1. 构建所有模块
   - 运行 `./gradlew clean build`
   - 检查所有模块的构建状态
2. 运行示例应用
   - 安装并运行 demo-app
   - 验证路由功能正常
3. 验证路由功能正常
   - 测试稳定路由跳转
   - 测试正则路由跳转
   - 测试拦截器功能

## 后续技术债务规划

### 长期目标：迁移到 AsmClassVisitorFactory

**优先级：** 中等（在 AGP 8.0 发布前完成）

**主要工作：**
1. 学习 ASM 框架（替代 Javassist）
2. 重构 `AbsOkRouterAction` 的字节码处理逻辑
3. 重构 `OkRouterTransformAction` 的类生成逻辑
4. 实现 `OkRouterClassVisitorFactory`
5. 通过 `ComponentRegistrar` 注册新 API
6. 全面测试和验证

**预期收益：**
- 更好的构建性能
- 长期 API 兼容性
- 更小的构建产物
- 更好的增量编译支持

## Open Questions

1. 是否需要在升级过程中保持向后兼容性？
   - 建议：否，直接升级到新版本，简化升级过程。

2. 是否需要同时更新 AndroidX 库的版本？
   - 建议：如果当前版本存在兼容性问题，则需要更新。先升级 AGP，观察是否有 AndroidX 相关错误。

3. 示例应用是否能正常运行？
   - 需要：在升级后验证所有路由功能和拦截器功能。

4. Javassist 在 AGP 7.4.0 上的兼容性如何？
   - 需要：实际测试验证，如果出现问题，需要评估迁移到 ASM 的紧急性。

5. 是否需要更新 CI/CD 环境的 JDK 版本？
   - 建议：需要更新到 JDK 17，确保构建环境与开发环境一致。
