# DPSK-OPC 消息总线开发任务清单

> 版本：v1.0.0
> 创建时间：2026-04-09
> 关联需求：[DPSK-OPC 通信机制概要设计文档](./DPSK-OPC 通信机制概要设计文档.md)

---

## 任务概述

本任务清单基于需求文档，拆解消息总线系统的实现任务。

---

## 任务列表

### 阶段一：项目基础结构

- [x] **Task 1.1**: 初始化 backend 项目结构 ✅
  - 创建 `backend/src/` 目录及 `__init__.py`
  - 创建配置模块 `config.py`
  - 创建入口文件 `main.py`
  - 预估工作量：0.5 人天
  - **完成状态**：已完成
    - 创建了完整的项目目录结构
    - 创建了 `pyproject.toml` 项目配置
    - 创建了 `.env.example` 环境变量示例
    - 创建了子模块初始化文件

- [x] **Task 1.2**: 配置管理模块 ✅
  - 实现 YAML 配置加载（支持多环境：development/production）
  - 实现消息总线配置模型
  - 预估工作量：0.5 人天
  - **完成状态**：已完成
    - 实现了 `Config` 主配置类
    - 实现了 `BusConfig`、`ServerConfig`、`LogConfig` 等子配置
    - 支持环境变量覆盖
    - 支持 YAML 文件加载
    - 包含测试文件 `tests/test_config.py`

- [x] **Task 1.3**: 日志与追踪基础设施 ✅
  - 配置结构化日志（JSON 格式）
  - 实现 trace_id 生成与传播
  - 预估工作量：0.5 人天
  - **完成状态**：已完成
    - 实现了 `src/utils/logging.py` - 结构化日志
    - 实现了 `src/utils/tracing.py` - 分布式追踪
    - 支持 JSON/Text 两种日志格式
    - 实现了 trace_id 上下文传播
    - 包含测试文件 `tests/test_logging.py`

---

### 阶段二：消息模型

- [x] **Task 2.1**: 消息核心模型 ✅
  - 实现 `Message` 数据类（包含 id, trace_id, msg_type, source, target, payload 等）
  - 实现消息类型枚举 `MessageType`
  - 实现目标类型 `Target`（agent/topic/group）
  - 预估工作量：1 人天
  - **完成状态**：已完成
    - 实现了 `Message` 基类（包含所有基础字段）
    - 实现了 `MessageType` 枚举（6种消息类型）
    - 实现了 `TargetType` 枚举（agent/topic/group）
    - 实现了 `Target` 模型（含 from_string 便捷构造）

- [x] **Task 2.2**: 消息类型定义 ✅
  - `TaskRequest` / `TaskResponse`
  - `Event`
  - `Command`
  - `Query` / `QueryResponse`
  - 预估工作量：0.5 人天
  - **完成状态**：已完成
    - 实现了 `TaskRequest` / `TaskResponse`（支持 success/result/error）
    - 实现了 `Event`（支持 event_type/event_data）
    - 实现了 `Command`（支持 command/command_data）
    - 实现了 `Query` / `QueryResponse`

- [x] **Task 2.3**: 消息序列化与反序列化 ✅
  - JSON 序列化支持
  - 消息验证（pydantic）
  - 预估工作量：0.5 人天
  - **完成状态**：已完成
    - 实现了 `to_dict()` / `from_dict()` 方法
    - 实现了 `to_json()` / `from_json()` 方法
    - 使用 Pydantic 进行数据验证
    - 包含测试文件 `tests/test_models.py`

---

### 阶段三：总线核心实现

- [x] **Task 3.1**: 总线抽象接口（Protocol） ✅
  - 定义 `MessageBus` 抽象类
  - 定义 `MessageStream` 接口
  - 预估工作量：1 人天
  - **完成状态**：已完成
    - 实现了 `MessageBus` 抽象基类
    - 实现了 `MessageStream` 抽象接口
    - 定义了所有核心方法签名
    - 实现了异常类 `BusError`, `TimeoutError`, `DeliveryError`

- [x] **Task 3.2**: 内存后端实现 ✅
  - 实现内存消息队列
  - 实现点对点投递
  - 实现订阅机制
  - 预估工作量：1.5 人天
  - **完成状态**：已完成
    - 实现了 `InMemoryMessageBus` 类
    - 使用 `asyncio.Queue` 实现消息队列
    - 实现了订阅管理（agent/topic/group）

- [x] **Task 3.3**: 请求-响应模式 ✅
  - 实现 `request()` 方法
  - 实现 `notify()` 方法
  - 实现 correlation_id 匹配
  - 预估工作量：1 人天
  - **完成状态**：已完成
    - 实现了 `request()` - 带超时控制的请求-响应
    - 实现了 `notify()` - 单向通知
    - 实现了 `PendingRequest` 状态管理
    - 实现了 correlation_id 自动匹配

- [x] **Task 3.4**: 组任务聚合 ✅
  - 实现 `group_request()` 方法
  - 实现 `AggregationPolicy`（ALL/ANY/FIRST）
  - 实现超时处理
  - 预估工作量：1.5 人天
  - **完成状态**：已完成
    - 实现了 `group_request()` 方法
    - 支持 `AggregationPolicy.ALL` - 等待所有子任务
    - 支持 `AggregationPolicy.ANY` - 任一完成即返回
    - 支持 `AggregationPolicy.FIRST` - 首个完成即返回
    - 实现了超时处理和取消机制

