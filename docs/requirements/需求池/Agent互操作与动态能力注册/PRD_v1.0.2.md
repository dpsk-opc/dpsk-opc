# DPSK-OPC Agent 互操作与动态能力注册需求文档

> **版本**: v1.0.2  
> **创建日期**: 2026-04-10  
> **作者**: DPSK-OPC 架构组  
> **状态**: 待开发

---

## 一、需求概述

### 1.1 背景

当前系统采用基于本地文件夹结构的多 Agent 协作模式，存在以下核心问题：

| 问题 | 描述 | 影响 |
|------|------|------|
| 能力声明依赖人工维护 | Agent 的能力描述、调度职责依赖用户在 agent.md 或配置文件中手工填写 | 容易出现"实际能力"与"声明能力"不一致 |
| 调度描述缺失风险 | 当用户定义的 Agent 具有调度职责，但忘记配置调度描述时 | 会导致任务无法正确派发，整个协作流程"断流" |
| 静态发现机制 | 秘书 Agent 通过扫描本地文件夹发现子 Agent | 无法支持 Agent 的动态上线、下线、版本升级 |
| 互操作能力薄弱 | Agent 之间的通信缺乏标准协议 | 无法与外部 Agent 系统进行互操作 |

### 1.2 升级愿景

引入 **A2A（Agent-to-Agent）开放协议** 与 **"Agent Card 即代码"** 理念，将 Agent 的能力声明从"静态配置文件"升级为"动态、可验证、可执行的代码实体"。

| 目标 | 说明 |
|------|------|
| 能力自描述 | Agent 启动时自动生成标准化的 Agent Card，无需人工维护 |
| 动态发现与注册 | Agent 可向注册中心动态注册/注销，调度节点实时感知可用 Agent 列表 |
| 标准化互操作 | Agent 间采用统一的 JSON-RPC 通信协议，支持跨进程、跨网络、跨组织的协作 |
| 调度自动化 | 调度节点自动获取子 Agent 的完整能力描述，自动生成调度策略 |

### 1.3 非目标（一期）

| 非目标 | 说明 |
|--------|------|
| 不实现 Agent Card 手动覆盖的高级特性 | 一期聚焦自动生成，暂不实现 FR-1.4 |
| 不实现注册中心集群化 | 一期单机部署，Phase 4 支持集群 |
| 不实现外部 Agent 互操作 | 一期聚焦内部 Agent，Phase 4 支持外部 Agent |

---

## 二、核心概念定义

### 2.1 Agent Card

遵循 A2A 协议规范的标准 JSON 文档，作为 Agent 的"数字身份证"。

```json
{
  "agent_id": "string",
  "name": "string",
  "version": "string",
  "description": "string",
  "url": "string",
  "capabilities": {
    "skills": [
      {
        "id": "string",
        "name": "string",
        "description": "string",
        "input_schema": {},
        "output_schema": {},
        "examples": []
      }
    ]
  },
  "security": {
    "auth_required": boolean,
    "auth_methods": ["api_key", "bearer"]
  },
  "metadata": {
    "调度偏好": {},
    "资源限制": {},
    "依赖_agent": []
  }
}
```

### 2.2 注册中心（Registry）

提供 Agent Card 的统一存储、查询、健康检查服务，是整个系统的"能力目录"。

| 功能 | 说明 |
|------|------|
| 注册与注销 | Agent 启动时上报 Card，停止时主动注销 |
| 查询接口 | 支持按名称、能力标签、Skill ID 等条件查询 Agent |
| 健康检查 | 定期探测 Agent 存活状态，自动剔除失效节点 |
| 版本管理 | 同一 Agent 的多个版本可共存，支持灰度升级与回滚 |

### 2.3 A2A 通信协议

基于 JSON-RPC 2.0 的标准化 Agent 间通信协议，核心抽象为 Task（任务）：

| 方法 | 说明 |
|------|------|
| tasks/sendMessage | 调用方通过此方法发送任务请求 |
| tasks/get | 根据 Task ID 查询任务状态与结果 |
| tasks/cancel | 取消正在执行的任务 |
| tasks/sendSubscribe | SSE 订阅，支持流式响应 |

### 2.4 "Agent Card 即代码"

