# Design: AGP 8.13.0 适配技术设计

## 架构概览

### 变更前架构（AGP 7.x）

```
┌─────────────────────────────────────────────────┐
│         OkRouterTransformPlugin              │
│                                         │
│  apply(project) {                        │
│      android.registerTransform(            │
│          OkRouterTransform                 │
│      )                                 │
│  }                                      │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         OkRouterTransform                   │
│         (extends Transform)                │
│                                         │
│  transform(invocation) {                │
│      1. 收集所有 class 文件              │
│      2. 调用 AbsOkRouterAction          │
│      3. 生成路由表代码                 │
│  }                                      │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         AbsOkRouterAction                  │
│                                         │
│  1. 使用 Javassist 扫描注解             │
│  2. 生成 OkRouterLoader 类             │
└─────────────────────────────────────────────────┘
```

### 变更后架构（AGP 8.x）

```
┌─────────────────────────────────────────────────┐
│         OkRouterTransformPlugin              │
│                                         │
│  apply(project) {                        │
│      android.applicationVariants.forEach {   │
│          variant ->                      │
│              // 1. 创建注册任务           │
│              val registerTask = ...        │
│              // 2. 创建编译任务           │
│              val compileTask = ...         │
│              // 3. 管理依赖链            │
│              setupDependencies(...)          │
│          }                                │
│  }                                      │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         OkRouterRegisterTask                │
│         (extends DefaultTask)              │
│                                         │
│  execute() {                            │
│      1. 收集 classpath                 │
│      2. 调用 AbsOkRouterAction          │
│      3. 生成 Java 源代码              │
│  }                                      │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         OkRouterCompileTask                 │
│         (JavaCompile)                      │
│                                         │
│  编译生成的 Java 代码                    │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         AbsOkRouterAction                  │
│                                         │
│  1. 使用 Javassist 扫描注解             │
│  2. 生成 OkRouterLoader 类             │
│  (完全复用，无变更！）                    │
└─────────────────────────────────────────────────┘
```

## 核心变更

### 1. 插件入口重构

#### 文件：`OkRouterTransformPlugin.kt`

**变更前**：
```kotlin
class OkRouterTransformPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.plugins.hasPlugin(AppPlugin::class.java)) {
            throw GradleException("...")
        }
        target.extensions.create("okRouter", OkRouterExtension::class.java)
        val android = target.extensions.getByType(AppExtension::class.java)
        val okRouterTransform = OkRouterTransform(target)
        android.registerTransform(okRouterTransform)
        Logger.v("${target.displayName} registered OkRouterTransformPlugin.")
    }
}
```

**变更后**：
```kotlin
class OkRouterTransformPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.plugins.hasPlugin(AppPlugin::class.java)) {
            throw GradleException("...")
        }
        target.extensions.create("okRouter", OkRouterExtension::class.java)
        
        val android = target.extensions.getByType(AppExtension::class.java)
        
        android.applicationVariants.forEach { variant ->
            setupVariantTasks(target, variant)
        }
        
        Logger.v("${target.displayName} registered OkRouterTransformPlugin.")
    }
    
    private fun setupVariantTasks(project: Project, variant: ApplicationVariant) {
        val okRouterExtension = project.extensions.getByType(OkRouterExtension::class.java)
        
        // 工作目录
        val workDir = project.layout.buildDirectory
            .dir("intermediates/okrouter/${variant.dirName}")
            .get()
            .asFile
        
        // 获取 Java 编译输出目录
        val javaCompileTask = variant.javaCompileProvider.get()
        val compileOutputDir = project.layout.buildDirectory
            .dir("intermediates/javac/${variant.name}/compile${variant.name.capitalize()}JavaWithJavac/classes")
            .get()
            .asFile
        
        // 构建 classpath
        val classpath = project.files(
            android.bootClasspath,
            javaCompileTask.classpath,
            compileOutputDir
        )
        
        // 注册扫描任务
        val registerTask = project.tasks.register(
            "okRouterRegister${variant.name.capitalize()}",
            OkRouterRegisterTask::class.java
        )
        registerTask.configure {
            it.setClasspath(classpath)
            it.setTargetDir(File(workDir, "src"))
            it.setProject(project)
        }
        
        // 注册编译任务
        val compileTask = project.tasks.register(
            "okRouterCompile${variant.name.capitalize()}",
            JavaCompile::class.java
        )
        compileTask.configure {
            it.setSource(File(workDir, "src"))
            it.include("**/*.java", "**/*.kt")
            it.setClasspath(classpath)
            it.destinationDirectory.set(compileOutputDir)
            it.setSourceCompatibility("17")
            it.setTargetCompatibility("17")
        }
        
        // 管理依赖关系
        registerTask.configure {
            it.mustRunAfter(javaCompileTask)
        }
        compileTask.configure {
            it.dependsOn(javaCompileTask)
            it.mustRunAfter(registerTask.get())
        }
        
        // 确保最终任务依赖
        variant.assembleProvider.get().dependsOn(registerTask, compileTask)
    }
}
```

