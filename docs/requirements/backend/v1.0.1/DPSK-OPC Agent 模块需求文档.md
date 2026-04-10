# DPSK-OPC Agent 模块需求文档

> **版本**: v1.0.1  
> **创建日期**: 2026-04-09  
> **作者**: DPSK-OPC 架构组  
> **状态**: 待开发

---

## 一、需求概述

### 1.1 背景与目标

在 DPSK-OPC 一人公司 AI 操作系统中，Agent 是最小执行单元，相当于"数字员工"。消息总线模块（v1.0.0）已就绪，本模块负责 Agent 的静态定义、动态实例化、技能执行、模型路由、经验记忆等核心能力，实现自然语言驱动的多 Agent 协作。

**核心目标**：

| 目标 | 说明 |
|------|------|
| 按"人"抽象 | Agent 支持不同模型、独立记忆、权限隔离 |
| 安全集成 | 与安全模块（权限、沙箱、密钥）无缝集成 |
| 通信集成 | 与消息总线自然协作，支持 AGENT/GROUP/TOPIC/SYSTEM 目标类型 |
| 组织模型 | 支持部门/团队/技能的组织结构 |
| 可观测性 | 全步骤日志、审计追踪 |

### 1.2 适用范围

本文档描述 Agent 模块的完整需求，包括：

- 核心概念与定位
- 静态定义（AgentDef）
- 动态实例化（AgentSpawner）
- Agent 运行器（agent_runner）
- 技能执行框架
- 多模型支持
- 记忆/经验池
- 与安全、消息总线模块的集成
- 管理 API

### 1.3 术语表

| 术语 | 说明 |
|------|------|
| Agent | 最小执行单元，无状态，无自主通信，默认零权限，运行于沙箱内 |
| Skill | Agent 具备的能力（如 debug_js, python_compile），通过函数实现 |
| Team | 一组 Agent 的功能归类（如 compiler-team） |
| Workspace | 业务隔离单元（如 engineering） |
| AgentDef | Agent 的静态定义（从 Markdown 文件解析） |
| AgentInstance | Agent 的动态实例（运行在沙箱中的进程） |
| 经验池 | 每个 Agent 独立的历史任务存储（SQLite），用于精确匹配复用 |
| 秘书 Agent | 系统内置的全局调度 Agent，负责任务拆解与分发 |

---

## 二、功能需求

### 2.1 静态定义（AgentDef）

#### 2.1.1 存储方式

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.1.1-01 | Agent 定义存放在用户可配置的目录（默认 `~/.dpskopc/agents`） | P0 |
| FR-2.1.1-02 | 每个 Agent 由一个 `.md` 文件定义，文件名（不含 `.md`）作为 `agent_id` | P0 |
| FR-2.1.1-03 | 目录层级仅用于人类组织，系统递归扫描所有 `.md` 文件 | P0 |
| FR-2.1.1-04 | 秘书 Agent 的定义文件必须放在根目录，文件名为 `秘书.md` | P0 |

#### 2.1.2 文件格式

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.1.2-01 | 使用 YAML Frontmatter + Markdown 正文格式 | P0 |
| FR-2.1.2-02 | YAML 必须包含字段：agent_id, name, team, workspace, skills, capabilities, dependencies, model, model_config, max_instances, queue_size, max_experience_entries, metadata | P0 |
| FR-2.1.2-03 | Markdown 正文为自然语言描述，供 LLM 理解 Agent 职责 | P0 |

**文件格式示例**：

```markdown
---
agent_id: secretary
name: 秘书 Agent
team: 总裁办
workspace: system
skills:
  - task_planning
  - agent_discovery
  - group_coordination
capabilities:
  - 解析用户自然语言需求
  - 拆解任务为子任务
  - 调用下游 Agent 执行
  - 汇总结果并回复用户
dependencies: []
model: gpt-4
model_config:
  temperature: 0.7
max_instances: 1
queue_size: 10
max_experience_entries: 1000
metadata:
  cpu_limit: 0.5
  memory_limit: 256
---
# 秘书 Agent 职责说明
（自然语言描述，供 LLM 理解）
```

