# Validation Guide: 路由文档完整性验证

## 🚨 重要提示

这是 AGP 8.13.0 适配中**最关键的验证点**！

## 为什么这个验证如此重要？

### 路由文档生成流程

```
┌─────────────────────────────────────────────────────────────────┐
│              路由文档生成流程                            │
├─────────────────────────────────────────────────────────────────┤
│                                                           │
│  1. 编译应用代码                                        │
│     └─ 生成所有 .class 文件                             │
│                                                           │
│  2. OkRouter 插件扫描字节码                              │
│     └─ 使用 Javassist 解析 .class 文件                    │
│     └─ 查找 @Router 和 @Interceptor 注解                │
│                                                           │
│  3. 生成路由表代码和文档                                 │
│     └─ 创建 OkRouterLoader.java                             │
│     └─ 创建 路由文档.md                                   │
│                                                           │
└─────────────────────────────────────────────────────────────────┘
```

### 文档内容的一致性 = 字节码扫描逻辑的正确性

| 验证结果 | 含义 | 行动 |
|---------|------|------|
| ✅ 内容完全一致 | 扫描逻辑正确，迁移成功 | 继续验证 |
| ❌ 内容不一致 | 扫描逻辑有问题 | ⚠️ **停止并调试** |

## 验证步骤

### 步骤 1：备份当前路由文档

```bash
# 在升级前备份路由文档
cp example/demo-app/路由文档.md example/demo-app/路由文档.md.before

# 验证备份成功
ls -la example/demo-app/路由文档.md*
```

**预期输出**：
```
-rw-r--r--  1 anymore  staff  1234 Apr 17 23:45 example/demo-app/路由文档.md
-rw-r--r--  1 anymore  staff  1234 Apr 17 23:45 example/demo-app/路由文档.md.before
```

---

### 步骤 2：完成 AGP 8.13.0 升级

执行以下操作：
1. 升级 Gradle 到 8.13
2. 升级 AGP 到 8.13.0
3. 升级 Kotlin 到 2.1.0
4. 重构插件到任务依赖模式
5. 运行完整构建

```bash
# 清理构建产物
./gradlew clean

# 运行完整构建
./gradlew build

# 或仅构建示例应用
./gradlew :demo-app:build
```

---

### 步骤 3：对比路由文档

#### 方法 1：使用 diff 命令（推荐）

```bash
# 使用 diff 对比（忽略创建时间）
diff -u example/demo-app/路由文档.md.before example/demo-app/路由文档.md
```

**期望结果**：
```diff
--- example/demo-app/路由文档.md.before
+++ example/demo-app/路由文档.md
@@ -1,4 +1,4 @@
 # OkRouter-Doc
-创建时间:2026-04-17 22:49:31
+创建时间:2026-04-17 23:XX:XX
 ## 1.路由
```

**只应该有创建时间的差异**，其他内容应该完全一致！

#### 方法 2：使用自动对比脚本

创建 `compare-router-doc.sh`：

