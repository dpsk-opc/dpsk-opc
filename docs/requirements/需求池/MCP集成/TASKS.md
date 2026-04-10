# MCP 集成开发任务清单

> **版本**: v1.0.2  
> **更新时间**: 2026-04-09

---

## Phase 1: 核心传输层

### Task 1.1: MCP 配置解析

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/config.py` |
| **依赖** | 无 |
| **任务项** | |

- [ ] 定义 `MCPServerConfig` 数据类
- [ ] 定义 `MCPServerMapping` 数据类
- [ ] 实现 YAML 配置文件加载
- [ ] 实现 `${VAR}` 环境变量替换
- [ ] 实现安全模块密钥替换（调用 Security API）
- [ ] 配置验证（Pydantic）

### Task 1.2: 传输层抽象

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/transport/base.py` |
| **依赖** | Task 1.1 |
| **任务项** | |

- [ ] 定义 `Transport` 抽象基类
- [ ] 定义 `send(request)` 方法
- [ ] 定义 `recv()` 方法
- [ ] 定义 `close()` 方法
- [ ] 定义连接状态枚举

### Task 1.3: stdio 传输实现

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/transport/stdio.py` |
| **依赖** | Task 1.2 |
| **任务项** | |

- [ ] 实现 `StdioTransport` 类
- [ ] 实现子进程启动（asyncio.subprocess）
- [ ] 实现 JSON-RPC 请求发送（stdout）
- [ ] 实现 JSON-RPC 响应接收（stdin）
- [ ] 实现多请求并发处理（使用 Task 队列）
- [ ] 实现心跳机制（ping/pong）
- [ ] 实现优雅关闭（SIGTERM + 等待退出）
- [ ] 实现强制关闭（超时后 SIGKILL）

### Task 1.4: JSON-RPC 协议实现

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/protocol.py` |
| **依赖** | Task 1.3 |
| **任务项** | |

- [ ] 定义 JSON-RPC 请求/响应数据结构
- [ ] 实现请求序列化（to_bytes）
- [ ] 实现响应反序列化（from_bytes）
- [ ] 实现错误响应封装
- [ ] 实现批量请求支持
- [ ] 实现 id 自动生成器

### Task 1.5: 异常定义

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/exceptions.py` |
| **依赖** | 无 |
| **任务项** | |

- [ ] 定义 `MCPError` 基类
- [ ] 定义 `ConnectionError`
- [ ] 定义 `TimeoutError`
- [ ] 定义 `ProtocolError`
- [ ] 定义 `ToolNotFoundError`
- [ ] 定义 `InvalidParamsError`

---

## Phase 2: MCP 客户端管理

### Task 2.1: MCPClientManager 核心

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/manager.py` |
| **依赖** | Task 1.3, Task 1.4 |
| **任务项** | |

- [ ] 定义 `MCPClientManager` 类
- [ ] 实现 `connect(server_id, config)` 方法
- [ ] 实现 `disconnect(server_id)` 方法
- [ ] 实现 `disconnect_all()` 方法
- [ ] 实现 `get_status(server_id)` 方法
- [ ] 实现连接池管理
- [ ] 实现自动重连机制（指数退避）

### Task 2.2: 工具操作

| 项目 | 内容 |
|------|------|
| **依赖** | Task 2.1 |
| **任务项** | |

- [ ] 实现 `list_tools(server_id)` 方法
- [ ] 实现 `call_tool(server_id, tool_name, arguments)` 方法
- [ ] 实现超时控制（可配置）
- [ ] 实现错误映射（MCP 错误 → MCPError）
- [ ] 实现重试逻辑（1 次，tool 不存在时不重试）

---

## Phase 3: 虚拟 Agent 集成

