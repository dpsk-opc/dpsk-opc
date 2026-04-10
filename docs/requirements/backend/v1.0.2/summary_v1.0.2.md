# OPC-Client 模块需求文档摘要

> **版本**: v1.0.2  
> **创建日期**: 2026-04-10  
> **作者**: DPSK-OPC 架构组

---

## 一、版本概述

OPC-Client（Orchestration Pipeline Client，v1.0.2）是 DPSK-OPC 后端系统的"运行内核"，负责串联安全模块、通信模块、Agent 管理模块和大模型接入模块，形成完整的端到端能力。

**核心价值**：

- **统一入口**：隐藏内部复杂调度细节，提供简洁的用户请求接口
- **智能编排**：利用大模型自动生成工作流，按依赖关系并行调度任务
- **模块解耦**：通过抽象接口实现模块间的松耦合，便于测试和扩展
- **全链路追踪**：结构化日志覆盖工作流生成到任务执行的全过程

---

## 二、功能范围

### 2.1 必须完成（P0）

| 功能模块 | 功能点 | 说明 |
|----------|--------|------|
| 工作流抽象 | WorkflowTask, Workflow 数据结构 | 完整的任务与工作流定义 |
| 工作流抽象 | 工作流校验 | ID 唯一性、循环检测、Agent 存在性 |
| Agent 调用 | AgentInvoker 协议 | 抽象调用接口 |
| Agent 调用 | LocalAgentInvoker | 本地 Agent 调用实现 |
| Agent 调用 | 安全校验集成 | 执行前调用 validate_invocation |
| 任务调度 | ParallelDependencyStrategy | 依赖解析与并行调度 |
| 任务调度 | 循环依赖检测 | 拓扑排序算法 |
| 主流程 | OPCClient.run() | 单一入口方法 |
| 主流程 | 执行流程 | 注册中心 → 工作流生成 → 安全校验 → 执行 → 汇总 |
| 管理 API | REST 接口 | /api/v1/opc/* |

### 2.2 应该完成（P1）

| 功能模块 | 功能点 | 说明 |
|----------|--------|------|
| 工作流抽象 | 静态工作流加载 | JSON/YAML 文件加载 |
| 工作流生成 | LLMWorkflowProvider | 大模型驱动的动态生成 |
| 工作流生成 | 重试机制 | JSON 解析错误重试 |
| 任务调度 | 错误处理策略 | 停止/跳过/重试配置 |
| 主流程 | 汇总 Agent | 结果二次加工 |
| 主流程 | 超时控制 | 最大执行时间限制 |
| 主流程 | 流式进度推送 | run_with_progress |

### 2.3 可以延后（P2）

| 功能模块 | 功能点 | 说明 |
|----------|--------|------|
| Agent 调用 | A2AAgentInvoker | A2A 协议远程调用 |
| 可靠性 | 断点续跑 | 意外中断后继续执行 |

---

## 三、技术架构

### 3.1 模块分层

```
┌─────────────────────────────────────────────────────────┐
│                      API 层                              │
│               (/api/v1/opc/*)                            │
├─────────────────────────────────────────────────────────┤
│                    OPCClient                            │
│         (主流程编排 / 依赖注入 / 超时控制)                │
├─────────────────────────────────────────────────────────┤
│    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│    │  Workflow   │  │   Agent     │  │  Execution  │    │
│    │  Provider   │  │  Invoker    │  │  Strategy   │    │
│    └─────────────┘  └─────────────┘  └─────────────┘    │
├─────────────────────────────────────────────────────────┤
│   ┌──────────┐   ┌──────────┐   ┌──────────┐           │
│   │ Registry │   │ Security │   │  Logger   │           │
│   └──────────┘   └──────────┘   └──────────┘           │
└─────────────────────────────────────────────────────────┘
```

### 3.2 关键数据流

**用户请求处理**：
```
用户请求 → OPCClient.run()
  → 注册中心获取 Agent 列表
  → WorkflowProvider 生成工作流
  → 安全模块校验工作流
  → ExecutionStrategy 执行任务
    → AgentInvoker.invoke()
      → 安全模块校验调用
      → Agent 执行任务
      → 返回 TaskResult
  → 汇总结果
  → 返回 FinalResult
```

---

## 四、项目结构

```
src/opc/
├── __init__.py
├── client.py                 # OPCClient 主类
├── config.py                 # OPCConfig 配置
├── models/
│   ├── __init__.py
│   ├── workflow.py           # Workflow, WorkflowTask
│   ├── result.py             # TaskResult, FinalResult
│   └── events.py             # ProgressEvent
├── providers/
│   ├── __init__.py
│   ├── base.py               # WorkflowProvider 基类
│   ├── llm_provider.py       # LLMWorkflowProvider
│   └── static_provider.py    # StaticFileWorkflowProvider
├── invokers/
│   ├── __init__.py
│   ├── base.py               # AgentInvoker 基类
│   ├── local_invoker.py      # LocalAgentInvoker
│   └── a2a_invoker.py        # A2AAgentInvoker（预留）
├── strategies/
│   ├── __init__.py
│   ├── base.py               # ExecutionStrategy 基类
│   └── parallel_strategy.py  # ParallelDependencyStrategy
├── errors.py                  # 自定义异常
└── api.py                    # FastAPI 路由
```

---

## 五、依赖关系

### 5.1 内部模块依赖

| 模块 | 依赖 | 依赖方向 |
|------|------|----------|
| OPC-Client | Agent Module | 通过 AgentInvoker 调用 Agent |
| OPC-Client | Registry | 获取可用 Agent 列表 |
| OPC-Client | Security Module | 校验工作流和任务调用 |
| OPC-Client | Bus Module | AgentInvoker 内部使用 |
| OPC-Client | LLM Service | LLMWorkflowProvider 使用 |
| OPC-Client | Logger | 全链路日志 |

### 5.2 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| pydantic | ≥2.5 | 数据验证与序列化 |
| structlog | ≥24.1 | 结构化日志 |
| aiofiles | ≥23.0 | 异步文件操作 |
| pyyaml | ≥6.0 | YAML 解析 |

---

## 六、验收标准

### 6.1 功能验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| UC-01 | 工作流校验 | 循环依赖的工作流被正确拒绝 |
| UC-02 | 静态工作流执行 | 从 JSON 加载的工作流能正确按依赖顺序执行 |
| UC-03 | 并行执行 | 无依赖关系的任务能并行执行 |
| UC-04 | 顺序执行 | 有依赖关系的任务必须等待前置任务完成后执行 |
| UC-05 | 失败处理 | 任务失败时根据配置策略处理后续任务 |
| UC-06 | LLM 生成 | 自然语言请求能生成有效工作流并执行 |
| UC-07 | 日志追踪 | trace_id 能串联整个请求的生命周期 |

### 6.2 集成验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| IC-01 | 注册中心集成 | OPCClient 能正确获取可用 Agent 列表 |
| IC-02 | 安全模块集成 | 任务执行前能正确调用安全校验 |
| IC-03 | Agent 模块集成 | 能正确调用 Agent 执行任务并获取结果 |

---

## 七、风险评估

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| 大模型 JSON 格式不稳定 | 中 | 高 | Schema 校验 + 重试 + Few-Shot |
| 循环依赖检测不完整 | 低 | 中 | 拓扑排序 + 单元测试覆盖 |
| 并发安全问题 | 低 | 高 | 异步锁保护 + 结果独立存储 |
| 接口不匹配 | 低 | 中 | 协议文档 + Mock 测试 |

---

## 八、开发建议

### 8.1 实现顺序

1. **Phase 1（核心骨架）**：数据结构 → LocalAgentInvoker → ParallelDependencyStrategy → OPCClient 框架
2. **Phase 2（LLM 集成）**：LLMWorkflowProvider → Prompt 模板 → JSON 校验
3. **Phase 3（完善）**：错误策略 → 超时控制 → 流式进度 → 日志完善
4. **Phase 4（A2A）**：A2AAgentInvoker → 混合编排（按需）

### 8.2 重点关注

- **抽象接口**：WorkflowProvider、AgentInvoker、ExecutionStrategy 三大接口设计必须清晰
- **安全集成**：每个任务执行前必须通过安全模块校验
- **可测试性**：使用 Mock 实现独立测试各组件

---

## 九、文档清单

| 文档 | 路径 | 说明 |
|------|------|------|
| 需求文档 | `DPSK-OPC OPC-Client 模块需求文档.md` | 完整需求规范 |
| 任务清单 | `tasks/TASKS.md` | 开发任务拆解 |
| 本文档 | `summary_v1.0.2.md` | 需求摘要 |

---

## 十、版本历史

| 版本 | 日期 | 作者 | 变更 |
|------|------|------|------|
| v1.0.2 | 2026-04-10 | DPSK-OPC 架构组 | 初始版本 |