指 Agent Card 不是由开发者手写，而是在 Agent 启动时由代码动态生成。生成逻辑会扫描 Agent 实际加载的工具函数、技能模块、配置信息，确保 Card 内容与 Agent 的真实能力严格一致。

---

## 三、总体设计

### 3.1 架构位置

```
┌─────────────────────────────────────────────────────────────────────┐
│                         注册中心（Registry）                            │
│                     Agent Card 统一存储/查询/健康检查                    │
└────────────────────────────────┬────────────────────────────────────┘
                                 │ HTTP / A2A Protocol
    ┌────────────────────────────┼────────────────────────────────────┐
    │                            │                                    │
    ▼                            ▼                                    ▼
┌──────────────┐          ┌──────────────┐                    ┌──────────────┐
│  秘书 Agent  │          │ 研发部 Agent │                    │ 产品部 Agent │
│  (调度节点)  │◀────────▶│              │◀──────────────────▶│              │
└──────┬───────┘          └──────┬───────┘                    └──────┬───────┘
       │                          │                                   │
       │    ┌─────────────────────┴─────────────────────┐             │
       │    │           A2A HTTP Server                │             │
       │    │  • tasks/sendMessage                     │             │
       │    │  • tasks/get                             │             │
       │    │  • tasks/sendSubscribe (SSE)             │             │
       │    └───────────────────────────────────────────┘             │
       │                                                                │
       ▼                                                                ▼
┌──────────────┐                                                ┌──────────────┐
│ Agent 模块   │                                                │  Agent 模块  │
│ v1.0.1       │                                                │  v1.0.1       │
└──────────────┘                                                └──────────────┘
```

### 3.2 核心组件关系

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Agent 模块 (v1.0.1+)                       │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────────┐  │
│  │  AgentCard    │  │  AgentCard    │  │  AgentRegistry          │  │
│  │  Generator    │  │  Builder      │  │  (扩展)                 │  │
│  │  (扫描Tools/   │  │  (合并用户    │  │  • register(card)      │  │
│  │   Skills)      │  │   覆盖)       │  │  • unregister(id)      │  │
│  └───────┬───────┘  └───────┬───────┘  │  • query()             │  │
│          │                  │          │  • heartbeat()         │  │
│          └────────┬─────────┘          └───────────┬─────────────┘  │
│                   ▼                                    │             │
│          ┌───────────────┐                           │             │
│          │  Agent Card   │◀──────────────────────────┘             │
│          │  (标准化JSON)  │                                          │
│          └───────────────┘                                          │
│                   │                                                  │
└───────────────────┼──────────────────────────────────────────────────┘
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          注册中心 (Registry)                          │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────────┐  │
│  │  REST API     │  │  Health Check │  │  Storage Backend        │  │
│  │  • POST /reg   │  │  • Ping/Pong  │  │  • SQLite (单机)        │  │
│  │  • DELETE /reg │  │  • TTL Check │  │  • PostgreSQL (集群)    │  │
│  │  • GET /query  │  │  • Cleanup   │  │  • Redis (缓存)         │  │
│  └───────────────┘  └───────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       A2A 通信层 (新增)                               │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────────┐  │
│  │  A2A Server   │  │  A2A Client   │  │  Task Manager           │  │
│  │  • HTTP Server│  │  • JSON-RPC   │  │  • Task Status          │  │
│  │  • SSE Stream │  │  • Sync/Async │  │  • Result Store         │  │
│  │  • WebSocket  │  │  • Retry     │  │  • TTL Cleanup          │  │
│  └───────────────┘  └───────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 四、功能需求

### 4.1 Agent Card 自动生成模块

#### 4.1.1 能力扫描

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.1.1-01 | Agent 启动时，系统应自动扫描其注册的所有工具函数（Tools）和技能模块（Skills） | P0 |
| FR-4.1.1-02 | 提取每个 Tool/Skill 的名称、描述、参数 Schema、返回值 Schema | P0 |
| FR-4.1.1-03 | 从函数签名（type hints）和 docstring 中提取结构化信息 | P0 |

