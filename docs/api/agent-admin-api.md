# Agent Admin API 接口文档

**版本**: v0.2.0  
**日期**: 2026-04-20  
**状态**: 开发中

---

## 1. 概述

### 1.1 接口地址

```
http://{host}:{port}/plugins/admin/api/agents
```

默认：`http://localhost:8000/plugins/admin/api/agents`

### 1.2 设计理念

本 API 采用**极简设计**，直接操作原始 Markdown 文件内容：

- **API 只处理两个核心字段**：`agent_id` 和 `markdown`
- **后端不做任何解析或转换**：用户提交什么就存储什么
- **前端/CLI 负责格式化**：markdown 的结构完全由用户控制

### 1.3 架构说明

```
┌─────────────────────────────────────────────────────────┐
│                      FastAPI App                        │
├─────────────────────────────────────────────────────────┤
│                   Plugin Manager                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              Admin Plugin (builtin)              │    │
│  │  ┌────────────────────────────────────────────┐ │    │
│  │  │           AgentService (Business Logic)    │ │    │
│  │  ├────────────────────────────────────────────┤ │    │
│  │  │        AgentRepository (File I/O)         │ │    │
│  │  └────────────────────────────────────────────┘ │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 1.4 数据存储

Agent 定义以 Markdown 文件形式存储在 `~/.dpskopc/agents/` 目录：

```
~/.dpskopc/agents/
├── 秘书.md
├── engineering/
│   ├── 前端.md
│   └── 后端.md
└── design/
    └── UI.md
