# Agent 模块开发任务清单

> **版本**: v1.0.1  
> **更新时间**: 2026-04-09

---

## Phase 1: 基础框架

### Task 1.1: AgentDef 数据结构

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/defs.py` |
| **依赖** | 无 |
| **任务项** | |

- [ ] 定义 `AgentDef` dataclass，包含所有字段
- [ ] 定义 `InstanceStatus` 枚举
- [ ] 定义 `AgentInstance` dataclass
- [ ] 使用 Pydantic 添加验证
- [ ] 添加类型注解和文档字符串

### Task 1.2: Markdown 解析器

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/parser.py` |
| **依赖** | Task 1.1 |
| **任务项** | |

- [ ] 实现 `parse_agent_def(file_path)` 函数
- [ ] 解析 YAML Frontmatter
- [ ] 提取 Markdown 正文作为 description
- [ ] 验证必填字段
- [ ] 返回 `AgentDef` 对象
- [ ] 实现文件路径扫描函数 `scan_agents_dir(dir_path)`

### Task 1.3: AgentRegistry

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/registry.py` |
| **依赖** | Task 1.1, Task 1.2 |
| **任务项** | |

- [ ] 定义 `AgentRegistry` 类
- [ ] 实现 `load_from_dir(dir_path)` 方法
- [ ] 实现 `get(agent_id)` 方法
- [ ] 实现 `list_all()` 方法
- [ ] 实现秘书 Agent 自动创建逻辑
- [ ] 添加线程安全（可选）

---

## Phase 2: agent_runner 框架

### Task 2.1: agent_runner 基础框架

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/runner.py` |
| **依赖** | Bus Module |
| **任务项** | |

- [ ] 定义 `SKILL_HANDLERS` 全局字典
- [ ] 实现 `register_skill(task_name, handler)` 函数
- [ ] 实现 `load_skills_from_dir(dir_path)` 函数
- [ ] 实现 `handle_message(message)` 主处理器
- [ ] 实现经验池查询（精确匹配）
- [ ] 实现经验池存储（异步）
- [ ] 实现主循环 `run()` 函数
- [ ] 添加日志和错误处理

### Task 2.2: 基础技能实现

| 项目 | 内容 |
|------|------|
| **目录** | `src/agent/skills/` |
| **依赖** | Task 2.1 |
| **任务项** | |

- [ ] 创建 `skills/` 目录结构
- [ ] 实现 `echo` 技能（测试用）
- [ ] 实现 `debug_js` 技能示例
- [ ] 实现技能加载机制

---

## Phase 3: 实例化与安全集成

### Task 3.1: AgentSpawner

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/spawner.py` |
| **依赖** | Security Module |
| **任务项** | |

- [ ] 定义 `AgentSpawner` 抽象基类
- [ ] 定义 `AgentHandle` 类
- [ ] 实现 `spawn(agent_def, task_context, reuse_existing, ttl_seconds)`
- [ ] 实现 `get_instance(instance_id)`
- [ ] 实现 `list_instances(agent_id)`
- [ ] 实现实例生命周期管理（stop, destroy）
- [ ] 实现临时令牌生成

### Task 3.2: 与安全模块集成

| 项目 | 内容 |
|------|------|
| **依赖** | Task 3.1, Security Module |
| **任务项** | |

- [ ] 集成安全模块的沙箱创建接口
- [ ] 集成环境变量注入
- [ ] 实现沙箱监控和心跳机制
- [ ] 实现实例状态同步

### Task 3.3: 并发控制与队列

| 项目 | 内容 |
|------|------|
| **依赖** | Task 3.1 |
| **任务项** | |

- [ ] 实现 `max_instances` 并发控制
- [ ] 实现任务队列
- [ ] 实现队列满时的错误处理
- [ ] 实现空闲实例的队列消费

---

## Phase 4: 管理与 API

### Task 4.1: AgentManager

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/manager.py` |
| **依赖** | Task 1.3, Task 3.1 |
| **任务项** | |

- [ ] 定义 `AgentManager` 类
- [ ] 实现 `spawn_agent(agent_id, ttl_seconds)`
- [ ] 实现 `destroy_instance(instance_id, force)`
- [ ] 实现 `list_instances(agent_id)`
- [ ] 实现 `get_agent_def(agent_id)`
- [ ] 实现 `list_agent_defs()`
- [ ] 实现秘书 Agent 自动启动

