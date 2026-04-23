# DPSK-OPC Agent 工作空间模块 - 任务清单

> **版本**: v1.0.6  
> **关联文档**: [DPSK-OPC Agent 工作空间模块需求文档](./DPSK-OPC%20Agent%20工作空间模块需求文档.md)  
> **创建日期**: 2026-04-23

---

## 任务概览

| Phase | 任务 | 优先级 | 状态 |
|-------|------|--------|------|
| Phase 1 | Workspace 数据模型 + WorkspaceManager | P0 | ⬜ |
| Phase 2 | AgentContext 管理 | P0 | ⬜ |
| Phase 3 | WorkspaceGuard 装饰器 | P0 | ⬜ |
| Phase 4 | 审计日志 | P0 | ⬜ |
| Phase 5 | ExchangeService | P1 | ⬜ |
| Phase 6 | 共享目录白名单 | P1 | ⬜ |
| Phase 7 | SecurityWorkspaceAdapter | P1 | ⬜ |
| Phase 8 | SharedAccessService（临时共享+审批） | P2 | ⬜ |
| Phase 9 | PathClassifier 路径分类器 | P0 | ⬜ |
| Phase 10 | TemporaryPermissionService 临时权限服务 | P0 | ⬜ |
| Phase 11 | ConfirmationService 用户确认服务 | P0 | ⬜ |
| Phase 12 | 前端确认弹窗 UI | P0 | ⬜ |
| Phase 13 | ExecutionGuard 执行守卫 | P0 | ⬜ |
| Phase 14 | SearchGuard 搜索守卫 | P0 | ⬜ |
| Phase 15 | ListDirGuard 目录列表守卫 | P0 | ⬜ |

---

## Phase 1: Workspace 数据模型 + WorkspaceManager

### 任务 1.1: 创建模块目录结构

**文件**: `backend/src/agent/workspace/__init__.py`

**任务描述**:
```
创建工作空间模块目录结构和初始化文件
```

**验收标准**:
- [ ] `backend/src/agent/workspace/` 目录已创建
- [ ] `__init__.py` 文件存在且导出公共接口

---

### 任务 1.2: 实现数据模型

**文件**: `backend/src/agent/workspace/models.py`

**任务描述**:
```
实现工作空间相关的数据模型：
- Permission 权限枚举
- WorkspaceStatus 状态枚举
- Workspace 数据类
- SharedAccessRequest 临时共享请求
- SharedAccessStatus 状态枚举
```

**验收标准**:
- [ ] `Permission` 枚举包含 READ, WRITE, EXECUTE, LIST, DELETE
- [ ] `Workspace` 数据类包含所有字段
- [ ] `SharedAccessRequest` 包含完整字段
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 1.3: 实现 WorkspaceManager

**文件**: `backend/src/agent/workspace/manager.py`

**任务描述**:
```
实现 WorkspaceManager 类：
- create_workspace(): 创建工作空间和物理目录
- get_workspace(): 根据 agent_id 获取工作空间
- validate_path(): 路径验证（realpath、符号链接、边界检查）
- archive_workspace(): 归档工作空间
- delete_workspace(): 删除工作空间
```

**验收标准**:
- [ ] `create_workspace()` 创建物理目录成功
- [ ] `validate_path()` 正确识别超出范围的路径
- [ ] `validate_path()` 正确识别符号链接穿越
- [ ] `validate_path()` 正确处理白名单目录
- [ ] 单元测试覆盖率 ≥ 80%

---

## Phase 2: AgentContext 管理

### 任务 2.1: 实现 AgentContext

**文件**: `backend/src/agent/workspace/context.py`

**任务描述**:
```
实现 AgentContext 数据类和上下文管理器：
- AgentContext 数据类
- 协程本地存储管理
- set_current_context()
- get_current_context()
- get_current_workspace()
- async context manager（用于 with 语法）
```

**验收标准**:
- [ ] AgentContext 包含 agent_id, workspace_id, workspace_root, permissions
- [ ] 多协程环境下 context 隔离
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 2.2: 集成到 Agent Runner

**文件**: `backend/src/agent/runner.py`

**任务描述**:
```
在 Agent Runner 中集成 AgentContext：
- Agent 启动时创建/获取工作空间
- 设置 AgentContext
- 任务结束时清理 Context
```

**验收标准**:
- [ ] Agent 启动时自动创建工作空间（如果不存在）
- [ ] 任务执行期间 Context 正确传递
- [ ] Context 在任务结束后正确清理

---

