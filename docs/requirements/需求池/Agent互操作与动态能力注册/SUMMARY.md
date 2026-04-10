# Agent 互操作与动态能力注册需求总结

> **版本**: v1.0.2  
> **创建日期**: 2026-04-10

---

## 需求概述

本需求文档定义了 DPSK-OPC Agent 模块的互操作与动态能力注册升级方案，核心目标是：

1. **Agent Card 自动生成**：Agent 启动时自动扫描并生成符合 A2A 规范的 Agent Card
2. **注册中心**：提供 Agent Card 的统一存储、查询、健康检查服务
3. **A2A 通信协议**：实现标准化的 Agent 间 JSON-RPC 2.0 通信
4. **调度自动化**：调度节点动态感知子 Agent 能力，自动生成调度策略

## 核心概念

| 概念 | 说明 |
|------|------|
| Agent Card | Agent 的标准化数字身份证，包含能力声明 |
| Agent Card 即代码 | Agent Card 由代码自动生成，而非手工维护 |
| 注册中心（Registry） | Agent Card 的统一存储/查询/健康检查服务 |
| A2A 协议 | Agent-to-Agent 通信开放协议，基于 JSON-RPC 2.0 |

## 实施路线图

| 阶段 | 主要内容 | 预计工时 |
|------|----------|----------|
| Phase 1 | 基础能力升级（Card 生成 + 注册中心 + 调度感知） | 10 人天 |
| Phase 2 | A2A 协议实现（HTTP Server + Client + 任务派发） | 12 人天 |
| Phase 3 | 高级特性（版本管理 + 认证 + 迁移工具） | 10 人天 |
| **合计** | | **32 人天** |

## 关键交付物

| 交付物 | 说明 |
|--------|------|
| AgentCard Generator | 自动扫描 Agent 的 Tools/Skills，生成标准化 Card |
| Registry Service | Agent Card 的注册/查询/健康检查服务 |
| A2A Server | Agent 的 HTTP 服务端，实现 JSON-RPC 协议 |
| A2A Client | Agent 的任务派发客户端封装 |
| Task Manager | 任务状态管理与结果存储 |
| 动态 Prompt | 调度节点根据子 Agent 能力自动生成的 System Prompt |

## 验收标准

### Phase 1

- [ ] Agent 启动后可在注册中心查询到自动生成的 Agent Card
- [ ] Agent Card 中的 skills 列表与实际注册的 Skill 一致
- [ ] 秘书 Agent 能从注册中心获取子 Agent 列表
- [ ] 秘书 Agent 的 System Prompt 包含子 Agent 能力描述
- [ ] 注册中心不可用时，系统降级使用本地缓存

### Phase 2

- [ ] 秘书 Agent 可通过 A2A 协议向子 Agent 发送任务
- [ ] 支持任务状态查询和 SSE 流式推送
- [ ] 支持任务取消

## 依赖关系

- 本需求依赖 Agent 模块 v1.0.1
- 本需求与 MCP 集成需求 v1.0.2 并行，可复用部分基础设施

## 文档列表

- [PRD_v1.0.2.md](./PRD_v1.0.2.md) - 完整需求文档
- [TASKS.md](./TASKS.md) - 详细任务清单
