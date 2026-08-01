# Pre-Implementation Checklist: AGP 8.13.0 适配实施前检查

## 🎯 实施前准备检查

在开始实施之前，请确认以下所有检查项都已完成。

---

## ✅ 研究和规划阶段

### 研究完成

- [x] 分析了 auto-service-android 的 AGP 8.13.0 适配实现
- [x] 发现了关键方案：任务依赖管理
- [x] 确定了版本选择：AGP 8.13.0 + Gradle 8.13 + Kotlin 2.1.0
- [x] 创建了完整的 OpenSpec 变更提案

### 文档完成

- [x] `proposal.md` - 变更提案
- [x] `design.md` - 技术设计
- [x] `tasks.md` - 任务清单
- [x] `summary.md` - 变更总结
- [x] `overview.md` - 可视化概览
- [x] `validation-guide.md` - 验证指南

### 关键验证点

- [x] 补充了路由文档对比验证
- [x] 创建了详细的验证指南
- [x] 更新了任务清单包含验证步骤

---

## 🔍 环境检查

### 当前项目状态

- [ ] 确认当前在 `feature/agp8` 分支
- [ ] 确认 Git 工作区干净（无未提交的更改）
- [ ] 确认 Gradle 版本为 7.5
- [ ] 确认 AGP 版本为 7.4.0
- [ ] 确认项目可以正常构建

```bash
# 检查当前分支
git branch --show-current

# 检查 Git 状态
git status

# 检查 Gradle 版本
./gradlew --version

# 检查当前构建状态
./gradlew build
```

---

## 📋 关键路由文档信息

### 当前路由文档统计

请确认当前路由文档的信息：

```markdown
# 路由统计
- 固定路由数：10 个
- 正则路由数：1 个
- 拦截器数：3 个

# 关键路由示例
1. okrouter://android/biz1 → Biz1Activity
2. okrouter://android/main → MainActivity
3. okrouter://android/login → LoginActivity

# 关键拦截器示例
1. Biz2Interceptor (priority: 0)
2. GlobalLoginInterceptor (global: true, singleton: true)
3. LoginInterceptor (alias: LoginCheckInterceptor, priority: -2147483648)
```

### 验证命令

```bash
# 查看当前路由文档
cat example/demo-app/路由文档.md

# 统计路由数量
echo "固定路由数：$(grep -E '^okrouter://' example/demo-app/路由文档.md | wc -l)"
echo "正则路由数：$(grep -E '^https?://' example/demo-app/路由文档.md | wc -l)"
echo "拦截器数：$(grep '^com.anymore' example/demo-app/路由文档.md | tail -n +3 | wc -l)"
```

---

## 💾 备份准备

### 备份当前路由文档

在开始升级前，**必须备份当前的路由文档**：

```bash
# 备份路由文档
cp example/demo-app/路由文档.md example/demo-app/路由文档.md.before

# 验证备份成功
ls -la example/demo-app/路由文档.md*

# 显示备份时间
echo "备份时间：$(stat -f '%Sm' -H '%y-%m-%d %H:%M:%S' example/demo-app/路由文档.md.before)"
```

**检查清单**：
- [ ] `路由文档.md.before` 文件已创建
- [ ] 备份文件大小与原文件相同
- [ ] 备份时间与当前时间一致

### 备份关键配置文件

```bash
# 创建备份目录
mkdir -p .backup/agp8-migration-$(date +%Y%m%d)

# 备份关键配置文件
cp build.gradle .backup/agp8-migration-$(date +%Y%m%d)/
cp buildSrc/build.gradle .backup/agp8-migration-$(date +%Y%m%d)/
cp gradle/wrapper/gradle-wrapper.properties .backup/agp8-migration-$(date +%Y%m%d)/

# 列出备份文件
ls -la .backup/agp8-migration-$(date +%Y%m%d)/
```

---

## 🎯 风险评估和缓解措施

### 已识别的风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Gradle 版本升级问题 | 低 | 中 | 有备份，可快速回退 |
| AGP 版本兼容性 | 中 | 高 | 参考已验证的 auto-service-android |
| Kotlin 版本升级问题 | 低 | 中 | 逐步升级 |
| 插件重构错误 | 中 | 高 | 详细参考示例实现 |
| 路由文档不一致 | 低 | 高 | ⭐ 优先验证，发现即停止 |