## Phase 3: WorkspaceGuard 装饰器

### 任务 3.1: 实现路径验证器

**文件**: `backend/src/agent/workspace/validator.py`

**任务描述**:
```
实现路径验证逻辑：
- resolve_path(): 解析真实路径
- check_boundary(): 检查边界
- check_symlink(): 检查符号链接
- check_permission(): 检查权限
```

**验收标准**:
- [ ] `resolve_path()` 处理各种路径格式
- [ ] 符号链接穿越被正确检测
- [ ] `..` 路径穿越被正确检测
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 3.2: 实现 WorkspaceGuard

**文件**: `backend/src/agent/workspace/guard.py`

**任务描述**:
```
实现 WorkspaceGuard 装饰器类：
- protect(): 装饰工具类
- extract_path_params(): 提取路径参数
- get_required_permission(): 推断所需权限
```

**验收标准**:
- [ ] `protect()` 正确拦截 execute 方法
- [ ] 路径验证失败时返回结构化错误
- [ ] 错误码为 WORKSPACE_ACCESS_DENIED
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 3.3: 集成到文件工具

**文件**: `backend/src/agent/tools/file_tools.py`

**任务描述**:
```
将 WorkspaceGuard 集成到所有文件工具：
- read_file
- write_to_file
- list_dir
- search_file
- search_content
- replace_in_file
- delete_file
- execute_command
```

**验收标准**:
- [ ] 所有文件工具被 WorkspaceGuard 保护
- [ ] 超出范围的路径访问被拒绝
- [ ] 权限不足的操作被拒绝
- [ ] 集成测试通过

---

## Phase 4: 审计日志

### 任务 4.1: 实现 AuditLogger

**文件**: `backend/src/agent/workspace/audit.py`

**任务描述**:
```
实现审计日志模块：
- AuditLogger 类
- log_access(): 记录文件访问
- log_exchange(): 记录交换操作
- log_denied(): 记录拒绝事件
- 异步写入
```

**验收标准**:
- [ ] 审计日志格式为 JSON Lines
- [ ] 包含 timestamp, agent_id, operation, path, allowed
- [ ] 支持异步写入
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 4.2: 集成审计日志

**文件**: `backend/src/agent/workspace/guard.py`

**任务描述**:
```
将审计日志集成到 WorkspaceGuard：
- 每次工具调用后记录审计日志
```

**验收标准**:
- [ ] 成功和失败的访问都被记录
- [ ] 审计日志写入 /storage/audit/ 目录

---

## Phase 5: ExchangeService

### 任务 5.1: 实现 Exchange 元数据模型

**文件**: `backend/src/agent/exchange/models.py`

**任务描述**:
```
创建交换区模块和元数据模型：
- ExchangeStatus 状态枚举
- ExchangeMeta 元数据类
```

**验收标准**:
- [ ] ExchangeStatus 包含 ACTIVE, CONSUMED, EXPIRED
- [ ] ExchangeMeta 包含完整字段
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 5.2: 实现 ExchangeService

**文件**: `backend/src/agent/exchange/service.py`

**任务描述**:
```
实现 ExchangeService：
- push_file(): 推送到交换区
- pull_file(): 从交换区拉取
- cleanup_expired(): 清理过期文件
```

**验收标准**:
- [ ] `push_file()` 创建文件和元数据
- [ ] `pull_file()` 验证权限后返回内容
- [ ] 权限验证正确（只在授权列表中的 Agent 可读）
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 5.3: 集成到 dispatch_task

**文件**: `backend/src/agent/tools/dispatch_task_tool.py`

**任务描述**:
```
在 dispatch_task 中支持 attachments 参数：
- 解析 attachments
- 调用 ExchangeService.push_file()
```

**验收标准**:
- [ ] dispatch_task 支持 attachments 参数
- [ ] attachments 中的文件写入交换区
- [ ] 接收方 Agent 可通过 exchange_id 获取文件

---

## Phase 6: 共享目录白名单

### 任务 6.1: 实现共享目录配置

**文件**: `backend/src/agent/workspace/manager.py`

**任务描述**:
```
扩展 WorkspaceManager：
- add_shared_dir(): 添加白名单目录
- remove_shared_dir(): 移除白名单目录
- get_shared_dirs(): 获取白名单列表
```

**验收标准**:
- [ ] 白名单目录可以添加和移除
- [ ] 验证时正确处理白名单目录
- [ ] 单元测试覆盖率 ≥ 80%

---

## Phase 7: SecurityWorkspaceAdapter

