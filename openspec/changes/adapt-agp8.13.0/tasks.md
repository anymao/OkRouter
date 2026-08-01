# Tasks: AGP 8.13.0 适配任务清单

## 阶段 1：环境准备和版本升级

### Task 1.1：升级 Gradle 到 8.13

- [ ] 备份当前的 `gradle/wrapper/gradle-wrapper.properties`
- [ ] 修改 `distributionUrl` 为 `gradle-8.13-bin.zip`
- [ ] 运行 `./gradlew --version` 验证 Gradle 版本
- [ ] 运行 `./gradlew build` 验证基础构建
- [ ] 解决任何 Gradle 版本相关的错误

**预计时间**：30 分钟  
**风险**：低  
**回退**：恢复备份的 gradle-wrapper.properties

---

### Task 1.2：升级 AGP 到 8.13.0

- [ ] 备份 `build.gradle`
- [ ] 修改 AGP 版本为 `8.13.0`
  ```groovy
  classpath "com.android.tools.build:gradle:8.13.0"
  ```
- [ ] 备份 `buildSrc/build.gradle`
- [ ] 修改 buildSrc 中的 AGP 版本为 `8.13.0`
  ```groovy
  api("com.android.tools.build:gradle:8.13.0")
  ```
- [ ] 运行 `./gradlew clean build`
- [ ] 解决 AGP 升级相关的配置错误

**预计时间**：1 小时  
**风险**：中等  
**回退**：恢复备份的 build.gradle 文件

---

### Task 1.3：升级 Kotlin 到 2.1.0

- [ ] 修改 `build.gradle` 中的 Kotlin 版本
  ```groovy
  ext.kotlin_version = "2.1.0"
  ```
- [ ] 修改 `buildSrc/build.gradle` 中的 Kotlin 版本
  ```groovy
  classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0"
  implementation "org.jetbrains.kotlin:kotlin-stdlib:2.1.0"
  ```
- [ ] 更新 `buildSrc/build.gradle` 中的 Kotlin JVM 目标
  ```groovy
  compileKotlin {
      kotlinOptions.jvmTarget = "17"
      kotlinOptions.languageVersion = "1.9"  // Kotlin 2.1.0 对应的语言版本
  }
  ```
- [ ] 运行 `./gradlew clean build`
- [ ] 解决 Kotlin 升级相关的错误

**预计时间**：30 分钟  
**风险**：低  
**回退**：恢复 Kotlin 版本配置

---

### Task 1.4：验证 namespace 配置

- [ ] 检查所有模块的 `build.gradle` 文件
- [ ] 验证每个模块都有 `namespace` 声明
- [ ] 列出缺少 namespace 的模块（如有）
- [ ] 为缺少 namespace 的模块添加配置

**预计时间**：15 分钟  
**风险**：低

---

## 阶段 2：插件重构

### Task 2.1：创建 OkRouterRegisterTask

- [ ] 在 `okrouter-plugin/src/main/kotlin/com/anymore/okrouter/` 创建新文件
- [ ] 实现 `OkRouterRegisterTask` 类
  ```kotlin
  abstract class OkRouterRegisterTask : DefaultTask() {
      @get:Input
      abstract val classpath: ConfigurableFileCollection
      
      @get:OutputDirectory
      abstract val targetDir: DirectoryProperty
      
      @get:Internal
      abstract val project: Property<Project>
      
      @TaskAction
      fun execute() {
          // 实现调用 AbsOkRouterAction 的逻辑
      }
  }
  ```
- [ ] 实现任务输入输出声明（支持增量编译）
- [ ] 实现任务执行逻辑
- [ ] 添加适当的日志输出

**预计时间**：1 小时  
**风险**：中等  
**依赖**：Task 1.3

---

### Task 2.2：重构 OkRouterTransformPlugin

- [ ] 备份 `OkRouterTransformPlugin.kt`
- [ ] 移除 `android.registerTransform()` 调用
- [ ] 添加 `android.applicationVariants.forEach` 遍历
- [ ] 实现 `setupVariantTasks()` 方法
  ```kotlin
  private fun setupVariantTasks(project: Project, variant: ApplicationVariant) {
      // 创建工作目录
      // 创建 registerTask
      // 创建 compileTask
      // 管理依赖关系
  }
  ```
- [ ] 实现任务依赖链管理
- [ ] 添加必要的错误处理

**预计时间**：2 小时  
**风险**：中等  
**依赖**：Task 2.1

---

