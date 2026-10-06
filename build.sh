#!/bin/bash
#===============================================================================
#  Yoo! 桌面应用打包脚本（聚合仓唯一构建入口）
#
#  流程：读取版本 → 构建后端 Java（mvn + jpackage）→ 拷贝后端到前端
#        → 构建前端（pnpm + electron-builder）→ 输出到 build/
#
#  使用方式:
#    bash build.sh                       # 默认构建（平台自动推断），版本取自 ./VERSION
#    bash build.sh --clean               # 清理后重新构建
#    bash build.sh --version 1.2.3       # 指定版本（覆盖 VERSION 文件）
#    bash build.sh --platform windows    # 指定平台：windows | linux | macos
#
#  要求:
#    - JDK 17+（带 jpackage）
#    - Maven
#    - Node.js >= 22.13 + pnpm 11（pnpm 11 依赖内置模块 node:sqlite，
#      Node 20/21 不存在该模块，启动即报 ERR_UNKNOWN_BUILTIN_MODULE）
#    - 跨平台提示：macOS 必须在 macOS 机器/runner 上构建（jpackage 不支持交叉编译）
#
#  目录假设（聚合仓结构，子模块为单层）:
#    <repo-root>/build.sh    本脚本
#    <repo-root>/VERSION     版本号唯一来源
#    <repo-root>/opc/        后端子模块（根即 Maven 工程）
#    <repo-root>/cat/        前端子模块（Electron + pnpm）
#    <repo-root>/build/      产物输出（backend / installer）
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
# 路径（全部相对聚合仓根，单层子模块）
#===============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
FRONTEND_DIR="$ROOT_DIR/cat"         # 前端子模块
BUILD_DIR="$ROOT_DIR/build"          # 产物统一输出
VERSION_FILE="$ROOT_DIR/VERSION"

# 后端 Maven 工程根：
#   - 拆分后（目标结构）：$ROOT_DIR/opc/pom.xml
#   - 拆分前（过渡兼容）：$ROOT_DIR/opc/opc/pom.xml
if [ -f "$ROOT_DIR/opc/pom.xml" ]; then
  BACKEND_DIR="$ROOT_DIR/opc"
elif [ -f "$ROOT_DIR/opc/opc/pom.xml" ]; then
  BACKEND_DIR="$ROOT_DIR/opc/opc"
  log_warn "检测到双层 opc 目录（拆分前结构），建议按文档 §3.2 完成拆分"
else
  BACKEND_DIR="$ROOT_DIR/opc"
fi

BACKEND_OUTPUT="$BUILD_DIR/backend"  # 后端 jpackage 产物
INSTALLER_OUTPUT="$BUILD_DIR/installer"  # 前端 electron-builder 产物

# 前端 electron-builder 的 extraResources 相对前端仓库根解析：
#   from: "backend/dist" -> to: "backend"
FRONTEND_BACKEND_DIR="$FRONTEND_DIR/backend/dist"

#===============================================================================
# 后端 jpackage 配置（搬迁自原 opc/build.sh）
#===============================================================================
# 后端启动器名（jpackage --name）：
#   ⚠️ 必须与前端 electron/main.ts 的 BACKEND_EXE_NAME 一致，且不能与前端 Electron
#   主程序同名（前端是 Yoo.exe，来自 productName）。若后端也叫 Yoo.exe，
#   electron 主进程 stopBackend / cleanupStaleBackend 里的
#   `taskkill /im Yoo.exe /f` 会误杀前端自身，应用表现为"启动后只剩前端/异常退出"。
APP_NAME="opc"
JPACKAGE_TYPE="app-image"            # app-image（文件夹含 exe）；Electron 内嵌用
VENDOR="XiaoMiZhou"
COPYRIGHT="Copyright © 2026"
BACKEND_MAIN_JAR="opc-im.jar"
BACKEND_JAR_DIR="opc-im/target"
COPY_CONFIG_EXAMPLE=true
CONFIG_FILE_NAME="application-uat.properties"

# 强制 UTF-8：中文 Windows 下 JVM 默认 GBK，会导致日志与控制台乱码
JAVA_OPTIONS="-Xmx512m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