#### 2.1.3 数据结构

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.1.3-01 | 定义 `AgentDef` 数据类，包含所有配置字段 | P0 |
| FR-2.1.3-02 | 支持 Pydantic 验证和数据序列化 | P0 |
| FR-2.1.3-03 | `metadata` 字段支持任意扩展（cpu_limit, memory_limit 等） | P1 |

### 2.2 解析与注册（AgentRegistry）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.2-01 | 系统启动时，`AgentRegistry` 递归扫描 `agents_root` 下所有 `.md` 文件 | P0 |
| FR-2.2-02 | 解析每个文件为 `AgentDef` 并存入内存 | P0 |
| FR-2.2-03 | 秘书 Agent 的定义必须存在，否则系统自动创建默认秘书 | P0 |
| FR-2.2-04 | 其他 Agent 只加载定义，不创建实例 | P0 |
| FR-2.2-05 | 提供 `get(agent_id)`、`list_all()` 等查询接口 | P0 |

### 2.3 动态实例化（AgentSpawner）

#### 2.3.1 接口定义

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.1-01 | 定义 `AgentSpawner` 抽象基类 | P0 |
| FR-2.3.1-02 | `spawn()` 方法：创建 Agent 实例，返回 `AgentHandle` | P0 |
| FR-2.3.1-03 | `get_instance()` 方法：按 instance_id 查询实例 | P0 |
| FR-2.3.1-04 | `list_instances()` 方法：列出所有实例，支持按 agent_id 过滤 | P0 |

#### 2.3.2 并发控制与队列

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.2-01 | 每个 AgentDef 可配置 `max_instances`（最大并发实例数） | P0 |
| FR-2.3.2-02 | 每个 AgentDef 可配置 `queue_size`（任务队列大小，0 表示不排队） | P0 |
| FR-2.3.2-03 | 当实例数达到上限时，任务可入队等待 | P0 |
| FR-2.3.2-04 | 队列满时返回错误，不阻塞调用方 | P0 |
| FR-2.3.2-05 | 实例完成任务后主动从队列取下一个任务 | P0 |

#### 2.3.3 生命周期管理

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.3-01 | `spawn()` 调用安全模块的 `create_sandbox` 创建沙箱 | P0 |
| FR-2.3.3-02 | 注入环境变量：`AGENT_ID`, `MESSAGE_BUS_ADDRESS`, `TEMP_TOKEN` | P0 |
| FR-2.3.3-03 | 在沙箱内启动 `agent_runner.py` | P0 |
| FR-2.3.3-04 | `stop()` 方法：优雅停止，发送 shutdown 命令 | P0 |
| FR-2.3.3-05 | `destroy()` 方法：强制销毁沙箱 | P0 |
| FR-2.3.3-06 | 支持 TTL：实例空闲超过指定时间后自动销毁 | P1 |

#### 2.3.4 临时令牌

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.4-01 | 主后端调用安全模块生成临时令牌（绑定 agent_id 和 sandbox_id） | P0 |
| FR-2.3.4-02 | 令牌通过环境变量 `TEMP_TOKEN` 注入沙箱 | P0 |
| FR-2.3.4-03 | Agent 进程使用令牌向主后端证明身份 | P0 |

### 2.4 Agent 运行器（agent_runner）

#### 2.4.1 核心职责

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.1-01 | 连接消息总线（跨进程通信） | P0 |
| FR-2.4.1-02 | 注册自己的 `Target(AGENT, agent_id)` 处理器 | P0 |
| FR-2.4.1-03 | 循环接收 TaskRequest，执行对应技能，返回 TaskResponse | P0 |
| FR-2.4.1-04 | 通过总线向主后端请求安全操作（读文件、解密密钥等） | P0 |
| FR-2.4.1-05 | 实现经验池的存储与检索 | P0 |

