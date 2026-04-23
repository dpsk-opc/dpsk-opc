# 流式输出功能设计

**版本**: v1.0.5  
**日期**: 2026-04-22  
**状态**: 开发中

---

## 1. 概述

### 1.1 目标
实现任务执行过程的实时流式输出，用户能够看到：
- Agent 理解用户意图的过程
- 任务规划和执行步骤
- 各子 Agent 的工作状态
- 最终结果

### 1.2 现有接口
沿用现有接口 **POST `/api/v1/chat/stream`**，不做改动，减少前后端改动成本。

---

## 2. API 规范

### 2.1 请求

**POST** `/api/v1/chat/stream`

```json
{
  "message": "帮我预定明天下午的会议室",
  "session_id": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | string | ✅ | 用户消息内容 |
| session_id | string | ❌ | 会话 ID，不传则创建新会话 |

### 2.2 响应

**Content-Type**: `text/event-stream`

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
X-Accel-Buffering: no
```

---

## 3. 事件类型详解

### 3.1 事件类型枚举

| type 值 | 说明 | 使用场景 |
|---------|------|----------|
| `agent_info` | Agent 信息 | 会话开始时发送，包含 agent 基本信息 |
| `log` | 日志信息 | 调试信息、状态变化提示 |
| `agent_event` | Agent 处理事件 | 核心功能，展示任务执行进度 |
| `message` | 消息内容 | 最终回复内容（流式输出） |
| `done` | 完成信号 | 整个请求处理完成 |

### 3.2 事件详情

---

#### 3.2.1 `agent_info` - Agent 信息

会话开始时发送，包含当前 Agent 的基本信息。

**触发时机**: 请求开始时，最先发送

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string | 固定值 `"agent_info"` |
| agent_id | string | Agent 唯一标识 |
| name | string | Agent 显示名称 |
| avatar | string \| null | Avatar URL |
| session_id | string | 会话 ID |
| timestamp | number | 事件时间戳（毫秒） |

**示例**:
```json
{
  "type": "agent_info",
  "agent_id": "秘书",
  "name": "秘书小秘",
  "avatar": null,
  "session_id": "abc123",
  "timestamp": 1745324567890
}
```

---

#### 3.2.2 `log` - 日志信息

系统日志，用于展示调试信息和状态变化。

**触发时机**: 关键步骤执行时

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string | 固定值 `"log"` |
| level | string | 日志级别：`INFO` / `WARN` / `ERROR` / `DEBUG` |
| content | string | 日志内容 |
| timestamp | number | 事件时间戳（毫秒） |

**示例**:
```json
{
  "type": "log",
  "level": "INFO",
  "content": "收到用户消息: 帮我预定会议室...",
  "timestamp": 1745324567891
}
```

---

#### 3.2.3 `agent_event` - Agent 处理事件 ⭐核心

展示 Agent 任务执行的详细进度。

**触发时机**: Agent 执行过程中的每个关键步骤

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string | 固定值 `"agent_event"` |
| event_type | string | 事件类型，见下方事件类型表 |
| message | string | 人类可读的事件描述 |
| react_step | string \| null | ReAct 当前步骤：`think` / `act` / `observe` / `finish` |
| tool_name | string \| null | 当前执行的工具名称 |
| agent_id | string \| null | Agent 标识 |
| timestamp | number | 事件时间戳（毫秒） |
| data | object | 额外数据，见下方各事件说明 |

---

#### 3.2.4 事件类型表 (`event_type`)

##### 任务生命周期

| event_type | 说明 | react_step | 触发时机 |
|------------|------|------------|----------|
| `agent:task_received` | 收到任务 | - | Agent 收到任务请求 |
| `agent:task_started` | 开始处理 | - | Agent 开始执行任务 |
| `agent:task_completed` | 任务完成 | `finish` | Agent 成功完成任务 |
| `agent:task_failed` | 任务失败 | `finish` | Agent 执行出错 |
| `agent:task_cancelled` | 任务取消 | - | 前端取消请求 |

##### ReAct 循环

| event_type | 说明 | react_step | 触发时机 |
|------------|------|------------|----------|
| `agent:react:think_start` | 开始思考 | `think` | LLM 开始分析任务 |
| `agent:react:think_end` | 思考完成 | `think` | LLM 完成分析 |
| `agent:react:act_start` | 开始行动 | `act` | 开始执行工具调用 |
| `agent:react:act_end` | 行动完成 | `act` | 工具调用完成 |
| `agent:react:observe_end` | 观察结果 | `observe` | 收到工具返回结果 |