### 任务 7.1: 实现适配器接口

**文件**: `backend/src/agent/security/adapter.py`

**任务描述**:
```
实现 SecurityWorkspaceAdapter：
- register_workspace_with_security(): 注册到安全模块
- create_isolated_environment(): 创建隔离环境
- destroy_isolated_environment(): 销毁隔离环境
```

**验收标准**:
- [ ] 接口定义完整
- [ ] 提供 Mock 实现供开发阶段使用
- [ ] 单元测试覆盖率 ≥ 80%

---

## Phase 8: SharedAccessService

### 任务 8.1: 实现临时共享服务

**文件**: `backend/src/agent/shared/service.py`

**任务描述**:
```
实现 SharedAccessService：
- request_access(): 申请临时访问
- approve_request(): 审批通过
- reject_request(): 审批拒绝
- get_pending_requests(): 获取待审批请求
```

**验收标准**:
- [ ] 申请创建待审批状态
- [ ] 审批后更新权限
- [ ] 支持过期时间
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 8.2: 集成用户审批流程

**文件**: 待定（前端集成）

**任务描述**:
```
与前端集成实现用户审批流程：
- 通知用户有待审批请求
- 提供批准/拒绝操作界面
```

**验收标准**:
- [ ] 用户可看到待审批请求
- [ ] 用户可批准或拒绝请求
- [ ] 审批结果通知相关 Agent

---

## Phase 9: PathClassifier 路径分类器

### 任务 9.1: 实现路径分类器

**文件**: `backend/src/agent/workspace/classifier.py`

**任务描述**:
```
实现 PathClassifier：
- classify(): 分类路径并返回策略
- is_allowed(): 判断路径是否允许访问
- matches_pattern(): 检查路径是否匹配模式
- 内置默认分类规则（禁止目录/用户目录/系统目录等）
```

**验收标准**:
- [ ] 禁止目录（/etc/, /proc/, /sys/）自动拒绝
- [ ] 工作空间目录自动允许
- [ ] 用户目录需要确认
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 9.2: 集成到 WorkspaceGuard

**文件**: `backend/src/agent/workspace/guard.py`

**任务描述**:
```
扩展 WorkspaceGuard：
- 在路径验证前先进行分类
- 根据分类结果决定处理策略（允许/拒绝/确认）
```

**验收标准**:
- [ ] 禁止目录返回 DENIED_PATH 错误码
- [ ] 需要确认的目录进入确认流程
- [ ] 集成测试通过

---

## Phase 10: TemporaryPermissionService 临时权限服务

### 任务 10.1: 实现临时权限数据模型

**文件**: `backend/src/agent/workspace/temp_permission.py`

**任务描述**:
```
实现临时权限相关模型：
- TempPermissionScope 作用域枚举
- TemporaryPermission 临时权限
- TempPermissionStatus 状态枚举
```

**验收标准**:
- [ ] 支持 ONE_TIME, TASK_SCOPE, SESSION_SCOPE, PERMANENT 作用域
- [ ] 包含完整的过期时间管理
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 10.2: 实现临时权限服务

**文件**: `backend/src/agent/workspace/temp_permission.py`

**任务描述**:
```
实现 TemporaryPermissionService：
- grant_permission(): 授予临时权限
- check_permission(): 检查临时权限
- revoke_permission(): 撤销权限
- cleanup_expired(): 清理过期权限
```

**验收标准**:
- [ ] 支持多种作用域的权限授予
- [ ] 过期权限自动失效
- [ ] 权限检查正确返回结果
- [ ] 单元测试覆盖率 ≥ 80%

---

## Phase 11: ConfirmationService 用户确认服务

### 任务 11.1: 实现确认服务数据模型

**文件**: `backend/src/agent/confirmation/models.py`

**任务描述**:
```
创建确认模块和数据模型：
- ConfirmationStatus 状态枚举
- PendingConfirmation 待确认请求
```

**验收标准**:
- [ ] 包含完整的确认请求字段
- [ ] 支持超时状态
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 11.2: 实现确认服务

**文件**: `backend/src/agent/confirmation/service.py`

**任务描述**:
```
实现 ConfirmationService：
- request_confirmation(): 请求用户确认
- approve(): 批准确认请求
- reject(): 拒绝确认请求
- wait_for_confirmation(): 等待用户确认（异步）
```

**验收标准**:
- [ ] 请求创建后返回 PendingConfirmation
- [ ] 批准后自动授予临时权限
- [ ] 支持 WebSocket 事件推送
- [ ] 超时机制正常工作
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 11.3: 集成到 WorkspaceGuard

