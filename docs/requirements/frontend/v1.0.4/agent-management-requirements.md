# DPSK-OPC v1.0.4 Agent 管理功能需求

**版本**: v1.0.4  
**日期**: 2026-04-20  
**状态**: 待评审  
**API 文档**: `docs/api/agent-admin-api.md`

---

## 1. 概述

### 1.1 功能定位

Agent 管理是 v1.0.4 新增的核心功能，位于 **L2 功能层**，通过侧边栏入口访问。

### 1.2 功能范围

| 功能 | 优先级 | 说明 |
|------|--------|------|
| Agent 列表 | P0 | 查看所有 Agent，支持按 Team 分组 |
| Agent 详情 | P0 | 查看单个 Agent 的 Markdown 内容 |
| Agent 创建 | P1 | 新建 Agent，支持 Markdown 编辑器 |
| Agent 编辑 | P1 | 编辑现有 Agent |
| Agent 删除 | P1 | 删除 Agent，含二次确认 |
| Team 管理 | P2 | 部门的增删操作 |

### 1.3 层级归属

```
┌─────────────────────────────────────────────────────────────┐
│  L2: 功能层 - Agent 管理                                    │
│  ─────────────────────────────────────────────────────────  │
│  入口：侧边栏「🤖 Agent 管理」                                │
│  子页面：                                                    │
│    - Agent 列表 (/chat/agents)                              │
│    - Agent 详情/编辑 (/chat/agents/:id) ← L3                │
│    - 新建 Agent (/chat/agents/new) ← L3                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. API 映射

### 2.1 接口对照表

| 前端功能 | API 端点 | 方法 | 说明 |
|----------|----------|------|------|
| 列表 | `/agents/` | GET | 获取所有 Agent |
| 列表(按Team) | `/agents/?team=xxx` | GET | 按 Team 过滤 |
| 详情 | `/agents/{agent_id}` | GET | 获取单个 Agent |
| 创建 | `/agents/` | POST | 创建新 Agent |
| 更新 | `/agents/{agent_id}` | PUT | 更新 Agent |
| 删除 | `/agents/{agent_id}` | DELETE | 删除 Agent |
| Team 列表 | `/agents/teams/` | GET | 获取所有 Team |
| 创建 Team | `/agents/teams/` | POST | 创建 Team |
| 删除 Team | `/agents/teams/{team_name}` | DELETE | 删除 Team |

### 2.2 API Base URL

```
http://localhost:8000/plugins/admin/api
```

---

## 3. Agent 列表页

### 3.1 页面结构

```
┌───────────────────────────────────────────────────────────────┐
│ ← 返回    Agent 管理                              [+ 新建]   │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  全部 (10)  │ engineering (5)  │ design (3)  │ 其他 (2)       │
│  ─────────────────────────────────────────────────────────   │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🤖 秘书小秘                                    →        │ │
│  │ 没有分类                                                    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  👥 engineering                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🤖 前端工程师                                    →        │ │
│  └─────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🤖 后端工程师                                    →        │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  👥 design                                                    │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🤖 UI 设计师                                        →        │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 3.2 功能需求

| 需求 | 说明 |
|------|------|
| Team 分组 | Agent 按所属 Team 分组显示，无 Team 的显示在「全部」下方 |
| 搜索 | 输入框支持按 Agent 名称搜索 |
| 筛选 | Tab 或下拉按 Team 筛选 |
| 新建入口 | 右上角「+ 新建」按钮 |
| 点击跳转 | 点击 Agent 项跳转到详情页 |

### 3.3 状态处理

| 状态 | UI |
|------|-----|
| 加载中 | 骨架屏 (Skeleton) |
| 空列表 | 空状态引导：「还没有 Agent，点击新建开始使用」 |
| 加载失败 | 错误提示 + 重试按钮 |

---

## 4. Agent 详情页

### 4.1 页面结构

