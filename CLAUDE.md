# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

OkRouter 是一个 Android 路由组件，利用 Gradle 插件生成路由表，避免了反射行为。实现方案参考了 DRouter 和 OkHttp。

## 项目结构

- `okrouter/` - 核心路由库（Android Library），包含路由注解、拦截器接口、路由分发逻辑
- `okrouter-plugin/` - Gradle Transform 插件源码，用于编译时生成路由表代码
- `okrouter-stub/` - 存根库，仅包含编译时需要的注解和接口
- `buildSrc/` - 构建逻辑，将 okrouter-plugin 的代码编译为 Gradle 插件
- `example/` - 示例应用目录
  - `demo-app/` - 主应用模块
  - `demo-base/`, `demo-login/`, `demo-web/`, `demo-biz1/`, `demo-biz2/` - 业务模块示例

## 核心架构

### 路由系统
- 使用 `@Router` 注解标记路由目标类
- 使用 `@Interceptor` 注解标记拦截器
- 路由表分为三类：
  - 稳定路由（无正则表达式）：`WareHouse.stableRouters`
  - 动态路由（正则匹配后的缓存）：`WareHouse.dynamicRouters`
  - 正则路由：`WareHouse.regexRouters`
- 支持全局拦截器和路由专属拦截器，按优先级排序执行

### 编译时生成
- 通过 `OkRouterTransformPlugin` 注册 Gradle Transform
- 使用 JavaPoet 和 Javassist 在编译时扫描字节码
- 生成路由表注册代码，避免运行时反射

## 常用命令

### 构建
```bash
./gradlew build
```

### 清理构建产物
```bash
```

### 运行示例应用
```bash
./gradlew :demo-app:installDebug
```

### 发布到 Maven（需要配置阿里云凭证）
```bash
export ALIYUN_USERNAME="username"
export ALIYUN_PASSWORD="password"
./gradlew :okrouter:uploadArchives
./gradlew :okrouter-plugin:uploadArchives
./gradlew :okrouter-stub:uploadArchives
```

## 开发注意事项

### 环境变量
Maven 发布需要设置以下环境变量：
- `ALIYUN_USERNAME` - 阿里云 Maven 用户名
- `ALIYUN_PASSWORD` - 阿里云 Maven 密码

### 版本信息
- Kotlin: 1.5.31
- Android Gradle Plugin: 4.2.2
- Compile SDK: 33
- Min SDK: 17

### 关健类
- `OkRouter` - 路由入口，提供 `init()`, `build()`, `start()` 方法
- `WareHouse` - 路由和拦截器仓储中心
- `RouterDispatcher` - 路由分发器，处理路由请求
- `OkRouterTransformPlugin` - Gradle 插件入口