- [x] **Task 3.5**: 事件发布-订阅 ✅
  - 实现 `publish_event()` 方法
  - 实现 `subscribe()` 方法
  - 实现 Topic 管理
  - 预估工作量：1 人天
  - **完成状态**：已完成
    - 实现了 `publish_event()` - 广播事件到 Topic
    - 实现了 `subscribe()` / `unsubscribe()` 方法
    - 支持多个订阅者同时接收同一 Topic 消息
    - 包含测试文件 `tests/test_bus.py`

---

### 阶段四：后端扩展

- [ ] **Task 4.1**: Redis Stream 后端
  - 实现 Redis 连接管理
  - 实现消息持久化
  - 实现消费者组
  - 预估工作量：2 人天

- [ ] **Task 4.2**: Kafka 后端（可选）
  - 实现 Kafka 生产者/消费者
  - 实现 offset 管理
  - 预估工作量：2 人天

---

### 阶段五：可观测性

- [ ] **Task 5.1**: 指标收集
  - 集成 Prometheus metrics
  - 实现 `bus_messages_total`
  - 实现 `bus_message_duration_seconds`
  - 实现 `bus_group_task_waiting_count`
  - 预估工作量：1 人天

- [ ] **Task 5.2**: 审计日志
  - 消息元数据持久化
  - 支持 trace_id 检索
  - 预估工作量：1 人天

- [ ] **Task 5.3**: 健康检查
  - 实现总线健康状态检查
  - 实现后端连接状态监控
  - 预估工作量：0.5 人天

---

### 阶段六：工作流引擎（可选）

- [ ] **Task 6.1**: 工作流引擎基础
  - DAG 解析器
  - 状态机实现
  - 预估工作量：2 人天

- [ ] **Task 6.2**: 工作流 API
  - `submit_workflow()`
  - `get_status()`
  - `cancel()`
  - 预估工作量：1 人天

---

### 阶段七：集成与测试

- [ ] **Task 7.1**: Python 绑定
  - PyO3 绑定实现
  - 与 security 模块集成
  - 预估工作量：1 人天

- [ ] **Task 7.2**: 单元测试
  - 消息模型测试
  - 总线接口测试
  - 各后端实现测试
  - 预估工作量：2 人天

- [ ] **Task 7.3**: 集成测试
  - 端到端通信测试
  - 性能基准测试
  - 预估工作量：1 人天

---

## 任务依赖关系

```
Task 1.1 → Task 1.2 → Task 1.3
    ↓
Task 2.1 → Task 2.2 → Task 2.3
    ↓
Task 3.1 → Task 3.2 → Task 3.3 → Task 3.4 → Task 3.5
    ↓
Task 4.1 (可独立) / Task 4.2 (可独立)
    ↓
Task 5.1 → Task 5.2 → Task 5.3
    ↓
Task 6.1 → Task 6.2
    ↓
Task 7.1 → Task 7.2 → Task 7.3
```

---

## 优先级建议

| 优先级 | 任务 | 理由 |
|--------|------|------|
| P0 | Task 1.x, Task 2.x, Task 3.x | 核心功能，必须实现 |
| P1 | Task 4.1, Task 5.x | 生产就绪必需 |
| P2 | Task 4.2, Task 6.x | 可选功能，延后实现 |
| P3 | Task 7.x | 测试驱动开发并行进行 |

---

## 总预估工作量

- **核心功能**：约 10 人天（Task 1-3）
- **后端扩展**：约 3 人天（Task 4）
- **可观测性**：约 2.5 人天（Task 5）
- **工作流引擎**：约 3 人天（Task 6，可选）
- **集成测试**：约 4 人天（Task 7）

**总计约 22.5 人天**

---

## 验收标准

1. ✅ 所有 P0 任务完成
2. ✅ 单元测试覆盖率 ≥ 80%（核心模块）
3. ✅ 内存后端可通过功能测试
4. ⏳ Prometheus 指标正常上报（后续迭代）
5. ✅ 代码通过 Linter 检查

---

## 完成情况

### 已完成的任务（v1.0.0 首次迭代）

| 阶段 | 任务数 | 完成数 | 状态 |
|------|--------|--------|------|
| 阶段一：项目基础结构 | 3 | 3 | ✅ 完成 |
| 阶段二：消息模型 | 3 | 3 | ✅ 完成 |
| 阶段三：总线核心实现 | 5 | 5 | ✅ 完成 |

**总计**：11/18 任务完成（核心功能已就绪）

### 待后续迭代完成

| 阶段 | 任务 | 优先级 |
|------|------|--------|
| 阶段四：后端扩展 | Task 4.1 Redis 后端 | P1 |
| 阶段四：后端扩展 | Task 4.2 Kafka 后端 | P2 |
| 阶段五：可观测性 | Task 5.1-5.3 | P1 |
| 阶段六：工作流引擎 | Task 6.1-6.2 | P2 |
| 阶段七：集成测试 | Task 7.1-7.3 | P3 |