```bash
#!/bin/bash

BEFORE_DOC="$1"
AFTER_DOC="$2"

echo "=========================================="
echo "路由文档对比工具"
echo "=========================================="
echo ""

# 提取固定路由部分
echo "1. 对比固定路由..."
grep -A 1000 "## 1.路由" "$BEFORE_DOC" > /tmp/before_routes.txt
grep -A 1000 "## 1.路由" "$AFTER_DOC" > /tmp/after_routes.txt

# 移除创建时间行
sed -i '' '/创建时间:/d' /tmp/before_routes.txt
sed -i '' '/创建时间:/d' /tmp/after_routes.txt

# 对比
ROUTE_DIFF=$(diff /tmp/before_routes.txt /tmp/after_routes.txt)
if [ -z "$ROUTE_DIFF" ]; then
    echo "✅ 固定路由：完全一致"
    ROUTES_MATCH=true
else
    echo "❌ 固定路由：发现差异"
    echo "$ROUTE_DIFF"
    ROUTES_MATCH=false
fi
echo ""

# 提取拦截器部分
echo "2. 对比拦截器..."
grep -A 1000 "## 2.拦截器" "$BEFORE_DOC" > /tmp/before_interceptors.txt
grep -A 1000 "## 2.拦截器" "$AFTER_DOC" > /tmp/after_interceptors.txt

# 对比
INTERCEPTOR_DIFF=$(diff /tmp/before_interceptors.txt /tmp/after_interceptors.txt)
if [ -z "$INTERCEPTOR_DIFF" ]; then
    echo "✅ 拦截器：完全一致"
    INTERCEPTORS_MATCH=true
else
    echo "❌ 拦截器：发现差异"
    echo "$INTERCEPTOR_DIFF"
    INTERCEPTORS_MATCH=false
fi
echo ""

# 总结
echo "=========================================="
echo "验证结果"
echo "=========================================="

if [ "$ROUTES_MATCH" = true ] && [ "$INTERCEPTORS_MATCH" = true ]; then
    echo "✅ 验证通过：所有内容一致（除创建时间）"
    echo ""
    echo "可以继续进行后续验证步骤。"
    exit 0
else
    echo "❌ 验证失败：发现不一致内容"
    echo ""
    echo "⚠️ 重要：请停止实施并调查字节码扫描逻辑"
    echo ""
    echo "可能的问题："
    echo "1. classpath 配置错误"
    echo "2. 字节码扫描范围错误"
    echo "3. 注解解析逻辑问题"
    echo "4. 生成代码的时机错误"
    exit 1
fi
```

使用脚本：
```bash
chmod +x compare-router-doc.sh
./compare-router-doc.sh example/demo-app/路由文档.md.before example/demo-app/路由文档.md
```

---

### 步骤 4：分析差异（如发现）

如果发现不一致，详细分析差异：

#### 4.1 统计差异

```bash
# 统计路由数量
echo "迁移前路由数量："
grep -E "^okrouter://|^https?://" example/demo-app/路由文档.md.before | wc -l

echo "迁移后路由数量："
grep -E "^okrouter://|^https?://" example/demo-app/路由文档.md | wc -l

# 统计拦截器数量
echo "迁移前拦截器数量："
grep "^com.anymore" example/demo-app/路由文档.md.before | tail -n +3 | wc -l

echo "迁移后拦截器数量："
grep "^com.anymore" example/demo-app/路由文档.md | tail -n +3kt | wc -l
```

#### 4.2 检查缺失的路由

```bash
# 提取路由列表
grep "^okrouter://" example/demo-app/路由文档.md.before | cut -d'|' -f1 > /tmp/before_routes_list.txt
grep "^okrouter://" example/demo-app/路由文档.md | cut -d'|' -f1 > /tmp/after_routes_list.txt

# 查找缺失的路由
echo "缺失的路由："
comm -23 /tmp/before_routes_list.txt /tmp/after_routes_list.txt

# 查找新增的路由
echo "新增的路由："
comm -13 /tmp/before_routes_list.txt /tmp/after_routes_list.txt
```

#### 4.3 检查类名变化

```bash
# 提取类名列表
grep "^okrouter://" example/demo-app/路由文档.md.before | cut -d'|' -f2 > /tmp/before_classes.txt
grep "^okrouter://" example/demo-app/路由文档.md | cut -d'|' -f2 > /tmp/after_classes.txt

# 对比类名
diff /tmp/before_classes.txt /tmp/after_classes.txt
```

---

## 常见问题和解决方案

### 问题 1：部分路由缺失

**症状**：
```
缺少的路由：
okrouter://android/biz1
okrouter://android/fragment1
```

**可能原因**：
1. classpath 没有包含所有模块的 class 文件
2. 字节码扫描只扫描了特定目录
3. 增量编译导致部分文件被跳过

**解决方案**：
- 检查任务依赖链，确保所有模块编译完成后再扫描
- 验证 classpath 包含所有模块的编译输出目录

### 问题 2：拦截器顺序错误

