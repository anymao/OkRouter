## 1. 基础依赖升级

- [x] 1.1 更新 Gradle Wrapper 到 7.5.2
- [x] 1.2 更新根 build.gradle 中的 AGP 版本到 7.4.0
- [x] 1.3 更新 Kotlin 版本到 1.8.0
- [x] 1.4 验证 Gradle 配置正确性

## 2. buildSrc 适配

- [x] 2.1 更新 buildSrc/build.gradle 中的 AGP 依赖版本到 7.4.0
- [x] 2.2 更新 buildSrc 中的 Kotlin 插件配置
- [x] 2.3 更新 buildSrc 中的 Kotlin 依赖版本
- [x] 2.4 测试 buildSrc 构建

## 3. Transform 插件适配

- [x] 3.1 验证现有 Transform API 与 AGP 7.4.0 的兼容性（确认兼容，内部 API 可能有风险）
- [x] 3.2 检查并更新 okrouter-plugin 中的依赖配置
- [x] 3.3 测试 Transform 插件注册和执行
- [x] 3.4 验证路由表代码生成功能正常

## 4. 模块配置更新

- [x] 4.1 更新 okrouter 模块的 build.gradle 配置
- [x] 4.2 更新 okrouter-stub 模块的 build.gradle 配置
- [x] 4.3 更新所有示例模块的 build.gradle 配置（demo-base, demo-login, demo-web, demo-biz1, demo-biz2, demo-app）
- [x] 4.4 统一 Java 版本配置到 17
- [x] 4.5 更新废弃的配置方式（如 minSdkVersion()）

## 5. 验证测试

- [x] 5.1 清理并构建所有模块
- [x] 5.2 构建 demo-app 应用
- [x] 5.3 验证构建产物正确性
- [x] 5.4 记录升级后的配置变更