#===============================================================================
# 目标平台配置
#   PLATFORM 取值：windows | linux | macos
#   默认按当前系统自动推断（本地开发用），CI 中显式传入
#===============================================================================
detect_platform() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*|Windows*) echo "windows" ;;
    Darwin*)                        echo "macos"   ;;
    Linux*)                         echo "linux"   ;;
    *)                              echo "unknown" ;;
  esac
}

# electron-builder 的平台与 target 参数
#   显式指定 target，避免依赖前端 package.json 是否已配置 linux/mac 段。
#   前端若已配置对应段，其 icon/category 等仍会生效（命令行只覆盖 target）。
eb_platform_arg() {
  case "$1" in
    windows) echo "--win" ;;
    linux)   echo "--linux AppImage deb" ;;
    macos)   echo "--mac dmg zip" ;;
    *)       echo "" ;;
  esac
}

# jpackage 平台专属参数
#   注意：--linux-shortcut 仅对 deb/rpm 安装包有效，app-image 不支持，故不传
jpackage_platform_args() {
  case "$1" in
    windows) echo "--win-console" ;;                # 保留控制台便于看日志
    linux)   echo "" ;;
    macos)   echo "" ;;
    *)       echo "" ;;
  esac
}

# jpackage app-image 的产物目录名
#   - Windows/Linux：<APP_NAME>
#   - macOS：<APP_NAME>.app
jpackage_output_dir() {
  case "$1" in
    macos) echo "$APP_NAME.app" ;;
    *)     echo "$APP_NAME" ;;
  esac
}

#===============================================================================
# 参数解析
#===============================================================================
DO_CLEAN=false
CLI_VERSION=""
PLATFORM=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --clean)    DO_CLEAN=true; shift ;;
    --version)  CLI_VERSION="$2"; shift 2 ;;
    --platform) PLATFORM="$2"; shift 2 ;;
    *) log_error "未知参数: $1"; exit 1 ;;
  esac
done

# 平台推断与校验
if [ -z "$PLATFORM" ]; then
  PLATFORM="${OPC_PLATFORM:-$(detect_platform)}"
fi
case "$PLATFORM" in
  windows|linux|macos) ;;
  *) log_error "不支持或无法识别的平台: '$PLATFORM'（可选 windows|linux|macos）"; exit 1 ;;
esac

