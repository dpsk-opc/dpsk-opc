# DPSK-OPC 通信机制概要设计文档

> 版本：v1.0
> 最后更新：2026-04-08
> 作者：DPSK-OPC 架构组

---

## 1. 引言

### 1.1 背景

DPSK-OPC 是一个"一人公司 AI 操作系统"，用户通过自然语言指挥多个 AI Agent 协作完成任务。Agent 之间需要一种高效、可靠、可扩展的通信机制。

### 1.2 设计目标

- **简单性**：Agent 不直接相互通信，降低复杂度。
- **灵活性**：支持多种通信模式（点对点、广播、组聚合、工作流）。
- **可观测性**：所有消息可追踪、可审计。
- **可扩展性**：底层实现可插拔（内存、Redis、Kafka），适应单机到集群。
- **层级友好**：父子 Agent 之间可以自然协调，不加重调度负担。

### 1.3 核心原则

1. **Agent 之间不直接建立网络连接**，所有消息通过总线中转。
2. **允许上下级 Agent 直接请求-响应**（通过总线点对点），符合管理直觉。
3. **总线提供可选的组任务聚合能力**，简化常见的"等待多个子任务"场景。
4. **复杂工作流由专门的工作流引擎处理**，不作为核心总线功能。
5. **消息模型抽象**，底层实现可插拔。

---

## 2. 整体架构

```
┌─────────┐ ┌─────────┐ ┌──────────┐
│ Gateway │ │ 秘书Agent│ │ 工作流引擎│
└────┬────┘ └────┬────┘ └─────┬────┘
     │           │            │
     └───────────┼────────────┘
                 │
          ┌──────▼───────┐
          │   消息总线    │
          │(Bus Abstraction)│
          └───────┬───────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼───┐    ┌───▼───┐    ┌───▼───┐
│Agent A│    │Agent B│    │Agent C│
└───────┘    └───────┘    └───────┘
```

- **消息总线**：核心枢纽，负责消息路由、投递、可观测性。
- **Agent**：通过总线客户端与总线交互，不感知其他 Agent 地址。
- **工作流引擎**：可选组件，用于复杂 DAG 任务编排。
- **Gateway**：用户入口，将自然语言指令转换为内部消息。

---

## 3. 消息模型

### 3.1 消息结构

```json
{
  "id": "uuid",
  "trace_id": "uuid",
  "msg_type": "TaskRequest | TaskResponse | Event | Command | Query | QueryResponse",
  "source": "agent_id | system",
  "target": {
    "type": "agent | topic | group",
    "value": "xxx"
  },
  "payload": { ... },
  "correlation_id": "uuid",
  "timestamp": 1234567890,
  "ttl": 60,
  "priority": 0
}
```

### 3.2 消息类型说明

| 类型 | 方向 | 说明 | 是否需要响应 |
|------|------|------|-------------|
| TaskRequest | 调用者 → 执行者 | 派发子任务 | 是（TaskResponse） |
| TaskResponse | 执行者 → 调用者 | 返回执行结果 | 否 |
| Event | 任意 → 总线 | 状态变更、进度通知 | 否 |
| Command | 用户/系统 → Agent | 控制指令（暂停、取消） | 可选 |
| Query | 任意 → 系统 | 查询上下文/状态 | 是（QueryResponse） |
| QueryResponse | 系统 → 查询者 | 返回查询结果 | 否 |

### 3.3 目标类型

| 类型 | 语义 | 底层实现示例 |
|------|------|-------------|
| agent:{id} | 点对点发送给指定 Agent | 队列/直接投递 |
| topic:{name} | 广播给所有订阅者 | Pub/Sub |
| group:{name} | 组内负载均衡，仅一个接收 | 消费者组 |

---

## 4. 通信模式

### 4.1 直接请求-响应（父子之间）

适用场景：上级 Agent 向下级派发任务，并等待结果。

**流程：**

1. 上级 Agent 调用 `bus.request(target=Agent("B"), payload, timeout)`。
2. 总线将消息投递给 Agent B。
3. Agent B 处理完成后，发送 TaskResponse（带 correlation_id）。
4. 总线将响应返回给上级 Agent。

**示例（Python 伪代码）：**

```python
result = await bus.request(
    target=Target.agent("code_compiler"),
    payload={"code": "print('hello')"},
    timeout=30
)
```

**特点：**

- 上级 Agent 完全控制流程，无需总线参与状态管理。
- 适合简单层级调用。

### 4.2 组任务聚合（总线辅助）

适用场景：上级需要向多个下级派发独立子任务，并等待所有完成（或任意一个完成）。

**流程：**

1. 上级发送 GroupTask 消息到 Group("team-abc")，携带子任务列表和聚合策略。
2. 总线将每个子任务分发给组内的一个 Agent（负载均衡）。
3. 总线收集所有响应。
4. 当满足聚合条件（如全部完成），总线将聚合结果返回给上级。

**示例：**

```python
result = await bus.group_request(
    group=Group("compilers"),
    subtasks=[
        {"lang": "python", "code": "..."},
        {"lang": "rust", "code": "..."},
    ],
    policy=AggregationPolicy.ALL,
    timeout=60
)
```

**总线需要维护：**

