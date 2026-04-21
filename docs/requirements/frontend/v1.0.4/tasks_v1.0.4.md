/mnt/w/workspace/dpsk-opc/docs/requirements/frontend/v1.0.4

**版本**: v1.0.4  
**日期**: 2026-04-20  
**需求文档**: `summary_v1.0.4.md`

---

## 1. 任务总览

### 1.1 迭代目标

| 目标 | 功能点 | 优先级 |
|------|--------|--------|
| **MVP 重构** | 侧边栏 + 路由调整 + 布局重构 | P0 |
| **Agent 管理** | 列表 + 详情 + CRUD | P0 |
| **桌面端适配** | Electron 集成 | P1 |

### 1.2 任务统计

| 模块 | 任务数 | P0 | P1 | P2 |
|------|--------|-----|-----|-----|
| MVP 重构 | 8 | 4 | 3 | 1 |
| Agent 管理 | 12 | 5 | 6 | 1 |
| 桌面端适配 | 3 | 2 | 1 | 0 |
| **总计** | **23** | **11** | **10** | **2** |

---

## 2. Sprint 1: MVP 重构

### 2.1 任务列表

| # | 任务 | 优先级 | 预估工时 | 状态 |
|---|------|--------|----------|------|
| 1.1 | 创建 BasicLayout 组件 | P0 | 2h | ⬜ |
| 1.2 | 实现 Sidebar 侧边栏 | P0 | 2h | ⬜ |
| 1.3 | 调整 UmiJS 路由配置 | P0 | 1h | ⬜ |
| 1.4 | 迁移 Chat 页面到新布局 | P0 | 1h | ⬜ |
| 1.5 | 删除 Home 页面，保留重定向 | P1 | 0.5h | ⬜ |
| 1.6 | 添加全局样式变量 | P1 | 1h | ⬜ |
| 1.7 | 响应式布局适配 | P1 | 1h | ⬜ |
| 1.8 | 样式细节调整 | P2 | 1h | ⬜ |

**Sprint 1 目标**: 完成侧边栏 + 基础布局重构

---

### 2.2 Task 1.1: 创建 BasicLayout 组件

**文件**: `cat/src/layouts/BasicLayout/index.tsx`

**验收标准**:
- [ ] 组件正确渲染侧边栏和内容区
- [ ] 支持 children 插槽
- [ ] 响应式布局（桌面端适配）
- [ ] 样式符合设计规范

---

### 2.3 Task 1.2: 实现 Sidebar 侧边栏

**文件**: `cat/src/layouts/BasicLayout/components/Sidebar/`

**验收标准**:
- [ ] 侧边栏可展开/收起
- [ ] 动画流畅（300ms ease-out）
- [ ] 遮罩层正确显示
- [ ] ESC 键关闭侧边栏
- [ ] 导航项正确高亮当前路由

---

### 2.4 Task 1.3: 调整路由配置

**文件**: `cat/src/app.ts`

**验收标准**:
- [ ] `/` 重定向到 `/chat`
- [ ] `/chat` 使用 BasicLayout
- [ ] `/chat/agents` 等子路由嵌套
- [ ] 刷新保持当前路由

---

### 2.5 Task 1.4: 迁移 Chat 页面

**文件**: `cat/src/pages/Chat/index.tsx`

**验收标准**:
- [ ] 页面在新布局中正常显示
- [ ] 原有功能不受影响
- [ ] 返回按钮逻辑正确

---

## 3. Sprint 2: Agent 列表 + 详情

### 3.1 任务列表

| # | 任务 | 优先级 | 预估工时 | 状态 |
|---|------|--------|----------|------|
| 2.1 | 创建 Agent 类型定义 | P0 | 0.5h | ⬜ |
| 2.2 | 创建 AgentService API 封装 | P0 | 1h | ⬜ |
| 2.3 | 实现 AgentList 列表页 | P0 | 2h | ⬜ |
| 2.4 | 实现 AgentDetail 详情页 | P0 | 2h | ⬜ |
| 2.5 | 实现 AgentCard 组件 | P0 | 1h | ⬜ |
| 2.6 | 实现 Sidebar 导航集成 | P0 | 0.5h | ⬜ |
| 2.7 | 实现加载状态/空状态 | P1 | 1h | ⬜ |
| 2.8 | 完善错误处理 | P1 | 1h | ⬜ |

