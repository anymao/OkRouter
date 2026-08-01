# 研究发现

## 项目当前状态

### 已完成的 AGP 7.x 适配
- 提交记录：e2f5084 feat: 完成 AGP 7.4.0 适配配置更新
- AGP 版本：7.4.0
- Kotlin 版本：1.8.0
- JDK 版本：17
- Maven 插件：已从 'maven' 迁移到 'maven-publish'

### 项目结构
- okrouter/ - 核心路由库
- okrouter-plugin/ - Gradle Transform 插件源码
- okrouter-stub/ - 存根库
- buildSrc/ - 将 okrouter-plugin 编译为 Gradle 插件
- example/ - 示例应用

---

## AGP 8.x 关键变更点

### Transform API 弃用
- AGP 8.x 正式弃用了 Transform API
- 替代方案：AsmClassVisitorFactory
- 影响：需要重构插件实现

### Gradle 版本要求
- AGP 8.x 需要 Gradle 8.0+

### namespace 强制
- 所有模块必须显式定义 namespace

### JDK 要求
- AGP 8.x 需要 JDK 17（已满足）

---

## auto-service-android AGP 8 适配分析（重要发现！）

### 版本配置
- **AGP 版本**：8.13.0
- **Gradle 版本**：8.13
- **Kotlin 版本**：2.1.0
- **适配方式**：任务依赖管理（非 AsmClassVisitorFactory）

### 核心发现
**不需要使用 AsmClassVisitorFactory！** auto-service-android 采用了任务依赖管理的方式。

### 架构设计
1. **插件入口**：`AutoServiceRegisterPlugin`
2. **变体遍历**：`android.applicationVariants.forEach { variant -> ... }`
3. **任务链设计**：
   - `registerTask`：扫描注解，生成 Java 代码
   - `compileTask`：编译生成的代码
   - 依赖管理：`compileTask.dependsOn(javaCompile)`, `compileTask.mustRunAfter(registerTask)`

### 代码路径
- 编译输出：`intermediates/javac/${variant.name}/compile${variant.name.capitalize()}JavaWithJavac/classes`
- 生成代码：`intermediates/auto_service/${variant.dirName}/src`

### 对 OkRouter 的建议
✅ **采用相同的任务依赖方式**，而非迁移到 AsmClassVisitorFactory

---

## 待研究事项

- [x] AsmClassVisitorFactory 的具体使用方式 → 发现更好的替代方案：任务依赖管理
- [x] Transform API → AsmClassVisitorFactory 迁移路径 → 改为：Transform → 任务依赖方式
- [ ] 设计 OkRouter基于任务依赖的插件架构
- [ ] 实现任务依赖链管理
- [ ] 测试增量编译支持

---

## ⭐ 关键验证点

### 路由文档完整性验证

**重要发现**：路由文档是验证字节码扫描逻辑正确性的关键指标！

#### 验证原理

```
路由文档生成过程：
1. 编译应用代码 → 生成 .class 文件
2. OkRouter 插件扫描字节码 → 解析 @Router 和 @Interceptor 注解
3. 生成路由表和路由文档.md

文档内容一致性 = 扫描逻辑正确性
```

#### 验证方法

```bash
# 1. 备份迁移前的文档
cp example/demo-app/路由文档.md example/demo-app/路由文档.md.before

# 2. 执行 AGP 8.13.0 升级和构建
./gradlew clean build

# 3. 对比两个文档（忽略创建时间）
diff -u example/demo-app/路由文档.md.before example/demo-app/路由文档.md
```

#### 期望结果

- ✅ 只有创建时间不同
- ✅ 所有路由信息完全一致（uri, class, type, interceptors, desc）
- ✅ 所有拦截器信息完全一致（class, alias, priority, global, singleton）
- ❌ **如果发现其他差异，说明字节码扫描逻辑有问题**

#### 当前路由文档示例

- **固定路由数**：10 个
- **正则路由数**：1 个
- **拦截器数**：3 个
- **关键路由示例**：
  - `okrouter://android/biz1` → Biz1Activity
  - `okrouter://android/main` → MainActivity
  - `okrouter://android/login` → LoginActivity

#### 相关文件

- ✅ `openspec/changes/adapt-agp8.13.0/validation-guide.md` - 详细验证指南
- ✅ `openspec/changes/adapt-agp8.13.0/tasks.md` - 包含 Task 3.5.1 验证步骤
- ✅ `example/demo-app/路由文档.md` - 当前生成的文档

**优先级**：⭐⭐⭐⭐⭐ 最高
