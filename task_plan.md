# AGP 8 适配任务规划

## 目标声明

将 OkRouter 项目从 Android Gradle Plugin 7.4.0 适配到 AGP 8.x，解决 Transform API 弃用问题，确保项目在 AGP 8.x 环境下正常构建和运行。

## 背景信息

- **当前状态**：AGP 7.4.0 + Gradle 自适应 + Kotlin 1.8.0 + JDK 17
- **AGP 7.x 适配**：已完成（参考提交 e2f5084）
- **关键挑战**：AGP 8.x 正式弃用了 Transform API，需要迁移到 AsmClassVisitorFactory

## AGP 8.x 主要变更

1. **Transform API 弃用** → 必须迁移到 AsmClassVisitorFactory
2. **Gradle 版本要求**：需要 Gradle 8.0+
3. **namespace 强制**：所有模块必须定义 namespace
4. **JDK 17 要求**：已满足（当前已配置 JDK 17）

---

## 阶段 1：研究 AGP 8.x 变更和迁移指南 [complete]

### 目标
了解 AGP 8.x 的所有破坏性变更，确定迁移路径。

### 任务
- [x] 阅读 AGP 8.0 迁移指南
- [x] 研究 Transform API 迁移方案
- [x] 查看 auto-service-android 的 AGP 8 适配实现
- [x] **重大发现：使用任务依赖管理而非 AsmClassVisitorFactory**
- [ ] 确定版本选择（推荐 8.13.0）

### 预期成果
- `findings.md` 中记录 AGP 8.x 关键变更点
- 明确迁移方案：Transform API → 任务依赖管理
- 列出需要修改的文件清单

---

## 阶段 2：升级 Gradle 版本 [pending]

### 目标
升级 Gradle 到 8.0+ 版本，满足 AGP 8.x 的依赖要求。

### 任务
- [ ] 检查当前 Gradle 版本
- [ ] 升级 Gradle Wrapper 到 8.x
- [ ] 验证 Gradle 版本兼容性
- [ ] 测试基础构建命令

### 预期成果
- Gradle 版本升级到 8.0+
- 项目基础构建可以正常执行

### 风险评估
- 低风险：Gradle 向后兼容性较好

---

## 阶段 3：升级 AGP 到 8.13.0 [pending]

### 目标
将 Android Gradle Plugin 从 7.4.0 升级到 8.13.0。

### 任务
- [x] 确定目标 AGP 版本：8.13.0（参考 auto-service-android）
- [ ] 更新根 build.gradle 中的 AGP 版本为 8.13.0
- [ ] 更新 buildSrc/build.gradle 中的 AGP 版本为 8.13.0
- [ ] 更新 Kotlin 版本为 2.1.0
- [ ] 检查所有模块的 namespace 配置
- [ ] 运行构建并解决 AGP 升级相关的配置错误

### 预期成果
- AGP 版本升级到 8.13.0
- Kotlin 版本升级到 2.1.0
- 所有模块正确定义 namespace
- 构建可以执行到相关步骤

### 风险评估
- 中等风险：可能会有配置变更导致构建失败

---

## 阶段 4：Transform API 迁移方案设计 [pending]

### 目标
分析现有 Transform 实现并设计迁移到任务依赖管理方式。

### 任务
- [x] 分析 OkRouterTransformPlugin 的实现
- [x] 分析 auto-service-android 的任务依赖实现
- [ ] 设计基于任务依赖的插件架构
- [ ] 确定如何复用现有的 AbsOkRouterAction 逻辑
- [ ] 确定输出目录和依赖链设计
- [ ] 评估迁移的工作量和风险

### 预期成果
- 完整的迁移技术方案（基于任务依赖）
- 迁移步骤清单
- 风险和回退策略

### 风险评估
- 中等风险：架构调整但核心逻辑复用

---

## 阶段 5：实现基于任务依赖的插件迁移 [pending]

### 目标
将插件从 Transform API 迁移到任务依赖管理方式。

### 任务
- [ ] 创建新的 OkRouterRegisterTask（参考 AutoServiceRegisterTask）
- [ ] 创建新的 OkRouterCompileTask（编译生成的代码）
- [ ] 重构 OkRouterTransformPlugin：
  - 移除 registerTransform 调用
  - 添加 android.applicationVariants.forEach 遍历
  - 管理任务依赖链
- [ ] 复用现有的 AbsOkRouterAction 和 OkRouterTransformAction
- [ ] 处理输出目录和文件生成

### 预期成果
- 新的基于任务依赖的插件实现
- 保持与旧插件相同的功能和输出
- 核心扫描和生成逻辑完全复用

### 风险评估
- 中等风险：架构调整但核心逻辑保持不变

---

## 阶段 6：测试和验证 [pending]

### 目标
验证迁移后的功能正常工作。

### 任务
- [ ] 清理构建产物
- [ ] 运行完整构建
- [ ] 验证生成的路由表代码正确
- [ ] 运行示例应用
- [ ] 检查运行时路由功能
- [ ] 进行增量编译测试

### 预期成果
- 构建成功无警告
- 运行时功能正常
- 性能与原实现相当或更好

### 风险评估
- 中等：可能出现运行时问题

---

## 阶段 7：更新文档和配置 [pending]

### 目标
更新项目文档和配置以反映 AGP 8.x 的适配。

### 任务
- [ ] 更新 README 中的版本信息
- [ ] 更新 CLAUDE.md 中的版本要求
- [ ] 更新构建脚本中的版本常量
- [ ] 创建适配说明文档

### 预期成果
- 文档与实际配置一致
- 用户可以了解版本兼容性要求

---

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| | | |

---

## 决策记录

| 决策 | 原因 | 状态 |
|------|------|------|
| 选择 AsmClassVisitorFactory | AGP 8.x 弃用 Transform API | 待确认 |
| AGP 目标版本 | 需要评估稳定性和兼容性 | 待确认 |

---

## 回退策略

如果迁移遇到无法解决的问题：
1. 保持 AGP 7.4.0 作为 LTS 支持
2. 为 AGP 8.x 创建新的插件分支
3. 考虑使用兼容库（如有）
