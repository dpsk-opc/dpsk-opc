#!/bin/bash
#===============================================================================
#  HeyYoo! 桌面应用打包脚本
#  流程：构建后端 Java → 构建前端 → 打包 Electron → 输出安装器
#
#  使用方式:
#    bash build.sh          # 默认构建
#    bash build.sh --clean  # 清理后重新构建
#
#  要求:
#    - JDK 14+ (带 jpackage)
#    - Maven
#    - Node.js 18+ + pnpm
#===============================================================================

set -e

#===============================================================================
# 颜色与日志
#===============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "\n${GREEN}════════════════════════════════════════${NC}"; echo -e "${GREEN}  $1${NC}"; echo -e "${GREEN}════════════════════════════════════════${NC}\n"; }

#===============================================================================
# 路径
#===============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
BACKEND_DIR="$ROOT_DIR/opc"
# FRONTEND_DIR="$ROOT_DIR/cat"
FRONTEND_DIR="$ROOT_DIR/../opc-cat"

# 后端构建产物
BACKEND_OUTPUT="$BACKEND_DIR/output/opc"

# 前端后端目录（Electron extraResources 需要的路径）
FRONTEND_BACKEND_DIR="$FRONTEND_DIR/backend/dist"

# 最终产物目录
FINAL_OUTPUT="$FRONTEND_DIR/electron-dist"

#===============================================================================
# 参数解析
#===============================================================================
DO_CLEAN=false
while [[ $# -gt 0 ]]; do
  case $1 in
    --clean) DO_CLEAN=true; shift ;;
    *) log_error "未知参数: $1"; exit 1 ;;
  esac
done

