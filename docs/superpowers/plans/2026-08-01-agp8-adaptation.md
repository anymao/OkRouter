# AGP 8 适配实施计划

> **供自动化执行者使用：** 按任务顺序执行；每个任务先完成失败验证，再完成最小实现与回归验证。

**目标：** 将 OkRouter Gradle 插件迁移到可在 AGP 8.13、Gradle 8.13 和 JDK 17 下构建并生成完整路由表的任务式实现。

**架构：** 插件在应用变体的 Java 编译完成后执行扫描任务，使用完整编译类路径生成 `OkRouterLoader.java`，再将该源码编译至该变体的 Java 类输出目录，并让 assemble 任务依赖此链路。生成器继续使用已有的 JavaPoet 实现，避免直接修改 AGP 中间 class 文件。

**技术栈：** Gradle 8.13、AGP 8.13.0、Kotlin 2.1、Java 17、JavaPoet、Javassist。

## 全局约束

- 保留所有现有未提交且与适配无关的改动。
- 扫描输入必须包含 Android boot classpath、Java 编译任务 classpath 与当前变体的编译输出。
- `OkRouterLoader` 必须覆盖 demo 的跨模块路由和拦截器。
- 生成任务与编译任务必须声明输入输出，以支持 Gradle 增量构建。

---

### 任务 1：建立失败验证并恢复插件编译

**文件：**
- 修改：`buildSrc/build.gradle`
- 删除：`okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterTransformAction.kt`

- [x] 运行 `./gradlew :buildSrc:compileKotlin`，确认现有 Javassist 导入导致失败。
- [x] 删除错误的字节码生成器，改由既有 `OkRouterTaskAction` 生成 Java 源码。
- [x] 再次运行 `./gradlew :buildSrc:compileKotlin`，确认插件源码可编译。

### 任务 2：实现可缓存的 AGP 8 注册与编译任务

**文件：**
- 新建：`okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterRegisterTask.kt`
- 修改：`okrouter-plugin/src/main/kotlin/com/anymore/okrouter/OkRouterTransformPlugin.kt`

- [x] 执行 `:demo-app:tasks --all`，确认当前任务链不能产出独立注册/编译任务。
- [x] 新增带 `@InputFiles` 和 `@OutputDirectory` 的注册任务，调用 `AbsOkRouterAction.forTask`。
- [x] 为每个应用变体注册源码生成与 Java 编译任务，并将其接入 assemble。
- [x] 运行 `:demo-app:assembleDebug`，确认任务链与 APK 构建成功。

### 任务 3：验收路由表完整性与发布配置

**文件：**
- 修改：`okrouter-plugin/build.gradle`
- 修改：`gradle.properties`
- 修改：`CLAUDE.md`

- [x] 检查生成的 `OkRouterLoader.java` 是否包含跨模块路由与三个拦截器。
- [x] 检查生成的 class 是否存在于 debug 变体输出目录。
- [x] 对比生成的路由文档与 demo 声明，确认稳定路由、正则路由和拦截器均被发现。
- [x] 统一独立插件模块的 AGP/Kotlin 版本，移除过期 Transform 配置。
- [x] 运行 `./gradlew build` 与 `git diff --check`。
