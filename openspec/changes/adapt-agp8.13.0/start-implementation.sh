#!/bin/bash

# AGP 8.13.0 适配开始脚本
# 用途：快速检查准备状态并开始实施

echo "==================================================="
echo "AGP 8.13.0 适配 - 实施前检查"
echo "==================================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查函数
check_status() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ $1${NC}"
        return 0
    else
        echo -e "${RED}✗ $1${NC}"
        return 1
    fi
}

warn() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

info() {
    echo -e "${GREEN}ℹ️ $1${NC}"
}

echo "📋 1. 检查当前分支"
CURRENT_BRANCH=$(git branch --show-current 2>/dev/null)
if [ "$CURRENT_BRANCH" = "feature/agp8" ]; then
    check_status "当前在 feature/agp8 分支"
else
    warn "当前不在 feature/agp8 分支，当前分支：$CURRENT_BRANCH"
    echo "建议切换到 feature/agp8 分支"
    read -p "是否继续？(y/N) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消，请切换到正确的分支后重试"
        exit 1
    fi
fi

echo ""
echo "📋 2. 检查 Git 工作区状态"
GIT_STATUS=$(git status --porcelain 2>/dev/null)
if [ -z "$GIT_STATUS" ]; then
    check_status "Git 工作区干净"
else
    warn "Git 工作区有未提交的更改："
    echo "$GIT_STATUS"
    read -p "是否继续？(y/N) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消，请提交或暂存更改后重试"
        exit 1
    fi
fi

echo ""
echo "📋 3. 检查 Gradle 版本"
GRADLE_VERSION=$(./gradlew --version 2>/dev/null | grep "^Gradle" | awk '{print $2}')
if [ "$GRADLE_VERSION" = "7.5" ]; then
    check_status "Gradle 版本正确：$GRADLE_VERSION"
else
    warn "Gradle 版本不是 7.5，当前：$GRADLE_VERSION"
    echo "请确认项目当前版本是否正确"
fi

echo ""
echo "📋 4. 检查路由文档"
ROUTER_DOC="example/demo-app/路由文档.md"
if [ -f "$ROUTER_DOC" ]; then
    check_status "路由文档存在：$ROUT_DOC_DOC"

    # 统计信息
    ROUTER_COUNT=$(grep -E '^okrouter://|^https?://' "$ROUTER_DOC" | wc -l | awk '{print $1}')
    INTERCEPTOR_COUNT=$(grep '^com.anymore' "$ROUTER_DOC" | tail -n +3 | wc -l | awk '{print $1}')

    echo "   - 固定路由：${ROUTER_COUNT} 个"
    echo "   - 拦截器：${{INTERCEPTOR_COUNT}} 个"
else
    warn "路由文档不存在：$ROUTER_DOC"
    echo "请确保项目可以正常构建"
fi

echo ""
echo "📋 5. 检查 OpenSpec 文档"
PROPOSAL_DIR="openspec/changes/adapt-agp8.13.0"
if [ -d "$PROPOSAL_DIR" ]; then
    check_status "变更提案目录存在"

    echo ""
    echo "   📄 变更提案文档："
    find "$PROPOSAL_DIR" -name "*.md" -type f | while read file; do
        filename=$(basename "$file")
        size=$(du -h "$file" | awk '{print $1}')
        echo "      - $filename ($size)"
    done
else
    warn "变更提案目录不存在：$PROPOSAL_DIR"
fi

echo ""
echo "📋 6. 备份路由文档"
BACKUP_DOC="example/demo-app/路由文档.md.before"
if [ ! -f "$BACKUP_DOC" ]; then
    echo "备份路由文档..."
    cp "$ROUTER_DOC" "$BACKUP_DOC"
    if [ $? -eq 0 ]; then
        check_status "路由文档已备份到：$BACKUP_DOC"
    else
        warn "备份失败"
    fi
else
    info "路由文档备份已存在：$BACKUP_DOC"
    read -p "是否覆盖备份？(y/N) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        cp "$ROUTER_DOC" "$BACKUP_DOC"
        check_status "路由文档已更新备份"
    fi
fi

echo ""
echo "📋 7. 创建备份目录"
BACKUP_DIR=".backup/agp8-migration-$(date +%Y%m%d)"
if [ ! -d "$BACKUP_DIR" ]; then
    mkdir -p "$BACKUP_DIR"
    check_status "备份目录已创建：$BACKUP_DIR"
else
    info "备份目录已存在：$BACKUP_DIR"
fi

echo ""
echo "📋 8. 备份关键配置文件"
echo "备份配置文件到：$BACKUP_DIR"
cp build.gradle "$BACKUP_DIR/" 2>/dev/null
check_status "build.gradle"

cp buildSrc/build.gradle "$BACKUP_DIR/" 2>/dev/null
check_status "buildSrc/build.gradle"

cp gradle/wrapper/gradle-wrapper.properties "$BACKUP_DIR/" 2>/dev/null
check_status "gradle-wrapper.properties"

echo ""
echo "==================================================="
echo "检查完成！"
echo "==================================================="
echo ""
echo "📝 下一步操作："
echo ""
echo "1. 查看 OpenSpec 变更提案："
echo "   cat openspec/changes/adapt-agp8.13.0/proposal.md"
echo ""
echo "2. 查看任务清单："
echo "   cat openspec/changes/adapt-agp8.13.0/tasks.md"
echo ""
echo "3. 查看验证指南："
echo "   cat openspec/changes/adapt-agp8.13.0/validation-guide.md"
echo ""
echo "4. 开始实施（选择一种方式）："
echo "   方式 A：使用 /opsx:apply 命令"
echo "   方式 B：手动执行任务清单"
echo ""
echo "5. 快速回退（如需要）："
echo "   git checkout HEAD~1 -- build.gradle buildSrc/build.gradle gradle/wrapper/gradle-wrapper.properties"
echo "   ./gradlew clean build"
echo ""