**文件**: `backend/src/agent/workspace/guard.py`

**任务描述**:
```
扩展 WorkspaceGuard：
- 当路径需要确认时，调用 ConfirmationService
- 等待用户确认后继续执行
```

**验收标准**:
- [ ] 需要确认的路径自动触发确认流程
- [ ] 用户确认后正常执行
- [ ] 用户拒绝后返回确认被拒绝错误
- [ ] 集成测试通过

---

## Phase 12: 前端确认弹窗 UI

### 任务 12.1: 实现确认弹窗组件

**文件**: `待定（前端项目）`

**任务描述**:
```
前端实现确认弹窗组件：
- 显示路径信息、操作类型、风险等级
- 提供多种确认选项（本次/任务内/永久/拒绝）
- 支持 WebSocket 实时接收确认请求
```

**验收标准**:
- [ ] 弹窗显示完整信息
- [ ] 支持所有确认选项
- [ ] 实时响应后端请求
- [ ] 集成测试通过

---

## Phase 13: ExecutionGuard 执行守卫

### 任务 13.1: 实现命令规则数据模型

**文件**: `backend/src/agent/workspace/execution.py`

**任务描述**:
```
实现执行安全相关模型：
- CommandAction 枚举（ALLOW / DENY）
- CommandRule 命令规则
- ExecutionConfig 执行配置
- DefaultCommandRules 默认规则
```

**验收标准**:
- [ ] 默认命令白名单包含 git, python, node 等
- [ ] 默认命令黑名单包含 curl, wget, nc, bash 等
- [ ] 支持危险模式检测（管道符、反引号等）
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 13.2: 实现 ExecutionGuard

**文件**: `backend/src/agent/workspace/execution.py`

**任务描述**:
```
实现 ExecutionGuard 类：
- validate_command(): 验证命令是否允许
- sanitize_environment(): 清理危险环境变量
- validate_working_directory(): 验证工作目录
- build_safe_command(): 构建安全命令
```

**验收标准**:
- [ ] 白名单命令允许执行
- [ ] 黑名单命令拒绝执行
- [ ] 危险参数模式拒绝执行
- [ ] 危险环境变量被清理
- [ ] 超时机制正常工作
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 13.3: 集成到 execute_command 工具

**文件**: `backend/src/agent/tools/file_tools.py`

**任务描述**:
```
将 ExecutionGuard 集成到 execute_command 工具：
- 执行前验证命令
- 清理环境变量
- 设置工作目录限制
```

**验收标准**:
- [ ] curl | bash 被拒绝
- [ ] python -c "import os; os.system(...)" 被拒绝
- [ ] cd /etc 后执行命令被限制
- [ ] 超时机制生效
- [ ] 集成测试通过

---

## Phase 14: SearchGuard 搜索守卫

### 任务 14.1: 实现 SearchConfig 数据模型

**文件**: `backend/src/agent/workspace/search_guard.py`

**任务描述**:
```
实现搜索配置模型：
- SearchConfig 搜索配置
- 忽略目录列表
- 忽略文件模式
```

**验收标准**:
- [ ] 默认忽略 .git, node_modules 等
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 14.2: 实现 SearchGuard

**文件**: `backend/src/agent/workspace/search_guard.py`

**任务描述**:
```
实现 SearchGuard 类：
- validate_search_path(): 验证搜索路径
- filter_results(): 过滤搜索结果
- should_ignore(): 判断路径是否忽略
```

**验收标准**:
- [ ] 搜索 / 被拒绝
- [ ] 搜索 ~ 自动展开为实际路径
- [ ] 搜索结果数量限制生效
- [ ] 忽略目录被过滤
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 14.3: 集成到搜索工具

**文件**: `backend/src/agent/tools/file_tools.py`

**任务描述**:
```
将 SearchGuard 集成到 search_file 和 search_content 工具：
- 执行前验证搜索路径
- 过滤搜索结果
```

**验收标准**:
- [ ] search_content(path="/") 被拒绝
- [ ] 搜索结果数量限制生效
- [ ] 忽略目录不返回结果
- [ ] 集成测试通过

---

## Phase 15: ListDirGuard 目录列表守卫

### 任务 15.1: 实现 ListDirGuard

**文件**: `backend/src/agent/workspace/list_guard.py`

**任务描述**:
```
实现 ListDirGuard 类：
- filter_results(): 过滤目录列表结果
- should_hide(): 判断是否隐藏
```