#### 4.1.2 Card 构建

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.1.2-01 | 系统应根据扫描结果自动构建符合 A2A 规范的 Agent Card JSON 对象 | P0 |
| FR-4.1.2-02 | 自动生成的 Card 应包含 Agent 的基础信息：名称、版本、描述、服务 URL（可配置） | P0 |
| FR-4.1.2-03 | 若 Agent 目录下存在子 Agent 文件夹，自动生成逻辑应将其识别为"调度节点" | P0 |
| FR-4.1.2-04 | 调度节点的 Card 中自动填充 sub_agents 字段及调度相关能力声明 | P0 |

#### 4.1.3 用户覆盖（Phase 2）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.1.3-01 | 支持用户在 agent-card.json 中提供覆盖字段（自定义描述、调度策略） | P1 |
| FR-4.1.3-02 | 自动生成逻辑应合并用户配置与自动扫描结果，用户配置优先 | P1 |

### 4.2 注册中心服务

#### 4.2.1 基础注册接口

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.2.1-01 | 提供 Agent 注册接口，接收 Agent Card JSON 并持久化存储 | P0 |
| FR-4.2.1-02 | 提供 Agent 注销接口，允许 Agent 在停止时主动移除自己的记录 | P0 |
| FR-4.2.1-03 | 注册接口应验证 Card 格式是否符合 A2A 规范 | P0 |

#### 4.2.2 查询接口

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.2.2-01 | 提供列出所有 Agent 的接口 | P0 |
| FR-4.2.2-02 | 提供按名称精确查询的接口 | P0 |
| FR-4.2.2-03 | 提供按能力标签（Skill 名称/描述）模糊搜索的接口 | P0 |
| FR-4.2.2-04 | 提供按 Skill ID 查询的接口 | P0 |

#### 4.2.3 健康检查与版本管理

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.2.3-01 | 实现心跳机制：Agent 定期发送心跳，注册中心更新 TTL | P1 |
| FR-4.2.3-02 | 实现健康检查：每隔 N 秒探测已注册 Agent 的存活状态 | P1 |
| FR-4.2.3-03 | 连续失败 M 次后自动注销失效节点 | P1 |
| FR-4.2.3-04 | 提供 Agent Card 的版本管理能力：同一 Agent 名称可注册多个版本 | P2 |
| FR-4.2.3-05 | 查询时默认返回最新版本，也可指定版本 | P2 |

### 4.3 A2A 通信协议实现

#### 4.3.1 A2A 服务端

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.3.1-01 | 每个 Agent 启动时应启动一个 HTTP 服务，作为 A2A 协议的服务端 | P0 |
| FR-4.3.1-02 | 实现 tasks/sendMessage JSON-RPC 方法：接收任务请求，创建 Task 对象并异步执行 | P0 |
| FR-4.3.1-03 | 实现 tasks/get JSON-RPC 方法：根据 Task ID 查询任务状态与结果 | P0 |
| FR-4.3.1-04 | 实现 tasks/cancel JSON-RPC 方法：取消正在执行的任务 | P0 |
| FR-4.3.1-05 | 支持通过 SSE 推送任务状态更新（进度、完成、失败） | P1 |

#### 4.3.2 A2A 客户端

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.3.2-01 | Agent 框架应提供统一的 A2A 客户端封装，用于向其他 Agent 发起任务请求 | P0 |
| FR-4.3.2-02 | 客户端应屏蔽底层 JSON-RPC 细节 | P0 |
| FR-4.3.2-03 | 客户端应支持同步调用（阻塞等待结果） | P0 |
| FR-4.3.2-04 | 客户端应支持异步调用（轮询或 SSE） | P1 |

#### 4.3.3 任务模型

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.3.3-01 | Task 对象包含任务 ID、状态（pending/working/completed/failed）、结果或错误信息 | P0 |
| FR-4.3.3-02 | Task 结果应持久化存储，支持后续查询 | P0 |
| FR-4.3.3-03 | 已完成的 Task 在一定时间后自动清理（TTL） | P1 |

### 4.4 调度节点（秘书 Agent）能力升级

#### 4.4.1 能力感知

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.4.1-01 | 调度节点启动时，从注册中心获取所有可用子 Agent 的 Card 信息 | P0 |
| FR-4.4.1-02 | 调度节点根据子 Agent 的 Card 构建内部能力视图 | P0 |

