# DPSK-OPC MCP 集成需求文档

> **版本**: v1.0.2  
> **创建日期**: 2026-04-09  
> **作者**: DPSK-OPC 架构组  
> **状态**: 待开发

---

## 一、需求概述

### 1.1 背景

DPSK-OPC 已设计 Agent 模块（v1.0.1），支持内部 Agent（运行于沙箱）通过消息总线协作。但实际业务中存在大量第三方能力（如代码执行、绘图、邮件发送、数据分析等），它们可能以 MCP（Model Context Protocol）服务器形式存在。

为了复用这些能力，避免重复开发，系统需要能够集成符合 MCP 协议的第三方 Agent 或 Skill。

### 1.2 目标

| 目标 | 说明 |
|------|------|
| MCP 协议支持 | 支持通过 MCP 协议（stdio / HTTP/SSE）调用外部工具（Tool）和资源（Resource） |
| 能力映射 | 将 MCP Server 提供的工具映射为 DPSK-OPC 中的 Skill，可选择包装为 Agent |
| 统一管理 | 保持与现有 Agent 调度、权限控制、审计日志的统一 |
| 两种模式 | **Skill 模式**：无状态工具；**Agent 模式**：有状态服务 |
| 安全保证 | 第三方服务凭证加密存储，调用经过权限网关校验，操作可审计 |

### 1.3 非目标（一期）

| 非目标 | 说明 |
|--------|------|
| 不支持 resources | 一期不实现 MCP 的 resources 能力 |
| 不支持动态注册 | 一期不支持 MCP Server 的动态注册/发现（手动配置） |
| 仅作客户端 | 一期不支持将 DPSK-OPC 内部能力暴露为 MCP Server |

---

## 二、总体设计

### 2.1 架构位置

```
┌──────────────────────────────────────────────────────────────┐
│                        秘书 Agent                              │
│                           │                                  │
│                    TaskRequest                               │
│                           ▼                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                      主后端                            │   │
│  │  ┌─────────────────┐  ┌─────────────────────────┐    │   │
│  │  │  AgentManager   │──│    MCPClientManager     │    │   │
│  │  │  (统一调度)      │  │    (MCP 客户端管理)      │    │   │
│  │  └────────┬────────┘  └───────────┬─────────────┘    │   │
│  │           │                        │                   │   │
│  │     ┌─────┴─────┐          ┌───────┴───────┐         │   │
│  │     ▼           ▼          ▼               ▼         │   │
│  │  ┌──────┐  ┌─────────┐ ┌────────┐  ┌──────────┐     │   │
│  │  │内部  │  │虚拟     │ │stdio   │  │HTTP/SSE  │     │   │
│  │  │Agent │  │MCP      │ │MCP     │  │MCP       │     │   │
│  │  │      │  │Agent    │ │Server  │  │Server    │     │   │
│  │  └──┬───┘  └────┬────┘ └───┬────┘  └────┬─────┘     │   │
│  │     │           │          │             │           │   │
│  │     ▼           └──────────┴─────────────┘           │   │
│  │  ┌────────┐              ┌──────────────┐            │   │
│  │  │ 沙箱   │              │ 第三方服务   │            │   │
│  │  └────────┘              └──────────────┘            │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 两种集成模式

| 模式 | 适用场景 | 实现方式 |
|------|----------|----------|
| **Skill 模式** | 无状态工具（如计算、发邮件） | MCP Server 直接映射为一个或多个 Skill，不创建独立 Agent；可由任意 Agent 调用（需授权） |
| **Agent 模式** | 有状态服务（如对话客服、代码解释器） | MCP Server 映射为一个虚拟 Agent，拥有自己的 agent_id，秘书 Agent 可单独调度 |

> **一期优先实现 Agent 模式**，Skill 模式可作为简化形式（将 MCP Server 视为一个特殊 Agent，其每个 tool 作为一个 skill）。

### 2.3 路由逻辑

```python
# 主后端消息路由
async def handle_task_request(request: TaskRequest):
    agent_def = registry.get(request.target.value)
    
    if agent_def.agent_type == "internal":
        # 内部 Agent：走沙箱
        return await agent_spawner.forward(...)
    
    elif agent_def.agent_type == "mcp":
        # MCP Agent：走 MCP Bridge
        return await mcp_manager.call_tool(
            server_id=agent_def.mcp_server_id,
            tool_name=request.task_name,
            arguments=request.task_data,
        )