**症状**：
```
迁移前：com.anymore.okrouter.demo.login.LoginInterceptor|LoginCheckInterceptor
迁移后：com.anymore.okrouter.demo.login.GlobalLoginInterceptor|LoginCheckInterceptor
```

**可能原因**：
- 拦截器扫描逻辑中的排序规则有问题
- 优先级处理逻辑变更

**解决方案**：
- 检查 AbsOkRouterAction 中的排序逻辑
- 验证 priority 字段的正确处理

### 问题 3：所有内容完全一致但创建时间错误

**症状**：
```
diff 显示内容完全相同，但创建时间格式不同
```

**可能原因**：
- 这不是问题！只要时间格式正确即可

**解决方案**：
- 忽略创建时间的差异
- 继续后续验证

### 问题 4：部分路由的 interceptors 列表不同

**症状**：
```
迁移前：okrouter://android/biz2|...|ACTIVITY|com.anymore.okrouter.demo.login.LoginInterceptor...
迁移后：okrouter://android/biz2|...|ACTIVITY|
```

**可能原因**：
- 拦截器类名解析逻辑有问题
- 别名处理逻辑有问题

**解决方案**：
- 检查 RouterElement 中的 interceptors 处理
- 验证 InterceptorAlias 的查找逻辑

---

## 验证检查清单

### 必须验证的项目

- [ ] ✅ 备份了迁移前的路由文档
- [ ] ✅ 完成了 AGP 8.13.0 升级
- [ ] ✅ 成功构建了项目
- [ ] ✅ 生成了新的路由文档
- [ ] ✅ 对比了两个文档的内容
- [ ] ✅ 验证了固定路由数量一致
- [ ] ✅ 验证了正则路由一致
- [ ] ✅ 验证了拦截器一致
- [ ] ✅ 验证了拦截器顺序一致
- [ ] ✅ 只有创建时间不同

### 如果验证通过

- [x] 继续进行运行时功能测试
- [x] 运行示例应用
- [x] 测试路由跳转功能

### 如果验证失败

- [x] ⚠️ **停止实施**
- [x] 记录详细的差异信息
- [x] 分析字节码扫描逻辑
- [x] 检查任务依赖链配置
- [x] 修复问题后重新验证

---

## 快速参考

### 当前路由文档示例（迁移前）

```markdown
# OkRouter-Doc
创建时间:2026-04-17 22:49:31
## 1.路由
### 1.1 固定路由
|uri|class|type|interceptors|desc|
|----|----|----|----|----|
|okrouter://android/biz1|com.anymore.okrouter.demo.biz1.Biz1Activity|ACTIVITY||Biz1主页面|
|okrouter://android/biz1_view_page|com.anymore.okrouter.demo.biz1.Biz1ViewPageActivity|ACTIVITY||Biz1 View Page|
|okrouter://android/fragment1|com.anymore.okrouter.demo.biz1.Fragment1|FRAGMENT||Fragment1|
|okrouter://android/fragment2|com.anymore.okrouter.demo.biz1.Fragment2|FRAGMENT||Fragment2|
|okrouter://android/biz2|com.anymore.okrouter.demo.biz2.Biz2Activity|ACTIVITY|com.anymore.okrouter.demo.login.LoginInterceptor->com.anymore.okrouter.demo.biz2.Biz2Interceptor|Biz2主页面|
|okrouter://android/biz2/biz2Service|com.anymore.okrouter.demo.biz2.Biz2Service|SERVICE|||
|okrouter://android/custom_text_view|com.anymore.okrouter.demo.biz2.widget.CustomTextView|VIEW||自定义View|
|okrouter://android/web|com.anymore.okrouter.demo.web.WebActivity|ACTIVITY||通用web容器|
|okrouter://android/login|com.anymore.okrouter.demo.login.LoginActivity|ACTIVITY||登录页面|
|okrouter://android/main|com.anymore.okrouter.demo.app.MainActivity|{ACTIVITY||应用主界面|
|okrouter://android/404|com.anymore.okrouter.demo.app.RouterNotFoundActivity|ACTIVITY||没有找到匹配路由的落地页面|
|okrouter://android/fragment3|com.anymore.okrouter.demo.app.Fragment3|FRAGMENT||Fragment3|
### 1.2 正则路由
|uri|class|type|interceptors|desc|
|----|----|----|----|----|
|https?://.*.*|com.anymore.okrouter.demo.web.HttpSchemeHandler|HANDLER|||
## 2.拦截器
|class|alias|priority|global|singleton|desc|
|----|----|----|----|----|----|
|com.anymore.okrouter.demo.biz2.Biz2Interceptor||0|false|false|Biz2Interceptor 测试|
|com.anymore.okrouter.demo.login.GlobalLoginInterceptor||0|true|true|全局登录拦截器，通过'extra_check_login'来设置是否检查登录状态|
|com.anymore.okrouter.demo.login.LoginInterceptor|LoginCheckInterceptor|-2147483648|false|true|局部登录拦截器，通过注解设置到路由目标上面|
```

