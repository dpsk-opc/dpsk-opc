"""Agent Workspace Security Module.

This module provides workspace isolation and security for agents:
- Workspace management (creation, validation, lifecycle)
- AgentContext for execution context
- WorkspaceGuard for tool-layer security enforcement
- Path validation and classification
- Temporary permission management
- Audit logging
"""

from .models import (
    Permission,
    Workspace,
    WorkspaceStatus,
    SharedAccessRequest,
    SharedAccessStatus,
    ExchangeMeta,
    ExchangeStatus,
    AgentContext,
    PathCategory,
    Policy,
    PathClassification,
    DefaultPathRules,
    TempPermissionScope,
    TemporaryPermission,
    TempPermissionStatus,
    PendingConfirmation,
    ConfirmationStatus,
    CommandAction,
    CommandRule,
    DefaultCommandRules,
    ExecutionConfig,
    SearchConfig,
    ListDirConfig,
    AuditConfig,
    ToolSecurityProfile,
    NetworkPolicy,
)

from .context import (
    set_current_context,
    get_current_context,
    get_current_workspace,
    get_current_agent_id,
    clear_current_context,
    AgentContextManager,
    has_permission,
    has_any_permission,
    has_all_permissions,
)

from .manager import WorkspaceManager, get_workspace_manager

from .classifier import PathClassifier, get_path_classifier, classify_path, is_path_allowed

from .validator import PathValidator, get_path_validator, validate_workspace_path

from .temp_permission import TemporaryPermissionService, get_temp_permission_service

from .audit import AuditLogger, get_audit_logger, audit_log

from .guard import WorkspaceGuard, workspace_guard, get_workspace_guard

from .execution_guard import ExecutionGuard, get_execution_guard

from .search_guard import SearchGuard, get_search_guard

from .list_dir_guard import ListDirGuard, get_list_dir_guard

from .exchange_service import ExchangeService, get_exchange_service

from .confirmation_service import ConfirmationService, get_confirmation_service

from .exceptions import (
    WorkspaceError,
    WorkspaceAccessDeniedError,
    WorkspacePermissionDeniedError,
    WorkspaceNotFoundError,
    WorkspaceInvalidPathError,
    WorkspaceSymlinkForbiddenError,
    WorkspaceForbiddenPathError,
    PathRequiresConfirmationError,
    ConfirmationTimeoutError,
    ConfirmationRejectedError,
    TempPermissionExpiredError,
    TempPermissionNotFoundError,
    TempPermissionRevokedError,
    ExchangeAccessDeniedError,
    ExchangeNotFoundError,
    ExchangeExpiredError,
    CommandDeniedError,
    CommandNotInWhitelistError,
    CommandDangerousPatternError,
    ExecutionTimeoutError,
    ExecutionOutputTooLargeError,
    NetworkAccessDeniedError,
    SearchPathInvalidError,
    SearchRootForbiddenError,
    SearchResultsExceededError,
)

__all__ = [
    # Models
    "Permission",
    "Workspace",
    "WorkspaceStatus",
    "SharedAccessRequest",
    "SharedAccessStatus",
    "ExchangeMeta",
    "ExchangeStatus",
    "AgentContext",
    "PathCategory",
    "Policy",
    "PathClassification",
    "DefaultPathRules",
    "TempPermissionScope",
    "TemporaryPermission",
    "TempPermissionStatus",
    "PendingConfirmation",
    "ConfirmationStatus",
    "CommandAction",
    "CommandRule",
    "DefaultCommandRules",
    "ExecutionConfig",
    "SearchConfig",
    "ListDirConfig",
    "AuditConfig",
    "ToolSecurityProfile",
    "NetworkPolicy",
    # Context
    "set_current_context",
    "get_current_context",
    "get_current_workspace",
    "get_current_agent_id",
    "clear_current_context",
    "AgentContextManager",
    "has_permission",
    "has_any_permission",
    "has_all_permissions",
    # Manager
    "WorkspaceManager",
    "get_workspace_manager",
    # Classifier
    "PathClassifier",
    "get_path_classifier",
    "classify_path",
    "is_path_allowed",
    # Validator
    "PathValidator",
    "get_path_validator",
    "validate_workspace_path",
    # Temp Permission
    "TemporaryPermissionService",
    "get_temp_permission_service",
    # Audit
    "AuditLogger",
    "get_audit_logger",
    "audit_log",
    # Guard
    "WorkspaceGuard",
    "workspace_guard",
    "get_workspace_guard",
    # Execution Guard
    "ExecutionGuard",
    "get_execution_guard",
    # Search Guard
    "SearchGuard",
    "get_search_guard",
    # List Dir Guard
    "ListDirGuard",
    "get_list_dir_guard",
    # Exchange Service
    "ExchangeService",
    "get_exchange_service",
    # Confirmation Service
    "ConfirmationService",
    "get_confirmation_service",
    # Exceptions
    "WorkspaceError",
    "WorkspaceAccessDeniedError",
    "WorkspacePermissionDeniedError",
    "WorkspaceNotFoundError",
    "WorkspaceInvalidPathError",
    "WorkspaceSymlinkForbiddenError",
    "WorkspaceForbiddenPathError",
    "PathRequiresConfirmationError",
    "ConfirmationTimeoutError",
    "ConfirmationRejectedError",
    "TempPermissionExpiredError",
    "TempPermissionNotFoundError",
    "TempPermissionRevokedError",
    "ExchangeAccessDeniedError",
    "ExchangeNotFoundError",
    "ExchangeExpiredError",
    "CommandDeniedError",
    "CommandNotInWhitelistError",
    "CommandDangerousPatternError",
    "ExecutionTimeoutError",
    "ExecutionOutputTooLargeError",
    "NetworkAccessDeniedError",
    "SearchPathInvalidError",
    "SearchRootForbiddenError",
    "SearchResultsExceededError",
]