- 每个 GroupTask 的状态（子任务完成情况）。
- 超时处理。
- 部分失败处理（可配置）。

### 4.3 事件广播

适用场景：进度通知、状态变更等，不关心谁接收。

**示例：**

```python
await bus.publish_event(topic="task.progress", event={"task_id": "123", "percent": 50})
```

任何订阅了该 Topic 的组件（如 Gateway、监控 Agent）都会收到。

### 4.4 工作流引擎（复杂协调）

适用场景：任务有复杂依赖（DAG），需要条件分支、重试、补偿。

**设计：**

- 工作流引擎是一个特殊的 Agent（或内置组件）。
- 用户提交工作流定义（YAML/JSON 或代码 DSL）。
- 引擎解析 DAG，使用总线的基础 API 调用执行 Agent，并管理状态。
- 引擎负责重试、超时、错误恢复。

**示例定义：**

```yaml
steps:
  - id: compile
    agent: code_compiler
  - id: test
    agent: test_runner
    depends_on: [compile]
  - id: package
    agent: packager
    depends_on: [test]
```

**优点：** 将复杂协调逻辑从 Agent 中剥离，Agent 保持纯粹。

---

## 5. 总线抽象接口

```rust
#[async_trait]
pub trait MessageBus: Send + Sync {
    // 点对点请求-响应
    async fn request(&self, target: AgentId, payload: Vec<u8>, timeout: Duration) -> Result<Vec<u8>>;
    
    // 单向通知（不等待响应）
    async fn notify(&self, target: AgentId, payload: Vec<u8>) -> Result<()>;
    
    // 组任务聚合（可选特性）
    async fn group_request(&self, group: GroupId, subtasks: Vec<Subtask>, policy: AggregationPolicy, timeout: Duration) -> Result<Vec<Vec<u8>>>;
    
    // 发布事件（广播）
    async fn publish_event(&self, topic: Topic, event: Vec<u8>) -> Result<()>;
    
    // 订阅消息（返回流）
    async fn subscribe(&self, target: Target) -> Result<Box<dyn MessageStream>>;
}
```

不同底层实现（内存、Redis、Kafka）需实现该接口。

---

## 6. 可靠性保证

| 底层实现 | 持久化 | 至少一次 | 顺序保证 | 适用场景 |
|----------|--------|----------|----------|----------|
| 内存 | 否 | 否（进程崩溃丢失） | 尽力 | 开发、单机轻量 |
| Redis Stream | 是（可配置） | 是（消费者确认） | 是（按组） | 单机/哨兵生产 |
| Kafka | 是 | 是（offset提交） | 是（分区内） | 集群、高吞吐 |
| NATS | 是（JetStream） | 是 | 是 | 云原生 |

生产环境推荐使用 Redis Stream 或 Kafka。

---

## 7. 可观测性

### 7.1 追踪

- 每个消息携带 trace_id，贯穿整个调用链。
- 总线记录关键事件：接收、入队、出队、投递、确认。
- 日志输出结构化 JSON，包含 trace_id、msg_id、source、target。

### 7.2 指标（Prometheus）

```
bus_messages_total{type, target_type, status}
bus_message_duration_seconds{type}
bus_group_task_waiting_count
bus_subscriber_count{topic}
```

### 7.3 审计

- 所有消息的元数据（不含 payload）持久化到审计日志。
- 支持按 trace_id、source、时间范围检索。

---

## 8. 配置与部署

### 8.1 配置示例（YAML）

```yaml
message_bus:
  backend: "redis"   # memory, redis, kafka, nats
  redis:
    url: "redis://localhost:6379"
    stream_max_len: 10000
  kafka:
    brokers: ["localhost:9092"]
    client_id: "dpskopc"
  memory:
    # 无额外配置
  request_timeout_secs: 30
  group_task_timeout_secs: 60
```

### 8.2 部署形态

| 场景 | 配置 | 说明 |
|------|------|------|
| 单机开发 | backend: memory | 无需外部依赖 |
| 单机生产 | backend: redis | 本地 Redis 进程 |
| 集群生产 | backend: kafka | 外部 Kafka 集群 |

---

## 9. 工作流引擎设计（可选）

工作流引擎作为一个独立 Agent，提供以下 API：

- `submit_workflow(definition) -> workflow_id`
- `get_status(workflow_id) -> status`
- `cancel(workflow_id)`

引擎内部使用总线的基础 API 调用执行 Agent，并维护 DAG 状态机。状态持久化（SQLite/PostgreSQL）以支持恢复。

---

## 10. 后续扩展

- **跨集群总线桥接**：通过 MirrorMaker 或自定义桥接实现多集群通信。
- **消息优先级队列**：在总线实现中支持优先级。
- **延迟消息**：支持 deliver_at 字段。

---

## 11. 附录：术语表

| 术语 | 说明 |
|------|------|
| Agent | AI 数字员工，可执行任务 |
| 总线 | 消息路由中枢 |
| 点对点 | 消息发送给指定 Agent |
| 广播 | 消息发送给所有订阅者 |
| 组任务 | 一组子任务，总线负责聚合结果 |
| 工作流引擎 | 执行 DAG 任务编排的组件 |
