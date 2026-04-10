"""Bus Module - Message Bus Core Implementation

This module provides the core message bus functionality for agent communication.

from __future__ import annotations must be at the very top of the file.
"""

from __future__ import annotations

"""Bus Module Documentation
================================================================================
                           BUS 通信使用指南
================================================================================

## 一、概述

Bus 模块是 DPSK-OPC 后端的核心消息总线，负责 Agent 之间的通信。
支持点对点请求/响应、发布订阅、群组任务分发等多种通信模式。

## 二、快速开始

```python
from src.bus import (
    InMemoryMessageBus,  # 生产环境可用 RedisMessageBus 等
    MessageBus,
    Target, TargetType,
    TaskRequest, TaskResponse,
    Event, Command, Query,
    AggregationPolicy,
)

# 1. 创建 Bus 实例
bus = InMemoryMessageBus()

# 2. 注册消息处理器（接收消息的 Agent 端）
async def my_handler(message):
    # 处理消息...
    return TaskResponse(
        source="my-agent",
        target=message.target,
        correlation_id=message.id,
        result={"status": "ok", "data": "processed"},
    )

await bus.subscribe(Target(type=TargetType.AGENT, value="my-agent"), my_handler)
```

## 三、通信模式

### 3.1 点对点请求-响应 (Request-Response)
   - 调用方发送 TaskRequest，等待 TaskResponse
   - 支持超时控制
   - 适用于需要确认结果的场景

   ```python
   # 发送方
   request = TaskRequest(
       source="caller-agent",
       target=Target(type=TargetType.AGENT, value="target-agent"),
       task_name="process_data",
       task_data={"input": "data to process"},
   )
   
   response = await bus.request(
       target=Target(type=TargetType.AGENT, value="target-agent"),
       message=request,
       timeout=30.0,
   )
   print(response.result)  # {"status": "ok", "data": "processed"}
   ```

### 3.2 通知 (Fire-and-Forget)
   - 发送方发送消息，不等待响应
   - 适用于广播通知、日志记录等

   ```python
   await bus.notify(
       target=Target(type=TargetType.AGENT, value="worker-agent"),
       message=Event(
           source="monitor",
           target=Target(type=TargetType.AGENT, value="worker-agent"),
           event_type="heartbeat",
           payload={"status": "alive"},
       ),
   )
   ```

### 3.3 发布-订阅 (Publish-Subscribe)
   - 多个订阅者接收同一主题的消息
   - 适用于事件广播、状态同步

   ```python
   # 订阅者 A
   async def subscriber_a(msg):
       print(f"A received: {msg.payload}")

   await bus.subscribe(
       Target(type=TargetType.TOPIC, value="system.events"),
       subscriber_a,
   )

   # 订阅者 B
   async def subscriber_b(msg):
       print(f"B received: {msg.payload}")

   await bus.subscribe(
       Target(type=TargetType.TOPIC, value="system.events"),
       subscriber_b,
   )

   # 发布者
   await bus.publish_event(
       topic="system.events",
       message=Event(
           source="sensor",
           target=Target(type=TargetType.TOPIC, value="system.events"),
           event_type="temperature",
           payload={"temp": 25.5},
       ),
   )
   # A 和 B 都会收到消息
   ```

### 3.4 群组任务分发 (Group Task Distribution)
   - 向一组 Agent 分发任务，根据聚合策略收集结果
   - 适用于并行计算、MapReduce 模式

   ```python
   # ALL 策略：等待所有 Agent 完成
   results = await bus.group_request(
       group=Target(type=TargetType.GROUP, value="compiler-group"),
       subtasks=[
           {"lang": "python", "code": "print('hello')"},
           {"lang": "rust", "code": "println!(\"hello\"); "},
       ],
       policy=AggregationPolicy.ALL,
       timeout=60.0,
   )
   # 所有 Agent 都完成后返回结果

   # ANY 策略：任一 Agent 完成后立即返回
   results = await bus.group_request(
       group=Target(type=TargetType.GROUP, value="search-group"),
       subtasks=[...],
       policy=AggregationPolicy.ANY,
       timeout=10.0,
   )

   # FIRST 策略：与 ANY 类似，但会取消其他任务
   results = await bus.group_request(
       group=Target(type=TargetType.GROUP, value="search-group"),
       subtasks=[...],
       policy=AggregationPolicy.FIRST,
       timeout=10.0,
   )
   ```

## 四、Target 类型说明

| TargetType    | 说明                     | 用途示例                      |
|---------------|------------------------|-------------------------------|
| AGENT         | 单个 Agent              | 点对点通信、请求-响应           |
| GROUP         | Agent 组                | 群组任务分发                   |
| TOPIC         | 主题                    | 发布-订阅、事件广播             |

## 五、消息类型说明

| 消息类型       | 说明                     | 使用场景                      |
|--------------|------------------------|-------------------------------|
| TaskRequest  | 任务请求                 | 需要响应的任务请求              |
| TaskResponse | 任务响应                 | TaskRequest 的响应结果         |
| Event        | 事件                     | 无需响应的通知、事件广播         |
| Command      | 命令                     | 控制指令、动作触发              |
| Query        | 查询                     | 数据查询请求                   |
| QueryResponse| 查询响应                 | Query 的响应结果               |

## 六、Handler 编写规范

Handler 是处理消息的回调函数，签名规范：

```python
async def handler(message: Message) -> Message | None:
    '''
    Args:
        message: 接收到的消息对象
    
    Returns:
        - 返回 Message: 作为响应发送回去（用于 request 模式）
        - 返回 None: 不发送响应（用于 notify/event 模式）
    '''
    # 处理逻辑...
    
    if need_response:
        return TaskResponse(...)
    else:
        return None
```

## 七、完整示例

```python
import asyncio
from src.bus import (
    InMemoryMessageBus,
    Target, TargetType,
    TaskRequest, TaskResponse,
    Event,
    AggregationPolicy,
)

async def main():
    bus = InMemoryMessageBus()

    # === Worker Agent ===
    async def worker_handler(msg: Message) -> Message:
        if isinstance(msg, TaskRequest):
            result = f"Processed: {msg.task_data}"
            return TaskResponse(
                source="worker",
                target=msg.target,
                correlation_id=msg.id,
                success=True,
                result={"output": result},
            )
        elif isinstance(msg, Event):
            print(f"Worker received event: {msg.payload}")
            return None

    # 注册 Worker
    await bus.subscribe(
        Target(type=TargetType.AGENT, value="worker"),
        worker_handler,
    )

    # === 发送请求 ===
    request = TaskRequest(
        source="client",
        target=Target(type=TargetType.AGENT, value="worker"),
        task_name="process",
        task_data={"input": "hello"},
    )
    
    response = await bus.request(
        target=Target(type=TargetType.AGENT, value="worker"),
        message=request,
        timeout=10.0,
    )
    print(f"Response: {response.result}")

    # === 发布事件 ===
    await bus.publish_event(
        topic="notifications",
        message=Event(
            source="client",
            target=Target(type=TargetType.TOPIC, value="notifications"),
            event_type="user_login",
            payload={"user_id": "123"},
        ),
    )

    # === 健康检查 ===
    health = await bus.health_check()
    print(f"Bus health: {health}")

asyncio.run(main())
```

================================================================================
"""

from src.bus.models import (
    AggregationPolicy,
    Command,
    Event,
    GroupTask,
    Message,
    MessageType,
    Query,
    QueryResponse,
    Target,
    TargetType,
    TaskRequest,
    TaskResponse,
)
from src.bus.memory import InMemoryMessageBus
from src.bus.protocol import MessageBus, MessageStream

__all__ = [
    # Models
    "Message",
    "MessageType",
    "Target",
    "TargetType",
    "AggregationPolicy",
    "GroupTask",
    "TaskRequest",
    "TaskResponse",
    "Event",
    "Command",
    "Query",
    "QueryResponse",
    # Protocol
    "MessageBus",
    "MessageStream",
    # Implementations
    "InMemoryMessageBus",
]