#### 2.4.2 任务处理流程

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.2-01 | 精确匹配经验池：计算输入的 SHA256，命中则直接返回 | P0 |
| FR-2.4.2-02 | 执行技能：查找 `SKILL_HANDLERS` 中对应的 handler | P0 |
| FR-2.4.2-03 | 异步存储经验：不阻塞响应流程 | P0 |
| FR-2.4.2-04 | 异常处理：技能执行失败返回错误响应 | P0 |

#### 2.4.3 安全操作请求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.3-01 | Agent 需要文件读写时，通过总线发送 Query 到 `system.security` | P0 |
| FR-2.4.3-02 | Agent 需要密钥解密时，通过总线发送 Query 到 `system.security` | P0 |
| FR-2.4.3-03 | 请求携带临时令牌用于身份验证 | P0 |

### 2.5 技能执行框架

#### 2.5.1 技能定义与加载

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.5.1-01 | 技能是 `task_name` 到异步函数的映射 | P0 |
| FR-2.5.1-02 | 内置技能放在 Agent 镜像的 `/app/skills/` 目录下 | P0 |
| FR-2.5.1-03 | 每个技能一个 Python 文件，必须导出 `async def run(params)` | P0 |
| FR-2.5.1-04 | 启动时动态加载所有技能模块，注册到 `SKILL_HANDLERS` | P0 |

#### 2.5.2 技能示例

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.5.2-01 | 提供 `debug_js` 技能示例：读取文件 → 调用 LLM → 返回修复代码 | P0 |
| FR-2.5.2-02 | 提供 `echo` 测试技能：直接返回输入 | P0 |

### 2.6 多模型支持

#### 2.6.1 模型配置

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.1-01 | AgentDef 中包含 `model` 和 `model_config` 字段 | P0 |
| FR-2.6.1-02 | 支持指定默认模型及参数（如 temperature） | P0 |

#### 2.6.2 模型调用流程

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.2-01 | Agent 不直接调用模型 API（无网络权限，无密钥） | P0 |
| FR-2.6.2-02 | Agent 通过总线发送 Query 到 `system.llm`，携带模型 ID、提示词 | P0 |
| FR-2.6.2-03 | 主后端的 LLMService 维护模型路由表，调用对应 API | P0 |
| FR-2.6.2-04 | 密钥从安全模块解密后内存使用，不暴露给 Agent | P0 |

#### 2.6.3 支持的模型（一期）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.3-01 | 支持 OpenAI GPT-4 / GPT-3.5 | P0 |
| FR-2.6.3-02 | 支持 Anthropic Claude | P0 |
| FR-2.6.3-03 | 预留本地 Ollama 接口 | P2 |

### 2.7 记忆/经验池

#### 2.7.1 设计原则

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.7.1-01 | 每个 Agent 拥有独立的经验库 | P0 |
| FR-2.7.1-02 | 新任务到达时，先精确匹配（SHA256）输入，命中则直接返回 | P0 |
| FR-2.7.1-03 | 异步存储经验，不阻塞响应 | P0 |

#### 2.7.2 存储方案（一期）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.7.2-01 | 使用 SQLite 数据库 | P0 |
| FR-2.7.2-02 | 每个 Agent 一个数据库文件（或共享一个表通过 agent_id 区分） | P0 |
| FR-2.7.2-03 | 表结构：id, agent_id, task_name, input_hash, input_json, output_json, created_at, hit_count | P0 |
| FR-2.7.2-04 | 建立复合索引：(agent_id, task_name, input_hash) | P0 |

#### 2.7.3 清理策略

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.7.3-01 | `max_experience_entries` 限制最大记录数 | P0 |
| FR-2.7.3-02 | 超限删除 hit_count 最低且 created_at 最早的 10% 记录 | P0 |

