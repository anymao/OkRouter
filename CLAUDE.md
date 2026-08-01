# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

OkRouter 是一个 Android 路由组件，利用 Gradle 插件生成路由表，避免了反射行为。实现方案参考了 DRouter 和 OkHttp。

## 项目结构

- `okrouter/` - 核心路由库（Android Library），包含路由注解、拦截器接口、路由分发逻辑
- `okrouter-plugin/` - Gradle 插件源码，用于编译时生成路由表代码
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
- 通过 `OkRouterTransformPlugin` 为每个应用变体注册路由扫描与编译任务
- 使用 Javassist 扫描完整编译类路径，并使用 JavaPoet 生成 `OkRouterLoader.java`
- 将生成的 Loader 编译至变体 classes 输出后参与 DEX 构建，避免运行时反射

## 常用命令

### 构建
```bash
./gradlew build
```

### 清理构建产物
```bash
./gradlew clean
```

### 运行示例应用
```bash
./gradlew :demo-app:installDebug
```

### 发布到私有 Maven（需要配置凭证）
```bash
./gradlew :okrouter:publishReleasePublicationToPrivateMavenRepository \
  && (cd buildSrc && ../gradlew publishMavenPublicationToPrivateMavenRepository)
```

## 开发注意事项

### 环境变量
Maven 发布需要设置以下环境变量：
- `maven_username` 或 `ALIYUN_USERNAME` - 私有 Maven 用户名
- `maven_password` 或 `ALIYUN_PASSWORD` - 私有 Maven 密码
- `maven_release_url` 或 `MAVEN_RELEASE_URL` - 可选，正式版仓库地址
- `maven_snapshot_url` 或 `MAVEN_SNAPSHOT_URL` - 可选，快照仓库地址

### 版本信息
- Kotlin: 2.1.0
- Android Gradle Plugin: 8.13.0
- Gradle: 8.13
- Compile SDK: 33
- Min SDK: 17

### 关健类
- `OkRouter` - 路由入口，提供 `init()`, `build()`, `start()` 方法
- `WareHouse` - 路由和拦截器仓储中心
- `RouterDispatcher` - 路由分发器，处理路由请求
- `OkRouterTransformPlugin` - Gradle 插件入口

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **OkRouter** (1257 symbols, 1847 relationships, 18 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "master"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/OkRouter/context` | Codebase overview, check index freshness |
| `gitnexus://repo/OkRouter/clusters` | All functional areas |
| `gitnexus://repo/OkRouter/processes` | All execution flows |
| `gitnexus://repo/OkRouter/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

## GBrain 配置（由 /setup-gbrain 配置）

- 模式：本地 stdio
- 引擎：PGLite
- 配置文件：`~/.gbrain/config.json`（权限 0600）
- 配置日期：2026-08-01
- MCP：已注册（用户范围）
- Artifacts 仓库：`https://github.com/anymao/gstack-artifacts.git`
- Artifacts 同步：仅产物（artifacts-only）
- 当前仓库策略：读写（read-write）
- 历史会话导入：关闭（当前 gstack 未提供扫描脚本）

## GBrain 检索指引（由 /sync-gbrain 配置）

<!-- gstack-gbrain-search-guidance:start -->

本机已配置并同步 GBrain。遇到语义问题，或尚不知道确切标识符时，优先使用
GBrain，而不是 Grep。当前可用的索引包括：

- 本仓库代码（`gstack-code-okrouter`）。
- `~/.gstack/` 的允许同步产物（通过私有 artifacts 仓库）。

优先使用 GBrain 的场景：

- “X 在哪里处理？”或不知道具体名称的语义检索：`gbrain search "关键词"` 或
  `gbrain query "问题"`。
- “符号 Y 定义在哪里？”：`gbrain code-def <符号>` 或
  `gbrain code-refs <符号>`。
- “谁调用了 Y？”或“Y 依赖什么？”：`gbrain code-callers <符号>` 或
  `gbrain code-callees <符号>`。
- “上次做了什么决定？”：在 artifacts 源中检索相关计划、复盘或学习记录。

已知精确字符串、正则、多行模式和文件 glob 仍应使用 Grep。每次 gstack skill
启动会增量同步；运行 `/sync-gbrain` 可强制刷新，`/sync-gbrain --full` 可完整重建。

<!-- gstack-gbrain-search-guidance:end -->
