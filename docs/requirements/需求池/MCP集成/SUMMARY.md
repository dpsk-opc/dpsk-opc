# MCP 集成需求文档摘要

> **版本**: v1.0.2  
> **创建日期**: 2026-04-09  
> **作者**: DPSK-OPC 架构组

---

## 一、版本概述

MCP（Model Context Protocol）集成模块用于将符合 MCP 协议的第三方服务接入 DPSK-OPC 系统，实现外部工具能力的统一管理和调度。

### 核心价值

- **生态扩展**：无缝接入 Anthropic 官方及社区的 MCP Server
- **安全可控**：凭证加密存储，权限网关统一管控
- **统一调度**：MCP Agent 与内部 Agent 使用相同的通信机制
- **可观测**：Prometheus 指标 + 全链路审计日志

---

## 二、功能范围

### 2.1 必须完成（P0）

| 功能 | 说明 |
|------|------|
| MCP 配置管理 | mcp_servers.yaml + 环境变量/密钥替换 |
| stdio 传输 | 子进程 + JSON-RPC 协议 |
| MCPClientManager | 统一客户端接口 |
| 虚拟 Agent 注册 | 动态生成 AgentDef 并注入 Registry |
| 消息路由扩展 | agent_type 分支判断 |
| 权限与审计 | 与现有安全模块集成 |

### 2.2 应该完成（P1）

| 功能 | 说明 |
|------|------|
| SSE 传输 | HTTP 长连接支持 |
| 自动重连 | 指数退避，最多 3 次 |
| 健康检查 | 定期 ping 检测连接 |

### 2.3 可以延后（P2）

| 功能 | 说明 |
|------|------|
| MCP resources | 外部数据源映射 |
| MCP prompts | 动态提示词 |
| 动态注册 | mDNS 发现 |

---

## 三、架构设计

### 3.1 模块位置

```
主后端
  ├── AgentManager ──▶ AgentRegistry
  ├── MCPClientManager ──▶ MCP Servers (stdio/HTTP)
  └── Bus ──▶ Agent 通信
```

### 3.2 两种集成模式

| 模式 | 说明 |
|------|------|
| **Agent 模式** | MCP Server → 虚拟 Agent（优先实现） |
| **Skill 模式** | MCP Server → 多个 Skill（一期作为简化形式） |

---

## 四、关键数据流

**MCP Agent 调用流程**：

```
秘书 Agent ──TaskRequest──▶ Bus ──▶ 主后端路由
                                       │
                              agent_type == "mcp"
                                       │
                                       ▼
                          MCPClientManager.call_tool()
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
               stdio 传输                          HTTP/SSE 传输
                    │                                     │
                    ▼                                     ▼
            MCP Server 子进程                     MCP Server HTTP API
```

---

## 五、项目结构

```
src/mcp/
├── __init__.py
├── config.py                   # 配置解析
├── manager.py                  # MCPClientManager
├── protocol.py                 # JSON-RPC 实现
├── transport/
│   ├── base.py                 # 传输层抽象
│   ├── stdio.py                # stdio 传输
│   └── sse.py                  # SSE 传输（后续）
├── virtual_agent.py            # 虚拟 Agent 生成
└── exceptions.py               # 异常定义
```

---

## 六、依赖关系

| 依赖 | 说明 |
|------|------|
| Agent Module | MCP Agent 注册到 Registry |
| Bus Module | 消息通信 |
| Security Module | 凭证解密 |
| 消息总线 | 统一消息格式 |

---

## 七、验收标准

| 编号 | 用例 |
|------|------|
| UC-01 | MCP Server 基础调用成功 |
| UC-02 | 配置加载与凭证替换 |
| UC-03 | 虚拟 Agent 注册到 Registry |
| IC-01 | 权限检查拒绝未授权调用 |
| IC-02 | 审计日志记录完整 |
| IC-03 | 子进程崩溃后自动重启 |
| OC-01 | Prometheus 指标正常暴露 |

---

## 八、风险评估

| 风险 | 应对 |
|------|------|
| 第三方服务不稳定 | 隔离设计 + 限流保护 |
| 凭证泄露 | 环境变量传递 + 不落盘 |
| stdio 并发有限 | HTTP Server 支持并发 |

---

## 九、实施计划

| 阶段 | 任务 | 工时 |
|------|------|------|
| 1 | stdio 传输 + JSON-RPC | 2 人天 |
| 2 | 虚拟 Agent 注册 | 1 人天 |
| 3 | 消息路由扩展 | 1 人天 |
| 4 | 权限与审计集成 | 1 人天 |
| 5 | 指标暴露 + 测试 | 1.5 人天 |
| **合计** | | **6.5 人天** |

---

## 十、文档清单

| 文档 | 说明 |
|------|------|
| PRD_v1.0.2.md | 完整需求规范 |
| TASKS.md | 开发任务拆解 |
| SUMMARY.md | 本文档 |

---

## 十一、版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0.2 | 2026-04-09 | 初始版本 |
