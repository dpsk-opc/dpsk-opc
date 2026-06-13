#!/bin/bash
# build.sh - 使用 jpackage 将 Java 应用打包成 Windows exe
# 需在 Windows 环境（Git Bash、WSL、MSYS2）中运行，要求 JDK 14+、Maven 已安装

set -e  # 遇到错误立即退出

# ========== 配置区域（可按需修改） ==========
APP_NAME="opc"               # 最终 exe 名称（不含 .exe）
JPACKAGE_TYPE="app-image"          # 打包类型：app-image（文件夹含 exe） 或 exe（安装程序）
VENDOR="XiaoMiZhou"
COPYRIGHT="Copyright © 2026"
VERSION="1.0"
OUTPUT_PATH="output"

# 可选：额外 JVM 参数（如内存限制）
JAVA_OPTIONS="-Xmx512m"


# 外部配置：是否将示例配置文件复制到输出目录
COPY_CONFIG_EXAMPLE=true
CONFIG_FILE_NAME="application-uat.properties"   # 配置文件名（与代码中读取的一致）
# ==========================================

echo "=== 开始打包 ${APP_NAME} ==="

# 1. 检查必要的命令
command -v mvn >/dev/null 2>&1 || { echo "错误: Maven (mvn) 未安装或不在 PATH 中"; exit 1; }
command -v jpackage >/dev/null 2>&1 || { echo "错误: jpackage 未找到，请使用 JDK 14+ 并确保 bin 目录在 PATH 中"; exit 1; }

# 2. 清理并打包 Maven 项目（生成 fat jar）
echo ">>> 执行 Maven 打包..."
mvn clean package -DskipTests

# 3. 定位生成的 JAR 文件（支持 Spring Boot 或 shade 插件生成的 fat jar）
# JAR_FILE=$(find /target -name "opc-im.jar" | head -1)
JAR_FILE="opc-im/target/opc-im.jar"
if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到 JAR 文件，请检查 Maven 构建是否成功"
    exit 1
fi
echo ">>> 使用 JAR 文件: ${JAR_FILE}"

# 4. 准备 jpackage 参数
JPACKAGE_CMD="jpackage \
    --input opc-im/target \
    --main-jar $(basename "${JAR_FILE}") \
    --name ${APP_NAME} \
    --type ${JPACKAGE_TYPE} \
    --vendor \"${VENDOR}\" \
    --copyright \"${COPYRIGHT}\" \
    --app-version ${VERSION} \
    --dest ${OUTPUT_PATH} "
#    --arguments --spring.config.location=file:W:/workspace/dpsk-opc/config/

# 添加主类（如果指定了）
if [ -n "$MAIN_CLASS" ]; then
    # JPACKAGE_CMD="${JPACKAGE_CMD} --main-class ${MAIN_CLASS}"
    JPACKAGE_CMD="${JPACKAGE_CMD}"
fi

# 添加 JVM 参数
if [ -n "$JAVA_OPTIONS" ]; then
    JPACKAGE_CMD="${JPACKAGE_CMD} --java-options \"${JAVA_OPTIONS}\""
fi

# Windows 特有：保留控制台窗口（方便查看日志），若需要后台服务可去掉 --win-console
JPACKAGE_CMD="${JPACKAGE_CMD} --win-console"

# 可选：指定输出目录（默认为当前目录下的与 APP_NAME 同名的文件夹或安装包）

rm -rf ./output

echo ">>> 执行 jpackage 命令:"
echo "${JPACKAGE_CMD}"
eval "${JPACKAGE_CMD}"

echo "=== 打包完成 ==="
if [ "${JPACKAGE_TYPE}" = "app-image" ]; then
    echo "可执行文件位置: ./${APP_NAME}/${APP_NAME}.exe"
    echo "您可以将 ${CONFIG_FILE_NAME} 放在与该 exe 相同的目录下，程序会自动加载。"
else
    echo "安装包位置: ./${APP_NAME}-${VERSION}.exe"
fi