```

---

## 三、功能需求

### 3.1 配置管理

#### 3.1.1 配置文件

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.1.1-01 | 在 Agent 根目录或配置目录下支持 `mcp_servers.yaml` 配置文件 | P0 |
| FR-3.1.1-02 | 支持 `servers` 数组配置，每个 Server 包含唯一标识、名称、类型 | P0 |
| FR-3.1.1-03 | 支持 `command` 字段（stdio 类型）指定启动命令和参数 | P0 |
| FR-3.1.1-04 | 支持 `url` 和 `headers` 字段（HTTP/SSE 类型）指定连接地址 | P0 |
| FR-3.1.1-05 | 支持 `env` 字段传递环境变量（凭证等） | P0 |
| FR-3.1.1-06 | 支持 `${VAR}` 语法引用环境变量或安全模块密钥 | P0 |
| FR-3.1.1-07 | 支持 `mapping` 配置段指定虚拟 Agent 的 agent_id、workspace、team、skills_prefix | P0 |

**配置文件示例**：

```yaml
servers:
  - id: code-runner
    name: 第三方代码执行服务
    type: stdio
    command: ["node", "/opt/mcp-servers/code-runner/index.js"]
    env:
      API_KEY: ${MCP_CODE_RUNNER_KEY}
    mapping:
      agent_id: code-runner-agent
      workspace: sandbox
      team: execution
      skills_prefix: ""

  - id: dall-e
    name: DALL-E 绘图服务
    type: sse
    url: https://api.example.com/mcp
    headers:
      Authorization: "Bearer ${DALL_E_TOKEN}"
    mapping:
      agent_id: image-generator
      workspace: creative
      team: design