```
┌───────────────────────────────────────────────────────────────┐
│ ← 返回    秘书小秘                                  [编辑]   │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                                                         │ │
│  │  Agent ID: 秘书                                          │ │
│  │  Team: -                                                │ │
│  │                                                         │ │
│  │  ───────────────────────────────                        │ │
│  │                                                         │ │
│  │  Markdown 内容预览：                                    │ │
│  │                                                         │ │
│  │  ```                                                     │ │
│  │  ---                                                     │ │
│  │  name: 秘书小秘                                          │ │
│  │  description: 您的私人助理                              │ │
│  │  ---                                                     │ │
│  │                                                         │ │
│  │  # 秘书小秘                                              │ │
│  │                                                         │ │
│  │  你是一个热情友好的秘书助手...                            │ │
│  │  ```                                                     │ │
│  │                                                         │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ─────────────────────────────────────────────────────────   │
│                                                               │
│                          [删除 Agent]                         │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 4.2 功能需求

| 需求 | 说明 |
|------|------|
| 信息展示 | 显示 Agent ID、Team、Markdown 内容 |
| Markdown 预览 | 代码块形式展示原始 Markdown |
| 编辑入口 | 右上角「编辑」按钮 |
| 删除入口 | 底部「删除 Agent」按钮 |
| 返回导航 | 左上角「← 返回」回到列表 |

### 4.3 Markdown 预览增强

| 功能 | 说明 |
|------|------|
| 语法高亮 | 代码块使用 Prism.js 或同款高亮 |
| 复制按钮 | 支持一键复制 Markdown 内容 |
| 滚动 | 内容区独立滚动 |

---

## 5. Agent 创建/编辑页

### 5.1 页面结构

```
┌───────────────────────────────────────────────────────────────┐
│ ← 返回    新建 Agent (编辑 Agent)                    [保存]   │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  Agent ID *                                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ engineering/frontend                                    │ │
│  └─────────────────────────────────────────────────────────┘ │
│  支持字母、数字、下划线，可包含路径分隔符                      │
│                                                               │
│  Team                                                         │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ engineering                               ▼            │ │
│  └─────────────────────────────────────────────────────────┘ │
│  留空表示根目录，或选择现有 Team                              │
│                                                               │
│  Markdown 内容 *                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Toolbar: B I | H1 H2 H3 | List | Code | Preview       │ │
│  ├─────────────────────────────────────────────────────────┤ │
│  │                                                         │ │
│  │ ---                                                      │ │
│  │ name: 前端工程师                                         │ │
│  │ description: 专业前端开发                                │ │
│  │ ---                                                      │ │
│  │                                                         │ │
│  │ # 前端工程师                                             │ │
│  │                                                         │ │
│  │ 你是一个专业的前端工程师...                              │ │
│  │                                                         │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ─────────────────────────────────────────────────────────   │
│  支持 Markdown 语法，frontmatter 用于配置 Agent 属性          │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 5.2 功能需求

| 需求 | 说明 |
|------|------|
| Agent ID 输入 | 必填，唯一性校验 |
| Team 选择 | 下拉选择已有 Team，或新建 |
| Markdown 编辑器 | 完整 Markdown 编辑功能 |
| 工具栏 | 常用格式快捷按钮 |
| 预览切换 | 编辑/预览切换 |
| 保存 | 提交表单，保存到后端 |
| 返回 | 放弃编辑，返回列表 |

### 5.3 表单验证

| 字段 | 规则 | 错误提示 |
|------|------|----------|
| Agent ID | 必填，格式正确 | "请输入 Agent ID" |
| Agent ID | 唯一 | "该 Agent ID 已存在" |
| Markdown | 必填，非空 | "请输入 Markdown 内容" |
| Markdown | 包含 frontmatter | "建议包含 frontmatter 配置" (警告) |

### 5.4 Markdown 编辑器选型

| 方案 | 优点 | 缺点 |
|------|------|------|
| @uiw/react-md-editor | 功能完整，主题丰富 | 包体积较大 |
| react-markdown + textarea | 轻量 | 功能简单 |
| Monaco Editor | 专业，代码友好 | 学习成本 |

**推荐**: `@uiw/react-md-editor`，平衡功能和体积。

---

## 6. Agent 删除确认

### 6.1 确认对话框