#===============================================================================
# 版本号解析与校验（唯一来源：聚合仓根 VERSION）
#===============================================================================
resolve_version() {
  local v
  if [ -n "$CLI_VERSION" ]; then
    v="$CLI_VERSION"
  elif [ -n "$OPC_VERSION" ]; then          # CI 可通过环境变量注入
    v="$OPC_VERSION"
  elif [ -f "$VERSION_FILE" ]; then
    v="$(tr -d '[:space:]' < "$VERSION_FILE")"
  else
    log_error "未找到版本号：请提供 --version，或创建 $VERSION_FILE"
    exit 1
  fi

  if ! [[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    log_error "版本号格式非法: '$v'（要求三段数字，如 1.0.0）"
    exit 1
  fi
  echo "$v"
}

#===============================================================================
# Node 版本校验
#   pnpm 11 启动时会 require 内置模块 node:sqlite，该模块自 Node 22.13 起默认可用。
#   Node 20/21 上 pnpm 直接抛 ERR_UNKNOWN_BUILTIN_MODULE，故在此提前拦截。
#===============================================================================
REQUIRED_NODE_MAJOR=22
REQUIRED_NODE_MINOR=13

check_node_version() {
  local cur major minor
  cur="$(node -v | sed 's/^v//')"
  major="${cur%%.*}"
  minor="$(echo "$cur" | cut -d. -f2)"
  if [ -z "$minor" ]; then minor=0; fi

  if [ "$major" -lt "$REQUIRED_NODE_MAJOR" ] \
     || { [ "$major" -eq "$REQUIRED_NODE_MAJOR" ] && [ "$minor" -lt "$REQUIRED_NODE_MINOR" ]; }; then
    log_error "Node.js 版本过低: v$cur（需 >= v$REQUIRED_NODE_MAJOR.$REQUIRED_NODE_MINOR，pnpm 11 依赖 node:sqlite）"
    exit 1
  fi
}

#===============================================================================
# pnpm 版本校验（与前端 cat/pnpm-workspace.yaml 联动）
#   前端的 pnpm-workspace.yaml 是 pnpm 11 配置（allowBuilds 字段，pnpm 10 名为
#   onlyBuiltDependencies）。若 pnpm < 11，该字段不生效，Electron / esbuild 的
#   postinstall 会被默认策略拦截（ERR_PNPM_IGNORED_BUILDS），导致 Electron 运行时
#   二进制缺失 —— 打包能过、装出来的应用起不来。故在此强制 pnpm >= 11。
#===============================================================================
REQUIRED_PNPM_MAJOR=11

check_pnpm_version() {
  local cur major
  cur="$(pnpm -v 2>/dev/null | head -1 | sed 's/^v//' || true)"
  major="${cur%%.*}"
  if [ -z "$major" ] || ! [[ "$major" =~ ^[0-9]+$ ]] || [ "$major" -lt "$REQUIRED_PNPM_MAJOR" ]; then
    log_error "pnpm 不可用或版本过低: ${cur:-未知}（需 >= ${REQUIRED_PNPM_MAJOR}.x，以兼容 cat/pnpm-workspace.yaml 的 allowBuilds）"
    exit 1
  fi
}

#===============================================================================
# 环境检查
#===============================================================================
check_prerequisites() {
  log_step "Step 0: 环境检查"

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

  if [ ! -f "$BACKEND_DIR/pom.xml" ]; then
    log_error "后端 Maven 工程未找到: $BACKEND_DIR/pom.xml"
    log_error "请确认已执行: git submodule update --init --recursive"
    exit 1
  fi
  if [ ! -d "$FRONTEND_DIR" ]; then
    log_error "前端子模块不存在: $FRONTEND_DIR"
    log_error "请确认已执行: git submodule update --init --recursive"
    exit 1
  fi
  if [ ! -f "$FRONTEND_DIR/package.json" ]; then
    log_error "前端 package.json 不存在: $FRONTEND_DIR/package.json"
    exit 1
  fi

  # pnpm 11 的专有配置（allowBuilds 等）只从前端 pnpm-workspace.yaml 读取。
  # 该文件缺失 = 所有依赖构建脚本被拦截 -> ERR_PNPM_IGNORED_BUILDS（Electron 二进制缺失）。
  # 常见原因：聚合仓的 cat 子模块指针过旧，未包含该文件，需更新指针。
  if [ ! -f "$FRONTEND_DIR/pnpm-workspace.yaml" ]; then
    log_error "未找到 $FRONTEND_DIR/pnpm-workspace.yaml（pnpm 11 的 allowBuilds 配置）"
    log_error "当前 cat 子模块: $(git -C "$FRONTEND_DIR" rev-parse --short HEAD 2>/dev/null || echo '未知')"
    log_error "请在聚合仓更新子模块指针：cd cat && git fetch && git checkout <含 pnpm-workspace.yaml 的 commit> && cd .. && git add cat"
    exit 1
  fi

  check_node_version
  check_pnpm_version
  log_info "Node: $(node -v) / pnpm: $(pnpm -v 2>/dev/null || echo '?')"
  log_info "环境检查通过"
}

#===============================================================================
# 清理
#===============================================================================
do_clean() {
  log_info "清理构建产物..."

  for d in "$BUILD_DIR" "$FRONTEND_BACKEND_DIR" "$FRONTEND_DIR/dist"; do
    if [ -d "$d" ]; then
      log_info "清理: $d"
      rm -rf "$d"
    fi
  done

  # 清理后端 Maven target
  for m in opc-cli opc-service opc-core opc-plugin opc-im; do
    if [ -d "$BACKEND_DIR/$m/target" ]; then
      rm -rf "$BACKEND_DIR/$m/target"
    fi
  done

  # 清理前端缓存
  if [ -d "$FRONTEND_DIR/node_modules/.cache" ]; then
    rm -rf "$FRONTEND_DIR/node_modules/.cache"
  fi

  log_info "清理完成"
}

#===============================================================================
# Step 1: 构建后端（mvn package + jpackage）
#===============================================================================
build_backend() {
  local version="$1"
  log_step "Step 1: 构建后端 (Java + jpackage)"

  # Maven 打包（生成 fat jar）
  log_info "执行 Maven 打包..."
  (cd "$BACKEND_DIR" && mvn clean package -DskipTests)

  local jar_path="$BACKEND_DIR/$BACKEND_JAR_DIR/$BACKEND_MAIN_JAR"
  if [ ! -f "$jar_path" ]; then
    log_error "未找到 JAR 文件: $jar_path"
    exit 1
  fi
  log_info "使用 JAR: $jar_path"

  # 准备 jpackage 参数
  rm -rf "$BACKEND_OUTPUT"
  mkdir -p "$BACKEND_OUTPUT"

  local cmd="jpackage"
  cmd="$cmd --input $BACKEND_DIR/$BACKEND_JAR_DIR"
  cmd="$cmd --main-jar $BACKEND_MAIN_JAR"
  cmd="$cmd --name $APP_NAME"
  cmd="$cmd --type $JPACKAGE_TYPE"
  cmd="$cmd --vendor \"$VENDOR\""
  cmd="$cmd --copyright \"$COPYRIGHT\""
  cmd="$cmd --app-version $version"
  cmd="$cmd --dest $BACKEND_OUTPUT"

  # JVM 参数逐项传入，避免含空格时 jpackage 解析异常
  if [ -n "$JAVA_OPTIONS" ]; then
    for opt in $JAVA_OPTIONS; do
      cmd="$cmd --java-options \"$opt\""
    done
  fi

  # 平台专属参数（Windows: --win-console；Linux: --linux-shortcut 等）
  local plat_args
  plat_args="$(jpackage_platform_args "$PLATFORM")"
  if [ -n "$plat_args" ]; then
    cmd="$cmd $plat_args"
  fi

  log_info "执行 jpackage..."
  eval "$cmd"

  local out_dir_name
  out_dir_name="$(jpackage_output_dir "$PLATFORM")"
  local out_dir="$BACKEND_OUTPUT/$out_dir_name"

  if [ ! -d "$out_dir" ]; then
    log_error "后端打包失败，产物不存在: $out_dir"
    exit 1
  fi

  # 复制示例配置
  if [ "$COPY_CONFIG_EXAMPLE" = true ] && [ -f "$BACKEND_DIR/$CONFIG_FILE_NAME" ]; then
    cp "$BACKEND_DIR/$CONFIG_FILE_NAME" "$out_dir/" 2>/dev/null || true
  fi

  log_info "后端构建完成: $out_dir"
}

#===============================================================================
# Step 2: 拷贝后端产物到前端工作区
#   electron-builder 的 extraResources { from: "backend/dist" } 相对前端仓库根解析
#   - Windows/Linux：jpackage app-image 产出 <APP_NAME>/ 目录（含可执行文件）
#   - macOS：产出 <APP_NAME>.app 目录
#===============================================================================
copy_backend() {
  log_step "Step 2: 拷贝后端产物到前端目录"

  local src
  src="$BACKEND_OUTPUT/$(jpackage_output_dir "$PLATFORM")"

  if [ ! -d "$src" ]; then
    log_error "后端产物目录不存在: $src"
    exit 1
  fi

  mkdir -p "$FRONTEND_BACKEND_DIR"
  rm -rf "${FRONTEND_BACKEND_DIR:?}"/*
  rm -rf "${FRONTEND_BACKEND_DIR:?}"/.[!.]* 2>/dev/null || true

  log_info "复制: $src/* → $FRONTEND_BACKEND_DIR"
  cp -r "$src"/. "$FRONTEND_BACKEND_DIR/"

  log_info "复制完成"
}

#===============================================================================
# Step 3: 构建前端 + 打包 Electron
#===============================================================================
build_frontend() {
  local version="$1"
  local plat_arg
  plat_arg="$(eb_platform_arg "$PLATFORM")"

  log_step "Step 3: 构建前端 + 打包 Electron ($PLATFORM)"

  cd "$FRONTEND_DIR"

  if [ ! -d "node_modules" ]; then
    log_info "安装前端依赖..."
    pnpm install
  fi

  log_info "编译 Electron 主进程..."
  pnpm run electron:tsc

  log_info "构建前端页面..."
  pnpm run build

  # FPM（deb 打包）要求 package.json 有顶级 homepage 字段。
  # 前端仓库若未配置，此处临时注入（打包后恢复），避免改动前端源码。
  local pkg="package.json"
  local pkg_bak="package.json.buildbak"
  local need_inject=false
  if [ "$PLATFORM" = "linux" ] && ! grep -q '"homepage"' "$pkg"; then
    need_inject=true
    cp "$pkg" "$pkg_bak"
    node -e "
      const fs=require('fs');
      const p=JSON.parse(fs.readFileSync('$pkg','utf8'));
      p.homepage='https://github.com/${GITHUB_REPOSITORY:-dpsk-opc/dpsk-opc}';
      if(!p.author) p.author='$VENDOR';
      fs.writeFileSync('$pkg', JSON.stringify(p,null,2));
    "
    log_info "已临时注入 package.json homepage/author（deb 打包需要）"
  fi

  log_info "打包 Electron (electron-builder $plat_arg)..."
  # 覆盖输出目录与版本号，无需改前端仓库源码
  # 注意：$plat_arg 故意不加引号，以便按空格拆分为多个参数
  # deb 需要 maintainer 元信息（FPM 强制要求）
  # shellcheck disable=SC2086
  npx electron-builder $plat_arg \
    --publish never \
    --config.directories.output="$INSTALLER_OUTPUT" \
    --config.extraMetadata.version="$version" \
    --config.linux.maintainer="$VENDOR <noreply@example.com>" \
    --config.linux.category="Utility" \
    --config.mac.category="public.app-category.productivity"

  # 恢复被临时修改的 package.json
  if [ "$need_inject" = true ] && [ -f "$pkg_bak" ]; then
    mv "$pkg_bak" "$pkg"
    log_info "已恢复 package.json"
  fi

  log_info "前端打包完成"
}

#===============================================================================
# Step 4: 校验产物
#===============================================================================
verify_output() {
  log_step "Step 4: 校验产物"

  if [ ! -d "$INSTALLER_OUTPUT" ]; then
    log_error "打包产物未找到: $INSTALLER_OUTPUT"
    exit 1
  fi

  echo ""
  log_info "=============================================="
  log_info "  打包成功！[$PLATFORM]"
  log_info "=============================================="
  log_info "后端产物: $BACKEND_OUTPUT"
  log_info "安装包目录: $INSTALLER_OUTPUT"
  log_info ""
  # 各平台产物扩展名
  ls -lh "$INSTALLER_OUTPUT/" 2>/dev/null \
    | grep -E "\.(exe|AppImage|deb|dmg|zip)$" \
    || ls -lh "$INSTALLER_OUTPUT/" 2>/dev/null \
    || true
  log_info "=============================================="
}

#===============================================================================
# 主流程
#===============================================================================
main() {
  echo ""
  echo "  ╔══════════════════════════════════╗"
  echo "  ║      Yoo! Desktop Builder        ║"
  echo "  ╚══════════════════════════════════╝"
  echo ""

  local start_time
  start_time=$(date +%s)

  local version
  version="$(resolve_version)"
  log_info "版本号: $version"
  log_info "目标平台: $PLATFORM"

  check_prerequisites

  if [ "$DO_CLEAN" = true ]; then
    do_clean
  fi

  build_backend "$version"
  copy_backend
  build_frontend "$version"
  verify_output

  local end_time duration
  end_time=$(date +%s)
  duration=$((end_time - start_time))
  log_info "总耗时: ${duration} 秒"
  log_info "版本 $version [$PLATFORM] 构建完成"
}

main "$@"