#### 4.4.2 动态 Prompt 生成

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.4.2-01 | 调度节点的 System Prompt 应基于子 Agent 的能力描述动态生成 | P0 |
| FR-4.4.2-02 | 动态生成的 Prompt 替代原有手工维护的文本，根除"忘记配置调度描述"问题 | P0 |
| FR-4.4.2-03 | 当注册中心中子 Agent 发生变化时，调度节点应能更新内部视图 | P1 |
| FR-4.4.2-04 | 通过轮询机制实现能力视图更新（轮询间隔可配置） | P1 |

#### 4.4.3 任务派发

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.4.3-01 | 调度节点通过 A2A 客户端向子 Agent 派发任务 | P0 |
| FR-4.4.3-02 | 调度节点监控任务执行状态，处理超时和失败 | P0 |
| FR-4.4.3-03 | 支持向多个子 Agent 并行派发聚合任务 | P1 |

### 4.5 向后兼容与平滑迁移

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-4.5-01 | 系统应保持对现有文件夹结构驱动模式的兼容 | P0 |
| FR-4.5-02 | 当注册中心不可用或 Agent 未配置 A2A 服务时，降级使用原有的文件夹扫描 + 本地函数调用模式 | P0 |
| FR-4.5-03 | 提供迁移工具：扫描现有 Agent 文件夹，自动生成初始的 Agent Card 模板文件 | P1 |

---

## 五、非功能需求

### 5.1 性能需求

| 需求 | 描述 | 目标 |
|------|------|------|
| NFR-5.1-01 | 注册中心查询接口在 100 个 Agent 规模下，响应时间应低于 50ms（P99） | P0 |
| NFR-5.1-02 | A2A 任务派发延迟（不含任务执行时间） | < 100ms |
| NFR-5.1-03 | Agent Card 生成时间 | < 500ms |
| NFR-5.1-04 | 支持同时运行的 Agent 实例数 | ≥ 50 |

### 5.2 可靠性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-5.2-01 | 注册中心支持集群部署，单节点故障不影响整体服务 | P2 |
| NFR-5.2-02 | Agent 异常退出后，注册中心能在配置时间内自动摘除 | P1 |
| NFR-5.2-03 | A2A 任务在 Agent 不可用时支持重试（可配置） | P1 |

### 5.3 安全性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-5.3-01 | Agent 间通信应支持基于 API Key 或 Bearer Token 的认证机制 | P1 |
| NFR-5.3-02 | 初期：无认证；后期：mTLS | P2 |
| NFR-5.3-03 | 注册中心应验证 Agent Card 的来源可信性 | P1 |

### 5.4 可扩展性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-5.4-01 | 注册中心的存储后端应设计为可插拔接口 | P0 |
| NFR-5.4-02 | 支持文件、SQLite、Redis、PostgreSQL 等多种存储后端 | P0 |

### 5.5 可观测性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-5.5-01 | Agent 注册、注销、任务执行等关键操作应输出结构化日志 | P0 |
| NFR-5.5-02 | 支持集成到现有监控系统（Prometheus metrics） | P0 |
| NFR-5.5-03 | 注册中心自身应暴露 /.well-known/agent-card.json 端点 | P2 |

### 5.6 标准化需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-5.6-01 | Agent Card 结构应严格遵循 A2A 开放标准 | P0 |
| NFR-5.6-02 | A2A 通信协议应严格遵循 JSON-RPC 2.0 规范 | P0 |

---

## 六、数据模型

### 6.1 AgentCard