**Sprint 2 目标**: Agent 核心功能 P0 上线

---

### 3.2 Task 2.1: Agent 类型定义

**文件**: `cat/src/models/agent.ts`

**验收标准**:
- [ ] `Agent` 接口定义完整
- [ ] `AgentDetail` 接口定义完整
- [ ] `Team` 接口定义完整
- [ ] 请求/响应类型定义

---

### 3.3 Task 2.2: AgentService API 封装

**文件**: `cat/src/services/agentService.ts`

**验收标准**:
- [ ] `list()` 获取所有 Agent
- [ ] `get(agentId)` 获取单个 Agent
- [ ] `create()` 创建 Agent
- [ ] `update()` 更新 Agent
- [ ] `delete()` 删除 Agent
- [ ] `listTeams()` 获取 Team 列表
- [ ] `createTeam()` 创建 Team
- [ ] `deleteTeam()` 删除 Team

---

### 3.4 Task 2.3: AgentList 列表页

**文件**: `cat/src/pages/Chat/agents/index.tsx`

**验收标准**:
- [ ] 正确显示所有 Agent
- [ ] 按 Team 分组
- [ ] 支持搜索
- [ ] 支持 Team 筛选
- [ ] 新建按钮可用

---

### 3.5 Task 2.4: AgentDetail 详情页

**文件**: `cat/src/pages/Chat/agents/$id.tsx`

**验收标准**:
- [ ] 正确显示 Agent 信息
- [ ] Markdown 预览正确
- [ ] 编辑入口可用
- [ ] 删除入口可用

---

## 4. Sprint 3: Agent CRUD + Team 管理

### 4.1 任务列表

| # | 任务 | 优先级 | 预估工时 | 状态 |
|---|------|--------|----------|------|
| 3.1 | 实现 AgentForm 新建/编辑页 | P1 | 3h | ⬜ |
| 3.2 | 集成 Markdown 编辑器 | P1 | 1h | ⬜ |
| 3.3 | 实现 Agent 删除 + 确认 | P1 | 1h | ⬜ |
| 3.4 | 实现 Team 管理功能 | P2 | 2h | ⬜ |
| 3.5 | 表单验证完善 | P1 | 1h | ⬜ |
| 3.6 | 单元测试 | P1 | 2h | ⬜ |
| 3.7 | E2E 测试 | P2 | 2h | ⬜ |

**Sprint 3 目标**: Agent 完整功能上线

---

### 4.2 Task 3.1: AgentForm 新建/编辑页

**文件**: `cat/src/pages/Chat/agents/new.tsx`  
**文件**: `cat/src/pages/Chat/agents/$id.tsx` (编辑部分)

**验收标准**:
- [ ] 新建页面表单完整
- [ ] 编辑页面数据回填
- [ ] 保存成功跳转
- [ ] 保存失败错误提示

---

### 4.3 Task 3.2: Markdown 编辑器集成

**依赖**: `@uiw/react-md-editor`

**验收标准**:
- [ ] 编辑器正常渲染
- [ ] 工具栏功能可用
- [ ] 预览切换正常
- [ ] 内容正确保存/加载

---

### 4.4 Task 3.3: Agent 删除确认

**验收标准**:
- [ ] 删除按钮触发确认对话框
- [ ] 取消操作不删除
- [ ] 确认删除调用 API
- [ ] 删除成功跳转列表

---

## 5. Sprint 4: 桌面端适配 + 收尾

### 5.1 任务列表

| # | 任务 | 优先级 | 预估工时 | 状态 |
|---|------|--------|----------|------|
| 4.1 | Electron 打包配置 | P1 | 2h | ⬜ |
| 4.2 | 窗口配置优化 | P0 | 1h | ⬜ |
| 4.3 | 桌面端特殊处理 | P0 | 1h | ⬜ |
| 4.4 | 集成测试 | P1 | 2h | ⬜ |
| 4.5 | 文档更新 | P2 | 1h | ⬜ |

