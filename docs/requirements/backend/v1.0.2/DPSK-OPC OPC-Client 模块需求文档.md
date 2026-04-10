# DPSK-OPC OPC-Client 模块需求文档

> **版本**: v1.0.2  
> **创建日期**: 2026-04-10  
> **作者**: DPSK-OPC 架构组  
> **状态**: 待开发

---

## 一、需求概述

### 1.1 背景与目标

在完成安全模块（v1.0.x）、通信模块（v1.0.0）、Agent 管理模块（v1.0.1）和大模型接入模块的独立建设后，系统需要一个统一的上层驱动程序来串联这些组件，形成完整的端到端能力。

**用户期望的交互流程**：
```
输入自然语言请求 → 系统自动拆解为带依赖关系的工作流 → 调度各 Agent 执行 → 汇总并返回结果
```

**核心目标**：

| 目标 | 说明 |
|------|------|
| 统一入口 | 提供唯一的用户请求入口，隐藏内部复杂调度细节 |
| 动态工作流生成 | 利用大模型，根据用户请求和可用 Agent 能力，自动生成带依赖关系的工作流定义 |
| 可靠的任务编排 | 解析工作流依赖，以最优并行策略调度任务执行 |
| 模块解耦 | 通过抽象接口与安全、通信、注册中心等模块交互，便于替换和测试 |
| 可观测性 | 全流程埋点，记录工作流生成、任务执行状态及耗时 |

### 1.2 适用范围

本文档描述 OPC-Client 模块的完整需求，包括：

- 核心概念与数据模型
- 工作流抽象与解析
- 大模型驱动的工作流生成
- Agent 调用抽象
- 依赖感知的任务调度
- 主流程驱动
- 与外部模块的集成
- 管理 API

### 1.3 术语表

| 术语 | 说明 |
|------|------|
| 工作流（Workflow） | 一组有依赖关系的任务集合，描述从用户请求到最终结果的执行计划 |
| 任务（Task） | 工作流中的最小执行单元，包含任务 ID、目标 Agent、任务描述、依赖列表等 |
| 工作流提供者（WorkflowProvider） | 负责生成工作流的抽象接口，可以从大模型、本地文件或远程服务获取 |
| Agent 调用者（AgentInvoker） | 负责向指定 Agent 发送任务并获取结果的抽象接口，屏蔽本地/远程调用差异 |
| 执行策略（ExecutionStrategy） | 定义如何根据依赖关系调度任务执行的算法（如并行就绪任务） |
| OPC-Client | Orchestration Pipeline Client，系统的运行内核 |

---

## 二、功能需求

### 2.1 工作流抽象与解析

#### 2.1.1 数据结构定义

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.1.1-01 | 定义 `WorkflowTask` 数据类，包含字段：id, agent, task, depends_on, context_hint | P0 |
| FR-2.1.1-02 | 定义 `Workflow` 数据类，包含字段：tasks, final_aggregator | P0 |
| FR-2.1.1-03 | 定义 `TaskResult` 数据类，包含字段：task_id, status, output, error, duration_ms | P0 |
| FR-2.1.1-04 | 定义 `TaskStatus` 枚举，包含：PENDING, RUNNING, COMPLETED, FAILED, SKIPPED | P0 |
| FR-2.1.1-05 | 支持 Pydantic 验证和数据序列化 | P0 |

#### 2.1.2 工作流校验

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.1.2-01 | 任务 ID 唯一性校验：同一工作流内任务 ID 不能重复 | P0 |
| FR-2.1.2-02 | 依赖关系无循环校验：使用拓扑排序检测循环依赖，抛出明确异常 | P0 |
| FR-2.1.2-03 | 目标 Agent 存在性校验：每个任务的 agent 必须在注册中心中存在 | P0 |
| FR-2.1.2-04 | 依赖引用的有效性校验：depends_on 中的任务 ID 必须在工作流中存在 | P0 |

#### 2.1.3 静态工作流加载

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.1.3-01 | 支持从 JSON 文件加载静态工作流 | P1 |
| FR-2.1.3-02 | 支持从 YAML 文件加载静态工作流 | P1 |
| FR-2.1.3-03 | 加载后自动执行工作流校验（FR-2.1.2） | P1 |

### 2.2 大模型驱动的工作流生成