```python
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any

class SkillDef(BaseModel):
    """技能定义"""
    id: str = Field(description="技能唯一标识")
    name: str = Field(description="技能名称")
    description: str = Field(description="技能描述")
    input_schema: Dict[str, Any] = Field(default_factory=dict, description="输入参数 Schema")
    output_schema: Dict[str, Any] = Field(default_factory=dict, description="输出结果 Schema")
    examples: List[Dict[str, Any]] = Field(default_factory=list, description="调用示例")

class SecurityConfig(BaseModel):
    """安全配置"""
    auth_required: bool = Field(default=False, description="是否需要认证")
    auth_methods: List[str] = Field(default_factory=list, description="支持的认证方式")

class AgentCard(BaseModel):
    """Agent Card - Agent 的标准化数字身份证"""
    agent_id: str = Field(description="Agent 唯一标识")
    name: str = Field(description="Agent 显示名称")
    version: str = Field(description="Agent 版本")
    description: str = Field(description="Agent 功能描述")
    url: Optional[str] = Field(default=None, description="A2A 服务 URL")
    
    capabilities: Dict[str, Any] = Field(default_factory=dict, description="能力声明")
    skills: List[SkillDef] = Field(default_factory=list, description="技能列表")
    
    security: SecurityConfig = Field(default_factory=SecurityConfig, description="安全配置")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="扩展元数据")
    
    # 调度相关（由注册中心填充）
    registered_at: Optional[str] = None
    last_heartbeat: Optional[str] = None
    status: str = "active"  # active, inactive, unknown
```

### 6.2 Task

```python
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any
from enum import Enum

class TaskStatus(str, Enum):
    PENDING = "pending"
    WORKING = "working"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELED = "canceled"

class Task(BaseModel):
    """A2A 任务对象"""
    task_id: str = Field(description="任务唯一标识")
    agent_id: str = Field(description="目标 Agent ID")
    
    status: TaskStatus = Field(default=TaskStatus.PENDING, description="任务状态")
    message: Dict[str, Any] = Field(description="任务消息内容")
    
    # 结果
    result: Optional[Dict[str, Any]] = Field(default=None, description="任务结果")
    error: Optional[str] = Field(default=None, description="错误信息")
    
    # 元数据
    created_at: str = Field(description="创建时间")
    updated_at: Optional[str] = Field(default=None, description="更新时间")
    completed_at: Optional[str] = Field(default=None, description="完成时间")
```

### 6.3 RegistryEntry

```python
from pydantic import BaseModel, Field
from typing import Optional

class RegistryEntry(BaseModel):
    """注册表条目"""
    agent_id: str = Field(description="Agent ID")
    card: "AgentCard" = Field(description="Agent Card")
    registered_at: str = Field(description="注册时间")
    last_heartbeat: str = Field(description="最后心跳时间")
    ttl_seconds: int = Field(default=60, description="TTL 秒数")
    status: str = Field(default="active", description="状态")
```

---

## 七、API 规范

### 7.1 注册中心 API

#### 7.1.1 注册 Agent

```
POST /api/v1/registry/agents
Content-Type: application/json

Request Body: AgentCard

Response 201:
{
  "success": true,
  "data": {
    "agent_id": "string",
    "registered_at": "ISO8601"
  }
}
```

#### 7.1.2 注销 Agent

```
DELETE /api/v1/registry/agents/{agent_id}

Response 200:
{
  "success": true
}
```

#### 7.1.3 列出所有 Agent

```
GET /api/v1/registry/agents

Query Parameters:
  - status: optional, "active" | "inactive" | "all"
  - limit: optional, default 100
  - offset: optional, default 0

Response 200:
{
  "success": true,
  "data": {
    "agents": [AgentCard],
    "total": 100,
    "limit": 100,
    "offset": 0
  }
}
```

#### 7.1.4 查询 Agent

```
GET /api/v1/registry/agents/{agent_id}

Response 200:
{
  "success": true,
  "data": AgentCard
}
```

#### 7.1.5 搜索 Agent

```
GET /api/v1/registry/agents/search

Query Parameters:
  - q: required, 搜索关键词（匹配名称、描述、Skill 名称）
  - skill_id: optional, 按 Skill ID 精确匹配

Response 200:
{
  "success": true,
  "data": {
    "agents": [AgentCard],
    "total": 10
  }
}
```

#### 7.1.6 心跳

```
POST /api/v1/registry/agents/{agent_id}/heartbeat

Response 200:
{
  "success": true,
  "ttl_seconds": 60
}
```

### 7.2 A2A Agent API

#### 7.2.1 发送任务

```
POST /api/v1/a2a/agents/{agent_id}/tasks/sendMessage
Content-Type: application/json
X-A2A-Session-Id: optional-session-id

Request Body:
{
  "message": {
    "role": "user",
    "content": "string",
    "data": {}
  }
}

Response 200:
{
  "jsonrpc": "2.0",
  "id": "task-uuid",
  "result": {
    "task_id": "task-uuid",
    "status": "pending"
  }
}
```