---

### 5.2 Task 4.1: Electron 打包配置

**文件**: `cat/electron/`

**验收标准**:
- [ ] electron-builder 配置正确
- [ ] 打包产物为 .exe
- [ ] 应用图标正确
- [ ] 版本号正确

---

## 6. 任务依赖关系

```
Sprint 1: MVP 重构
    │
    ├── Task 1.1 ──┐
    ├── Task 1.2 ──┼── Task 1.3 ── Task 1.4
    └── Task 1.5 ──┘

Sprint 2: Agent 列表 + 详情
    │
    ├── Task 2.1 ──┐
    ├── Task 2.2 ──┼── Task 2.3 ── Task 2.6
    │              └── Task 2.5 ──┘
    └── Task 2.4 ── Task 2.7

Sprint 3: Agent CRUD + Team
    │
    ├── Task 3.1 ── Task 3.2 ── Task 3.5
    ├── Task 3.3
    └── Task 3.4

Sprint 4: 桌面端适配
    │
    ├── Task 4.1 ── Task 4.2 ── Task 4.3
    └── Task 4.4 ── Task 4.5
```

---

## 7. 工时估算

| Sprint | 任务数 | 预估工时 | 说明 |
|--------|--------|----------|------|
| Sprint 1 | 8 | 8.5h | MVP 重构 |
| Sprint 2 | 8 | 9h | Agent 核心 |
| Sprint 3 | 7 | 9h | Agent 完整 |
| Sprint 4 | 5 | 7h | 桌面端 |
| **总计** | **28** | **~33.5h** | 约 5 人天 |

---

## 8. 里程碑

| 里程碑 | 日期 | 交付 |
|--------|------|------|
| M1 | Sprint 1 结束 | 侧边栏 + 新布局可用 |
| M2 | Sprint 2 结束 | Agent 列表 + 详情可用 |
| M3 | Sprint 3 结束 | Agent 完整 CRUD 可用 |
| M4 | Sprint 4 结束 | Electron 打包完成 |

---

## 9. 任务状态

### Sprint 1: MVP 重构
- [ ] Task 1.1: 创建 BasicLayout 组件
- [ ] Task 1.2: 实现 Sidebar 侧边栏
- [ ] Task 1.3: 调整 UmiJS 路由配置
- [ ] Task 1.4: 迁移 Chat 页面到新布局
- [ ] Task 1.5: 删除 Home 页面，保留重定向
- [ ] Task 1.6: 添加全局样式变量
- [ ] Task 1.7: 响应式布局适配
- [ ] Task 1.8: 样式细节调整

### Sprint 2: Agent 列表 + 详情
- [ ] Task 2.1: 创建 Agent 类型定义
- [ ] Task 2.2: 创建 AgentService API 封装
- [ ] Task 2.3: 实现 AgentList 列表页
- [ ] Task 2.4: 实现 AgentDetail 详情页
- [ ] Task 2.5: 实现 AgentCard 组件
- [ ] Task 2.6: 实现 Sidebar 导航集成
- [ ] Task 2.7: 实现加载状态/空状态
- [ ] Task 2.8: 完善错误处理

### Sprint 3: Agent CRUD + Team 管理
- [ ] Task 3.1: 实现 AgentForm 新建/编辑页
- [ ] Task 3.2: 集成 Markdown 编辑器
- [ ] Task 3.3: 实现 Agent 删除 + 确认
- [ ] Task 3.4: 实现 Team 管理功能
- [ ] Task 3.5: 表单验证完善
- [ ] Task 3.6: 单元测试
- [ ] Task 3.7: E2E 测试

### Sprint 4: 桌面端适配 + 收尾
- [ ] Task 4.1: Electron 打包配置
- [ ] Task 4.2: 窗口配置优化
- [ ] Task 4.3: 桌面端特殊处理
- [ ] Task 4.4: 集成测试
- [ ] Task 4.5: 文档更新

---

**文档版本**: v1.0.4  
**创建日期**: 2026-04-20