### Task 2.3：删除废弃的文件

- [ ] 删除 `OkRouterTransform.kt`
- [ ] 删除 `OkRouterTransformAction.kt`
- [ ] 验证项目结构正确

**预计时间**：10 分钟  
**风险**：低  
**依赖**：Task 2.2

---

### Task 2.4：编译和验证插件

- [ ] 运行 `./gradlew :buildSrc:clean :buildSrc:build`
- [ ] 验证插件编译成功
- [ ] 检查生成的插件 jar 文件

**预计时间**：15 分钟  
**风险**：低  
**依赖**：Task 2.3

---

## 阶段 3：测试和验证

### Task 3.1：清理构建产物

- [ ] 运行 `./gradlew clean`
- [ ] 验证 `build/` 目录已清空

**预计时间**：5 分钟  
**风险**：无

---

### Task 3.2：运行完整构建

- [ ] 运行 `./gradlew build`
- [ ] 检查构建输出
- [ ] 记录构建时间
- [ ] 解决任何构建错误

**预计时间**：15 分钟  
**风险**：中等  
**依赖**：Task 3.1

---

### Task 3.3：验证生成的代码

- [ ] 检查 `build/intermediates/okrouter/` 目录
- [ ] 验证 `OkRouterLoader.java` 文件存在
- [ ] 检查生成代码的内容和结构
- [ ] 验证编译后的 class 文件存在

**预计时间**：15 分钟  
**风险**：低  
**依赖**：Task 3.2

---

### Task 3.4：运行示例应用

- [ ] 运行 `./gradlew :demo-app:installDebug`
- [ ] 等待应用安装完成
- [ ] 启动应用

**预计时间**：10 分钟  
**风险**：中等  
**依赖**：Task 3.3

---

### Task 3.5：验证路由功能

- [ ] 测试基本路由跳转功能
- [ ] 测试带参数的路由
- [ ] 测试拦截器功能
- [ ] 测试正则路由
- [ ] 检查日志输出

**预计时间**：30 分钟  
**风险**：中等  
**依赖**：Task 3.4

---

### Task 3.5.1：⭐ 关键验证 - 路由文档对比

- [ ] 备份当前的路由文档（迁移前）
  ```bash
  cp example/demo-app/路由文档.md example/demo-app/路由文档.md.before
  ```
- [ ] 运行完整构建后生成新的路由文档
- [ ] 对比两个文档的内容（除创建时间外）
- [ ] 验证以下内容必须完全一致：
  - [ ] 所有固定路由（uri, class, type, interceptors, desc）
  - [ ] 所有正则路由
  - [ ] 所有拦截器（class, alias, priority, global, singleton, desc）
- [ ] 如果发现差异，记录详细对比信息
- [ ] 如果内容不一致，**停止实施并调查字节码扫描逻辑**

**重要提示**：
```
这是最关键的验证点！

原因：
- 路由文档是由扫描字节码生成的
- 文档内容一致 = 字节码扫描逻辑正确
- 文档内容不一致 = 扫描逻辑有问题

示例验证：
迁移前的路由文档：
  okrouter://android/biz1|comBiz1Activity|ACTIVITY||...

迁移后的路由文档（必须一致）：
  okrouter://android/biz1|comBiz1Activity|ACTIVITY||...

唯一允许的差异：创建时间戳
```

**对比方法**：
```bash
# 方法 1：手动对比
diff -u example/demo-app/路由文档.md.before example/demo-app/路由文档.md

# 方法 2：脚本对比（忽略创建时间）
cat > compare_docs.sh << 'EOF'
#!/bin/bash
# 提取路由内容（忽略创建时间）
grep -A 1000 "## 1.路由" $1 > /tmp/doc1_routes.txt
grep -A 1000 "## 1.路由" $2 > /tmp/doc2_routes.txt

# 对比路由内容
diff /tmp/doc1_routes.txt /tmp/doc2_routes.txt
EOF

chmod +x compare_docs.sh
./compare_docs.sh example/demo-app/路由文档.md.before example/demo-app/路由文档.md
```

**预计时间**：45 分钟  
**风险**：高（如果扫描逻辑有问题）  
**依赖**：Task 3.4  
**阻断条件**：**文档内容不一致必须先解决才能继续**

---

### Task 3.6：测试增量编译