```
┌───────────────────────────────────────────────────────────────┐
│                                                               │
│                        ⚠️ 确认删除                            │
│                                                               │
│           确定要删除「前端工程师」吗？                          │
│           此操作无法撤销。                                     │
│                                                               │
│           ─────────────────────────────────                   │
│                                                               │
│           [取消]                         [确认删除]            │
│           (次要样式)                    (危险样式/红色)         │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 6.2 交互流程

| 步骤 | 操作 |
|------|------|
| 1 | 点击「删除 Agent」 |
| 2 | 弹出确认对话框 |
| 3 | 用户确认 → 调用 DELETE API → 返回列表 |
| 4 | 用户取消 → 关闭对话框 |

### 6.3 样式规范

| 元素 | 样式 |
|------|------|
| 确认按钮 | 背景色 `#ff4d4f`，hover `#ff7875` |
| 取消按钮 | 边框样式，无填充 |
| 警告图标 | 黄色 `#faad14`，位于标题前 |
| 遮罩 | 半透明黑色 `rgba(0,0,0,0.45)` |

---

## 7. Team 管理

### 7.1 功能范围

| 功能 | 优先级 | 说明 |
|------|--------|------|
| Team 列表 | P2 | 获取所有 Team 及 Agent 数量 |
| 创建 Team | P2 | 在创建/编辑 Agent 时支持创建 |
| 删除 Team | P2 | 删除空 Team（含 Agent 时需确认） |

### 7.2 Team 选择器

```
┌─────────────────────────────────────────────────────────────┐
│ Team                                                         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ engineering                                   ▼         │ │
│ └─────────────────────────────────────────────────────────┘ │
│ [+ 新建 Team]                                               │
└─────────────────────────────────────────────────────────────┘
```

### 7.3 新建 Team 对话框

```
┌───────────────────────────────────────────────────────────────┐
│                        新建 Team                              │
│                                                               │
│   Team 名称 *                                                 │
│   ┌─────────────────────────────────────────────────────────┐ │
│   │ devops                                                │ │
│   └─────────────────────────────────────────────────────────┘ │
│   支持嵌套，如 engineering/backend                             │
│                                                               │
│   ─────────────────────────────────────────────────────────   │
│                                                               │
│           [取消]                           [创建]              │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

---

## 8. 服务层设计

### 8.1 API 服务封装

```typescript
// src/services/agentService.ts

const BASE_URL = 'http://localhost:8000/plugins/admin/api';

// Agent CRUD
export const AgentService = {
  list: (team?: string) => fetch(`${BASE_URL}/agents/?team=${team}`),
  get: (agentId: string) => fetch(`${BASE_URL}/agents/${encodeURIComponent(agentId)}`),
  create: (data: { agent_id: string; markdown: string }) => 
    fetch(`${BASE_URL}/agents/`, { method: 'POST', body: JSON.stringify(data) }),
  update: (agentId: string, markdown: string) => 
    fetch(`${BASE_URL}/agents/${encodeURIComponent(agentId)}`, { 
      method: 'PUT', 
      body: JSON.stringify({ markdown }) 
    }),
  delete: (agentId: string) => 
    fetch(`${BASE_URL}/agents/${encodeURIComponent(agentId)}`, { method: 'DELETE' }),
};

// Team CRUD
export const TeamService = {
  list: () => fetch(`${BASE_URL}/agents/teams/`),
  create: (teamName: string) => 
    fetch(`${BASE_URL}/agents/teams/`, { 
      method: 'POST', 
      body: JSON.stringify({ team_name: teamName }) 
    }),
  delete: (teamName: string, force?: boolean) => 
    fetch(`${BASE_URL}/agents/teams/${teamName}?force=${force}`, { method: 'DELETE' }),
};
```

### 8.2 类型定义

```typescript
// src/models/agent.ts

export interface Agent {
  agent_id: string;
  name?: string;        // 从 frontmatter 提取
  team?: string;
}

export interface AgentDetail {
  agent_id: string;
  markdown: string;     // 原始 Markdown 内容
}

export interface Team {
  name: string;
  path: string;
  agent_count: number;
}

export interface CreateAgentRequest {
  agent_id: string;
  markdown: string;
}