### 2. 新增注册任务

#### 文件：`OkRouterRegisterTask.kt`（新建）

```kotlin
abstract class OkRouterRegisterTask : DefaultTask() {
    
    @get:Input
    abstract val classpath: ConfigurableFileCollection
    
    @get:OutputFile
    abstract val targetDir: RegularFileProperty
    
    @get:Internal
    abstract val project: Property<Project>
    
    @TaskAction
    fun execute() {
        val start = System.currentTimeMillis()
        
        // 调用现有的扫描和生成逻辑
        AbsOkRouterAction.forTransform(
            classpath.files,
            targetDir.get().asFile,
            project.get()
        ).execute()
        
        val duration = (System.currentTimeMillis() - start) / 1000.0
        Logger.s("OkRouter generation completed in [$duration s]")
    }
}
```

### 3. 删除的文件

```
❌ OkRouterTransform.kt          - 不再需要 Transform 类
❌ OkRouterTransformAction.kt     - 改为生成 Java 源代码
```

### 4. 修改的文件

```
📝 OkRouterTransformPlugin.kt     - 完全重构
📝 OkRouterTransformAction.kt     - 改为生成 Java 源代码而非 class
```

### 5. 保持不变的文件

```
✅ AbsOkRouterAction.kt          - 完全复用
✅ RouterElement.kt              - 完全复用
✅ InterceptorElement.kt          - 完全复用
✅ OkRouterExtension.kt            - 完全复用
✅ Logger.kt                    - 完全复用
```

## 目录结构

### 生成代码目录

```
build/
└── intermediates/
    └── okrouter/
        └── debug/              (variant.dirName)
            └── src/
                └── com/
                    └── anymore/
                        └── okrouter/
                            └── warehouse/
                                └── OkRouterLoader.java
```

### 编译输出目录

```
build/
└── intermediates/
    └── javac/
        └── debug/              (variant.name)
            └── compileDebugJavaWithJavac/
                └── classes/
                    └── com/
                        └── anymore/
                            └── okrouter/
                                └── warehouse/
                                    └── OkRouterLoader.class
```

## 版本升级

### 升级清单

| 文件 | 当前版本 | 目标版本 | 变更内容 |
|------|---------|----------|----------|
| `build.gradle` | AGP 7.4.0 | AGP 8.13.0 | `com.android.tools.build:gradle:8.13.0` |
| `build.gradle` | Kotlin 1.8.0 | Kotlin 2.1.0 | `kotlin_version = "2.1.0"` |
| `buildSrc/build.gradle` | AGP 7.4.0 | AGP 8.13.0 | `com.android.tools.build:gradle:8.13.0` |
| `buildSrc/build.gradle` | Kotlin 1.8.0 | Kotlin 2.1.0 | `kotlin-gradle-plugin:2.1.0` |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 7.5 | Gradle 8.13 | `gradle-8.13-bin.zip` |

### 配置变更

#### 1. build.gradle

```groovy
buildscript {
    ext.kotlin_version = "2.1.0"  // 从 1.8.0 升级
    ext.maven_username = ...
    ext.maven.com_password = ...
    
    repositories {
        google()
        mavenCentral()
        // ... 其他仓库
    }
    
    dependencies {
        classpath "com.android.tools.build:gradle:8.13.0"  // 从 7.4.0 升级
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath("com.anymore:auto-service-register:0.0.8")
    }
}
```

#### 2. buildSrc/build.gradle