#### 7.2.2 获取任务状态

```
GET /api/v1/a2a/agents/{agent_id}/tasks/{task_id}

Response 200:
{
  "jsonrpc": "2.0",
  "id": "task-uuid",
  "result": {
    "task_id": "task-uuid",
    "status": "completed",
    "result": {}
  }
}
```

#### 7.2.3 订阅任务（SSE）

```
GET /api/v1/a2a/agents/{agent_id}/tasks/{task_id}/subscribe

Response: SSE Stream
event: status
data: {"task_id": "xxx", "status": "working"}

event: status
data: {"task_id": "xxx", "status": "completed", "result": {}}
```

---

## 八、项目结构

```
src/
├── agent/                              # Agent 模块（改动）
│   ├── card_generator.py              # Agent Card 自动生成（新增）
│   ├── card_builder.py                # Card 构建器，支持用户覆盖（新增）
│   ├── registry.py                     # 扩展，支持 Card 注册
│   └── ...
│
├── registry/                           # 注册中心模块（新增）
│   ├── __init__.py
│   ├── app.py                         # FastAPI 应用
│   ├── models.py                      # Registry 数据模型
│   ├── storage/
│   │   ├── __init__.py
│   │   ├── base.py                    # 存储抽象接口
│   │   ├── sqlite.py                  # SQLite 实现
│   │   └── redis.py                   # Redis 实现（可选）
│   ├── service.py                      # 注册中心服务逻辑
│   ├── health.py                      # 健康检查
│   └── exceptions.py                  # 异常定义
│
├── a2a/                                # A2A 通信模块（新增）
│   ├── __init__.py
│   ├── protocol.py                    # JSON-RPC 2.0 实现
│   ├── server.py                      # A2A HTTP Server
│   ├── client.py                      # A2A Client
│   ├── task_manager.py                # Task 管理
│   ├── exceptions.py                  # A2A 异常
│   └── types.py                       # A2A 类型定义
│
└── main.py                             # 集成所有模块
```

---

## 九、依赖关系

### 9.1 模块依赖

```
┌──────────────┐     ┌──────────────┐
│  Agent Card │────▶│    Agent     │
│  Generator   │     │   Module     │
└──────────────┘     └──────┬───────┘
       │                    │
       │                    ▼
       │            ┌──────────────┐
       │            │   Registry   │
       │            │   Module     │
       │            └──────┬───────┘
       │                   │
       ▼                   ▼
┌──────────────┐     ┌──────────────┐
│     A2A      │◀───▶│     Bus     │
│   Module     │     │   Module     │
└──────────────┘     └──────────────┘
```

### 9.2 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| fastapi | ≥0.100 | 注册中心 HTTP 服务 |
| pydantic | ≥2.5 | 数据验证与序列化 |
| structlog | ≥24.1 | 结构化日志 |
| sse-starlette | ≥1.6 | SSE 支持 |
| aiosqlite | ≥0.19 | SQLite 异步访问 |

---

## 十、优先级矩阵

### 10.1 Phase 1：基础能力升级

#### P0（必须完成）

| 编号 | 功能 | 依赖 | 复杂度 | 负责人 |
|------|------|------|--------|--------|
| P1-01 | Agent Card 数据结构定义 | - | 低 | |
| P1-02 | Agent Card Generator（扫描 Tools/Skills） | - | 中 | |
| P1-03 | 调度节点识别（sub_agents 字段） | P1-02 | 中 | |
| P1-04 | 注册中心基础 API（注册/注销/查询） | - | 中 | |
| P1-05 | 注册中心 SQLite 存储后端 | P1-04 | 中 | |
| P1-06 | Agent 启动时自动注册 Card | P1-01, P1-04 | 低 | |
| P1-07 | Agent 停止时自动注销 | P1-04 | 低 | |
| P1-08 | 降级兼容（文件夹扫描模式） | - | 中 | |
| P1-09 | 调度节点从注册中心获取子 Agent 信息 | P1-06 | 中 | |
| P1-10 | 调度节点动态生成 System Prompt | P1-09 | 中 | |