```

---

## 2. 接口列表

### 2.1 Agent 管理

| 接口 | 方法 | 描述 |
|------|------|------|
| [`/agents/`](#31-列出所有-agent-get-agents) | GET | 列出所有 Agent |
| [`/agents/{agent_id}`](#32-获取单个-agent-get-agentsagent_id) | GET | 获取 Agent 原始 Markdown |
| [`/agents/`](#33-创建-agent-post-agents) | POST | 创建 Agent |
| [`/agents/{agent_id}`](#34-更新-agent-put-agentsagent_id) | PUT | 更新 Agent |
| [`/agents/{agent_id}`](#35-删除-agent-delete-agentsagent_id) | DELETE | 删除 Agent |

### 2.2 Team (部门) 管理

| 接口 | 方法 | 描述 |
|------|------|------|
| [`/agents/teams/`](#41-列出所有部门-get-agentssteams) | GET | 列出所有部门 |
| [`/agents/teams/`](#42-创建部门-post-agentssteams) | POST | 创建部门 |
| [`/agents/teams/{team_name}`](#43-删除部门-delete-agentssteamsteam_name) | DELETE | 删除部门 |

---

## 3. Agent 接口详情

### 3.1 列出所有 Agent

**端点**: `GET /plugins/admin/api/agents/`

获取所有 Agent 列表，可选按部门过滤。

#### 请求

**Query 参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| team | string | ❌ | 按部门名称过滤 |

#### 响应

**200 OK**
```json
[
  {
    "agent_id": "engineering/frontend",
    "name": "前端工程师",
    "team": "engineering"
  },
  {
    "agent_id": "秘书",
    "name": "秘书小秘",
    "team": ""
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| agent_id | string | Agent 唯一标识 |
| name | string | 显示名称（从 frontmatter 提取） |
| team | string | 所属部门（空字符串表示根目录） |

---

### 3.2 获取单个 Agent

**端点**: `GET /plugins/admin/api/agents/{agent_id}`

获取指定 Agent 的原始 Markdown 内容。

#### 请求

**路径参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agent_id | string | ✅ | Agent 标识 |

#### 响应

**200 OK**
```json
{
  "agent_id": "engineering/frontend",
  "markdown": "---\nname: 前端工程师\ndescription: 专业前端开发\nskills:\n  - react\n---\n\n# 前端工程师\n\n你是一个专业的前端工程师..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| agent_id | string | Agent 标识 |
| markdown | string | 原始 Markdown 内容（包含 frontmatter） |

**404 Not Found**
```json
{
  "detail": "Agent not found: engineering/frontend"
}
```

---

### 3.3 创建 Agent

**端点**: `POST /plugins/admin/api/agents/`

创建新的 Agent。

#### 请求

**Body**:
```json
{
  "agent_id": "engineering/frontend",
  "markdown": "---\nname: 前端工程师\ndescription: 专业前端开发\nskills:\n  - react\n---\n\n# 前端工程师\n\n你是一个专业的前端工程师，擅长 React、TypeScript..."
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agent_id | string | ✅ | 唯一标识，可包含部门前缀 |
| markdown | string | ✅ | 原始 Markdown 内容 |

#### 响应

**201 Created**
```json
{
  "agent_id": "engineering/frontend",
  "message": "Agent 'engineering/frontend' created successfully"
}
```

**400 Bad Request**（验证失败）
```json
{
  "detail": "Markdown content cannot be empty"
}
```

**409 Conflict**（已存在）
```json
{
  "detail": "Agent already exists: engineering/frontend"
}
```

---

### 3.4 更新 Agent

**端点**: `PUT /plugins/admin/api/agents/{agent_id}`

更新现有 Agent 的 Markdown 内容。

#### 请求

**路径参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agent_id | string | ✅ | Agent 标识 |

**Body**:
```json
{
  "markdown": "---\nname: 高级前端工程师\ndescription: 更新后的描述\n---\n\n# 高级前端工程师\n\n更新后的内容..."
}
```

#### 响应

**200 OK**
```json
{
  "agent_id": "engineering/frontend",
  "message": "Agent 'engineering/frontend' updated successfully"
}
```

**404 Not Found**
```json
{
  "detail": "Agent not found: engineering/frontend"
}
```

---

### 3.5 删除 Agent

**端点**: `DELETE /plugins/admin/api/agents/{agent_id}`

删除指定的 Agent。

#### 请求

**路径参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agent_id | string | ✅ | Agent 标识 |

#### 响应

**200 OK**
```json
{
  "agent_id": "engineering/frontend",
  "message": "Agent 'engineering/frontend' deleted successfully"
}
```

**404 Not Found**
```json
{
  "detail": "Agent not found: engineering/frontend"
}
```

---

## 4. Team 接口详情

### 4.1 列出所有部门

**端点**: `GET /plugins/admin/api/agents/teams/`

获取所有部门（子目录）列表。

#### 请求

无参数

#### 响应

**200 OK**
```json
[
  {
    "name": "engineering",
    "path": "engineering",
    "agent_count": 3
  },
  {
    "name": "design",
    "path": "design",
    "agent_count": 1
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 部门名称 |
| path | string | 目录路径 |
| agent_count | int | 该部门下的 Agent 数量 |

---

### 4.2 创建部门

**端点**: `POST /plugins/admin/api/agents/teams/`

创建新的部门目录。

#### 请求

**Body**:
```json
{
  "team_name": "engineering"
}
```

支持嵌套目录：
```json
{
  "team_name": "engineering/backend"
}
```

#### 响应

**201 Created**
```json
{
  "team": "engineering",
  "path": "engineering",
  "message": "Team 'engineering' created successfully"
}
```

**409 Conflict**
```json
{
  "detail": "Team already exists: engineering"
}
```

---

### 4.3 删除部门

**端点**: `DELETE /plugins/admin/api/agents/teams/{team_name}`

删除指定的部门目录。

#### 请求

**路径参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| team_name | string | ✅ | 部门名称 |

**Query 参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| force | boolean | ❌ | 是否强制删除非空目录，默认 false |

#### 响应

**200 OK**
```json
{
  "team": "engineering",
  "message": "Team 'engineering' deleted successfully"
}
```

**409 Conflict**（非空且未强制）
```json
{
  "detail": "Team 'engineering' is not empty (3 agents). Use force=true to delete anyway."
}
```

---

## 5. 错误处理

### 5.1 HTTP 状态码

| 状态码 | 说明 | 常见原因 |
|--------|------|----------|
| 200 | 成功 | GET/PUT/DELETE 请求成功 |
| 201 | 已创建 | POST 请求成功创建资源 |
| 400 | 请求参数错误 | markdown 为空、agent_id 格式错误 |
| 404 | 资源不存在 | agent_id 或 team_name 不存在 |
| 409 | 资源冲突 | agent_id 或 team_name 已存在 |
| 500 | 服务器内部错误 | 文件系统错误等 |

### 5.2 错误响应格式

```json
{
  "detail": "错误描述"
}
```

---

## 6. 使用示例

### 6.1 cURL 示例

```bash
# 列出所有 Agent
curl http://localhost:8000/plugins/admin/api/agents/

# 获取单个 Agent（返回原始 markdown）
curl http://localhost:8000/plugins/admin/api/agents/秘书

# 创建 Agent
curl -X POST http://localhost:8000/plugins/admin/api/agents/ \
  -H "Content-Type: application/json" \
  -d '{
    "agent_id": "engineering/frontend",
    "markdown": "---\nname: 前端工程师\ndescription: 专业前端开发\nskills:\n  - react\n---\n\n# 前端工程师\n\n你是一个专业的前端工程师..."
  }'

# 更新 Agent
curl -X PUT http://localhost:8000/plugins/admin/api/agents/engineering/frontend \
  -H "Content-Type: application/json" \
  -d '{"markdown": "---\nname: 高级前端工程师\n---\n\n# 高级前端工程师\n\n更新后的内容..."}'

# 删除 Agent
curl -X DELETE http://localhost:8000/plugins/admin/api/agents/engineering/frontend

# 列出所有部门
curl http://localhost:8000/plugins/admin/api/agents/teams/

# 创建部门
curl -X POST http://localhost:8000/plugins/admin/api/agents/teams/ \
  -H "Content-Type: application/json" \
  -d '{"team_name": "devops"}'
```

### 6.2 Python 示例

```python
import requests

BASE_URL = "http://localhost:8000/plugins/admin/api"

# 列出所有 Agent
response = requests.get(f"{BASE_URL}/agents/")
print(response.json())

# 获取单个 Agent
response = requests.get(f"{BASE_URL}/agents/engineering/frontend")
markdown_content = response.json()["markdown"]
print(markdown_content)

# 创建 Agent
agent_markdown = """---
name: 后端工程师
description: 专业后端开发
skills:
  - python
  - fastapi
---

# 后端工程师

你是一个专业后端工程师...
"""
response = requests.post(
    f"{BASE_URL}/agents/",
    json={
        "agent_id": "engineering/backend",
        "markdown": agent_markdown
    }
)
print(response.json())

# 更新 Agent
response = requests.put(
    f"{BASE_URL}/agents/engineering/backend",
    json={"markdown": "新的 markdown 内容..."}
)

# 删除 Agent
response = requests.delete(f"{BASE_URL}/agents/engineering/backend")
print(response.json())
```

### 6.3 JavaScript 示例

```javascript
const BASE_URL = 'http://localhost:8000/plugins/admin/api';

// 列出所有 Agent
async function listAgents() {
  const response = await fetch(`${BASE_URL}/agents/`);
  return response.json();
}

// 获取单个 Agent
async function getAgent(agentId) {
  const response = await fetch(`${BASE_URL}/agents/${encodeURIComponent(agentId)}`);
  return response.json();
}

// 创建 Agent
async function createAgent(agentId, markdown) {
  const response = await fetch(`${BASE_URL}/agents/`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ agent_id: agentId, markdown })
  });
  return response.json();
}