```groovy
buildscript {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/repository/google' }
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0"  // 从 1.8.0 升级
    }
}

apply plugin: 'java-library'
apply plugin: 'groovy'
apply plugin: 'java'
apply plugin: 'kotlin'

repositories {
    google()
    mavenCentral()
    // ... 其他仓库
}

sourceCompatibility = 17

compileKotlin {
    kotlinOptions.jvmTarget = "17"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation('com.squareup:javapoet:1.13.0')
    api("com.android.tools.build:gradle:8.13.0")  // 从 7.4.0 升级
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.javassist:javassist:3.28.0-GA")
    implementation "org.jetbrains.kotlin:kotlin-stdlib:2.1.0"  // 从 1.8.0 升级
}
```

#### 3. gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip  # 从 7.5 升级
distributionPath=wrapper/dists
zipStorePath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
```

## 任务依赖链

### 依赖关系图

```
JavaCompile (应用代码)
    ↓
OkRouterRegisterTask (扫描和生成)
    ↓
OkRouterCompileTask (编译生成的代码)
    ↓
DexBuilder / Assemble (最终构建)
```

### 依赖实现代码

```kotlin
// 1. 注册任务必须在 Java 编译后运行
registerTask.configure {
    it.mustRunAfter(javaCompileTask)
}

// 2. 编译任务依赖于 Java 编译
compileTask.configure {
    it.dependsOn(javaCompileTask)
    // 3. 编译任务必须在注册任务后运行
    it.mustRunAfter(registerTask.get())
}

// 4. 确保最终任务依赖于我们的任务
variant.assembleProvider.get().dependsOn(registerTask, compileTask)
```

## 增量编译支持

### 检测变更

```kotlin
@get:InputFiles
abstract val classpath: ConfigurableFileCollection

@get:OutputDirectory
abstract val targetDir: DirectoryProperty
```

Gradle 会自动检测：
- 输入文件（classpath）的变更
- 输出目录的变更
- 只在必要时重新执行任务

### 性能优化

```
首次构建：
  OkRouterRegisterTask: 100ms
  OkRouterCompileTask: 200ms
  总计: 300ms

增量构建（无变更）：
  OkRouterRegisterTask: UP-TO-DATE
  OkRouterCompileTask: UP-TO-DATE
  总计: 0ms

增量构建（路由注解变更）：
  OkRouterRegisterTask: 100ms
  OkRouterCompileTask: 200ms
  总计: 300ms

增量构建（非路由代码变更）：
  OkRouterRegisterTask: UP-TO-DATE
  OkRouterCompileTask: UP-TO-DATE
  总计: 0ms
```

## 迁移步骤

### 阶段 1：环境准备

1. ✅ 升级 Gradle 到 8.13
2. ✅ 升级 AGP 到 8.13.0
3. ✅ 升级 Kotlin 到 2.1.0
4. ✅ 验证基础构建成功

### 阶段 2：插件重构

1. ✅ 创建 `OkRouterRegisterTask.kt`
2. ✅ 重构 `OkRouterTransformPlugin.kt`
3. ✅ 删除 `OkRouterTransform.kt`
4. ✅ 删除 `OkRouterTransformAction.kt`

### 阶段 3：测试验证

1. ✅ 清理构建产物
2. ✅ 运行完整构建
3. ✅ 验证生成的代码正确
4. ✅ **⭐ 关键验证：路由文档对比**
5. ✅ 运行示例应用
6. ✅ 测试路由功能
7. ✅ 验证增量编译

### 阶段 4：文档更新

1. ✅ 更新 README.md
2. ✅ 更新 CLAUDE.md
3. ✅ 更新版本常量
4. ✅ 创建迁移说明

## 回退策略

如果迁移遇到无法解决的问题：

1. **版本回退**
   ```bash
   git revert <commit>
   # 手动恢复版本配置
   ```

2. **功能回退**
   - 保持 AGP 7.4.0 分支
   - 为 AGP 8.x 创建新分支
   - 提供多版本支持

3. **方案回退**
   - 如果任务依赖方案失败
   - 尝试迁移到 AsmClassVisitorFactory
   - 参考官方文档和示例

---

**设计版本**：1.0
**创建日期**：2026-04-17
**参考实现**：auto-service-android AGP 8.13.0 适配