### 期望的迁移后文档

```markdown
# OkRouter-Doc
创建时间:2026-04-17 23:XX:XX  ⬅️ 只有时间不同
## 1.路由
### 1.1 固定路由
|uri|class|type|interceptors|desc|
|----|----|----|----|----|
|okrouter://android/biz1|com.anymore.okrouter.demo.biz1.Biz1Activity|ACTIVITY||Biz1主页面|
|okrouter://android/biz1_view_page|com.anymore.okrouter.demo.biz1.Biz1ViewPageActivity|ACTIVITY||Biz1 View Page|
|okrouter://android/fragment1|com.anymore.okrouter.demo.biz1.Fragment1|FRAGMENT||Fragment1|
|okrouter://android/fragment2|com.anymore.okrouter.demo.biz1.Fragment2|FRAGMENT||Fragment2|
|okrouter://android/biz2|com.anymore.okrouter.demo.biz2.Biz2Activity|ACTIVITY|com.anymore.okrouter.demo.login.LoginInterceptor->com.anymore.okrouter.demo.biz2.Biz2Interceptor|Biz2主页面|
|okrouter://android/biz2/biz2Service|com.anymore.okrouter.demo.biz2.Biz2Service|SERVICE|||
|okrouter://android/custom_text_view|com.anymore.okrouter.demo.biz2.widget.CustomTextView|VIEW||自定义View|
|okrouter://android/web|com.anymore.okrouter.demo.web.WebActivity|ACTIVITY||通用web容器|
|okrouter://android/login|com.anymore.okrouter.demo.login.LoginActivity|ACTIVITY||登录页面|
|okrouter://android/main|com.anymore.okrouter.demo.app.MainActivity|ACTIVITY||应用主界面|
|okrouter://android/404|com.anymore.okrouter.demo.app.RouterNotFoundActivity|ACTIVITY||没有找到匹配路由的落地页面|
|okrouter://android/fragment3|com.anymore.okrouter.demo.app.Fragment3|FRAGMENT||Fragment3|
### 1.2 正则路由
|uri|class|type|interceptors|desc|
|----|----|----|----|----|
|https?://.*.*|com.anymore.okrouter.demo.web.HttpSchemeHandler|HANDLER|||
## 2.拦截器
|class|alias|priority|global|singleton|desc|
|----|----|----|----|----|----|
|com.anymore.okrouter.demo.biz2.Biz2Interceptor||0|false|false|Biz2Interceptor 测试|
|com.anymore.okrouter.demo.login.GlobalLoginInterceptor||0|true|true|全局登录拦截器，通过'extra_check_login'来设置是否检查登录状态|
|com.anymore.okrouter.demo.login.LoginInterceptor|LoginCheckInterceptor|-2147483648|false|true|局部登录拦截器，通过注解设置到路由目标上面|
```

---

**验证指南版本**：1.0
**创建日期**：2026-04-17
**重要性**：⭐⭐⭐⭐⭐ 最高优先级验证