- [ ] 记录完整构建时间
- [ ] 修改一个非路由相关的文件
- [ ] 运行增量构建
- [ ] 验证 OkRouter 相关任务被跳过（UP-TO-DATE）
- [ ] 修改一个路由注解
- [ ] 运行增量构建
- [ ] 验证 OkRouter 相关任务被重新执行
- [ ] 对比增量构建和完整构建的时间

**预计时间**：30 分钟  
**风险**：低  
**依赖**：Task 3.5

---

## 阶段 4：文档更新

### Task 4.1：更新 README.md

- [ ] 查找所有 AGP 7.4.0 的引用
- [ ] 更新为 AGP 8.13.0
- [ ] 查找所有 Kotlin 1.8.0 的引用
- [ ] 更新为 Kotlin 2.1.0
- [ ] 查找所有 Gradle 7.5 的引用
- [ ] 更新为 Gradle 8.13
- [ ] 更新快速开始指南

**预计时间**：30 分钟  
**风险**：低

---

### Task 4.2：更新 CLAUDE.md

- [ ] 更新版本要求部分
  ```markdown
  ### 版本信息
  - Kotlin: 2.1.0
  - Android Gradle Plugin: 8.13.0
  - Gradle: 8.13
  - Compile SDK: 33
  - Min SDK: 17
  ```
- [ ] 更新项目概述
- [ ] 更新架构说明

**预计时间**：15 分钟  
**风险**：低

---

### Task 4.3：更新 buildSrc 依赖

- [ ] 检查 buildSrc/build.gradle 中的所有依赖
- [ ] 确保所有版本与升级后的版本一致
- [ ] 验证依赖解析成功

**预计时间**：10 分钟  
**风险**：低

---

### Task 4.4：创建迁移说明文档

- [ ] 创建 `MIGRATION_AGP8.md` 文件
- [ ] 记录迁移背景和原因
- [ ] 记录技术方案选择
- [ ] 记录迁移步骤
- [ ] 记录性能对比数据
- [ ] 记录已知问题和解决方案

**预计时间**：45 分钟  
**风险**：低

---

### Task 4.5：更新版本号和发布信息

- [ ] 更新 `version.gradle`（如存在）
- [ ] 更新 `CHANGELOG.md`
- [ ] 准备发布说明

**预计时间**：30 分钟  
**风险**：低

---

## 验收标准

### 功能验证

- [ ] ✅ 所有模块构建成功无错误
- [ ] ✅ 构建无警告（AGP 弃用警告除外）
- [ ] ✅ 示例应用可以正常安装和运行
- [ ] ✅ 所有路由功能正常工作
- [ ] ✅ 拦截器功能正常工作
- [ ] ✅ 增量编译正常工作

### 性能验证

- [ ] ✅ 完整构建时间 ≤ 原实现的 110%
- [ ] ✅ 增量编译（无变更）时间 ≤ 100ms
- [ ] ✅ 增量编译（路由变更）时间 ≤ 原实现的 110%

### 文档验证

- [ ] ✅ 所有文档中的版本信息已更新
- [ ] ✅ 迁移说明文档完整清晰
- [ ] ✅ README 中的构建命令可以正常工作

### 代码质量

- [ ] ✅ 新增代码有适当的注释
- [ ] ✅ 删除的文件确实不需要
- [ ] ✅ 项目结构清晰合理

---

## 风险和缓解措施

| 任务 | 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|------|---------|
| 1.2 | AGP 版本兼容性问题 | 中 | 高 | 参考已验证的 auto-service-android |
| 2.2 | 任务依赖链配置错误 | 中 | 高 | 详细参考示例实现 |
| 2.2 | 变体 API 使用不当 | 中 | 中 | 查阅 AGP 8.x 文档 |
| 3.2 | 构建失败 | 中 | 中 | 逐步解决错误，记录日志 |
| 3.5 | 运行时功能异常 | 低 | 高 | 完整测试所有路由场景 |
| 3.6 | 增量编译不工作 | 低 | 中 | 检查输入输出声明 |

---

## 时间汇总

| 阶段 | 任务数 | 预计时间 |
|------|--------|---------|
| 阶段 1：环境准备 | 4 | 2 小时 15 分钟 |
| 阶段 2：插件重构 | 4 | 3 小时 30 分钟 |
| 阶段 3：测试验证 | 6 | 2 小时 |
| 阶段 4：文档更新 | 5 | 2 小时 10 分钟 |
| **总计** | **19** | **约 10 小时** |

---

**任务清单版本**：1.0
**创建日期**：2026-04-17
**参考**：auto-service-android AGP 8.13.0 适配任务清单
