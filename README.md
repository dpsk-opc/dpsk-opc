# Yoo

> 桌面客户端 **Yoo** 的打包聚合仓库（Release / Distribution Repo）

本仓库**不含业务代码**，仅用于聚合后端与前端源码、统一版本号、并产出三平台安装包。
业务代码分别位于以下子模块仓库：

| 子模块 | 仓库 | 说明 |
|---|---|---|
| `opc/` | [dpsk-opc/opc](https://github.com/dpsk-opc/opc) | 后端（Java / Spring Boot / Maven 多模块） |
| `cat/` | [dpsk-opc/opc-cat](https://github.com/dpsk-opc/opc-cat) | 前端（Electron + umi + pnpm） |

---

## 仓库结构

```
.
├── VERSION               # 版本号唯一来源（纯三段数字，如 1.0.0）
├── build.sh              # 唯一构建脚本（后端 jpackage + 前端 electron-builder）
├── .gitmodules           # 子模块声明
├── .github/
│   ├── workflows/
│   │   └── release.yml   # 三平台并行构建 + 汇总发布
│   └── actions/
│       └── init-submodules/   # 复合 action：用 PAT 拉取私有子模块
├── opc/                  # [子模块] 后端源码
├── cat/                  # [子模块] 前端源码
└── build/                # 构建产物（不提交，gitignore）
    ├── backend/          # 后端 jpackage app-image
    └── installer/        # 最终安装包
```

---

## 快速开始

### 1. 克隆（含子模块）

```bash
git clone --recurse-submodules git@github.com:dpsk-opc/dpsk-opc.git
cd dpsk-opc
```

若已克隆但未拉子模块：

```bash
git submodule update --init --recursive
```

### 2. 环境要求

| 工具 | 版本 | 备注 |
|---|---|---|
| JDK | 17+ | **必须带 `jpackage`**（Temurin / Oracle JDK） |
| Maven | 3.8+ | |
| Node.js | 18+ | 建议 20 |
| pnpm | 9 | |

> ⚠️ **跨平台构建限制**：macOS 安装包必须在 macOS 上构建（`jpackage` 不支持交叉编译），
> Linux 安装包建议在 Linux 上构建。详见 `build.sh --platform <目标平台>`。

### 3. 构建

```bash
# 默认构建（平台按当前系统自动推断，版本取自 ./VERSION）
bash build.sh

# 清理后重新构建
bash build.sh --clean

# 指定版本（覆盖 VERSION 文件）
bash build.sh --version 1.2.3

# 指定目标平台
bash build.sh --platform windows
bash build.sh --platform linux
bash build.sh --platform macos
```

产物输出至 `build/`：

| 路径 | 内容 |
|---|---|
| `build/backend/` | 后端 jpackage app-image（Windows/Linux 为目录，macOS 为 `.app`） |
| `build/installer/` | 最终安装包（见下表） |

---

## 产物矩阵

| 平台 | 后端 | 前端安装包 |
|---|---|---|
| Windows | `Yoo/`（含 `Yoo.exe`） | `Yoo Setup <version>.exe`（NSIS） |
| Linux | `Yoo/` | `*.AppImage`、`*.deb` |
| macOS | `Yoo.app/` | `*.dmg`、`*.zip`（**未签名**） |

### macOS 安装说明（未签名版本）

本项目的 macOS 产物**未经 Apple 代码签名与公证**，首次打开会被系统拦截：

1. 将 `Yoo.app` 拖入「应用程序」文件夹
2. **右键 → 打开**（不要双击），在弹窗中再次点「打开」
3. 若仍提示「已损坏」，在终端执行：
   ```bash
   xattr -cr /Applications/Yoo.app
   ```

### Linux 安装说明

```bash
# AppImage（免安装）
chmod +x Yoo-*.AppImage && ./Yoo-*.AppImage

# deb（Debian / Ubuntu）
sudo dpkg -i yoo_*.deb
# 依赖缺失时
sudo apt-get -f install
```

---

## 发版流程

版本号唯一来源为仓库根 `VERSION` 文件，**纯三段数字**（如 `1.0.0`），由人工维护。

```bash
# 1. 确认子模块指针已更新到要发布的版本
git submodule update --remote opc cat
git add opc cat
git commit -m "chore: 更新子模块指针"

# 2. 修改版本号
echo "1.0.1" > VERSION
git add VERSION
git commit -m "chore: bump version to 1.0.1"

# 3. 合并/推送到 main，自动触发构建与发布
git push origin main
```

CI 会自动：

1. 校验 `VERSION` 格式
2. **幂等检查**：若 tag `v<version>` 已存在 → 跳过（提示先改版本号）
3. 三平台并行构建（Windows / Linux / macOS）
4. 打 tag `v<version>`
5. 创建 GitHub Release，上传三平台产物，自动生成 changelog

> 💡 **必须修改 VERSION 才能发版**：未改版本号就推送 main，CI 会因 tag 已存在而跳过。

---

## CI 说明

### 触发条件

- `push` 到 `main` 分支
- 手动触发（`workflow_dispatch`）

### Job 结构

| Job | Runner | 说明 |
|---|---|---|
| `version` | ubuntu | 读取校验版本、幂等判断 |
| `build-windows` | windows-latest | 构建 NSIS 安装包 |
| `build-linux` | ubuntu-latest | 构建 AppImage + deb |
| `build-macos` | macos-latest | 构建未签名 dmg + zip |
| `release` | ubuntu | 汇总产物、打 tag、建 Release |
| `skipped` | ubuntu | tag 已存在时的提示 |

### 必需配置

**Secrets**（仓库 Settings → Secrets and variables → Actions）：

| Secret | 说明 |
|---|---|
| `SUBMODULE_TOKEN` | 对 `dpsk-opc/opc`、`dpsk-opc/opc-cat` 有 **Contents: Read** 权限的 PAT |

> 子模块为私有仓库，`actions/checkout` 无法直接用默认 `GITHUB_TOKEN` 拉取。
> CI 会把 `.gitmodules` 中的 SSH 地址临时改写为 HTTPS + token 完成拉取（见 `.github/actions/init-submodules`）。

**Workflow 权限**（Settings → Actions → General → Workflow permissions）：

- 需设为 **Read and write permissions**（用于打 tag / 建 Release）

### 成本提示

`macos-latest` runner 在私有仓库按约 **10 倍倍率**扣减额度，请留意用量。

---

## 常见问题

**Q：为什么不把业务代码放在这个仓库？**

A：本仓库定位是"聚合器 + 打包入口"。业务代码在独立子模块仓库中维护，
便于前端/后端各自迭代、各自管理分支与权限，聚合仓只负责版本与发布。

**Q：构建失败提示找不到后端 Maven 工程？**

A：通常是子模块未拉取，执行：
```bash
git submodule update --init --recursive
```

**Q：本地构建报 fpm / AppImage 下载 429？**

A：electron-builder 二进制镜像被限流。CI 中已改用官方源；
本地可尝试设置 `ELECTRON_BUILDER_BINARIES_MIRROR` 或稍后重试。

**Q：macOS 产物打开提示"已损坏"？**

A：未签名版本的正常现象，见上文「macOS 安装说明」。