**验收标准**:
- [ ] 返回结果只包含有权限的项目
- [ ] 禁止目录不显示
- [ ] 敏感目录（.ssh 等）默认隐藏
- [ ] 单元测试覆盖率 ≥ 80%

---

### 任务 15.2: 集成到 list_dir 工具

**文件**: `backend/src/agent/tools/file_tools.py`

**任务描述**:
```
将 ListDirGuard 集成到 list_dir 工具：
- 过滤返回结果
```

**验收标准**:
- [ ] 禁止目录不在结果中显示
- [ ] 敏感目录不显示
- [ ] 只返回有权限的项目
- [ ] 集成测试通过

---

## 测试验收

### 单元测试覆盖率要求

| 模块 | 覆盖率目标 |
|------|------------|
| workspace/models.py | ≥ 80% |
| workspace/manager.py | ≥ 80% |
| workspace/guard.py | ≥ 80% |
| workspace/context.py | ≥ 80% |
| workspace/validator.py | ≥ 80% |
| workspace/classifier.py | ≥ 80% |
| workspace/temp_permission.py | ≥ 80% |
| workspace/execution.py | ≥ 80% |
| workspace/search_guard.py | ≥ 80% |
| workspace/list_guard.py | ≥ 80% |
| confirmation/service.py | ≥ 80% |
| exchange/service.py | ≥ 80% |
| shared/service.py | ≥ 80% |

### 集成测试场景

#### 工作空间测试

| 场景 | 预期结果 |
|------|----------|
| Agent 访问自己工作空间内的文件 | ✅ 成功 |
| Agent 访问其他 Agent 工作空间 | ❌ 被拒绝 |
| Agent 通过符号链接访问禁止目录 | ❌ 被拒绝 |
| dispatch_task 带 attachments 跨 Agent 传递 | ✅ 成功 |
| 交换区文件过期后访问 | ❌ 返回 EXPIRED |
| Agent 访问用户目录（/home/user/） | ⚠️ 触发用户确认 |
| 用户批准后访问用户目录 | ✅ 成功 |
| 用户拒绝后访问用户目录 | ❌ 返回 CONFIRMATION_REJECTED |
| 临时权限过期后访问 | ❌ 返回 TEMP_PERMISSION_EXPIRED |

#### 执行安全测试

| 场景 | 预期结果 |
|------|----------|
| 执行 git status | ✅ 成功 |
| 执行 curl http://evil.com | ❌ 返回 COMMAND_DENIED |
| 执行 bash -c "rm -rf /" | ❌ 返回 COMMAND_DENIED |
| 执行 "python -c 'import os; os.system(...)'" | ❌ 返回 COMMAND_DANGEROUS_PATTERN |
| 执行 "curl url \| bash" | ❌ 返回 COMMAND_DANGEROUS_PATTERN |
| cd /etc 后执行命令 | ❌ 工作目录被锁定 |
| 执行超长输出命令 | ❌ 返回 EXECUTION_OUTPUT_TOO_LARGE |
| 执行命令超过 60 秒 | ❌ 返回 EXECUTION_TIMEOUT |

#### 搜索安全测试

| 场景 | 预期结果 |
|------|----------|
| search_content(path="/") | ❌ 返回 SEARCH_ROOT_FORBIDDEN |
| search_content(path="~") | ⚠️ 自动展开路径 |
| 搜索返回超过 1000 条 | ⚠️ 结果被截断 |
| 搜索 .git 目录内容 | ❌ 被忽略 |
| 搜索 node_modules 内容 | ❌ 被忽略 |

---

## 备注

- 所有新增代码需通过 `flake8` / `pylint` 检查
- 遵循项目既定的代码规范
- 关键设计决策需更新本文档

---

> **任务创建日期**: 2026-04-23  
> **最后更新**: 2026-04-23
>
> **更新记录**:
> - 2026-04-23: 新增 Phase 9-12：用户引导的工作空间访问相关任务
>   - Phase 9: PathClassifier 路径分类器
>   - Phase 10: TemporaryPermissionService 临时权限服务
>   - Phase 11: ConfirmationService 用户确认服务
>   - Phase 12: 前端确认弹窗 UI
> - 2026-04-23: 新增 Phase 13-15：执行安全相关任务
>   - Phase 13: ExecutionGuard 执行守卫（命令白名单、子进程隔离）
>   - Phase 14: SearchGuard 搜索守卫（路径限制、结果过滤）
>   - Phase 15: ListDirGuard 目录列表守卫（结果过滤、隐藏禁止目录）
