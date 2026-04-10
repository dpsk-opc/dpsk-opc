# DPSK-OPC MVP Chat API 需求文档

**版本**: v1.0.0  
**日期**: 2026-04-10  
**状态**: 待评审

---

## 1. 概述

### 1.1 业务背景

前端已完成MVP页面开发，是一个简单的聊天界面：
- 包含一个输入框和一个消息展示区
- 用户在输入框输入消息后，请求后端并接收回复

### 1.2 目标

搭建后端MVP版本的后端服务，串起完整的聊天流程，为后续迭代奠定基础。

### 1.3 目标用户

- 内部测试用户
- 前端开发联调

---

## 2. 功能需求

### 2.1 对话上下文管理

**描述**: 后端需要支持多轮对话，具备基本的上下文记忆能力。

**具体要求**:
| 项目 | 要求 |
|------|------|
| 存储方式 | 内存存储（MVP阶段不持久化） |
| 上下文窗口 | 最近 10 轮对话（20条消息） |
| 超限处理 | 超出窗口时，丢弃最早的对话 |
| 会话隔离 | 暂不支持多会话，MVP单会话 |

**数据结构**:
```json
{
  "session_id": "uuid",
  "messages": [
    {"role": "user", "content": "消息内容", "timestamp": 1234567890},
    {"role": "assistant", "content": "回复内容", "timestamp": 1234567891}
  ]
}
```

### 2.2 固定Agent模式

**描述**: MVP阶段使用固定的秘书Agent，暂不支持Agent切换。

**Agent信息**:
| 字段 | 值 |
|------|-----|
| agent_id | secretary |
| name | 秘书小秘 |
| avatar | null（预留字段） |

**预留能力**: 后续支持通过配置或接口切换Agent。

### 2.3 流式返回（SSE）

**描述**: 后端通过 Server-Sent Events 向前端推送消息，支持流式输出。

**SSE事件类型**:

| 事件类型 | 说明 | 字段 |
|----------|------|------|
| `agent_info` | Agent信息 | agent_id, name, avatar |
| `log` | 结构化日志 | level, content, timestamp |
| `message` | 消息片段 | content, is_final |
| `done` | 结束信号 | - |

**完整响应流程示例**:
```
data: {"type": "agent_info", "agent_id": "secretary", "name": "秘书小秘", "avatar": null, "session_id": "xxx"}

data: {"type": "log", "level": "INFO", "content": "开始处理消息...", "timestamp": 1712749440}

data: {"type": "log", "level": "INFO", "content": "调用模型中...", "timestamp": 1712749440}

data: {"type": "message", "content": "你好！", "is_final": false}

data: {"type": "message", "content": "我是", "is_final": false}

data: {"type": "message", "content": "秘书小秘", "is_final": false}

data: {"type": "message", "content": "。", "is_final": false}

data: {"type": "log", "level": "INFO", "content": "回复完成", "timestamp": 1712749441}

data: {"type": "message", "content": "", "is_final": true}

data: {"type": "done"}
```

### 2.4 日志事件（结构化）

**描述**: 后端处理过程中的关键事件以结构化日志形式输出到前端。

**日志级别定义**:

| 级别 | 使用场景 |
|------|----------|
| INFO | 开始处理、模型调用、回复完成 |
| WARN | 模型响应慢、超时、备用方案触发 |
| DEBUG | 详细调试信息（MVP可暂不输出） |

**日志事件列表**:

| 阶段 | 日志内容 |
|------|----------|
| 收到请求 | "收到用户消息: {message[:20]}..." |
| 上下文准备 | "已加载 {count} 条上下文" |
| 模型调用 | "开始调用 LLM..." |
| 模型响应 | "模型响应首字节" |
| 回复完成 | "回复生成完成，耗时 {duration}ms" |
| 异常 | "处理异常: {error}" |

### 2.5 Mock LLM实现

**描述**: MVP阶段使用Mock LLM，输出预设响应，不调用真实API。

**Mock响应规则**:
| 输入关键词 | Mock响应 |
|------------|----------|
| 你好/hi/hello/嗨 | 友好打招呼 + 自我介绍 |
| 名字/叫什么 | 介绍Agent名字 |
| 谢谢/感谢 | 礼貌感谢 |
| 帮助/帮/怎么 | 介绍能力范围 |
| 其他 | 默认回复 + 原话回显 |