#### 2.2.1 LLMWorkflowProvider

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.2.1-01 | 实现 `LLMWorkflowProvider`，调用大模型生成工作流 JSON | P0 |
| FR-2.2.1-02 | Prompt 动态注入注册中心提供的可用 Agent 列表及能力摘要 | P0 |
| FR-2.2.1-03 | 工作流 JSON 必须通过 FR-2.1.2 的校验，若校验失败返回明确错误 | P0 |
| FR-2.2.1-04 | 实现重试机制处理 JSON 解析错误（默认 3 次） | P1 |
| FR-2.2.1-05 | 支持配置大模型参数（温度、最大 token 等） | P1 |

#### 2.2.2 Prompt 设计

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.2.2-01 | 设计标准 Prompt 模板，包含系统指令、可用 Agent 描述、输出格式要求 | P0 |
| FR-2.2.2-02 | 支持 Few-Shot 示例，提高 JSON 输出稳定性 | P1 |
| FR-2.2.2-03 | 优雅降级：生成失败时降级为静态工作流或返回明确错误 | P0 |

### 2.3 Agent 调用抽象

#### 2.3.1 AgentInvoker 协议

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.1-01 | 定义 `AgentInvoker` 抽象协议 | P0 |
| FR-2.3.1-02 | `invoke(agent_id, task, context)` 方法签名 | P0 |
| FR-2.3.1-03 | 返回 `TaskResult` 类型结果 | P0 |

#### 2.3.2 LocalAgentInvoker

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.2-01 | 实现 `LocalAgentInvoker`，直接调用本地 Agent 实例 | P0 |
| FR-2.3.2-02 | 通过 AgentManager 获取 Agent 句柄并执行任务 | P0 |
| FR-2.3.2-03 | 封装任务调用的超时处理 | P0 |

#### 2.3.3 A2AAgentInvoker（预留）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.3-01 | 定义 `A2AAgentInvoker` 接口 | P2 |
| FR-2.3.3-02 | 支持通过 A2A 协议调用远程 Agent | P2 |

#### 2.3.4 安全校验

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.3.4-01 | AgentInvoker 在执行任务前调用安全模块的 `validate_invocation` 方法 | P0 |
| FR-2.3.4-02 | 校验失败时返回 FAILED 状态的 TaskResult | P0 |

### 2.4 依赖感知的任务调度

#### 2.4.1 ParallelDependencyStrategy

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.1-01 | 实现 `ParallelDependencyStrategy`，识别所有依赖已满足的"就绪任务" | P0 |
| FR-2.4.1-02 | 并行执行所有就绪任务 | P0 |
| FR-2.4.1-03 | 等待就绪任务完成后，更新依赖状态，识别下一批就绪任务 | P0 |
| FR-2.4.1-04 | 循环执行直到所有任务完成或失败 | P0 |

#### 2.4.2 循环依赖检测

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.2-01 | 执行前使用拓扑排序检测循环依赖 | P0 |
| FR-2.4.2-02 | 检测到循环时抛出 `CyclicDependencyError` 异常 | P0 |

#### 2.4.3 错误处理策略

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.3-01 | 支持配置任务失败时的处理策略 | P1 |
| FR-2.4.3-02 | 策略一：立即停止（STOP_ON_FAILURE） | P1 |
| FR-2.4.3-03 | 策略二：跳过后续依赖该任务的任务（SKIP_DEPENDENTS） | P1 |
| FR-2.4.3-04 | 策略三：重试指定次数（RETRY_N_TIMES） | P1 |
| FR-2.4.3-05 | 默认策略为 STOP_ON_FAILURE | P0 |

#### 2.4.4 日志记录

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.4.4-01 | 记录每个任务的状态变更：PENDING → RUNNING → COMPLETED / FAILED | P0 |
| FR-2.4.4-02 | 日志包含 trace_id 以串联同一次用户请求 | P0 |
| FR-2.4.4-03 | 记录任务开始时间、结束时间、持续时间 | P0 |

### 2.5 OPC-Client 核心

#### 2.5.1 OPCClient 主入口

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.5.1-01 | `OPCClient` 提供单一入口方法 `run(user_request)` | P0 |
| FR-2.5.1-02 | `run` 方法返回 `FinalResult`，包含所有任务结果 | P0 |
| FR-2.5.1-03 | 支持进度推送入口 `run_with_progress` 返回 `AsyncIterator[ProgressEvent]` | P1 |

#### 2.5.2 执行流程

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.5.2-01 | 阶段一：从注册中心拉取可用 Agent 列表 | P0 |
| FR-2.5.2-02 | 阶段二：调用 WorkflowProvider 生成工作流 | P0 |
| FR-2.5.2-03 | 阶段三：对工作流进行安全校验 | P0 |
| FR-2.5.2-04 | 阶段四：调用 ExecutionStrategy 执行工作流 | P0 |
| FR-2.5.2-05 | 阶段五：汇总任务结果，形成最终返回 | P0 |