```

#### 3.1.2 凭证安全

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.1.2-01 | `${VAR}` 引用在主后端启动时从安全模块解密后替换 | P0 |
| FR-3.1.2-02 | 对于 stdio 类型，凭证通过子进程环境变量传递（不落盘） | P0 |
| FR-3.1.2-03 | 对于 HTTP 类型，凭证存储在内存中，用于构造请求头 | P0 |

### 3.2 MCP 客户端管理器

#### 3.2.1 接口定义

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.2.1-01 | 定义 `MCPClientManager` 类，提供统一接口 | P0 |
| FR-3.2.1-02 | `connect(server_id, config)`：建立与 MCP Server 的连接 | P0 |
| FR-3.2.1-03 | `list_tools(server_id)`：调用 tools/list，返回工具定义列表 | P0 |
| FR-3.2.1-04 | `call_tool(server_id, tool_name, arguments)`：调用工具并返回结果 | P0 |
| FR-3.2.1-05 | `disconnect(server_id)`：关闭指定连接 | P0 |
| FR-3.2.1-06 | `disconnect_all()`：关闭所有连接 | P0 |
| FR-3.2.1-07 | `get_status(server_id)`：查询连接状态 | P0 |

#### 3.2.2 传输类型支持

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.2.2-01 | **stdio 传输**：通过 asyncio.subprocess 启动子进程，使用 JSON-RPC over stdin/stdout | P0 |
| FR-3.2.2-02 | **SSE 传输**：通过 HTTP 长连接，支持双向消息 | P1 |
| FR-3.2.2-03 | JSON-RPC 2.0 协议实现 | P0 |
| FR-3.2.2-04 | 请求-响应模型实现 | P0 |

#### 3.2.3 生命周期管理

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.2.3-01 | 主后端启动时，根据配置文件连接所有 MCP Server | P0 |
| FR-3.2.3-02 | 支持自动重连（最多 3 次，指数退避） | P0 |
| FR-3.2.3-03 | 主后端关闭时，优雅断开所有连接（发送 shutdown 通知） | P0 |
| FR-3.2.3-04 | 子进程优雅关闭（发送 terminate 信号并等待退出） | P0 |
| FR-3.2.3-05 | 连接超时配置（默认 10 秒） | P0 |

### 3.3 虚拟 Agent 与 Skill 映射

#### 3.3.1 动态生成 AgentDef

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.3.1-01 | 启动时，对于每个 MCP Server，调用 list_tools 获取工具列表 | P0 |
| FR-3.3.1-02 | 构造 AgentDef，agent_id 使用配置中的 mapping.agent_id | P0 |
| FR-3.3.1-03 | AgentDef 的 agent_type 字段设为 "mcp" | P0 |
| FR-3.3.1-04 | AgentDef 的 skills 字段填充所有工具名称（可加前缀） | P0 |
| FR-3.3.1-05 | 将 AgentDef 注入 AgentRegistry，与普通 Agent 定义一起管理 | P0 |
| FR-3.3.1-06 | 支持 skills_prefix 配置，为所有 tool 名称加前缀避免冲突 | P0 |

#### 3.3.2 请求处理

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.3.2-01 | 主后端识别 agent_type == "mcp" 的请求 | P0 |
| FR-3.3.2-02 | 从 task_name 还原出原始工具名称（去掉前缀） | P0 |
| FR-3.3.2-03 | 调用 MCPClientManager.call_tool(server_id, tool_name, task_data) | P0 |
| FR-3.3.2-04 | 将结果封装为 TaskResponse 返回 | P0 |

#### 3.3.3 错误处理

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.3.3-01 | MCP 协议错误（如工具不存在、参数错误）映射为 TaskResponse.success=False | P0 |
| FR-3.3.3-02 | 网络超时或子进程崩溃：主后端重试一次，仍失败则返回错误 | P0 |
| FR-3.3.3-03 | 请求超时可配置（默认 30 秒） | P0 |
| FR-3.3.3-04 | 返回的错误信息包含 MCP 错误码和消息 | P0 |

### 3.4 权限与审计

#### 3.4.1 权限控制

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.4.1-01 | 虚拟 Agent 同样受权限网关约束 | P0 |
| FR-3.4.1-02 | 通过策略文件（OPA）控制哪些秘书 Agent 可以调用该 MCP Agent | P0 |
| FR-3.4.1-03 | 通过策略文件控制该 MCP Agent 的哪些 skill 可以被使用 | P0 |
| FR-3.4.1-04 | 权限检查发生在主后端调用 MCP 之前 | P0 |

#### 3.4.2 审计日志

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-3.4.2-01 | 每次 MCP 调用记录审计事件 | P0 |
| FR-3.4.2-02 | 审计内容包括：agent_id、skill（tool）、输入参数、输出结果（可配置脱敏）、耗时、成功/失败 | P0 |
| FR-3.4.2-03 | 审计引擎与内部 Agent 使用同一套接口 | P0 |
| FR-3.4.2-04 | 支持审计日志级别配置（记录全部/仅错误/仅摘要） | P1 |

### 3.5 对现有模块的改动

| 模块 | 改动内容 | 优先级 |
|------|----------|--------|
| AgentDef | 增加 agent_type 字段（默认 internal，新增 mcp）；增加 mcp_server_id 字段 | P0 |
| AgentRegistry | 支持从 MCP 配置动态生成 AgentDef 并注册 | P0 |
| AgentSpawner | spawn 方法对 agent_type == "mcp" 返回轻量句柄（不创建沙箱） | P0 |
| AgentManager | 加载 MCP 配置并注册虚拟 Agent | P0 |
| 主后端消息路由 | 在 handle_task_request 中增加分支，转发给 MCPClientManager | P0 |
| 安全模块 | 提供 API 供主后端解密凭证（若需） | P0 |
| 审计引擎 | 无改动（主后端主动调用 audit.log） | - |

---

## 四、非功能需求

### 4.1 性能需求

| 需求 | 描述 | 目标 |
|------|------|------|
| NFR-4.1-01 | MCP 调用延迟（不含外部服务自身处理时间） | < 100ms |
| NFR-4.1-02 | stdio 类型 MCP Server 支持并发调用（请求排队） | ≥ 10 并发 |
| NFR-4.1-03 | HTTP 类型 MCP Server 支持真正的并发调用 | 无限制 |
| NFR-4.1-04 | 连接池：对于 HTTP 类型，维护长连接复用 | P0 |

### 4.2 可靠性需求

| 需求 | 描述 | 目标 |
|------|------|------|
| NFR-4.2-01 | 子进程意外退出：自动重启（最多 3 次） | P0 |
| NFR-4.2-02 | 指数退避重连策略（1s, 2s, 4s） | P0 |
| NFR-4.2-03 | 请求超时可配置（默认 30 秒） | P0 |
| NFR-4.2-04 | 隔离：某个 MCP Server 的故障不影响其他 Server 和内部 Agent | P0 |
| NFR-4.2-05 | 连接健康检查（定期 ping） | P1 |

### 4.3 可观测性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-4.3-01 | 暴露 Prometheus 指标：mcp_call_total（调用计数） | P0 |
| NFR-4.3-02 | 暴露 Prometheus 指标：mcp_call_duration_seconds（调用耗时） | P0 |
| NFR-4.3-03 | 暴露 Prometheus 指标：mcp_connection_status（连接状态） | P0 |
| NFR-4.3-04 | 日志记录每个 MCP 请求的详细过程（DEBUG 级别） | P0 |
| NFR-4.3-05 | 日志记录连接建立/断开事件（INFO 级别） | P0 |

### 4.4 兼容性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-4.4-01 | 与 Agent 模块 v1.0.1 完全兼容 | P0 |
| NFR-4.4-02 | 与消息总线模块完全兼容 | P0 |
| NFR-4.4-03 | Python 3.11+ | P0 |

---

## 五、数据模型

### 5.1 MCPServerConfig

```python
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any

