# dpsk-opc

## 项目结构

dpsk-opc/                          # 主 Git 仓库
├── .gitmodules
├── .gitignore
├── README.md
├── LICENSE
│
├── backend/                       # 后端目录
│   ├── pyproject.toml
│   ├── requirements.txt
│   ├── .env.example
│   ├── Dockerfile
│   │
│   ├── src/                       # Python 源码（直接作为包根目录）
│   │   ├── __init__.py            # 使 src 成为 Python 包
│   │   ├── main.py
│   │   ├── config.py
│   │   │
│   │   ├── api/                   # HTTP/WebSocket 路由
│   │   │   ├── __init__.py
│   │   │   ├── gateway.py
│   │   │   ├── admin.py
│   │   │   └── health.py
│   │   │
│   │   ├── core/                  # 核心业务逻辑
│   │   │   ├── __init__.py
│   │   │   ├── scheduler.py
│   │   │   ├── agent_manager.py
│   │   │   ├── workflow_engine.py
│   │   │   └── context_store.py
│   │   │
│   │   ├── agents/                # 内置 Agent
│   │   │   ├── __init__.py
│   │   │   ├── base.py
│   │   │   ├── secretary.py
│   │   │   └── ...
│   │   │
│   │   ├── bus/                   # 消息总线客户端
│   │   │   ├── __init__.py
│   │   │   ├── client.py
│   │   │   ├── memory_client.py
│   │   │   └── redis_client.py
│   │   │
│   │   ├── security/              # 安全模块 Python 绑定
│   │   │   ├── __init__.py
│   │   │   ├── wrapper.py
│   │   │   └── permission.py
│   │   │
│   │   ├── static/                # 前端静态文件（构建后）
│   │   │   └── index.html
│   │   │
│   │   ├── models/                # 数据模型
│   │   │   ├── __init__.py
│   │   │   ├── task.py
│   │   │   ├── agent.py
│   │   │   └── context.py
│   │   │
│   │   ├── db/                    # 数据库访问
│   │   │   ├── __init__.py
│   │   │   ├── session.py
│   │   │   └── repositories/
│   │   │
│   │   ├── services/              # 业务服务层
│   │   │   ├── __init__.py
│   │   │   ├── task_service.py
│   │   │   └── agent_service.py
│   │   │
│   │   ├── utils/                 # 工具函数
│   │   │   ├── __init__.py
│   │   │   ├── logging.py
│   │   │   ├── tracing.py
│   │   │   └── helpers.py
│   │   │
│   │   └── exceptions/            # 自定义异常
│   │       ├── __init__.py
│   │       ├── bus_error.py
│   │       └── permission_error.py
│   │
│   ├── tests/                     # 单元测试（镜像 src 结构）
│   ├── scripts/                   # 构建脚本
│   │   ├── build_security.sh
│   │   ├── build_web.sh
│   │   ├── copy_artifacts.sh
│   │   ├── run_dev.sh
│   │   └── package.sh
│   │
│   └── config/                    # 配置文件
│       ├── default.yaml
│       ├── development.yaml
│       └── production.yaml
│
├── security/                      # Git submodule -> dpsk-opc-security
│   └── ...
│
├── web/                           # Git submodule -> dpsk-opc-web
│   └── ...
│
└── sandbox_images/                # 可选：沙箱镜像
    └── ...