#### P1（应该完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P1-11 | 心跳机制 | P1-04 | 低 |
| P1-12 | 健康检查与自动注销 | P1-11 | 中 |
| P1-13 | 能力视图轮询更新 | P1-09 | 低 |

### 10.2 Phase 2：A2A 协议实现

#### P0（必须完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P2-01 | JSON-RPC 2.0 协议实现 | - | 中 |
| P2-02 | A2A HTTP Server（tasks/sendMessage, tasks/get） | P2-01 | 中 |
| P2-03 | Task Manager（状态管理、结果存储） | P2-02 | 中 |
| P2-04 | A2A Client 封装（同步调用） | P2-01 | 中 |
| P2-05 | 调度节点通过 A2A 派发任务 | P2-03, P2-04 | 中 |

#### P1（应该完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P2-06 | SSE 流式响应（tasks/sendSubscribe） | P2-02 | 中 |
| P2-07 | A2A Client 异步调用模式 | P2-06 | 中 |
| P2-08 | 任务取消（tasks/cancel） | P2-02 | 低 |
| P2-09 | Task TTL 自动清理 | P2-03 | 低 |

### 10.3 Phase 3：高级特性

| 编号 | 功能 | 优先级 | 复杂度 |
|------|------|--------|--------|
| P3-01 | Agent Card 用户覆盖配置 | P1 | 中 |
| P3-02 | 注册中心版本管理 | P2 | 中 |
| P3-03 | API Key 认证 | P2 | 中 |
| P3-04 | 迁移工具（生成 Card 模板） | P1 | 中 |
| P3-05 | 并行任务派发 | P2 | 高 |

---

## 十一、风险与应对

| 风险 | 概率 | 影响 | 应对策略 |
|------|------|------|----------|
| A2A 协议尚未成为广泛采纳的工业标准 | 中 | 高 | 保持架构的可扩展性，核心抽象层允许替换协议；A2A 由 Google 等推动，生态前景较好 |
| 自动生成的 Card 描述不够精确 | 中 | 中 | 持续优化扫描提取逻辑（从 docstring、类型注解提取） |
| 网络通信引入延迟，影响任务执行效率 | 低 | 中 | Phase 1 保持本地调用模式作为降级通道 |
| 注册中心成为单点故障 | 中 | 高 | Phase 3 规划集群方案；初期 Agent 本地缓存子 Agent 列表 |
| 调度节点依赖注册中心，启动顺序问题 | 中 | 中 | Agent 支持延迟注册；注册中心不可用时使用本地缓存 |

---

## 十二、验收标准

### 12.1 Phase 1 验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| UC-P1-01 | Agent 启动 | 任意 Agent 启动后，可在注册中心查询到其自动生成的 Agent Card |
| UC-P1-02 | Card 内容正确 | Agent Card 中的 skills 列表与 Agent 实际注册的 Skill 一致 |
| UC-P1-03 | 调度节点感知 | 秘书 Agent 启动后，从注册中心获取子 Agent 列表 |
| UC-P1-04 | 动态 Prompt | 秘书 Agent 的 System Prompt 中包含所有子 Agent 的能力描述 |
| UC-P1-05 | 动态感知 | 新增子 Agent 后，秘书 Agent 在轮询间隔内感知到变化 |
| UC-P1-06 | 降级兼容 | 关闭注册中心后，秘书 Agent 使用本地缓存继续工作 |
| UC-P1-07 | 注销感知 | 子 Agent 停止后，秘书 Agent 在健康检查周期内感知并更新视图 |
| UC-P1-08 | 调度功能正常 | 秘书 Agent 能正确将任务派发给子 Agent（本地调用模式） |

### 12.2 Phase 2 验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| UC-P2-01 | A2A 任务派发 | 秘书 Agent 可通过 A2A 协议向子 Agent 发送任务并获取结果 |
| UC-P2-02 | 跨进程通信 | A2A 任务在独立的 Agent 进程中执行 |
| UC-P2-03 | 任务状态查询 | 可通过 tasks/get 查询任务状态和结果 |
| UC-P2-04 | SSE 订阅 | 支持通过 SSE 流式获取任务进度更新 |
| UC-P2-05 | 任务取消 | 可通过 tasks/cancel 取消正在执行的任务 |