#### 2.5.3 结果汇总

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.5.3-01 | 简单拼接模式：将所有任务结果按 ID 组装为字典返回 | P0 |
| FR-2.5.3-02 | 汇总 Agent 模式：指定一个 Agent 对结果进行二次加工 | P1 |
| FR-2.5.3-03 | 配置 `final_aggregator` 指定汇总 Agent ID | P1 |

#### 2.5.4 超时控制

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.5.4-01 | 支持配置最大执行时间 | P1 |
| FR-2.5.4-02 | 超时后优雅终止并返回超时错误 | P1 |

### 2.6 与外部模块的集成

#### 2.6.1 依赖注入

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.1-01 | 通过依赖注入方式接收 AgentRegistry、SecurityModule、Logger 实例 | P0 |
| FR-2.6.1-02 | 不直接硬编码依赖，实现模块解耦 | P0 |

#### 2.6.2 与注册中心交互

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.2-01 | 仅限于查询 Agent 列表和能力 | P0 |
| FR-2.6.2-02 | 不修改注册中心状态 | P0 |

#### 2.6.3 与安全模块交互

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.3-01 | 工作流生成后的整体校验 | P0 |
| FR-2.6.3-02 | 每个任务执行前的参数校验 | P0 |

#### 2.6.4 与通信模块交互

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.6.4-01 | 封装在 AgentInvoker 实现中 | P0 |
| FR-2.6.4-02 | OPC-Client 核心不直接依赖通信细节 | P0 |

### 2.7 管理 API

| 需求 | 描述 | 优先级 |
|------|------|--------|
| FR-2.7-01 | `POST /api/v1/opc/run`：执行用户请求 | P0 |
| FR-2.7-02 | `POST /api/v1/opc/run-stream`：流式执行并推送进度 | P1 |
| FR-2.7-03 | `GET /api/v1/opc/health`：健康检查 | P0 |

---

## 三、非功能需求

### 3.1 性能需求

| 需求 | 描述 | 目标 |
|------|------|------|
| NFR-3.1-01 | 典型工作流（4-5 个任务）从请求到开始执行第一个任务的延迟（不含 LLM 生成耗时） | < 100ms |
| NFR-3.1-02 | 支持并行执行的最大任务数 | ≥ 20 |
| NFR-3.1-03 | 单任务调用的超时配置 | 默认 300s，可配置 |

### 3.2 可靠性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.2-01 | 工作流执行过程中若发生意外中断，应能记录当前进度 | P0 |
| NFR-3.2-02 | 支持可选的断点续跑（Phase 2） | P2 |

### 3.3 可扩展性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.3-01 | 新增一种 WorkflowProvider 仅需实现对应接口，无需修改核心代码 | P0 |
| NFR-3.3-02 | 新增一种 ExecutionStrategy 仅需实现对应接口 | P0 |

### 3.4 可测试性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.4-01 | 通过接口抽象，各组件应能独立进行单元测试 | P0 |
| NFR-3.4-02 | 提供 Mock 实现便于测试 | P0 |

### 3.5 可观测性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.5-01 | 所有关键步骤（工作流生成、任务开始/结束、错误）必须输出结构化日志 | P0 |
| NFR-3.5-02 | 日志包含 trace_id 以串联同一次用户请求 | P0 |
| NFR-3.5-03 | 支持 trace_id 生成与传递 | P0 |

### 3.6 安全性需求

| 需求 | 描述 | 优先级 |
|------|------|--------|
| NFR-3.6-01 | 工作流定义中若包含不在注册中心内的 Agent 名称，必须拒绝执行并报错 | P0 |
| NFR-3.6-02 | 每个任务执行前必须通过安全模块校验 | P0 |

---

## 四、数据模型

### 4.1 WorkflowTask

```python
from dataclasses import dataclass, field
from typing import List, Optional
from enum import Enum

class TaskStatus(Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"

@dataclass
class WorkflowTask:
    id: str                      # 任务唯一 ID
    agent: str                   # 目标 Agent ID
    task: str                    # 任务描述/指令
    depends_on: List[str] = field(default_factory=list)  # 依赖的任务 ID 列表
    context_hint: Optional[str] = None  # 上下文传递提示
```

### 4.2 Workflow