##### 工具执行

| event_type | 说明 | react_step | 触发时机 |
|------------|------|------------|----------|
| `agent:tool:call_start` | 开始调用工具 | `act` | 即将调用指定工具 |
| `agent:tool:call_end` | 工具调用结束 | `act` | 工具执行完成 |

##### Agent 间通信

| event_type | 说明 | react_step | 触发时机 |
|------------|------|------------|----------|
| `agent:dispatched` | 分发任务 | `act` | 秘书分发任务给子 Agent |
| `agent:sub_task_result` | 子任务结果 | `act` | 子 Agent 返回执行结果 |

---

#### 3.2.5 各事件类型详细格式

##### `agent:task_received` - 收到任务

```json
{
  "type": "agent_event",
  "event_type": "agent:task_received",
  "message": "收到任务请求",
  "react_step": null,
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567890,
  "data": {
    "task_name": "execute",
    "correlation_id": "uuid"
  }
}
```

##### `agent:task_started` - 开始处理

```json
{
  "type": "agent_event",
  "event_type": "agent:task_started",
  "message": "开始处理任务...",
  "react_step": null,
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567891,
  "data": {}
}
```

##### `agent:react:think_start` - 开始思考

```json
{
  "type": "agent_event",
  "event_type": "agent:react:think_start",
  "message": "正在思考...",
  "react_step": "think",
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567892,
  "data": {
    "react_iteration": 1
  }
}
```

##### `agent:react:think_end` - 思考完成

```json
{
  "type": "agent_event",
  "event_type": "agent:react:think_end",
  "message": "思考完成，决定调用 1 个工具",
  "react_step": "think",
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567893,
  "data": {
    "react_iteration": 1,
    "llm_model": "deepseek-chat",
    "token_usage": {
      "prompt_tokens": 500,
      "completion_tokens": 50
    }
  }
}
```

##### `agent:tool:call_start` - 开始调用工具

```json
{
  "type": "agent_event",
  "event_type": "agent:tool:call_start",
  "message": "正在调用工具: dispatch_task",
  "react_step": "act",
  "tool_name": "dispatch_task",
  "agent_id": "秘书",
  "timestamp": 1745324567894,
  "data": {
    "react_iteration": 1,
    "tool_args": {
      "to_agent": "行政助理",
      "task_description": "查询可用会议室"
    }
  }
}
```

##### `agent:tool:call_end` - 工具调用结束

```json
{
  "type": "agent_event",
  "event_type": "agent:tool:call_end",
  "message": "工具 dispatch_task 执行完成",
  "react_step": "act",
  "tool_name": "dispatch_task",
  "agent_id": "秘书",
  "timestamp": 1745324567895,
  "data": {
    "react_iteration": 1,
    "duration_ms": 1234,
    "tool_result": {
      "success": true,
      "result": {
        "message": "任务已分发"
      }
    }
  }
}
```

##### `agent:dispatched` - 分发任务给子 Agent

```json
{
  "type": "agent_event",
  "event_type": "agent:dispatched",
  "message": "📤 任务已分发给 行政助理，正在处理...",
  "react_step": "act",
  "tool_name": "dispatch_task",
  "agent_id": "秘书",
  "timestamp": 1745324567896,
  "data": {
    "target_agent": "行政助理",
    "task_id": "sub-task-uuid"
  }
}
```

##### `agent:react:act_end` - 行动完成

```json
{
  "type": "agent_event",
  "event_type": "agent:react:act_end",
  "message": "已完成 1 个工具调用",
  "react_step": "act",
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567897,
  "data": {
    "react_iteration": 1,
    "tools_executed": ["dispatch_task"]
  }
}
```

##### `agent:react:observe_end` - 观察结果

```json
{
  "type": "agent_event",
  "event_type": "agent:react:observe_end",
  "message": "已收到工具结果，继续思考...",
  "react_step": "observe",
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567898,
  "data": {
    "react_iteration": 1,
    "tools_count": 1
  }
}
```

##### `agent:task_completed` - 任务完成

```json
{
  "type": "agent_event",
  "event_type": "agent:task_completed",
  "message": "🎉 任务完成",
  "react_step": "finish",
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567899,
  "data": {
    "react_iteration": 2,
    "duration_ms": 5000,
    "tools_used": ["dispatch_task"]
  }
}
```

