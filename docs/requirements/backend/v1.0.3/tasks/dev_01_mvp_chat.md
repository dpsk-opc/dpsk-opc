# v1.0.3 MVP Chat API 开发任务清单

**版本**: v1.0.3  
**创建日期**: 2026-04-10  
**需求文档**: `../mvp-chat-requirements.md`

---

## 任务总览

| 序号 | 任务名称 | 优先级 | 预估工时 | 依赖 |
|------|----------|--------|----------|------|
| 1 | 创建数据模型 | P1 | 1h | - |
| 2 | 创建聊天服务层 | P1 | 3h | Task 1 |
| 3 | 创建 Chat API 路由 | P1 | 2h | Task 2 |
| 4 | 集成到 main.py | P1 | 0.5h | Task 3 |
| 5 | 编写单元测试 | P0 | 2h | Task 2 |
| 6 | 功能验收测试 | P0 | 1h | All |

---

## 详细任务

### Task 1: 创建数据模型

**文件**: `backend/src/models/chat.py`

**任务描述**:  
定义 MVP Chat 功能所需的数据模型，包括：
- `ChatMessage`: 单条消息模型
- `ChatSession`: 会话模型
- `ChatRequest`: 请求模型
- `AgentInfo`: Agent 信息模型
- `SSEResponse`: SSE 事件模型

**验收标准**:
- [ ] ChatMessage 包含 role, content, timestamp
- [ ] ChatSession 包含 session_id, messages 列表
- [ ] 支持 Pydantic 序列化
- [ ] 类型注解完整

**预估工时**: 1h

---

### Task 2: 创建聊天服务层

**文件**: `backend/src/services/chat_service.py`

**任务描述**:  
实现核心业务逻辑：
- `ChatService`: 聊天服务类
  - `create_session()`: 创建新会话
  - `get_session()`: 获取会话
  - `add_message()`: 添加消息到会话
  - `get_context()`: 获取上下文（最近10轮）
  
- `MockLLM`: Mock LLM 实现
  - 基于关键词返回预设响应
  - 支持流式输出（async generator）
  - 30-50ms 随机延迟模拟打字

- `ContextManager`: 上下文管理器
  - 内存存储会话
  - 限制上下文窗口为 10 轮（20条消息）
  - 自动清理超限消息

**验收标准**:
- [ ] Mock LLM 响应符合需求文档中的规则
- [ ] 流式输出正常工作
- [ ] 上下文窗口限制生效
- [ ] 线程安全（asyncio）

**预估工时**: 3h  
**依赖**: Task 1

---

### Task 3: 创建 Chat API 路由

**文件**: `backend/src/api/chat.py`

**任务描述**:  
实现 REST API 接口：

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/chat/stream` | POST | 发送消息（SSE流式） |
| `/api/v1/chat/agent` | GET | 获取Agent信息 |
| `/api/v1/chat/session/{session_id}` | GET | 获取会话历史 |
| `/api/v1/chat/session` | POST | 创建新会话 |

**SSE 事件格式**:
```
data: {"type": "agent_info", ...}
data: {"type": "log", "level": "INFO", ...}
data: {"type": "message", "content": "...", "is_final": false}
data: {"type": "done"}
```

**验收标准**:
- [ ] SSE 流式输出正常
- [ ] 错误处理完善（400/500）
- [ ] 日志事件正确输出
- [ ] session_id 正确处理

**预估工时**: 2h  
**依赖**: Task 2

---

### Task 4: 集成到 main.py

**文件**: `backend/src/main.py`

**任务描述**:  
- 导入 Chat API router
- 在 lifespan 中初始化 ChatService
- 注册路由

**验收标准**:
- [ ] 启动无报错
- [ ] 路由正确注册

**预估工时**: 0.5h  
**依赖**: Task 3

---

### Task 5: 编写单元测试

**文件**: `backend/tests/test_chat_service.py`

**任务描述**:  
为以下模块编写单元测试：

1. **ContextManager 测试**
   - 创建会话
   - 添加消息
   - 上下文窗口限制

2. **MockLLM 测试**
   - 关键词匹配
   - 流式输出
   - 延迟模拟

3. **ChatService 测试**
   - 创建会话
   - 获取会话
   - 添加消息

4. **API 路由测试** (如果有时间)

**验收标准**:
- [ ] 测试覆盖率 ≥ 80%
- [ ] 所有测试通过
- [ ] Mock 对象使用正确

**预估工时**: 2h  
**依赖**: Task 2

---

### Task 6: 功能验收测试

**任务描述**:  
手动测试所有验收标准中的场景：

| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 打招呼 | "你好" | 友好回复 |
| 问名字 | "你叫什么" | 自我介绍 |
| 多轮对话 | 连续3条消息 | 上下文正确 |
| 空消息 | "" | 返回400错误 |
| 特殊字符 | "test!@#$%" | 正常处理 |

**验收标准**:
- [ ] 接口能正常请求和响应
- [ ] SSE流式输出正常
- [ ] 日志事件能正确输出到前端
- [ ] 多轮对话上下文保持
- [ ] session_id 正确创建和传递

**预估工时**: 1h  
**依赖**: All

---

## 任务状态

- [x] Task 1: 创建数据模型 ✅
- [x] Task 2: 创建聊天服务层 ✅
- [x] Task 3: 创建 Chat API 路由 ✅
- [x] Task 4: 集成到 main.py ✅
- [x] Task 5: 编写单元测试 ✅
- [x] Task 6: 功能验收测试 ✅ (通过自动化测试验证)

---

## 备注

- MVP阶段不包含持久化存储
- Agent 固定为"秘书小秘"
- 日志级别：INFO/WARN/DEBUG