// 更新 Agent
async function updateAgent(agentId, markdown) {
  const response = await fetch(`${BASE_URL}/agents/${encodeURIComponent(agentId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ markdown })
  });
  return response.json();
}

// 删除 Agent
async function deleteAgent(agentId) {
  const response = await fetch(`${BASE_URL}/agents/${encodeURIComponent(agentId)}`, {
    method: 'DELETE'
  });
  return response.json();
}
```

### 6.4 Markdown 编辑器集成

由于 API 直接操作原始 Markdown，前端可以使用现有的 Markdown 编辑器：

```javascript
// 使用 @monaco-editor 或其他 Markdown 编辑器
const editor = new MarkdownEditor(document.getElementById('editor'));

// 获取内容并保存
const markdown = editor.getValue();
await updateAgent(agentId, markdown);

// 加载内容
const { markdown } = await getAgent(agentId);
editor.setValue(markdown);
```

---

## 7. CLI 扩展（规划中）

```bash
# 列出 Agent
dpsk agent list

# 获取 Agent
dpsk agent get engineering/frontend

# 编辑 Agent（使用默认编辑器打开 markdown）
dpsk agent edit engineering/frontend

# 创建 Agent
dpsk agent create engineering/frontend --markdown "file.md"

# 删除 Agent
dpsk agent delete engineering/frontend

# 管理部门
dpsk team list
dpsk team create engineering
dpsk team delete engineering
```

---

## 8. 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.2.0 | 2026-04-20 | 简化设计：API 直接操作原始 markdown |
| v0.1.0 | 2026-04-20 | 初始版本，复杂结构设计 |