```python
@dataclass
class Workflow:
    tasks: List[WorkflowTask]                 # 任务列表
    final_aggregator: Optional[str] = None   # 汇总 Agent ID（可选）
```

### 4.3 TaskResult

```python
@dataclass
class TaskResult:
    task_id: str                    # 对应任务 ID
    status: TaskStatus              # 执行状态
    output: Any = None              # 执行输出
    error: Optional[str] = None     # 错误信息
    duration_ms: int = 0            # 执行耗时（毫秒）
```

### 4.4 FinalResult

```python
@dataclass
class FinalResult:
    request: str                     # 用户原始请求
    workflow: Workflow               # 生成的工作流
    results: Dict[str, TaskResult]   # 任务 ID → 结果
    status: WorkflowStatus           # 整体状态
    total_duration_ms: int          # 总耗时
    trace_id: str                    # 追踪 ID
    error: Optional[str] = None     # 整体错误（如果有）
```

### 4.5 ProgressEvent

```python
@dataclass
class ProgressEvent:
    type: str                        # 事件类型：task_start, task_complete, task_failed, workflow_complete
    task_id: Optional[str] = None    # 关联任务 ID
    status: Optional[str] = None     # 状态描述
    data: Optional[Dict] = None     # 附加数据
    timestamp: float                 # 时间戳
```

---

## 五、项目结构

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
│   └── a2a_invoker.py         # A2AAgentInvoker（预留）
├── strategies/
│   ├── __init__.py
│   ├── base.py               # ExecutionStrategy 基类
│   └── parallel_strategy.py  # ParallelDependencyStrategy
├── errors.py                  # 自定义异常
└── api.py                     # FastAPI 路由
```

---

## 六、模块交互流程

```
┌─────────┐     ┌───────────────┐     ┌──────────────────┐
│  User   │────▶│   OPCClient   │────▶│ WorkflowProvider │
└─────────┘     └───────────────┘     └──────────────────┘
                      │                        │
                      │                        ▼
                      │               ┌──────────────────┐
                      │               │  AgentRegistry   │
                      │               └──────────────────┘
                      │
                      ▼
              ┌───────────────┐
              │   Security    │
              │   (校验)      │
              └───────────────┘
                      │
                      ▼
              ┌───────────────┐     ┌──────────────────┐
              │    Logger      │◀────│ ExecutionStrategy│
              └───────────────┘     └──────────────────┘
                                              │
                                              ▼
                                      ┌──────────────────┐
                                      │  AgentInvoker   │
                                      └──────────────────┘
                                              │
                                              ▼
                                      ┌──────────────────┐
                                      │  AgentManager   │
                                      └──────────────────┘
```

---

## 七、依赖关系

### 7.1 模块依赖

```
┌─────────────┐     ┌─────────────┐
│   OPC      │────▶│    Bus      │
│   Client   │     │   Module    │
└─────────────┘     └─────────────┘
       │                   │
       │                   │
       ▼                   ▼
┌─────────────┐     ┌─────────────┐
│  Security   │     │    Agent    │
│   Module    │────▶│   Module    │
└─────────────┘     └─────────────┘
       │                   │
       │                   │
       ▼                   ▼