### 2.8 AgentManager

#### 2.8.1 核心职责

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.8.1-01 | 静态定义管理：查询所有 AgentDef，按 agent_id 获取定义 | P0 |
| FR-2.8.1-02 | 实例生命周期：创建（spawn）、销毁（destroy）、列出实例 | P0 |
| FR-2.8.1-03 | 任务队列管理：查看队列长度、等待任务数 | P1 |

#### 2.8.2 接口定义

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.8.2-01 | `spawn_agent(agent_id, ttl_seconds)`：创建实例，返回句柄 | P0 |
| FR-2.8.2-02 | `destroy_instance(instance_id, force)`：销毁实例 | P0 |
| FR-2.8.2-03 | `list_instances(agent_id)`：列出实例 | P0 |
| FR-2.8.2-04 | `get_agent_def(agent_id)`：获取定义 | P0 |
| FR-2.8.2-05 | `list_agent_defs()`：列出所有定义 | P0 |

### 2.9 管理 API

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.9-01 | `GET /api/v1/agents/defs`：列出所有 Agent 定义 | P0 |
| FR-2.9-02 | `GET /api/v1/agents/defs/{agent_id}`：获取单个 Agent 定义详情 | P0 |
| FR-2.9-03 | `GET /api/v1/agents/instances`：列出所有运行中的实例 | P0 |
| FR-2.9-04 | `POST /api/v1/agents/{agent_id}/spawn`：手动创建 Agent 实例 | P0 |
| FR-2.9-05 | `DELETE /api/v1/agents/instances/{instance_id}`：销毁指定实例 | P0 |
| FR-2.9-06 | `GET /api/v1/agents/instances/{instance_id}/queue`：查询队列状态 | P1 |

### 2.10 可观测性

#### 2.10.1 日志收集

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.10.1-01 | 沙箱内 Agent 日志由安全模块捕获并转发给主后端 | P0 |
| FR-2.10.1-02 | 主后端写入 `logs/agent_<agent_id>.log` 文件 | P0 |
| FR-2.10.1-03 | 步骤级日志：每个技能调用的输入、输出、耗时、错误 | P0 |
| FR-2.10.1-04 | Agent 通过总线发送结构化日志到 `system.logging` | P0 |

#### 2.10.2 健康检查

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.10.2-01 | Agent 启动后发送 Event（type="agent.ready"）到总线 | P0 |
| FR-2.10.2-02 | 主后端监听并更新实例状态 | P0 |
| FR-2.10.2-03 | 主后端提供 REST API 查询 Agent 实例状态 | P0 |

---

## 三、非功能需求

### 3.1 性能需求

| 需求 | 描述 | 目标 |
|------|------|------|
| NFR-3.1-01 | Agent 实例创建时间（不含沙箱启动） | < 100ms |
| NFR-3.1-02 | 经验池查询响应时间 | < 10ms |
| NFR-3.1-03 | 消息总线通信延迟 | < 50ms |
| NFR-3.1-04 | 支持同时运行的 Agent 实例数 | ≥ 50 |

### 3.2 安全需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.2-01 | Agent 默认零权限，所有操作需通过安全模块授权 | P0 |
| NFR-3.2-02 | Agent 运行于独立沙箱，网络隔离 | P0 |
| NFR-3.2-03 | 临时令牌短时效，过期自动失效 | P0 |
| NFR-3.2-04 | 密钥不暴露给 Agent，通过安全模块代理访问 | P0 |

### 3.3 可靠性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.3-01 | Agent 崩溃后主后端能感知并更新状态 | P0 |
| NFR-3.3-02 | 支持优雅停机，不丢失正在处理的任务 | P0 |
| NFR-3.3-03 | 任务超时后返回明确错误信息 | P0 |