### Task 4.2: 管理 API

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/api.py` |
| **依赖** | Task 4.1 |
| **任务项** | |

- [ ] 实现 `GET /api/v1/agents/defs`
- [ ] 实现 `GET /api/v1/agents/defs/{agent_id}`
- [ ] 实现 `GET /api/v1/agents/instances`
- [ ] 实现 `POST /api/v1/agents/{agent_id}/spawn`
- [ ] 实现 `DELETE /api/v1/agents/instances/{instance_id}`
- [ ] 实现 `GET /api/v1/agents/instances/{instance_id}/queue` (P1)

---

## Phase 5: 经验池

### Task 5.1: 经验池基础

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/experience.py` |
| **依赖** | 无 |
| **任务项** | |

- [ ] 实现 `ExperienceDB` 类
- [ ] 实现 SQLite 表创建
- [ ] 实现 `store(agent_id, task_name, input_data, output_data)`
- [ ] 实现 `lookup(agent_id, task_name, input_data)` - 精确匹配
- [ ] 实现 `get_stats(agent_id)`
- [ ] 实现索引优化

### Task 5.2: 经验池清理

| 项目 | 内容 |
|------|------|
| **依赖** | Task 5.1 |
| **任务项** | |

- [ ] 实现 `cleanup(agent_id, max_entries)` 方法
- [ ] 实现清理策略：删除低命中+早期记录
- [ ] 实现定时清理（可选）

---

## Phase 6: 可观测性

### Task 6.1: 日志收集

| 项目 | 内容 |
|------|------|
| **文件** | `src/agent/logs.py` |
| **依赖** | 无 |
| **任务项** | |

- [ ] 定义步骤级日志结构
- [ ] 实现 `StepLog` 数据类
- [ ] 实现日志发送到 `system.logging`
- [ ] 实现主后端日志接收和存储

### Task 6.2: 健康检查

| 项目 | 内容 |
|------|------|
| **依赖** | Task 2.1, Task 3.2 |
| **任务项** | |

- [ ] 实现 Agent 发送 `agent.ready` 事件
- [ ] 实现主后端监听并更新状态
- [ ] 实现实例超时检测

---

## Phase 7: 集成测试

### Task 7.1: 单元测试

| 项目 | 内容 |
|------|------|
| **目录** | `tests/` |
| **任务项** | |

- [ ] `test_defs.py` - AgentDef 测试
- [ ] `test_parser.py` - 解析器测试
- [ ] `test_registry.py` - Registry 测试
- [ ] `test_experience.py` - 经验池测试
- [ ] `test_manager.py` - Manager 测试

### Task 7.2: 集成测试

| 项目 | 内容 |
|------|------|
| **任务项** | |

- [ ] 端到端流程测试：定义 → 注册 → 实例化 → 执行 → 销毁
- [ ] 与安全模块集成测试（Mock）
- [ ] 与消息总线集成测试

---

## 进度跟踪

| Phase | 任务 | 状态 | 完成日期 |
|-------|------|------|----------|
| 1.1 | AgentDef 数据结构 | ✅ 完成 | 2026-04-09 |
| 1.2 | Markdown 解析器 | ✅ 完成 | 2026-04-09 |
| 1.3 | AgentRegistry | ✅ 完成 | 2026-04-09 |
| 2.1 | agent_runner 框架 | ✅ 完成 | 2026-04-09 |
| 2.2 | 基础技能实现 | ✅ 完成 | 2026-04-09 |
| 3.1 | AgentSpawner | ✅ 完成 | 2026-04-09 |
| 3.2 | 安全模块集成 | ⚠️ 待集成 | - |
| 3.3 | 并发控制与队列 | ✅ 完成 | 2026-04-09 |
| 4.1 | AgentManager | ✅ 完成 | 2026-04-09 |
| 4.2 | 管理 API | ✅ 完成 | 2026-04-09 |
| 5.1 | 经验池基础 | ✅ 完成 | 2026-04-09 |
| 5.2 | 经验池清理 | ✅ 完成 | 2026-04-09 |
| 6.1 | 日志收集 | ✅ 完成 | 2026-04-09 |
| 6.2 | 健康检查 | ⚠️ 待集成 | - |
| 7.1 | 单元测试 | ✅ 完成 | 2026-04-09 |
| 7.2 | 集成测试 | ⬜ 进行中 | - |