#===============================================================================
# 环境检查
#===============================================================================
check_prerequisites() {
  log_step "Step 0: 环境检查"

  # 检查必需命令
  local missing=()
  for cmd in mvn jpackage node pnpm; do
    if ! command -v $cmd &>/dev/null; then
      missing+=("$cmd")
    fi
  done

  if [ ${#missing[@]} -gt 0 ]; then
    log_error "缺少以下工具: ${missing[*]}"
    exit 1
  fi

  # 检查目录
  if [ ! -d "$BACKEND_DIR" ]; then
    log_error "后端目录不存在: $BACKEND_DIR"
    exit 1
  fi
  if [ ! -f "$BACKEND_DIR/build.sh" ]; then
    log_error "后端构建脚本不存在: $BACKEND_DIR/build.sh"
    exit 1
  fi
  if [ ! -d "$FRONTEND_DIR" ]; then
    log_error "前端目录不存在: $FRONTEND_DIR"
    exit 1
  fi
  if [ ! -f "$FRONTEND_DIR/package.json" ]; then
    log_error "前端 package.json 不存在: $FRONTEND_DIR/package.json"
    exit 1
  fi

  log_info "环境检查通过"
}

#===============================================================================
# 清理
#===============================================================================
do_clean() {
  log_info "清理构建产物..."

  # 清理后端产物
  if [ -d "$BACKEND_OUTPUT" ]; then
    log_info "清理: $BACKEND_OUTPUT"
    rm -rf "$BACKEND_OUTPUT"
  fi

  # 清理前端后端目录
  if [ -d "$FRONTEND_BACKEND_DIR" ]; then
    log_info "清理: $FRONTEND_BACKEND_DIR"
    rm -rf "$FRONTEND_BACKEND_DIR"
  fi

  # 清理前端构建产物
  if [ -d "$FRONTEND_DIR/dist" ]; then
    log_info "清理: $FRONTEND_DIR/dist"
    rm -rf "$FRONTEND_DIR/dist"
  fi

  # 清理 Electron 打包产物
  if [ -d "$FINAL_OUTPUT" ]; then
    log_info "清理: $FINAL_OUTPUT"
    rm -rf "$FINAL_OUTPUT"
  fi

  # 清理前端 node_modules/.cache
  if [ -d "$FRONTEND_DIR/node_modules/.cache" ]; then
    rm -rf "$FRONTEND_DIR/node_modules/.cache"
  fi

  # 清理后端 Maven target
  if [ -d "$BACKEND_DIR/opc-im/target" ]; then
    log_info "清理: opc-im/target"
    rm -rf "$BACKEND_DIR/opc-im/target"
  fi
  if [ -d "$BACKEND_DIR/opc-core/target" ]; then
    log_info "清理: opc-core/target"
    rm -rf "$BACKEND_DIR/opc-core/target"
  fi

  log_info "清理完成"
}

#===============================================================================
# Step 1: 构建后端
#===============================================================================
build_backend() {
  log_step "Step 1: 构建后端 (Java)"

  cd "$BACKEND_DIR"

  log_info "执行: bash build.sh"
  bash build.sh

  if [ ! -d "$BACKEND_OUTPUT" ]; then
    log_error "后端构建失败，产物不存在: $BACKEND_OUTPUT"
    exit 1
  fi

  log_info "后端构建完成: $BACKEND_OUTPUT"
}

#===============================================================================
# Step 2: 复制后端到前端
#===============================================================================
copy_backend() {
  log_step "Step 2: 复制后端产物到前端目录"

  # 确保目标目录存在
  mkdir -p "$FRONTEND_BACKEND_DIR"

  # 清空旧文件
  rm -rf "$FRONTEND_BACKEND_DIR"/*

  log_info "复制: $BACKEND_OUTPUT → $FRONTEND_BACKEND_DIR"
  cp -r "$BACKEND_OUTPUT"/* "$FRONTEND_BACKEND_DIR/"

  log_info "复制完成"
  log_info "后端文件列表:"
  ls -la "$FRONTEND_BACKEND_DIR/" | head -20
}

#===============================================================================
# Step 3: 构建前端 + 打包 Electron
#===============================================================================
build_frontend() {
  log_step "Step 3: 构建前端 + 打包 Electron"

  cd "$FRONTEND_DIR"

  # 安装依赖（如有需要）
  if [ ! -d "node_modules" ]; then
    log_info "安装前端依赖..."
    pnpm install
  fi

  # 编译 Electron TypeScript
  log_info "编译 Electron 主进程..."
  pnpm run electron:tsc

  # 构建前端页面
  log_info "构建前端页面..."
  pnpm run build

  # 打包 Electron（使用 electron-builder）
  log_info "打包 Electron 应用 (electron-builder --win)..."
  npx electron-builder --win

  log_info "前端打包完成"
}

#===============================================================================
# Step 4: 校验产物
#===============================================================================
verify_output() {
  log_step "Step 4: 校验产物"

  if [ -d "$FINAL_OUTPUT" ]; then
    log_info "=============================================="
    log_info "  打包成功！"
    log_info "=============================================="
    log_info "产物目录: $FINAL_OUTPUT"
    log_info ""
    log_info "文件列表:"
    ls -lh "$FINAL_OUTPUT/" | grep -E "\.exe$|\.zip$|\.dmg$|\.AppImage$|\.deb$" || ls -lh "$FINAL_OUTPUT/"
    log_info "=============================================="
  else
    log_error "打包产物未找到: $FINAL_OUTPUT"
    exit 1
  fi
}

#===============================================================================
# 主流程
#===============================================================================
main() {
  echo ""
  echo "  ╔══════════════════════════════════╗"
  echo "  ║     HeyYoo! Desktop Builder     ║"
  echo "  ╚══════════════════════════════════╝"
  echo ""

  local start_time=$(date +%s)

  # 环境检查
  check_prerequisites

  # 清理
  if [ "$DO_CLEAN" = true ]; then
    do_clean
  fi

  # 构建后端
  build_backend

  # 复制后端产物
  copy_backend

  # 构建前端 + Electron 打包
  build_frontend

  # 校验产物
  verify_output

  local end_time=$(date +%s)
  local duration=$((end_time - start_time))
  log_info "总耗时: ${duration} 秒"
}

main "$@"
