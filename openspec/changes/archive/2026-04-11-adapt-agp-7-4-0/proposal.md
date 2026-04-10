## Why

当前项目使用 Android Gradle Plugin (AGP) 4.2.2 和 Kotlin 1.5.31，这些版本已经过时且不支持现代 Android 开发特性。升级到 AGP 7.4.0 可以带来更好的构建性能、最新的 API 支持，以及与现代 Android 工具链的兼容性。

## What Changes

- 升级根 build.gradle 中的 AGP 版本从 4.2.2 到 7.4.0
- 升级 buildSrc/build.gradle 中的 AGP 依赖从 4.2.2 到 7.4.0
- 升级 Kotlin 版本从 1.5.31 到 1.8.0（AGP 7.4.0 推荐版本）
- 更新 Gradle Transform API 使用方式以适配 AGP 7.4.0
- 更新 buildSrc 的 Kotlin 插件配置方式
- 更新 Java 版本配置以符合 AGP 7.4.0 的要求
- 移除废弃的 buildscript 配置，改用 plugins DSL
- 更新模块 build.gradle 文件以兼容新的 AGP 版本

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- 根 build.gradle：构建脚本配置
- buildSrc/build.gradle：Gradle 插件构建配置
- okrouter-plugin/：Transform 插件代码需要适配新的 Transform API
- 所有模块的 build.gradle：需要检查兼容性
- 示例应用：需要验证构建和运行