export interface UpdateAgentRequest {
  markdown: string;
}
```

---

## 9. 错误处理

### 9.1 错误类型映射

| HTTP 状态码 | 错误类型 | 用户提示 |
|-------------|----------|----------|
| 400 | 请求参数错误 | "Agent ID 或 Markdown 格式不正确" |
| 404 | 资源不存在 | "Agent 不存在或已被删除" |
| 409 | 资源冲突 | "该 Agent ID 已存在" |
| 500 | 服务器错误 | "服务器异常，请稍后重试" |

### 9.2 前端错误处理

```typescript
// 统一错误处理
const handleError = (error: Response) => {
  switch (error.status) {
    case 404:
      message.error('Agent 不存在或已被删除');
      break;
    case 409:
      message.error('该 Agent ID 已存在');
      break;
    default:
      message.error('操作失败，请稍后重试');
  }
};
```

---

## 10. 组件清单

### 10.1 新增组件

| 组件 | 路径 | 说明 |
|------|------|------|
| AgentList | `pages/Chat/agents/index.tsx` | Agent 列表页 |
| AgentDetail | `pages/Chat/agents/$id.tsx` | Agent 详情页 |
| AgentForm | `pages/Chat/agents/new.tsx` | 新建/编辑页 |
| AgentCard | `components/AgentCard/` | Agent 列表项 |
| TeamSelector | `components/TeamSelector/` | Team 下拉选择器 |
| MarkdownEditor | `components/MarkdownEditor/` | Markdown 编辑器封装 |
| ConfirmDialog | `components/ConfirmDialog/` | 确认对话框 |

### 10.2 修改组件

| 组件 | 修改内容 |
|------|----------|
| Sidebar | 添加 Agent 管理入口 |

---

## 11. 验收标准

### 11.1 Agent 列表

- [ ] 正确显示所有 Agent
- [ ] 按 Team 分组显示
- [ ] 支持按 Team 筛选
- [ ] 支持按名称搜索
- [ ] 空状态引导正确显示
- [ ] 加载中状态正确显示

### 11.2 Agent 详情

- [ ] 正确显示 Agent ID、Team
- [ ] Markdown 内容正确预览
- [ ] 复制功能正常
- [ ] 编辑入口可用
- [ ] 删除入口可用

### 11.3 Agent 创建/编辑

- [ ] 表单验证正确
- [ ] Markdown 编辑器功能完整
- [ ] 保存成功提示
- [ ] 保存失败错误提示
- [ ] Team 选择/创建功能正常

### 11.4 Agent 删除

- [ ] 确认对话框正确显示
- [ ] 取消操作不删除
- [ ] 确认操作正确删除
- [ ] 删除后跳转回列表

### 11.5 Team 管理

- [ ] Team 列表正确获取
- [ ] 新建 Team 成功
- [ ] 删除空 Team 成功
- [ ] 删除非空 Team 提示确认

---

## 12. 任务分解

| 序号 | 任务 | 优先级 | 预估工时 | 依赖 |
|------|------|--------|----------|------|
| 1 | 创建 Agent 类型定义 | P0 | 0.5h | - |
| 2 | 创建 AgentService | P0 | 1h | Task 1 |
| 3 | 实现 AgentList 列表页 | P0 | 2h | Task 2 |
| 4 | 实现 AgentDetail 详情页 | P0 | 2h | Task 2 |
| 5 | 实现 AgentForm 新建/编辑页 | P1 | 3h | Task 2 |
| 6 | 集成 Markdown 编辑器 | P1 | 1h | Task 5 |
| 7 | 实现 Agent 删除功能 | P1 | 1h | Task 4 |
| 8 | 实现 Team 管理功能 | P2 | 2h | Task 2 |
| 9 | 完善错误处理 | P1 | 1h | Task 2 |
| 10 | 集成到 Sidebar | P0 | 0.5h | Task 3 |
| 11 | 单元测试 | P1 | 2h | Task 2-9 |
| 12 | E2E 测试 | P2 | 2h | Task 2-9 |

---

## 13. 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| API 尚未实现 | 高 | 提前对接，使用 Mock 数据 |
| Markdown 编辑器体积大 | 中 | 按需加载，考虑 CodeMirror |
| 特殊字符导致 URL 编码问题 | 中 | 使用 encodeURIComponent |
| Markdown 解析失败 | 低 | 使用可靠的解析库 |

---

**文档版本**: v1.0.4  
**相关文档**:
- 设计原则: `docs/design-principles.md`
- Agent API: `docs/api/agent-admin-api.md`
- MVP 调整: `mvp-refactor-requirements.md`