### 12.3 可观测性验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| OC-01 | 结构化日志 | Agent 注册、注销、任务执行等操作有结构化日志 |
| OC-02 | 注册中心健康 | /health 端点返回注册中心健康状态 |
| OC-03 | Agent Card 端点 | 注册中心暴露 /.well-known/agent-card.json |

---

## 十三、实施计划

### 13.1 Phase 1：基础能力升级（预计 2-3 周）

| 阶段 | 任务 | 预计工时 | 交付物 |
|------|------|----------|--------|
| 1.1 | 设计 AgentCard 数据结构，实现 Card Generator | 2 人天 | Card Generator |
| 1.2 | 设计注册中心 API，实现 SQLite 存储后端 | 2 人天 | Registry API |
| 1.3 | Agent 启动/停止流程集成 Card 注册/注销 | 1 人天 | 生命周期集成 |
| 1.4 | 实现降级兼容模式（文件夹扫描） | 1 人天 | 降级逻辑 |
| 1.5 | 调度节点从注册中心获取子 Agent 信息 | 1 人天 | 能力感知 |
| 1.6 | 实现动态 System Prompt 生成 | 1 人天 | Prompt 生成 |
| 1.7 | 心跳机制与健康检查 | 1 人天 | 健康检查 |
| 1.8 | 单元测试与文档 | 1 人天 | 测试、文档 |
| **合计** | | **10 人天** | |

### 13.2 Phase 2：A2A 协议实现（预计 2-3 周）

| 阶段 | 任务 | 预计工时 | 交付物 |
|------|------|----------|--------|
| 2.1 | 实现 JSON-RPC 2.0 协议 | 1 人天 | Protocol |
| 2.2 | 实现 A2A HTTP Server | 2 人天 | A2A Server |
| 2.3 | 实现 Task Manager | 2 人天 | Task Manager |
| 2.4 | 实现 A2A Client 封装 | 2 人天 | A2A Client |
| 2.5 | 调度节点集成 A2A 派发 | 1 人天 | 任务派发 |
| 2.6 | SSE 流式响应支持 | 2 人天 | SSE 支持 |
| 2.7 | 单元测试与集成测试 | 2 人天 | 测试 |
| **合计** | | **12 人天** | |

### 13.3 Phase 3：高级特性（预计 2-3 周）

| 阶段 | 任务 | 预计工时 | 交付物 |
|------|------|----------|--------|
| 3.1 | Agent Card 用户覆盖配置 | 1 人天 | 覆盖配置 |
| 3.2 | 注册中心版本管理 | 2 人天 | 版本管理 |
| 3.3 | API Key 认证 | 2 人天 | 认证机制 |
| 3.4 | 迁移工具 | 1 人天 | 迁移工具 |
| 3.5 | 并行任务派发 | 2 人天 | 并行派发 |
| 3.6 | 测试与文档 | 2 人天 | 测试、文档 |
| **合计** | | **10 人天** | |

---

## 十四、术语对照

| 术语 | 说明 |
|------|------|
| A2A | Agent-to-Agent，Agent 间通信开放协议 |
| Agent Card | Agent 的标准化数字身份证，包含能力声明 |
| Agent Card 即代码 | Agent Card 由代码自动生成，而非手工维护 |
| Registry | 注册中心，Agent Card 的统一存储/查询服务 |
| Task | A2A 协议中的任务抽象 |
| JSON-RPC 2.0 | 基于 JSON 的远程过程调用协议 |
| SSE | Server-Sent Events，服务器推送技术 |
| TTL | Time To Live，存活时间 |

---

## 十五、参考文档

- [A2A 协议官网](https://a2a-protocol.org)
- [A2A 协议规范 GitHub](https://github.com/google/A2A)
- [Agent 模块需求文档](../backend/v1.0.1/DPSK-OPC%20Agent%20模块需求文档.md)
- [MCP 集成需求文档](../需求池/MCP集成/PRD_v1.0.2.md)
- [消息总线开发总结](../backend/v1.0.0/summary_v1.0.0.md)