### 3.4 兼容性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.4-01 | Python 3.11+ | P0 |
| NFR-3.4-02 | 与消息总线 v1.0.0 完全兼容 | P0 |
| NFR-3.4-03 | 与安全模块接口兼容 | P0 |

---

## 四、数据模型

### 4.1 AgentDef

```python
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any
from pathlib import Path

@dataclass
class AgentDef:
    agent_id: str                     # 唯一标识，与文件名相同
    name: str                         # 显示名称
    file_path: Path                   # 源文件路径
    skills: List[str] = field(default_factory=list)
    capabilities: List[str] = field(default_factory=list)
    team: Optional[str] = None
    workspace: Optional[str] = None
    dependencies: List[str] = field(default_factory=list)
    description: str = ""             # Markdown 正文
    model: Optional[str] = None       # 模型 ID，如 "gpt-4", "claude-3-opus"
    model_config: Dict[str, Any] = field(default_factory=dict)
    max_instances: int = 1            # 最大并发实例数
    queue_size: int = 0               # 任务队列大小（0 表示不排队）
    max_experience_entries: int = 1000
    metadata: Dict[str, Any] = field(default_factory=dict)
```

### 4.2 AgentInstance

```python
@dataclass
class AgentInstance:
    instance_id: str                  # 实例唯一 ID
    agent_id: str                     # 对应的 AgentDef ID
    sandbox_id: str                   # 沙箱 ID
    status: InstanceStatus            # CREATING/RUNNING/STOPPING/STOPPED/FAILED
    created_at: float                 # 创建时间戳
    last_heartbeat: float             # 最后心跳时间
    task_count: int = 0               # 已处理任务数
```

### 4.3 Experience

```sql
CREATE TABLE experiences (
    id INTEGER PRIMARY KEY,
    agent_id TEXT NOT NULL,
    task_name TEXT NOT NULL,
    input_hash TEXT NOT NULL,
    input_json TEXT,
    output_json TEXT NOT NULL,
    created_at REAL,
    hit_count INTEGER DEFAULT 1
);
CREATE INDEX idx_agent_task_hash ON experiences(agent_id, task_name, input_hash);
```

---

## 五、项目结构

```
src/agent/
├── __init__.py
├── defs.py                # AgentDef 数据结构
├── parser.py              # Markdown 解析器（YAML Frontmatter）
├── registry.py            # AgentRegistry（内存存储）
├── spawner.py             # AgentSpawner 接口及实现
├── manager.py             # AgentManager（管理逻辑）
├── runner.py              # agent_runner.py 模板（沙箱内运行）
├── skills/                # 内置技能基类（主后端侧）
│   ├── __init__.py
│   └── base.py
├── experience.py          # 经验池管理（SQLite）
└── api.py                 # FastAPI 路由（管理接口）
```

**Agent 沙箱镜像内结构**：

```
/app/
├── agent_runner.py        # 主循环脚本
├── skills/                # 动态加载的技能模块
│   ├── __init__.py
│   ├── echo.py
│   └── debug_js.py
├── experience.db          # SQLite 经验库
└── requirements.txt
```

---

## 六、依赖关系

### 6.1 模块依赖

```
┌─────────────┐     ┌─────────────┐
│   Agent     │────▶│   Security   │
│   Module    │     │   Module     │
└─────────────┘     └─────────────┘
       │                   │
       │                   │
       ▼                   ▼
┌─────────────┐     ┌─────────────┐
│    Bus      │◀────│   Config     │
│   Module    │     │   Module     │
└─────────────┘     └─────────────┘
```

### 6.2 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| pydantic | ≥2.5 | 数据验证与序列化 |
| aiosqlite | ≥0.19 | SQLite 异步访问 |
| pyyaml | ≥6.0 | YAML 解析 |

---

## 七、优先级矩阵

