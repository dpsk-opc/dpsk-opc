# MVP Chat API 接口文档

**版本**: v1.0.3  
**日期**: 2026-04-10  
**状态**: 已完成，可对接

---

## 1. 概述

### 1.1 接口地址

```
http://{host}:{port}/api/v1/chat
```

默认：`http://localhost:8000/api/v1/chat`

### 1.2 认证

MVP 阶段暂不支持认证，所有接口公开访问。

---

## 2. 接口列表

| 接口 | 方法 | 描述 |
|------|------|------|
| [`/chat/stream`](#31-发送消息-post-chatstream) | POST | 发送消息（SSE流式） |
| [`/chat/agent`](#32-获取agent信息-get-chatagent) | GET | 获取Agent信息 |
| [`/chat/session`](#33-创建会话-post-chatsession) | POST | 创建新会话 |
| [`/chat/session/{session_id}`](#34-获取会话历史-get-chatsessionsession_id) | GET | 获取会话历史 |

---

## 3. 接口详情

### 3.1 发送消息（流式）

**端点**: `POST /api/v1/chat/stream`

发送消息并接收 SSE 流式响应。

#### 请求

**Headers**:
```
Content-Type: application/json
```

**Body**:
```json
{
  "message": "你好",
  "session_id": "可选，不传则创建新会话"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | string | ✅ | 消息内容，不能为空或纯空格 |
| session_id | string | ❌ | 会话ID，不传则自动创建新会话 |

#### 响应

**Content-Type**: `text/event-stream; charset=utf-8`

**响应状态码**:
| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误（消息为空等） |
| 500 | 服务器内部错误 |

#### SSE 事件格式

响应是多个 `data:` 行，每行是一个 JSON 对象：

**1. Agent 信息事件**（首行）
```json
{
  "type": "agent_info",
  "agent_id": "secretary",
  "name": "秘书小秘",
  "avatar": null,
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1712749440
}
```

**2. 日志事件**（可选多条）
```json
{
  "type": "log",
  "level": "INFO",
  "content": "收到用户消息: 你好",
  "timestamp": 1712749440
}
```

日志级别说明：
- `INFO`: 正常流程日志（收到消息、模型调用、回复完成）
- `WARN`: 警告日志（响应慢、超时等）

**3. 消息片段事件**（多条）
```json
{"type": "message", "content": "你", "is_final": false}
{"type": "message", "content": "好", "is_final": false}
{"type": "message", "content": "！", "is_final": false}
{"type": "message", "content": "", "is_final": true}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string | 固定为 "message" |
| content | string | 消息内容片段（一个字或几个字） |
| is_final | boolean | 是否是最后一条消息片段 |

**4. 结束事件**（最后一行）
```json
{"type": "done"}
```

#### 完整响应示例

```
data: {"type": "agent_info", "agent_id": "secretary", "name": "秘书小秘", "avatar": null, "session_id": "550e8400-e29b-41d4-a716-446655440000", "timestamp": 1712749440}

data: {"type": "log", "level": "INFO", "content": "收到用户消息: 你好", "timestamp": 1712749440}

data: {"type": "log", "level": "INFO", "content": "已加载 0 条上下文", "timestamp": 1712749440}

data: {"type": "log", "level": "INFO", "content": "开始调用 LLM...", "timestamp": 1712749440}

data: {"type": "message", "content": "你", "is_final": false}

data: {"type": "message", "content": "好", "is_final": false}

data: {"type": "message", "content": "！", "is_final": false}

data: {"type": "log", "level": "INFO", "content": "回复生成完成，耗时 1234ms", "timestamp": 1712749450}

data: {"type": "message", "content": "", "is_final": true}

data: {"type": "done"}
```

#### 前端处理示例

```javascript
async function sendMessage(message, sessionId = null) {
  const response = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message, session_id: sessionId }),
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  let sessionId = null;
  let fullMessage = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const text = decoder.decode(value);
    const lines = text.split('\n');

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = JSON.parse(line.slice(6));

        switch (data.type) {
          case 'agent_info':
            sessionId = data.session_id;
            console.log('会话ID:', sessionId);
            break;

          case 'log':
            console.log(`[${data.level}] ${data.content}`);
            // 可选择性地显示在界面上
            break;

          case 'message':
            if (data.content) {
              fullMessage += data.content;
              // 实时显示在聊天框
              updateMessageDisplay(fullMessage);
            }
            if (data.is_final) {
              console.log('消息完成:', fullMessage);
            }
            break;

          case 'done':
            console.log('对话结束');
            break;
        }
      }
    }
  }

  return { sessionId, message: fullMessage };
}
```

---

### 3.2 获取Agent信息

**端点**: `GET /api/v1/chat/agent`

获取当前 Agent 的信息。

#### 请求

无参数

#### 响应

**200 OK**
```json
{
  "agent_id": "secretary",
  "name": "秘书小秘",
  "avatar": null,
  "available": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| agent_id | string | Agent 唯一标识 |
| name | string | Agent 显示名称 |
| avatar | string \| null | 头像 URL（预留字段） |
| available | boolean | 是否在线 |

---

### 3.3 创建会话

**端点**: `POST /api/v1/chat/session`

创建一个新的聊天会话。

#### 请求

无参数

#### 响应

**200 OK**
```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | string | 新会话的唯一标识（UUID格式） |

---

### 3.4 获取会话历史

**端点**: `GET /api/v1/chat/session/{session_id}`

获取指定会话的消息历史。

#### 请求

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| session_id | string | 会话ID |

#### 响应

**200 OK**
```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    {
      "role": "user",
      "content": "你好",
      "timestamp": 1712749440
    },
    {
      "role": "assistant",
      "content": "你好！很高兴认识你。",
      "timestamp": 1712749441
    }
  ],
  "created_at": 1712749440
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | string | 会话ID |
| messages | array | 消息列表 |
| messages[].role | string | 角色：user / assistant |
| messages[].content | string | 消息内容 |
| messages[].timestamp | number | 时间戳（秒） |
| created_at | number | 会话创建时间戳 |

**404 Not Found**（会话不存在）
```json
{
  "detail": "Session not found: xxx"
}
```

---

## 4. 错误处理

### 4.1 错误响应格式

所有错误响应均为 JSON：

```json
{
  "detail": "错误描述"
}
```

### 4.2 错误码说明

| HTTP状态码 | 说明 | 常见原因 |
|------------|------|----------|
| 200 | 成功 | - |
| 400 | 请求参数错误 | message为空或纯空格 |
| 404 | 资源不存在 | session_id 不存在 |
| 500 | 服务器内部错误 | 服务异常 |

---

## 5. Mock LLM 响应规则

MVP 阶段使用 Mock LLM，响应规则如下：

| 输入关键词 | 响应示例 |
|------------|----------|
| 你好/hi/hello/嗨 | "你好！很高兴认识你。我是你的智能助手秘书小秘，有什么可以帮你的吗？" |
| 名字/叫什么/你是谁 | "我叫秘书小秘，是你的专属智能助手..." |
| 谢谢/感谢 | "不客气！很高兴能帮到你。" |
| 帮助/帮/怎么 | "我可以帮助你做很多事情：\n1. 回答各种问题\n2. 帮你整理信息\n..." |
| 其他 | "我收到你的消息了：'{输入内容}'..." |

---

## 6. 前端对接注意事项

### 6.1 SSE 连接处理

1. **连接断开重试**：SSE 连接可能因网络问题断开，建议实现重连逻辑
2. **进度显示**：可解析 `log` 事件在界面显示处理进度
3. **流式渲染**：逐字追加 `message.content` 到聊天框实现打字机效果

### 6.2 会话管理

1. **首次对话**：不传 `session_id`，后端自动创建
2. **后续对话**：使用首次返回的 `session_id` 保持上下文
3. **新会话**：调用 `POST /api/v1/chat/session` 创建新会话

### 6.3 打字机效果实现

```javascript
// 伪代码
let messageDiv = createMessageElement('assistant');

// 处理每个消息片段
for (const chunk of messageChunks) {
  messageDiv.textContent += chunk;
  await sleep(50); // 可选：模拟打字延迟
}
```

---

## 7. 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0.3 | 2026-04-10 | 初始版本，完成 MVP 功能 |