### Task 3.1: 虚拟 Agent 生成

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/virtual_agent.py` |
| **依赖** | Task 2.2 |
| **任务项** | |

- [ ] 定义 `ToolDef` 数据类
- [ ] 实现 `generate_agent_def(server_config, tools)` 函数
- [ ] 实现 skills 前缀处理
- [ ] 实现 tools/list → AgentDef.skills 映射

### Task 3.2: AgentDef 扩展

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/defs.py` |
| **依赖** | 无 |
| **任务项** | |

- [ ] AgentDef 增加 `agent_type` 字段（默认 "internal"）
- [ ] AgentDef 增加 `mcp_server_id` 字段
- [ ] 更新 Pydantic 验证

### Task 3.3: AgentRegistry 扩展

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/registry.py` |
| **依赖** | Task 3.1, Task 3.2 |
| **任务项** | |

- [ ] 实现 `register_mcp_agent(agent_def)` 方法
- [ ] 实现 MCP Agent 查询优化
- [ ] 实现 agent_type 过滤

### Task 3.4: AgentSpawner 扩展

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/spawner.py` |
| **依赖** | Task 3.2 |
| **任务项** | |

- [ ] spawn 方法对 `agent_type == "mcp"` 返回轻量句柄
- [ ] 实现 `MCPAgentHandle` 类（不创建沙箱）
- [ ] 实现 destroy 方法（仅标记为不可用）

---

## Phase 4: 主后端集成