### P0（必须完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P0-01 | AgentDef 数据结构与解析 | - | 低 |
| P0-02 | AgentRegistry 加载与注册 | P0-01 | 低 |
| P0-03 | AgentSpawner 实例化 | Security Module | 中 |
| P0-04 | agent_runner 基础框架 | Bus Module | 中 |
| P0-05 | 技能执行框架 | P0-04 | 中 |
| P0-06 | 经验池（存储与检索） | - | 低 |
| P0-07 | AgentManager 管理逻辑 | P0-02, P0-03 | 低 |
| P0-08 | 管理 API | P0-07 | 低 |
| P0-09 | 与安全模块集成 | Security Module | 中 |
| P0-10 | 与消息总线集成 | Bus Module | 中 |
| P0-11 | 可观测性（日志与健康检查） | P0-04 | 低 |

### P1（应该完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P1-01 | 并发实例控制与队列 | P0-03 | 中 |
| P1-02 | 经验池清理策略 | P0-06 | 低 |
| P1-03 | TTL 自动销毁 | P0-03 | 低 |
| P1-04 | 队列状态查询 API | P1-01 | 低 |

### P2（可以延后）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P2-01 | 多模型路由 | P0-10 | 高 |
| P2-02 | Ollama 本地模型支持 | P2-01 | 高 |
| P2-03 | 向量检索经验池 | - | 高 |

---

## 八、风险与应对

| 风险 | 等级 | 应对策略 |
|------|------|----------|
| 安全模块接口不稳定 | 高 | 与安全模块团队对齐接口，提前 Mock 测试 |
| 跨进程通信性能瓶颈 | 中 | 选择高效的 IPC 方案（Unix Socket + JSON） |
| 沙箱启动时间过长 | 中 | 优化镜像大小，预热常用 Agent |
| 经验池 SQLite 并发写入 | 低 | 使用 WAL 模式，限制单 Agent 写入频率 |
| Agent 定义文件格式不标准 | 低 | 提供 Schema 验证，友好的错误提示 |

---

## 九、验收标准

### 9.1 功能验收

| 用例 | 验收条件 |
|------|----------|
| UC-01 | 启动主后端后，秘书 Agent 自动运行 |
| UC-02 | 通过 API 可查看所有已加载的 AgentDef |
| UC-03 | 手动创建下游 Agent 实例成功 |
| UC-04 | 秘书 Agent 通过总线发送任务，下游 Agent 执行并返回结果 |
| UC-05 | 经验池命中时直接返回结果，不调用 LLM |
| UC-06 | Agent 崩溃后主后端感知并更新状态 |
| UC-07 | 调用 API 销毁实例后，沙箱被正确销毁 |
| UC-08 | 查看日志确认步骤级记录完整 |

### 9.2 集成验收

| 用例 | 验收条件 |
|------|----------|
| IC-01 | Agent 可通过总线请求安全模块读取文件 |
| IC-02 | Agent 可通过总线请求安全模块解密密钥 |
| IC-03 | 权限检查在每次安全操作时正确执行 |
| IC-04 | 临时令牌过期后 Agent 无法继续请求安全操作 |

---

## 十、附录

### 10.1 实现顺序建议

1. **Phase 1: 基础框架**
   - `defs.py` + `parser.py` + `registry.py`
   - `agent_runner.py` 基础框架（连接总线、处理任务）

2. **Phase 2: 实例化**
   - `spawner.py`（调用安全模块创建沙箱）
   - 与安全模块集成

3. **Phase 3: 管理与 API**
   - `manager.py` + `api.py`
   - 技能执行框架

4. **Phase 4: 高级功能**
   - 经验池完善
   - 并发控制与队列
   - 可观测性增强

5. **Phase 5: 测试与优化**
   - 端到端测试
   - 性能优化

### 10.2 参考文档

- [DPSK-OPC 通信机制概要设计文档](../v1.0.0/DPSK-OPC%20通信机制概要设计文档.md)
- [消息总线开发总结](../v1.0.0/summary_v1.0.0.md)