##### `agent:task_failed` - 任务失败

```json
{
  "type": "agent_event",
  "event_type": "agent:task_failed",
  "message": "💥 任务失败: 网络错误",
  "react_step": "finish",
  "tool_name": null,
  "agent_id": "秘书",
  "timestamp": 1745324567900,
  "data": {
    "error": "网络错误",
    "react_iteration": 1
  }
}
```

---

#### 3.2.6 `message` - 消息内容

最终回复内容，通过 SSE 逐字符/逐词发送。

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string | 固定值 `"message"` |
| content | string | 消息内容（单字符或词语） |
| is_final | boolean | 是否为最后一个片段 |

**示例**:
```json
{"type": "message", "content": "已", "is_final": false}
{"type": "message", "content": "为", "is_final": false}
{"type": "message", "content": "你", "is_final": false}
{"type": "message", "content": "预", "is_final": false}
{"type": "message", "content": "定", "is_final": false}
...
{"type": "message", "content": "会议室。", "is_final": true}
```

---

#### 3.2.7 `done` - 完成信号

整个请求处理完成，用于前端关闭连接或清理状态。

**示例**:
```json
{"type": "done"}
```

---

## 4. 完整事件流示例

```
// 1. Agent 信息
{"type": "agent_info", "agent_id": "秘书", "name": "秘书小秘", "session_id": "abc123", "timestamp": 1745324567890}

// 2. 系统日志
{"type": "log", "level": "INFO", "content": "收到用户消息: 帮我预定会议室...", "timestamp": 1745324567891}

// 3. Agent 事件 - 任务接收
{"type": "agent_event", "event_type": "agent:task_received", "message": "收到任务请求", ...}

// 4. Agent 事件 - 开始处理
{"type": "agent_event", "event_type": "agent:task_started", "message": "开始处理任务...", ...}

// 5. Agent 事件 - 开始思考
{"type": "agent_event", "event_type": "agent:react:think_start", "message": "正在思考...", "react_step": "think", ...}

// 6. Agent 事件 - 思考完成
{"type": "agent_event", "event_type": "agent:react:think_end", "message": "思考完成，决定调用 1 个工具", ...}

// 7. Agent 事件 - 工具调用开始
{"type": "agent_event", "event_type": "agent:tool:call_start", "message": "正在调用工具: dispatch_task", "tool_name": "dispatch_task", ...}

// 8. Agent 事件 - 任务分发
{"type": "agent_event", "event_type": "agent:dispatched", "message": "📤 任务已分发给 行政助理...", ...}

// 9. 系统日志
{"type": "log", "level": "INFO", "content": "通过 Agent 直接执行请求...", "timestamp": ...}

// 10. Agent 事件 - 任务完成
{"type": "agent_event", "event_type": "agent:task_completed", "message": "🎉 任务完成", ...}

// 11. 最终消息 - 流式输出
{"type": "message", "content": "已", "is_final": false}
{"type": "message", "content": "为", "is_final": false}
...
{"type": "message", "content": "好！", "is_final": true}

// 12. 完成信号
{"type": "done"}
```

---

## 5. 前端集成指南

### 5.1 连接 SSE

```javascript
async function connectToChat(message, sessionId = null) {
  const response = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      message: message,
      session_id: sessionId
    })
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const text = decoder.decode(value);
    const lines = text.split('\n');

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = JSON.parse(line.slice(6));
        handleEvent(data);
      }
    }
  }
}
```

### 5.2 事件处理函数

```javascript
function handleEvent(event) {
  switch (event.type) {
    case 'agent_info':
      // 更新 UI 显示当前 Agent 信息
      updateAgentInfo(event);
      break;

    case 'log':
      // 显示系统日志（可选）
      if (event.level === 'ERROR') {
        showError(event.content);
      }
      break;

    case 'agent_event':
      // 核心：根据 event_type 显示进度
      handleAgentEvent(event);
      break;

    case 'message':
      // 追加消息内容
      appendMessage(event.content, event.is_final);
      break;

    case 'done':
      // 请求完成，清理状态
      onComplete();
      break;
  }
}
```

### 5.3 Agent 事件渲染逻辑