### Task 4.1: AgentManager 扩展

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/manager.py` |
| **依赖** | Task 3.3 |
| **任务项** | |

- [ ] 启动时加载 MCP 配置文件
- [ ] 初始化 MCPClientManager
- [ ] 连接所有 MCP Server
- [ ] 注册虚拟 Agent 到 Registry
- [ ] 关闭时断开所有 MCP 连接

### Task 4.2: 消息路由扩展

| 项目 | 内容 |
|------|------|
| **文件** | `src/core/router.py`（或消息处理模块） |
| **依赖** | Task 4.1 |
| **任务项** | |

- [ ] 实现 agent_type 分支判断
- [ ] internal Agent 走原有沙箱路径
- [ ] mcp Agent 走 MCPClientManager
- [ ] 实现结果封装（TaskResponse）

### Task 4.3: 凭证安全集成

| 项目 | 内容 |
|------|------|
| **依赖** | Security Module |
| **任务项** | |

- [ ] 调用安全模块 API 解密密钥
- [ ] 实现环境变量安全注入
- [ ] HTTP headers 凭证内存管理

---

## Phase 5: 安全与观测

### Task 5.1: 权限检查集成

| 项目 | 内容 |
|------|------|
| **依赖** | Security Module, Task 4.2 |
| **任务项** | |

- [ ] 调用权限网关校验 MCP Agent 调用权限
- [ ] 传入 agent_id、skill、caller_context
- [ ] 权限拒绝时返回明确错误

### Task 5.2: 审计日志集成

| 项目 | 内容 |
|------|------|
| **依赖** | Task 4.2 |
| **任务项** | |

- [ ] MCP 调用前记录审计事件
- [ ] MCP 调用后记录结果（可配置脱敏）
- [ ] 记录耗时、成功率
- [ ] 实现日志级别配置

### Task 5.3: Prometheus 指标

| 项目 | 内容 |
|------|------|
| **依赖** | Task 4.2 |
| **任务项** | |

- [ ] 实现 `mcp_call_total` 计数器
- [ ] 实现 `mcp_call_duration_seconds` 直方图
- [ ] 实现 `mcp_connection_status` 状态 Gauge
- [ ] 暴露 /metrics 端点

### Task 5.4: 日志增强

| 项目 | 内容 |
|------|------|
| **依赖** | Task 4.2 |
| **任务项** | |

- [ ] 连接建立/断开日志（INFO）
- [ ] JSON-RPC 请求/响应日志（DEBUG）
- [ ] 错误日志包含完整堆栈
- [ ] 重连事件日志

---

## Phase 6: SSE 传输（后续扩展）

### Task 6.1: SSE 传输实现

| 项目 | 内容 |
|------|------|
| **文件** | `src/mcp/transport/sse.py` |
| **依赖** | Task 1.2 |
| **任务项** | |

- [ ] 实现 `SSETransport` 类
- [ ] 实现 HTTP 连接建立
- [ ] 实现 Server-Sent Events 接收
- [ ] 实现 HTTP POST 请求发送
- [ ] 实现连接复用（Keep-Alive）

---

## Phase 7: 测试

### Task 7.1: 单元测试

| 项目 | 内容 |
|------|------|
| **目录** | `tests/mcp/` |
| **任务项** | |

- [ ] `test_config.py` - 配置解析测试
- [ ] `test_protocol.py` - JSON-RPC 测试
- [ ] `test_stdio_transport.py` - stdio 传输测试（Mock 子进程）
- [ ] `test_manager.py` - Manager 测试
- [ ] `test_virtual_agent.py` - 虚拟 Agent 测试

### Task 7.2: 集成测试

| 项目 | 内容 |
|------|------|
| **任务项** | |

- [ ] Mock MCP Server 测试
- [ ] 端到端调用测试
- [ ] 权限检查测试
- [ ] 审计日志测试
- [ ] 故障恢复测试（子进程崩溃）

### Task 7.3: 性能测试

| 项目 | 内容 |
|------|------|
| **任务项** | |

- [ ] stdio 并发能力测试
- [ ] 延迟基准测试
- [ ] 内存使用测试

---

## 进度跟踪

| Phase | Task | 功能 | 状态 | 完成日期 |
|-------|------|------|------|----------|
| 1.1 | 配置解析 | MCPServerConfig + YAML 加载 | ⬜ | - |
| 1.2 | 传输层抽象 | Transport 基类 | ⬜ | - |
| 1.3 | stdio 传输 | StdioTransport | ⬜ | - |
| 1.4 | JSON-RPC | 协议实现 | ⬜ | - |
| 1.5 | 异常定义 | MCPError 系列 | ⬜ | - |
| 2.1 | MCPClientManager | 核心功能 | ⬜ | - |
| 2.2 | 工具操作 | list_tools + call_tool | ⬜ | - |
| 3.1 | 虚拟 Agent | ToolDef + 生成逻辑 | ⬜ | - |
| 3.2 | AgentDef 扩展 | 新增字段 | ⬜ | - |
| 3.3 | Registry 扩展 | MCP 注册 | ⬜ | - |
| 3.4 | Spawner 扩展 | MCPAgentHandle | ⬜ | - |
| 4.1 | AgentManager | MCP 集成启动 | ⬜ | - |
| 4.2 | 消息路由 | 类型分支 | ⬜ | - |
| 4.3 | 凭证安全 | 密钥注入 | ⬜ | - |
| 5.1 | 权限检查 | 权限网关集成 | ⬜ | - |
| 5.2 | 审计日志 | 事件记录 | ⬜ | - |
| 5.3 | Prometheus | 指标暴露 | ⬜ | - |
| 5.4 | 日志增强 | 结构化日志 | ⬜ | - |
| 6.1 | SSE 传输 | HTTP/SSE | ⬜ | - |
| 7.1 | 单元测试 | 各模块测试 | ⬜ | - |
| 7.2 | 集成测试 | 端到端测试 | ⬜ | - |
| 7.3 | 性能测试 | 基准测试 | ⬜ | - |

---

## 里程碑

| 里程碑 | 包含任务 | 预计日期 |
|--------|----------|----------|
| M1: 传输层就绪 | 1.1 - 1.5 | - |
| M2: MCP 客户端 | 2.1 - 2.2 | - |
| M3: 虚拟 Agent | 3.1 - 3.4 | - |
| M4: 主后端集成 | 4.1 - 4.3 | - |
| M5: 安全与观测 | 5.1 - 5.4 | - |
| M6: 测试完成 | 7.1 - 7.3 | - |
| **v1.0.2 交付** | M1 + M2 + M3 + M4 + M5 + 7.1 + 7.2 | - |