@dataclass
class MCPServerConfig:
    id: str                          # 唯一标识
    name: str                        # 显示名称
    type: str                        # "stdio" 或 "sse"
    
    # stdio 类型
    command: Optional[List[str]] = None
    env: Dict[str, str] = field(default_factory=dict)
    
    # HTTP/SSE 类型
    url: Optional[str] = None
    headers: Dict[str, str] = field(default_factory=dict)
    
    # 映射配置
    mapping: "MCPServerMapping" = None
    
    # 连接配置
    timeout: float = 30.0
    retry_count: int = 3
    retry_backoff: float = 1.0

@dataclass
class MCPServerMapping:
    agent_id: str                   # 虚拟 Agent ID
    workspace: Optional[str] = None
    team: Optional[str] = None
    skills_prefix: str = ""          # tool 名称前缀
```

### 5.2 ToolDef

```python
from dataclasses import dataclass
from typing import Any, Dict, Optional

@dataclass
class ToolDef:
    name: str                        # 工具名称
    description: str                 # 工具描述
    input_schema: Dict[str, Any]     # 参数 JSON Schema
    original_name: str               # 原始名称（未加前缀）
```

### 5.3 AgentDef 扩展

```python
# AgentDef 新增字段
agent_type: str = "internal"         # "internal" 或 "mcp"
mcp_server_id: Optional[str] = None # MCP Server ID（仅 agent_type == "mcp" 时）
```

---

## 六、项目结构

```
src/
├── mcp/                            # MCP 集成模块（新增）
│   ├── __init__.py
│   ├── config.py                   # MCP 配置解析
│   ├── manager.py                  # MCPClientManager
│   ├── protocol.py                 # JSON-RPC 协议实现
│   ├── transport/
│   │   ├── __init__.py
│   │   ├── base.py                 # 传输层抽象
│   │   ├── stdio.py                # stdio 传输实现
│   │   └── sse.py                  # SSE 传输实现（后续）
│   ├── virtual_agent.py           # 虚拟 Agent 生成
│   └── exceptions.py               # MCP 异常定义
│
├── agent/                          # Agent 模块（改动）
│   ├── defs.py                     # AgentDef 增加 agent_type 字段
│   ├── registry.py                 # 支持 MCP 虚拟 Agent 注册
│   ├── spawner.py                  # MCP Agent 轻量句柄
│   ├── manager.py                  # 加载 MCP 配置
│   └── api.py                      # MCP Server 管理 API
│
└── main.py                         # 集成 MCPClientManager
```

---

## 七、依赖关系

### 7.1 模块依赖

```
┌──────────────┐     ┌──────────────┐
│     MCP      │────▶│    Agent     │
│   Module     │     │   Module     │
└──────────────┘     └──────────────┘
       │                    │
       │                    ▼
       │            ┌──────────────┐
       │            │     Bus     │
       │            │   Module     │
       │            └──────────────┘
       ▼                    │