┌─────────────┐     ┌─────────────┐
│    LLM      │     │  Registry   │
│   Service   │     │             │
└─────────────┘     └─────────────┘
```

### 7.2 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| pydantic | ≥2.5 | 数据验证与序列化 |
| structlog | ≥24.1 | 结构化日志 |
| aiofiles | ≥23.0 | 异步文件操作 |
| pyyaml | ≥6.0 | YAML 解析 |

---

## 八、实施路线图

### Phase 1：核心骨架与本地执行（预计 1.5 周）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P1-01 | 数据结构定义（Workflow, WorkflowTask, TaskResult） | - | 低 |
| P1-02 | LocalAgentInvoker 实现 | Agent Module | 中 |
| P1-03 | ParallelDependencyStrategy 实现 | P1-01 | 中 |
| P1-04 | StaticFileWorkflowProvider | P1-01 | 低 |
| P1-05 | OPCClient 主流程框架 | P1-02, P1-03, P1-04 | 中 |
| P1-06 | 集成测试（简单工作流 A→B→C） | P1-05 | 中 |

**验收标准**：
- 使用静态工作流文件，能够正确按依赖顺序调用本地 Agent 并返回汇总结果

### Phase 2：大模型工作流生成（预计 1.5 周）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P2-01 | LLMWorkflowProvider 实现 | P1-01 | 中 |
| P2-02 | Prompt 模板设计 | P2-01 | 中 |
| P2-03 | JSON 解析与校验 | P2-01 | 中 |
| P2-04 | 重试机制 | P2-03 | 低 |

**验收标准**：
- 输入自然语言请求，大模型能返回符合 Schema 的工作流，并被成功执行
- 模拟大模型返回格式错误时，系统能优雅降级并给出提示

### Phase 3：完善与生产特性（预计 1 周）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P3-01 | 错误处理策略配置 | Phase 1 | 低 |
| P3-02 | 超时控制 | Phase 1 | 低 |
| P3-03 | 流式进度推送 | Phase 1 | 中 |
| P3-04 | 结构化日志完善 | Phase 1 | 低 |

**验收标准**：
- 用户可通过进度事件实时感知工作流执行状态
- 超时或任务失败时，系统行为符合预期配置

### Phase 4：远程调用与 A2A 集成（按需）

| 编号 | 功能 | 依赖 | 复杂度 |
|------|------|------|--------|
| P4-01 | A2AAgentInvoker 实现 | Agent Module | 高 |
| P4-02 | 本地与远程 Agent 混合编排 | P4-01 | 高 |

---

## 九、风险与应对

| 风险 | 可能性 | 影响 | 应对措施 |
|------|--------|------|----------|
| 大模型生成的工作流 JSON 格式不稳定 | 中 | 高 | 实现严格的 Schema 校验 + 解析重试机制；在 Prompt 中提供 Few-Shot 示例；必要时降级为静态工作流 |
| 依赖循环未能完全检测 | 低 | 中 | 使用标准拓扑排序算法检测循环；单元测试覆盖常见循环场景 |
| 并行执行时的并发安全问题 | 低 | 高 | ExecutionStrategy 内部状态使用异步锁保护；任务执行结果按 ID 独立存储，无共享写 |
| 与安全模块、通信模块的接口不匹配 | 低 | 中 | 前期定义清晰的接口协议文档，各模块独立开发时遵守约定；通过集成测试及早发现问题 |

---

## 十、验收标准

### 10.1 功能验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| UC-01 | 工作流校验 | 循环依赖的工作流被正确拒绝 |
| UC-02 | 静态工作流执行 | 从 JSON 加载的工作流能正确按依赖顺序执行 |
| UC-03 | 并行执行 | 无依赖关系的任务能并行执行 |
| UC-04 | 顺序执行 | 有依赖关系的任务必须等待前置任务完成后执行 |
| UC-05 | 失败处理 | 任务失败时根据配置策略处理后续任务 |
| UC-06 | LLM 生成 | 自然语言请求能生成有效工作流并执行 |
| UC-07 | 日志追踪 | trace_id 能串联整个请求的生命周期 |

### 10.2 集成验收

| 编号 | 用例 | 验收条件 |
|------|------|----------|
| IC-01 | 注册中心集成 | OPCClient 能正确获取可用 Agent 列表 |
| IC-02 | 安全模块集成 | 任务执行前能正确调用安全校验 |
| IC-03 | Agent 模块集成 | 能正确调用 Agent 执行任务并获取结果 |

---

## 十一、附录

### 11.1 接口设计

#### OPCClient 公开接口

```python
class OPCClient:
    def __init__(
        self,
        workflow_provider: WorkflowProvider,
        agent_invoker: AgentInvoker,
        execution_strategy: ExecutionStrategy,
        registry: AgentRegistry,
        security: SecurityModule,
        logger: Logger,
        config: OPCConfig
    ):
        ...

    async def run(self, user_request: str) -> FinalResult:
        """主入口，返回最终结果"""
        ...

    async def run_with_progress(self, user_request: str) -> AsyncIterator[ProgressEvent]:
        """支持流式进度推送的入口"""
        ...
```

#### 核心抽象协议

```python
class WorkflowProvider(Protocol):
    async def generate(self, user_request: str, context: WorkflowContext) -> Workflow:
        ...

class AgentInvoker(Protocol):
    async def invoke(self, agent_id: str, task: str, context: Dict[str, Any]) -> TaskResult:
        ...

class ExecutionStrategy(Protocol):
    async def execute(self, tasks: List[WorkflowTask], invoker: AgentInvoker) -> Dict[str, TaskResult]:
        ...
```

### 11.2 参考文档

- [DPSK-OPC Agent 模块需求文档](../v1.0.1/DPSK-OPC%20Agent%20模块需求文档.md)
- [DPSK-OPC 通信机制概要设计文档](../v1.0.0/DPSK-OPC%20通信机制概要设计文档.md)