```javascript
function handleAgentEvent(event) {
  // 根据 event_type 决定展示方式
  switch (event.event_type) {
    case 'agent:task_started':
      showStatus('▶ 开始处理任务...');
      break;

    case 'agent:react:think_start':
      showStatus('🤔 正在思考...');
      break;

    case 'agent:react:think_end':
      hideStatus(); // 思考完成，隐藏 thinking 状态
      break;

    case 'agent:tool:call_start':
      showStatus(`🔧 正在调用 ${event.tool_name}...`);
      showToolArgs(event.data?.tool_args);
      break;

    case 'agent:tool:call_end':
      showToolResult(event.data?.tool_result);
      break;

    case 'agent:dispatched':
      showStatus(`📤 ${event.data?.target_agent} 正在处理...`);
      break;

    case 'agent:task_completed':
      showStatus('🎉 任务完成');
      break;

    case 'agent:task_failed':
      showError(event.data?.error || '任务执行失败');
      break;
  }
}
```

### 5.4 UI 状态管理建议

```javascript
// 状态结构
const chatState = {
  sessionId: null,
  agentInfo: null,
  messages: [],
  status: null,          // 当前状态描述
  isThinking: false,     // 是否在思考
  isToolExecuting: false, // 是否有工具在执行
  isComplete: false,
  error: null
};
```

---

## 6. 事件到 UI 图标映射

| event_type | 图标 | 说明 |
|------------|------|------|
| `agent:task_received` | 📥 | 收到任务 |
| `agent:task_started` | ▶ | 开始执行 |
| `agent:react:think_start` | 🤔 | 思考中 |
| `agent:react:think_end` | 💭 | 思考完成 |
| `agent:react:act_start` | ⚡ | 开始行动 |
| `agent:tool:call_start` | 🔧 | 调用工具 |
| `agent:tool:call_end` | ✅ | 工具完成 |
| `agent:dispatched` | 📤 | 分发任务 |
| `agent:react:act_end` | 🔄 | 行动完成 |
| `agent:react:observe_end` | 👁️ | 观察结果 |
| `agent:task_completed` | 🎉 | 任务完成 |
| `agent:task_failed` | 💥 | 任务失败 |

---

## 7. 折叠策略建议

### 7.1 按阶段折叠

| 阶段 | react_step | 默认状态 | 可折叠 |
|------|------------|----------|--------|
| 思考过程 | `think` | 展开 | ✅ |
| 工具执行 | `act` | 展开 | ✅ |
| 观察结果 | `observe` | 折叠 | ✅ |

### 7.2 折叠内容

展开时显示：
- 事件图标 + 消息
- 工具参数详情
- 工具执行结果

折叠后显示：
- 最后一条状态
- "点击展开查看详情"

---

## 8. 错误处理

### 8.1 SSE 错误

```javascript
async function connectToChat(message) {
  try {
    const response = await fetch('/api/v1/chat/stream', options);
    
    if (!response.ok) {
      throw new Error(`HTTP error: ${response.status}`);
    }

    // 正常处理...
    
  } catch (error) {
    showError(`连接失败: ${error.message}`);
  }
}
```

### 8.2 事件解析错误

```javascript
for (const line of lines) {
  if (line.startsWith('data: ')) {
    try {
      const data = JSON.parse(line.slice(6));
      handleEvent(data);
    } catch (e) {
      console.warn('Failed to parse SSE event:', line);
    }
  }
}
```

---

## 9. 附录

### 9.1 现有代码位置

| 文件 | 说明 |
|------|------|
| `src/api/chat.py` | API 端点定义 |
| `src/services/chat_service.py` | 聊天服务核心逻辑 |
| `src/models/chat.py` | 数据模型定义 |
| `src/bus/models.py` | Agent 事件类型定义 |
| `src/agent/runner.py` | Agent 执行逻辑（事件发射点） |

### 9.2 关键类/函数

| 类/函数 | 位置 | 说明 |
|---------|------|------|
| `chat_stream()` | chat.py | SSE 端点 |
| `ChatService.process_message()` | chat_service.py | 核心处理函数，返回事件流 |
| `SSEResponse` | chat.py | SSE 响应模型 |
| `AgentEventType` | bus/models.py | Agent 事件类型枚举 |
| `AgentEventEmitter` | bus/models.py | 事件发射器 |
| `handle_task_request()` | runner.py | Agent 任务处理入口 |

---

## 10. TODO

- [ ] 前端实现 SSE 连接和事件处理
- [ ] 前端实现进度 UI 渲染
- [ ] 前端实现折叠/展开功能
- [ ] 测试完整事件流
- [ ] 优化事件粒度（避免事件过多）
