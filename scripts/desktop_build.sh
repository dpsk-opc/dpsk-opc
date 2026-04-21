#!/bin/bash
#===============================================================================
# 前端桌面应用打包脚本
# 功能：构建前端 + 打包成 Electron exe
#===============================================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CAT_DIR="$PROJECT_ROOT/cat"
ELECTRON_DIR="$CAT_DIR/electron"

#===============================================================================
# 步骤 1: 检查依赖
#===============================================================================
log_info "Step 1: Checking dependencies..."

# 检查 cat 目录
if [ ! -d "$CAT_DIR" ]; then
    log_error "cat directory not found at: $CAT_DIR"
    exit 1
fi

# 检查 electron 目录
if [ ! -d "$ELECTRON_DIR" ]; then
    log_error "electron directory not found at: $ELECTRON_DIR"
    exit 1
fi

# 检查后端打包产物
BACKEND_EXE="$ELECTRON_DIR/backend/backend.exe"
if [ ! -f "$BACKEND_EXE" ]; then
    log_warn "Backend executable not found at: $BACKEND_EXE"
    log_warn "Please run backend_build.sh first"
fi

# 检查 node_modules
if [ ! -d "$ELECTRON_DIR/node_modules" ]; then
    log_info "Installing Electron dependencies..."
    cd "$ELECTRON_DIR"
    npm install
fi

#===============================================================================
# 步骤 2: 构建前端
#===============================================================================
log_info "Step 2: Building frontend..."

cd "$CAT_DIR"

# 使用 .env.desktop 环境变量构建
export NODE_ENV=production
pnpm build

if [ $? -ne 0 ]; then
    log_error "Frontend build failed"
    exit 1
fi

log_info "Frontend built successfully"

#===============================================================================
# 步骤 3: 打包 Electron
#===============================================================================
log_info "Step 3: Packaging Electron app..."

cd "$ELECTRON_DIR"

# 执行 electron-builder
npm run build

if [ $? -ne 0 ]; then
    log_error "Electron packaging failed"
    exit 1
fi

#===============================================================================
# 完成
#===============================================================================
log_info "=============================================="
log_info "Desktop build completed successfully!"
log_info "=============================================="
log_info "Output: $ELECTRON_DIR/dist/"
log_info "=============================================="

# 列出构建产物
if [ -d "$ELECTRON_DIR/dist" ]; then
    ls -la "$ELECTRON_DIR/dist/" | grep -E "\.exe$|\.zip$|\.dmg$"
fi