### 回退准备

```bash
# 创建回退脚本
cat > .backup/revert-agp8.sh << 'EOF'
#!/bin/bash
echo "回退 AGP 8.13.0 迁移..."
git checkout HEAD~1 -- build.gradle buildSrc/build.gradle gradle/wrapper/gradle-wrapper.properties
./gradlew clean build
echo "回退完成"
EOF

chmod +x .backup/revert-agp8.sh
```

---

## 📝 实施时间规划

### 预计总时间

| 阶段 | 任务数 | 预计时间 | 建议时间窗口 |
|------|--------|---------|------------|
| 阶段 1：环境准备 | 4 | 2 小时 15 分钟 | 连续的 2-3 小时 |
| 阶段 2：插件重构 | 4 | 3 小时 30 分钟 | 集中的半天 |
| 阶段 3：测试验证 | 6 | 2 小时 45 分钟 | 集中的半天 |
| 阶段 4：文档更新 | 5 | 2 小时 10 分钟 | 集中的 1 小时 |
| **总计** | **20** | **约 10 小时 10 分钟** | **建议：1-2 个工作日** |

### 建议的时间分配

**方案 A：一次性完成**（推荐）
- 优点：一气呵成，避免中间状态
- 缺点：需要较长时间连续投入
- 建议时间：一个完整的工作日（8-10 小时）

**方案 B：分阶段完成**
- 优点：每次只专注一个阶段
- 缺点：需要多次提交和测试
- 建议时间：2-3 个工作日

---

## 🚦 开始实施前的最终检查

### 必须完成的检查

- [ ] ✅ 研究和规划阶段全部完成
- [ ] 🔄 环境检查（当前分支、Git 状态）
- [ ] 🔄 当前路由文档已备份
- [ ] 🔄 关键配置文件已备份
- [ ] 🔄 确认了路由文档统计信息
- [ ] 🔄 评估了风险并准备了回退方案
- [ ] 🔄 规划了实施时间

### 开始实施的最终确认

在开始实施前，请确认以下几点：

```
□ 我已经阅读并理解了技术方案（design.md）
□ 我已经阅读并理解了任务清单（tasks.md）
□ 我已经阅读并理解了验证指南（validation-guide.md）
□ 我已经备份了路由文档
□ 我已经备份了关键配置文件
□ 我准备好了 10 小时的连续工作时间
□ 我知道如果遇到问题如何快速回退
□ 我确认当前在 feature/agp8 分支
□ 我确认 Git 工作区干净
```

---

## 📞 快速参考

### 关键文件位置

```
变更文档：
openspec/changes/adapt-agp8.13.0/
├── proposal.md              - 变更提案
├── design.md               - 技术设计
├── tasks.md                - 任务清单
├── summary.md              - 变更总结
├── overview.md             - 可视化概览
└── validation-guide.md      - 验证指南

备份文件：
example/demo-app/路由文档.md.before      - 路由文档备份
.backup/agp8-migration-YYYYMMDD/      - 配置文件备份
```

### 常用命令

```bash
# 开始实施
./gradlew clean build

# 回退
git checkout HEAD~1 -- build.gradle buildSrc/build.gradle
./gradlew clean build

# 查看进度
cat progress.md

# 查看任务清单
cat openspec/changes/adapt-agp8.13.0/tasks.md

# 验证路由文档
diff -u example/demo-app/路由文档.md.before example/demo-app/路由文档.md
```

---

## ✅ 准备就绪确认

当你完成以上所有检查项后，就可以开始实施了。

### 开始实施的下一步

1. 使用 `/opsx:apply` 命令开始执行任务
2. 或者手动按照 `tasks.md` 中的任务列表逐一执行
3. 完成每个任务后更新 `progress.md`

### 预祝

祝 AGP 8.13.0 适配顺利！如有任何问题，可以随时查阅 `validation-guide.md` 获取帮助。

---

**检查清单版本**：1.0
**创建日期**：2026-04-18
**关联文档**：所有 OpenSpec 变更文档