**Mock延迟**: 每个词之间 30-50ms 随机延迟，模拟打字效果。

---

## 3. 接口规范

### 3.1 聊天接口

**端点**: `POST /api/v1/chat/stream`

**请求头**:
```
Content-Type: application/json
```

**请求体**:
```json
{
  "message": "用户输入的消息",
  "session_id": "可选的会话ID，不传则创建新会话"
}
```

**响应**: `text/event-stream` (SSE)

**响应状态码**:
| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 500 | 服务器内部错误 |

### 3.2 获取Agent信息

**端点**: `GET /api/v1/chat/agent`

**响应**:
```json
{
  "agent_id": "secretary",
  "name": "秘书小秘",
  "avatar": null,
  "available": true
}
```

### 3.3 获取当前会话历史

**端点**: `GET /api/v1/chat/session/{session_id}`

**响应**:
```json
{
  "session_id": "xxx",
  "messages": [
    {"role": "user", "content": "你好", "timestamp": 1234567890},
    {"role": "assistant", "content": "你好！我是秘书小秘", "timestamp": 1234567891}
  ]
}
```

### 3.4 创建新会话

**端点**: `POST /api/v1/chat/session`

**响应**:
```json
{
  "session_id": "新创建的会话ID"
}
```

---

## 4. 非功能需求

### 4.1 性能要求

| 指标 | 要求 |
|------|------|
| 首字节响应时间 | < 500ms（Mock阶段） |
| 并发连接数 | 支持 10+ 并发会话 |
| 消息处理延迟 | < 100ms/字（Mock延迟外） |

### 4.2 日志输出

- 后端控制台输出完整日志
- 日志格式: `[时间] [级别] [模块] 消息`
- 日志级别可通过配置调整

### 4.3 错误处理

| 错误场景 | HTTP状态码 | 响应内容 |
|----------|------------|----------|
| message为空 | 400 | `{"error": "message is required"}` |
| 服务器异常 | 500 | `{"error": "Internal error", "detail": "..."}` |

---

## 5. 技术实现

### 5.1 文件结构

```
backend/src/
├── api/
│   ├── __init__.py
│   └── chat.py          # MVP Chat API
├── services/
│   ├── __init__.py
│   └── chat_service.py  # 聊天业务逻辑
├── models/
│   ├── __init__.py
│   └── chat.py          # 数据模型
└── ...
```

### 5.2 核心模块

| 模块 | 职责 |
|------|------|
| `api/chat.py` | HTTP接口定义 |
| `services/chat_service.py` | 对话管理、上下文维护、Mock LLM |
| `models/chat.py` | 数据模型定义 |

### 5.3 依赖

- FastAPI（已有）
- SSE支持（FastAPI内置）

---

## 6. 验收标准

### 6.1 功能验收

- [ ] 接口能正常请求和响应
- [ ] SSE流式输出正常，前端能逐字显示
- [ ] 日志事件能正确输出到前端
- [ ] 多轮对话，上下文能保持
- [ ] session_id能正确创建和传递

### 6.2 测试场景

| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 打招呼 | "你好" | 友好回复 |
| 问名字 | "你叫什么" | 自我介绍 |
| 多轮对话 | 连续3条消息 | 上下文正确 |
| 空消息 | "" | 返回400错误 |
| 特殊字符 | "test!@#$%" | 正常处理 |

---

## 7. 后续扩展点

以下为MVP后的扩展方向，记录但不实现：

1. **真实LLM接入**: 替换Mock为真实API调用
2. **持久化存储**: 从内存存储迁移到Redis/数据库
3. **多Agent支持**: 支持Agent切换和能力配置
4. **WebSocket支持**: 支持双向通信
5. **认证鉴权**: 添加用户身份验证

---

## 8. 优先级

| 优先级 | 功能 |
|--------|------|
| P0 | SSE流式返回、结构化日志 |
| P0 | 简单上下文记忆 |
| P1 | Mock LLM响应 |
| P1 | Agent信息接口 |
| P2 | 会话管理接口 |

---

**评审待确认后开始实现**
