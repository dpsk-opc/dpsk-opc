"""Data models for Agent Workspace Security Module.

This module defines all data models required for workspace security:
- Permission: File operation permissions
- Workspace: Agent workspace model
- Exchange: Cross-agent file exchange
- AgentContext: Execution context
- PathClassification: Path categorization and policies
- TemporaryPermission: Time-limited access permissions
- ExecutionConfig: Command execution security settings
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum, auto
from pathlib import Path
from typing import Optional, Any


# =============================================================================
# Permission Models
# =============================================================================


class Permission:
    """Agent file operation permissions (uses int values for bitwise operations)."""
    NONE = 0
    READ = 1
    WRITE = 2
    EXECUTE = 4
    LIST = 8
    DELETE = 16
    
    @classmethod
    def from_string(cls, value: str) -> "Permission":
        """Create permission from string."""
        mapping = {
            "READ": cls.READ,
            "WRITE": cls.WRITE,
            "EXECUTE": cls.EXECUTE,
            "LIST": cls.LIST,
            "DELETE": cls.DELETE,
            "NONE": cls.NONE,
        }
        return mapping.get(value.upper(), cls.NONE)
    
    def __str__(self) -> str:
        return self.name
    
    def __repr__(self) -> str:
        return f"Permission.{self.name}"
    
    def __eq__(self, other: object) -> bool:
        if isinstance(other, Permission):
            return self.value == other.value
        return False
    
    def __hash__(self) -> int:
        return hash(self.value)
    
    def __or__(self, other: "Permission") -> int:
        return self.value | other.value
    
    def __and__(self, other: "Permission") -> bool:
        return bool(self.value & other.value)
    
    @property
    def name(self) -> str:
        for k, v in type(self).__dict__.items():
            if v == self.value and not k.startswith('_'):
                return k
        return "NONE"

# For type hints
PermissionType = int


class WorkspaceStatus(Enum):
    """Workspace status."""
    ACTIVE = "active"       # Active
    ARCHIVED = "archived"    # Archived
    DELETED = "deleted"     # Deleted


class SharedAccessStatus(Enum):
    """Shared access request status."""
    PENDING = "pending"      # Pending approval
    APPROVED = "approved"   # Approved
    REJECTED = "rejected"   # Rejected
    EXPIRED = "expired"     # Expired


class ExchangeStatus(Enum):
    """Exchange file status."""
    ACTIVE = "active"       # Active, can be read
    CONSUMED = "consumed"   # Already read by receiver
    EXPIRED = "expired"     # Expired


class TempPermissionStatus(Enum):
    """Temporary permission status."""
    ACTIVE = "active"       # Active
    EXPIRED = "expired"     # Expired
    REVOKED = "revoked"     # Revoked
    CONSUMED = "consumed"   # Used (ONE_TIME scenario)


class ConfirmationStatus(Enum):
    """Confirmation status."""
    PENDING = "pending"      # Pending
    APPROVED = "approved"    # Approved
    REJECTED = "rejected"    # Rejected
    TIMEOUT = "timeout"      # Timed out


class CommandAction(Enum):
    """Command action."""
    ALLOW = "allow"         # Allow
    DENY = "deny"           # Deny


class NetworkPolicy(Enum):
    """Network access policy."""
    ALLOW = "allow"          # Allow
    DENY = "deny"            # Deny
    ASK = "ask"              # Ask


class TempPermissionScope(Enum):
    """Temporary permission scope."""
    ONE_TIME = "one_time"              # Valid for one operation
    TASK_SCOPE = "task_scope"           # Valid for current task
    SESSION_SCOPE = "session_scope"     # Valid for current session
    PERMANENT = "permanent"             # Permanent (add to whitelist)


# =============================================================================
# Workspace Models
# =============================================================================


@dataclass
class Workspace:
    """Agent workspace model."""
    
    workspace_id: str                           # Unique workspace ID, format: ws_{agent_id}
    agent_id: str                               # Associated agent ID
    root_path: Path                             # Workspace root directory path
    permissions: int                             # Current permissions (bitmask)
    created_at: float                           # Creation timestamp
    updated_at: float                           # Update timestamp
    
    # Sharing configuration
    shared_dirs: list[Path] = field(default_factory=list)  # Whitelisted shared directories
    pending_shared_access: list[SharedAccessRequest] = field(default_factory=list)  # Pending temporary shared access
    
    # Hierarchy
    parent_workspace_id: Optional[str] = None  # Parent workspace (can inherit permissions)
    child_workspace_ids: list[str] = field(default_factory=list)
    
    # Status
    status: WorkspaceStatus = WorkspaceStatus.ACTIVE  # ACTIVE / ARCHIVED / DELETED
    
    # Sandbox config (reserved for Firecracker)
    sandbox_config: Optional[dict] = None
    
    def has_permission(self, perm: Permission) -> bool:
        """Check if workspace has a specific permission."""
        perm_val = perm.value if hasattr(perm, 'value') else perm
        return bool(self.permissions & perm_val)
    
    @classmethod
    def create(
        cls,
        agent_id: str,
        root_path: Path,
        permissions: int = Permission.READ | Permission.LIST,
        parent_workspace_id: Optional[str] = None,
    ) -> "Workspace":
        """Create a new workspace."""
        now = time.time()
        return cls(
            workspace_id=f"ws_{agent_id}",
            agent_id=agent_id,
            root_path=root_path,
            permissions=permissions,
            created_at=now,
            updated_at=now,
            parent_workspace_id=parent_workspace_id,
        )
    
    def add_shared_dir(self, path: Path) -> None:
        """Add a whitelisted shared directory."""
        if path not in self.shared_dirs:
            self.shared_dirs.append(path)
            self.updated_at = time.time()
    
    def remove_shared_dir(self, path: Path) -> bool:
        """Remove a whitelisted shared directory."""
        if path in self.shared_dirs:
            self.shared_dirs.remove(path)
            self.updated_at = time.time()
            return True
        return False
    
    def is_active(self) -> bool:
        """Check if workspace is active."""
        return self.status == WorkspaceStatus.ACTIVE
    
    def archive(self) -> None:
        """Archive the workspace."""
        self.status = WorkspaceStatus.ARCHIVED
        self.updated_at = time.time()
    
    def delete(self) -> None:
        """Mark workspace as deleted."""
        self.status = WorkspaceStatus.DELETED
        self.updated_at = time.time()


@dataclass
class SharedAccessRequest:
    """Temporary shared access request."""
    
    request_id: str                      # Request unique ID
    from_agent_id: str                    # Requesting agent
    to_agent_id: str                      # Authorized agent
    target_path: Path                     # Target path
    requested_permissions: Permission      # Requested permissions
    reason: str                           # Request reason
    expires_at: Optional[float] = None    # Expiration time (optional)
    status: SharedAccessStatus = SharedAccessStatus.PENDING  # Status
    created_at: float = field(default_factory=time.time)    # Creation time
    
    @classmethod
    def create(
        cls,
        from_agent_id: str,
        to_agent_id: str,
        target_path: Path,
        requested_permissions: Permission,
        reason: str,
        expires_at: Optional[float] = None,
    ) -> "SharedAccessRequest":
        """Create a new shared access request."""
        import uuid
        return cls(
            request_id=str(uuid.uuid4()),
            from_agent_id=from_agent_id,
            to_agent_id=to_agent_id,
            target_path=target_path,
            requested_permissions=requested_permissions,
            reason=reason,
            expires_at=expires_at,
        )
    
    def approve(self) -> None:
        """Approve the request."""
        self.status = SharedAccessStatus.APPROVED
    
    def reject(self) -> None:
        """Reject the request."""
        self.status = SharedAccessStatus.REJECTED
    
    def expire(self) -> None:
        """Mark as expired."""
        self.status = SharedAccessStatus.EXPIRED
    
    def is_pending(self) -> bool:
        """Check if request is pending."""
        return self.status == SharedAccessStatus.PENDING


# =============================================================================
# Exchange Models
# =============================================================================


@dataclass
class ExchangeMeta:
    """Exchange file metadata."""
    
    exchange_id: str                      # Exchange file unique ID
    from_agent_id: str                    # Sender agent
    authorized_agents: list[str]         # Agents authorized to read
    original_filename: str               # Original filename
    created_at: float                    # Creation time
    expires_at: Optional[float]          # Expiration time (default 24 hours)
    status: ExchangeStatus = ExchangeStatus.ACTIVE  # ACTIVE / CONSUMED / EXPIRED
    
    @classmethod
    def create(
        cls,
        from_agent_id: str,
        authorized_agents: list[str],
        original_filename: str,
        ttl_seconds: int = 86400,  # 24 hours default
    ) -> "ExchangeMeta":
        """Create new exchange metadata."""
        import uuid
        now = time.time()
        return cls(
            exchange_id=str(uuid.uuid4()),
            from_agent_id=from_agent_id,
            authorized_agents=authorized_agents,
            original_filename=original_filename,
            created_at=now,
            expires_at=now + ttl_seconds,
        )
    
    def consume(self) -> None:
        """Mark as consumed."""
        self.status = ExchangeStatus.CONSUMED
    
    def expire(self) -> None:
        """Mark as expired."""
        self.status = ExchangeStatus.EXPIRED
    
    def is_active(self) -> bool:
        """Check if exchange is active and not expired."""
        if self.status != ExchangeStatus.ACTIVE:
            return False
        if self.expires_at and time.time() > self.expires_at:
            self.expire()
            return False
        return True
    
    def can_be_read_by(self, agent_id: str) -> bool:
        """Check if agent can read this exchange."""
        return (
            self.is_active() and
            agent_id in self.authorized_agents
        )


# =============================================================================
# Agent Context
# =============================================================================


@dataclass
class AgentContext:
    """Agent execution context (thread/coroutine local storage)."""
    
    agent_id: str                         # Current agent ID
    workspace_id: str                     # Current workspace ID
    workspace_root: Path                  # Workspace root directory
    permissions: int                      # Current permissions (bitmask)
    session_id: Optional[str] = None     # Current session ID (for multi-task)
    parent_context: Optional[AgentContext] = None  # Parent context (on dispatch)
    temporary_permissions: list[TemporaryPermission] = field(default_factory=list)  # Temporary permissions
    
    # Additional metadata
    task_id: Optional[str] = None         # Current task ID
    is_admin: bool = False               # Whether agent has admin privileges
    
    def has_permission(self, permission: Permission) -> bool:
        """Check if has specific permission."""
        perm_val = permission.value if hasattr(permission, 'value') else permission
        return bool(self.permissions & perm_val)
    
    def has_any_permission(self, *permissions: Permission) -> bool:
        """Check if has any of the specified permissions."""
        return any(self.has_permission(p) for p in permissions)
    
    def has_all_permissions(self, *permissions: Permission) -> bool:
        """Check if has all specified permissions."""
        return all(self.has_permission(p) for p in permissions)
    
    def add_temporary_permission(self, temp_perm: TemporaryPermission) -> None:
        """Add a temporary permission."""
        self.temporary_permissions.append(temp_perm)
    
    def clear_expired_temporary_permissions(self) -> None:
        """Clear expired temporary permissions."""
        now = time.time()
        self.temporary_permissions = [
            p for p in self.temporary_permissions
            if p.expires_at is None or p.expires_at > now
        ]


# =============================================================================
# Path Classification Models
# =============================================================================


class PathCategory(Enum):
    """Path category."""
    WORKSPACE = "workspace"        # Agent workspace
    SHARED = "shared"             # Shared whitelist directory
    USER_HOME = "user_home"        # User home directory
    PROJECT = "project"           # Project directory
    SYSTEM = "system"             # System directory
    SENSITIVE = "sensitive"        # Sensitive directory
    FORBIDDEN = "forbidden"        # Forbidden directory


class Policy(Enum):
    """Access policy."""
    ALLOW = "allow"                           # Auto allow
    ALLOW_WITH_CONFIRM = "allow_with_confirm"  # Allow after confirmation
    DENY = "deny"                             # Auto deny


@dataclass
class PathClassification:
    """Path classification configuration."""
    
    pattern: str                    # Path pattern (supports glob, e.g. /etc/*)
    category: PathCategory          # Category
    default_policy: Policy          # Default policy
    requires_user_confirmation: bool = False  # Requires user confirmation


class DefaultPathRules:
    """Default path classification rules."""
    
    RULES: list[PathClassification] = [
        # Agent workspace - auto allow
        PathClassification("/storage/ws/*", PathCategory.WORKSPACE, Policy.ALLOW),
        PathClassification("/storage/shared/*", PathCategory.SHARED, Policy.ALLOW),
        
        # User directory - requires confirmation
        PathClassification("/home/*", PathCategory.USER_HOME, Policy.ALLOW_WITH_CONFIRM),
        PathClassification("~/*", PathCategory.USER_HOME, Policy.ALLOW_WITH_CONFIRM),
        
        # Project directory - requires confirmation
        PathClassification("/mnt/*", PathCategory.PROJECT, Policy.ALLOW_WITH_CONFIRM),
        PathClassification("/workspace/*", PathCategory.PROJECT, Policy.ALLOW_WITH_CONFIRM),
        
        # System directory - auto deny
        PathClassification("/etc/*", PathCategory.SYSTEM, Policy.DENY),
        PathClassification("/usr/*", PathCategory.SYSTEM, Policy.DENY),
        PathClassification("/var/*", PathCategory.SYSTEM, Policy.DENY),
        
        # Sensitive directory - auto deny
        PathClassification("/root/*", PathCategory.SENSITIVE, Policy.DENY),
        PathClassification("/proc/*", PathCategory.SENSITIVE, Policy.DENY),
        PathClassification("/sys/*", PathCategory.SENSITIVE, Policy.DENY),
        
        # Forbidden directory - auto deny
        PathClassification("/", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/bin/*", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/sbin/*", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/boot/*", PathCategory.FORBIDDEN, Policy.DENY),
        PathClassification("/dev/*", PathCategory.FORBIDDEN, Policy.DENY),
    ]


# =============================================================================
# Temporary Permission Models
# =============================================================================


@dataclass
class TemporaryPermission:
    """Temporary permission."""
    
    permission_id: str                    # Permission unique ID
    agent_id: str                        # Authorized agent
    path: Path                            # Authorized path
    permission: int                       # Granted permission (bitmask)
    scope: TempPermissionScope            # Scope
    granted_by: str                       # Grantor (user ID)
    task_id: Optional[str] = None         # Associated task ID (for TASK_SCOPE)
    session_id: Optional[str] = None      # Associated session ID (for SESSION_SCOPE)
    created_at: float = field(default_factory=time.time)  # Creation time
    expires_at: Optional[float] = None   # Expiration time
    status: TempPermissionStatus = TempPermissionStatus.ACTIVE  # Status
    
    @classmethod
    def create(
        cls,
        agent_id: str,
        path: Path,
        permission: Permission,
        scope: TempPermissionScope,
        granted_by: str,
        task_id: Optional[str] = None,
        session_id: Optional[str] = None,
        ttl_seconds: Optional[int] = None,
    ) -> "TemporaryPermission":
        """Create a new temporary permission."""
        import uuid
        now = time.time()
        expires_at = None
        if ttl_seconds:
            expires_at = now + ttl_seconds
        elif scope == TempPermissionScope.TASK_SCOPE:
            expires_at = now + 3600  # 1 hour default for task scope
        elif scope == TempPermissionScope.SESSION_SCOPE:
            expires_at = now + 86400  # 24 hours default for session scope
        
        perm_value = permission.value if isinstance(permission, Permission) else permission
        
        return cls(
            permission_id=str(uuid.uuid4()),
            agent_id=agent_id,
            path=path,
            permission=perm_value,
            scope=scope,
            granted_by=granted_by,
            task_id=task_id,
            session_id=session_id,
            expires_at=expires_at,
        )
    
    def is_active(self) -> bool:
        """Check if permission is active and not expired."""
        if self.status != TempPermissionStatus.ACTIVE:
            return False
        if self.expires_at and time.time() > self.expires_at:
            self.status = TempPermissionStatus.EXPIRED
            return False
        return True
    
    def revoke(self) -> None:
        """Revoke this permission."""
        self.status = TempPermissionStatus.REVOKED
    
    def consume(self) -> None:
        """Mark as consumed (for ONE_TIME scope)."""
        self.status = TempPermissionStatus.CONSUMED


@dataclass
class PendingConfirmation:
    """Pending confirmation request."""
    
    request_id: str                    # Request ID
    agent_id: str                      # Agent requesting permission
    path: str                          # Requested path
    operation: str                     # Operation type (read/write/execute)
    reason: str                        # Request reason
    task_id: Optional[str] = None      # Associated task ID
    created_at: float = field(default_factory=time.time)
    status: ConfirmationStatus = ConfirmationStatus.PENDING
    timeout_seconds: float = 120.0    # Default timeout: 2 minutes
    
    @classmethod
    def create(
        cls,
        agent_id: str,
        path: str,
        operation: str,
        reason: str,
        task_id: Optional[str] = None,
    ) -> "PendingConfirmation":
        """Create a new pending confirmation."""
        import uuid
        return cls(
            request_id=str(uuid.uuid4()),
            agent_id=agent_id,
            path=path,
            operation=operation,
            reason=reason,
            task_id=task_id,
        )
    
    def approve(self) -> None:
        """Approve the confirmation."""
        self.status = ConfirmationStatus.APPROVED
    
    def reject(self) -> None:
        """Reject the confirmation."""
        self.status = ConfirmationStatus.REJECTED
    
    def timeout(self) -> None:
        """Mark as timed out."""
        self.status = ConfirmationStatus.TIMEOUT
    
    def is_pending(self) -> bool:
        """Check if confirmation is pending."""
        return self.status == ConfirmationStatus.PENDING


# =============================================================================
# Execution Security Models
# =============================================================================


@dataclass
class CommandRule:
    """Command rule."""
    pattern: str                    # Command pattern (supports glob)
    action: CommandAction           # ALLOW / DENY
    description: str = ""           # Rule description


class DefaultCommandRules:
    """Default command rules."""
    
    # Allowed commands (whitelist)
    ALLOWED: list[CommandRule] = [
        CommandRule("git", CommandAction.ALLOW, "Version control"),
        CommandRule("python*", CommandAction.ALLOW, "Python interpreter"),
        CommandRule("node*", CommandAction.ALLOW, "Node.js runtime"),
        CommandRule("pip*", CommandAction.ALLOW, "Python package manager"),
        CommandRule("npm*", CommandAction.ALLOW, "Node.js package manager"),
        CommandRule("ls", CommandAction.ALLOW, "List directory"),
        CommandRule("cat", CommandAction.ALLOW, "View file"),
        CommandRule("cp", CommandAction.ALLOW, "Copy file"),
        CommandRule("mv", CommandAction.ALLOW, "Move file"),
        CommandRule("mkdir", CommandAction.ALLOW, "Create directory"),
        CommandRule("ps", CommandAction.ALLOW, "View processes"),
        CommandRule("df", CommandAction.ALLOW, "View disk"),
        CommandRule("free", CommandAction.ALLOW, "View memory"),
    ]
    
    # Denied commands (blacklist)
    DENIED: list[CommandRule] = [
        CommandRule("curl", CommandAction.DENY, "Prohibit network download"),
        CommandRule("wget", CommandAction.DENY, "Prohibit network download"),
        CommandRule("nc", CommandAction.DENY, "Prohibit network tools"),
        CommandRule("ncat", CommandAction.DENY, "Prohibit network tools"),
        CommandRule("bash", CommandAction.DENY, "Prohibit bash"),
        CommandRule("sh", CommandAction.DENY, "Prohibit sh"),
        CommandRule("zsh", CommandAction.DENY, "Prohibit zsh"),
        CommandRule("fish", CommandAction.DENY, "Prohibit fish"),
        CommandRule("dd", CommandAction.DENY, "Prohibit disk operations"),
        CommandRule("mkfs", CommandAction.DENY, "Prohibit formatting"),
        CommandRule("fdisk", CommandAction.DENY, "Prohibit disk partitioning"),
        CommandRule("ssh", CommandAction.DENY, "Prohibit SSH"),
        CommandRule("scp", CommandAction.DENY, "Prohibit SCP"),
        CommandRule("ftp", CommandAction.DENY, "Prohibit FTP"),
        CommandRule("telnet", CommandAction.DENY, "Prohibit Telnet"),
        CommandRule("rm", CommandAction.DENY, "Prohibit rm (restricted)"),
    ]
    
    # Dangerous patterns in commands
    DANGEROUS_PATTERNS: list[str] = [
        r"\|\s*(bash|sh|zsh|fish)",       # Pipe to shell
        r";\s*(rm|del|format)",            # Semicolon followed by dangerous commands
        r"`.*`",                            # Backtick command substitution
        r"\$\(.*\)",                       # $() command substitution
        r"&&\s*(rm|del)",                  # && followed by delete
        r"\|\s*curl",                      # Pipe download
        r"import\s+os",                    # Python os module import
        r"import\s+subprocess",            # Python subprocess module import
        r"os\.system",                     # Python os.system call
        r"subprocess\.run",                # Python subprocess.run call
    ]


@dataclass
class ExecutionConfig:
    """Execution configuration."""
    
    command_rules: list[CommandRule] = field(default_factory=list)  # Command rules
    network_policy: NetworkPolicy = NetworkPolicy.DENY               # Network policy
    allowed_hosts: list[str] = field(default_factory=list)         # Allowed hosts
    blocked_ports: list[str] = field(default_factory=list)         # Blocked ports
    timeout_seconds: int = 60                                       # Timeout
    max_output_size: int = 1024 * 1024  # 1MB, max output size
    cleanup_env_vars: list[str] = field(default_factory=lambda: [
        "LD_PRELOAD",
        "LD_LIBRARY_PATH",
    ])  # Environment variables to clean
    
    @classmethod
    def default_config(cls) -> "ExecutionConfig":
        """Create default execution config."""
        return cls(
            command_rules=DefaultCommandRules.ALLOWED + DefaultCommandRules.DENIED,
            network_policy=NetworkPolicy.DENY,
            allowed_hosts=[],
            blocked_ports=["1-1024"],
            timeout_seconds=60,
            max_output_size=1024 * 1024,
            cleanup_env_vars=["LD_PRELOAD", "LD_LIBRARY_PATH", "PYTHONPATH"],
        )


@dataclass
class SearchConfig:
    """Search configuration."""
    
    max_results: int = 1000                # Max results
    ignored_dirs: list[str] = field(default_factory=lambda: [
        ".git",
        "node_modules",
        "__pycache__",
        ".venv",
        "venv",
        ".ssh",
        ".gnupg",
        ".aws",
        ".docker",
        ".kube",
        ".pki",
    ])  # Ignored directories
    ignored_patterns: list[str] = field(default_factory=lambda: [
        "*.pyc",
        "*.pyo",
        "*.so",
        "*.dll",
        "*.dylib",
    ])  # Ignored file patterns
    binary_extensions: list[str] = field(default_factory=lambda: [
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico",
        ".pdf", ".zip", ".tar", ".gz", ".exe", ".dll",
    ])  # Binary file extensions
    
    @classmethod
    def default_config(cls) -> "SearchConfig":
        """Create default search config."""
        return cls()


@dataclass
class ListDirConfig:
    """List directory configuration."""
    
    hidden_dirs: list[str] = field(default_factory=lambda: [
        ".ssh",
        ".gnupg",
        ".aws",
        ".docker",
        ".kube",
    ])  # Hidden directories
    hidden_files: list[str] = field(default_factory=lambda: [
        ".bash_history",
        ".zsh_history",
        ".gitconfig",
        ".netrc",
    ])  # Hidden files
    max_items: int = 10000  # Max items to return
    
    @classmethod
    def default_config(cls) -> "ListDirConfig":
        """Create default list dir config."""
        return cls()


@dataclass
class AuditConfig:
    """Audit configuration."""
    
    enabled: bool = True                    # Enable audit
    log_path: Path = Path("/storage/audit")  # Audit log path
    log_format: str = "jsonl"               # Log format: jsonl, json, plain
    filename_pattern: str = "audit_{date}.log"  # Filename pattern
    include_successful: bool = True         # Log successful access
    include_denied: bool = True            # Log denied access
    include_commands: bool = True          # Log command execution
    masking_enabled: bool = True           # Enable sensitive data masking
    
    @classmethod
    def default_config(cls) -> "AuditConfig":
        """Create default audit config."""
        return cls()


@dataclass
class ToolSecurityProfile:
    """Tool security configuration."""
    
    workspace: Workspace                      # Workspace
    execution: ExecutionConfig = field(default_factory=ExecutionConfig)  # Execution config
    search: SearchConfig = field(default_factory=SearchConfig)          # Search config
    list_dir: ListDirConfig = field(default_factory=ListDirConfig)      # List dir config
    audit: AuditConfig = field(default_factory=AuditConfig)             # Audit config
    allowed_paths: list[Path] = field(default_factory=list)             # Additional allowed paths
    denied_paths: list[Path] = field(default_factory=list)             # Denied paths
    network_enabled: bool = False             # Allow network access
