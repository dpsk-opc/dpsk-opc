# DPSK-OPC Backend

DPSK-OPC 后端服务，基于 Python 实现的消息总线系统。

## 项目结构

```
backend/
├── src/                    # 源代码
│   ├── __init__.py         # 包初始化
│   ├── main.py             # 应用入口
│   ├── config.py            # 配置管理
│   │
│   ├── bus/                # 消息总线核心
│   │   ├── __init__.py
│   │   ├── models.py       # 消息模型
│   │   ├── protocol.py     # 总线抽象接口
│   │   ├── memory.py        # 内存后端实现
│   │   └── redis.py         # Redis 后端实现
│   │
│   ├── api/                # HTTP/WebSocket 接口
│   │   ├── __init__.py
│   │   ├── gateway.py
│   │   └── health.py
│   │
│   ├── core/                # 核心业务逻辑
│   │   ├── __init__.py
│   │   └── workflow.py       # 工作流引擎
│   │
│   └── utils/               # 工具函数
│       ├── __init__.py
│       ├── logging.py
│       └── tracing.py
│
├── tests/                   # 测试代码
│   ├── __init__.py
│   ├── test_models.py
│   ├── test_bus.py
│   └── test_config.py
│
├── config/                  # 配置文件
│   ├── default.yaml
│   ├── development.yaml
│   └── production.yaml
│
├── pyproject.toml
├── requirements.txt
└── Dockerfile
```

## 快速开始

### 安装依赖

```bash
pip install -r requirements.txt
```

### 运行开发服务器

```bash
python -m src.main
```

### 运行测试

```bash
pytest tests/
```

## 开发指南

详见 [docs/requirements/backend/v1.0.0/](./docs/requirements/backend/v1.0.0/) 目录下的需求文档。