┌──────────────┐             │
│   Security   │◀────────────┘
│   Module     │
└──────────────┘
```

### 7.2 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| pydantic | ≥2.5 | 数据验证与序列化 |
| httpx | ≥0.25 | HTTP 客户端（用于 SSE） |
| structlog | ≥24.1 | 结构化日志 |
| opentelemetry-api | ≥1.20 | 追踪埋点 |

---

## 八、优先级矩阵

### P0（必须完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P0-01 | MCP 配置解析（YAML + env 替换） | - | 低 |
| P0-02 | stdio 传输层实现 | - | 中 |
| P0-03 | JSON-RPC 协议实现 | P0-02 | 中 |
| P0-04 | MCPClientManager 接口 | P0-02, P0-03 | 中 |
| P0-05 | AgentDef 扩展字段 | - | 低 |
| P0-06 | 虚拟 Agent 动态注册 | P0-01, P0-04 | 中 |
| P0-07 | 主后端消息路由扩展 | P0-06 | 低 |
| P0-08 | 权限检查集成 | Agent Module | 低 |
| P0-09 | 审计日志集成 | Agent Module | 低 |
| P0-10 | Prometheus 指标暴露 | - | 低 |

### P1（应该完成）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P1-01 | SSE 传输层实现 | P0-03 | 高 |
| P1-02 | 自动重连机制 | P0-04 | 中 |
| P1-03 | 连接健康检查 | P0-04 | 低 |
| P1-04 | 审计日志级别配置 | P0-09 | 低 |

### P2（可以延后）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P2-01 | MCP resources 支持 | - | 高 |
| P2-02 | MCP prompts 支持 | - | 中 |
| P2-03 | 动态注册/发现 | - | 高 |

---

## 九、风险与应对

| 风险 | 概率 | 影响 | 应对策略 |
|------|------|------|----------|
| MCP Server 格式不标准 | 中 | 中 | 实现容错解析，记录日志；提供验证工具 |
| 第三方服务不稳定 | 中 | 中 | 隔离设计，限流保护；详细错误日志 |
| 凭证泄露风险 | 低 | 高 | 凭证不落盘，环境变量传递；安全模块集成 |
| stdio 并发能力有限 | 中 | 中 | HTTP Server 支持并发；提供性能对比文档 |
| JSON-RPC 版本兼容 | 低 | 中 | 严格遵循 JSON-RPC 2.0 规范 |

---

## 十、验收标准

### 10.1 功能验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| UC-01 | 基础调用 | 启动一个简单的 MCP Server（如 echo），通过秘书 Agent 调用成功并返回正确结果 |
| UC-02 | 配置加载 | 主后端启动时正确加载 mcp_servers.yaml 并连接所有 Server |
| UC-03 | 虚拟 Agent 注册 | 在 AgentRegistry 中可查询到动态生成的 MCP AgentDef |
| UC-04 | 凭证安全 | 配置中的 ${VAR} 引用正确从环境变量/安全模块替换 |
| UC-05 | 工具映射 | MCP Server 的 tools/list 结果正确映射为 AgentDef.skills |

### 10.2 集成验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| IC-01 | 权限校验 | 配置策略禁止某秘书 Agent 调用该 MCP Agent，验证请求被拒绝 |
| IC-02 | 审计日志 | 调用后检查审计表中是否有对应记录（agent_id, tool, params, result, duration） |
| IC-03 | 故障恢复 | 手动杀死 MCP Server 子进程，验证主后端自动重启并恢复服务 |
| IC-04 | 并发调用 | 同时发送多个请求到同一个 stdio MCP Server，验证串行排队 |
| IC-05 | 超时处理 | MCP 调用超时后返回明确错误，不阻塞其他请求 |
| IC-06 | 优雅关闭 | 主后端关闭时，所有 MCP 连接正确断开，子进程被终止 |

### 10.3 可观测性验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| OC-01 | 指标暴露 | /metrics 端点可查询 mcp_call_total, mcp_call_duration_seconds, mcp_connection_status |
| OC-02 | 日志记录 | DEBUG 级别可查看完整的 JSON-RPC 请求/响应 |
| OC-03 | 连接事件 | 连接建立/断开/重连事件有 INFO 日志 |

---

## 十一、实施计划

| 阶段 | 任务 | 预计工时 | 交付物 |
|------|------|----------|--------|
| 1 | 设计 MCPClientManager 接口，实现 stdio 传输 | 2 人天 | MCP 客户端核心 |
| 2 | 实现配置解析与虚拟 Agent 动态注册 | 1 人天 | 配置加载、Agent 注册 |
| 3 | 修改主后端消息路由，集成 MCP 调用 | 1 人天 | 路由扩展 |
| 4 | 增加权限检查与审计日志 | 1 人天 | 安全集成 |
| 5 | 暴露 Prometheus 指标 | 0.5 人天 | 指标埋点 |
| 6 | 编写测试用例与文档 | 1 人天 | 测试用例、用户文档 |
| **合计** | | **6.5 人天** | |

---

## 十二、后续扩展（二期）

| 功能 | 说明 | 优先级 |
|------|------|--------|
| MCP resources | 将外部数据源映射为 VFS 中的只读文件 | P1 |
| MCP prompts | 用于动态生成提示词 | P1 |
| 自动注册 | 通过 mDNS 或配置中心发现 MCP Server | P2 |
| 反向暴露 | 将 DPSK-OPC 内部 Agent 暴露为 MCP Server | P2 |
| HTTP 增强 | HTTP/SSE 传输的负载均衡和连接池 | P1 |

---

## 十三、附录

### 13.1 术语对照

| 术语 | 说明 |
|------|------|
| MCP | Model Context Protocol，Anthropic 提出的模型上下文协议 |
| Tool | MCP Server 提供的可调用函数 |
| Resource | MCP Server 提供的只读数据源（如文件、数据库） |
| stdio | 标准输入输出，MCP 支持的传输方式之一 |
| SSE | Server-Sent Events，MCP 支持的 HTTP 流式传输 |
| JSON-RPC | 基于 JSON 的远程过程调用协议 |

### 13.2 MCP 协议参考

- **MCP 规范版本**：v1.0
- **JSON-RPC 版本**：2.0
- **传输要求**：stdio 必须支持 JSON-RPC 2.0 over stdin/stdout

### 13.3 参考文档

- [Agent 模块需求文档](../backend/v1.0.1/PRD_v1.0.1.md)
- [消息总线开发总结](../backend/v1.0.0/summary_v1.0.0.md)
