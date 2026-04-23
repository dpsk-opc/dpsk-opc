"""WorkspaceGuard decorator for DPSK-OPC Agent.

This module provides security guards for tool execution:
- Intercept file operation tools
- Validate paths and permissions
- Support all file operation tools
"""

from __future__ import annotations

import functools
import logging
from pathlib import Path
from typing import Any, Callable, Optional, TypeVar, Union

from .context import get_current_context
from .manager import WorkspaceManager, get_workspace_manager
from .classifier import PathClassifier, get_path_classifier
from .temp_permission import TemporaryPermissionService, get_temp_permission_service
from .audit import AuditLogger, get_audit_logger
from .models import Permission, PathCategory, Policy, TempPermissionScope
from .exceptions import (
    WorkspaceAccessDeniedError,
    WorkspacePermissionDeniedError,
    WorkspaceForbiddenPathError,
    PathRequiresConfirmationError,
)

logger = logging.getLogger(__name__)

F = TypeVar("F", bound=Callable[..., Any])


class WorkspaceGuard:
    """Security guard for workspace operations."""
    
    def __init__(
        self,
        workspace_manager: Optional[WorkspaceManager] = None,
        classifier: Optional[PathClassifier] = None,
        temp_permission_service: Optional[TemporaryPermissionService] = None,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the workspace guard.
        
        Args:
            workspace_manager: Workspace manager instance
            classifier: Path classifier instance
            temp_permission_service: Temporary permission service
            audit_logger: Audit logger instance
        """
        self.workspace_manager = workspace_manager or get_workspace_manager()
        self.classifier = classifier or get_path_classifier()
        self.temp_permission_service = temp_permission_service or get_temp_permission_service()
        self.audit_logger = audit_logger or get_audit_logger()
    
    def guard_path(
        self,
        path: str,
        required_permission: Permission,
        operation: str,
    ) -> tuple[bool, str]:
        """Guard a path access.
        
        Args:
            path: Path to guard
            required_permission: Required permission
            operation: Operation name
            
        Returns:
            Tuple of (allowed, error_message)
        """
        context = get_current_context()
        
        if not context:
            # No context means no guard - allow for backwards compatibility
            logger.warning(f"[WorkspaceGuard] No context for operation: {operation}")
            return True, ""
        
        workspace = self.workspace_manager.get_workspace(context.agent_id)
        
        if not workspace:
            # No workspace - deny access
            return False, f"No workspace for agent: {context.agent_id}"
        
        # Check basic permission
        if not context.has_permission(required_permission):
            return False, f"Missing required permission: {required_permission}"
        
        # Validate path is within workspace or shared dirs
        valid, error = self.workspace_manager.validator.validate_path(
            path=path,
            workspace_root=workspace.root_path,
            shared_dirs=workspace.shared_dirs,
        )
        
        if not valid:
            # Check if path requires confirmation
            category, policy = self.classifier.classify(path)
            
            if policy == Policy.ALLOW_WITH_CONFIRM:
                # Check temporary permissions
                has_temp, _ = self.temp_permission_service.check_permission(
                    agent_id=context.agent_id,
                    path=path,
                    required_permission=required_permission,
                    task_id=context.task_id,
                    session_id=context.session_id,
                )
                
                if has_temp:
                    return True, ""
                
                return False, f"Path '{path}' requires user confirmation"
            
            return False, error
        
        return True, ""
    
    def check_and_raise(
        self,
        path: str,
        required_permission: Permission,
        operation: str,
    ) -> None:
        """Check path access and raise exception if denied.
        
        Args:
            path: Path to check
            required_permission: Required permission
            operation: Operation name
            
        Raises:
            WorkspaceAccessDeniedError: If access is denied
        """
        context = get_current_context()
        workspace_id = getattr(context, "workspace_id", "unknown") if context else "unknown"
        
        allowed, error = self.guard_path(path, required_permission, operation)
        
        if not allowed:
            # Log the denied access
            self.audit_logger.log_workspace_access(
                agent_id=context.agent_id if context else "unknown",
                workspace_id=workspace_id,
                operation=operation,
                path=path,
                allowed=False,
                permission_used=required_permission.name,
                reason=error,
                result="denied",
            )
            
            # Check if it requires confirmation
            category, policy = self.classifier.classify(path)
            if policy == Policy.ALLOW_WITH_CONFIRM:
                raise PathRequiresConfirmationError(
                    f"Path '{path}' requires user confirmation",
                    path=path,
                    operation=operation,
                    category=category.value,
                )
            
            raise WorkspaceAccessDeniedError(
                f"[Workspace Guard] {error}",
                path=path,
                operation=operation,
            )


def workspace_guard(
    required_permission: Permission,
    operation: str,
    path_arg: str = "path",
    guard: Optional[WorkspaceGuard] = None,
) -> Callable[[F], F]:
    """Decorator to guard a function with workspace checks.
    
    Args:
        required_permission: Required permission for the operation
        operation: Operation name for logging
        path_arg: Name of the path argument
        guard: WorkspaceGuard instance (uses global if not provided)
        
    Returns:
        Decorated function
    """
    def decorator(func: F) -> F:
        @functools.wraps(func)
        async def async_wrapper(*args: Any, **kwargs: Any) -> Any:
            guard_instance = guard or get_workspace_guard()
            
            # Get path from kwargs or args
            path = kwargs.get(path_arg)
            if path is None:
                # Try to get from function signature
                import inspect
                sig = inspect.signature(func)
                params = list(sig.parameters.keys())
                if path_arg in params:
                    idx = params.index(path_arg)
                    if idx < len(args):
                        path = args[idx]
            
            if path is None:
                logger.warning(f"[WorkspaceGuard] No path found for {operation}")
                return await func(*args, **kwargs)
            
            # Check and raise if denied
            context = get_current_context()
            workspace_id = getattr(context, "workspace_id", "unknown") if context else "unknown"
            
            allowed, error = guard_instance.guard_path(path, required_permission, operation)
            
            # Log the access
            guard_instance.audit_logger.log_workspace_access(
                agent_id=context.agent_id if context else "unknown",
                workspace_id=workspace_id,
                operation=operation,
                path=str(path),
                allowed=allowed,
                permission_used=required_permission.name,
                result="success" if allowed else "denied",
            )
            
            if not allowed:
                # Check if requires confirmation
                category, policy = guard_instance.classifier.classify(str(path))
                if policy == Policy.ALLOW_WITH_CONFIRM:
                    raise PathRequiresConfirmationError(
                        f"Path '{path}' requires user confirmation",
                        path=str(path),
                        operation=operation,
                        category=category.value,
                    )
                raise WorkspaceAccessDeniedError(
                    f"[Workspace Guard] {error}",
                    path=str(path),
                    operation=operation,
                )
            
            return await func(*args, **kwargs)
        
        @functools.wraps(func)
        def sync_wrapper(*args: Any, **kwargs: Any) -> Any:
            guard_instance = guard or get_workspace_guard()
            
            path = kwargs.get(path_arg)
            if path is None:
                import inspect
                sig = inspect.signature(func)
                params = list(sig.parameters.keys())
                if path_arg in params:
                    idx = params.index(path_arg)
                    if idx < len(args):
                        path = args[idx]
            
            if path is None:
                logger.warning(f"[WorkspaceGuard] No path found for {operation}")
                return func(*args, **kwargs)
            
            context = get_current_context()
            workspace_id = getattr(context, "workspace_id", "unknown") if context else "unknown"
            
            allowed, error = guard_instance.guard_path(path, required_permission, operation)
            
            guard_instance.audit_logger.log_workspace_access(
                agent_id=context.agent_id if context else "unknown",
                workspace_id=workspace_id,
                operation=operation,
                path=str(path),
                allowed=allowed,
                permission_used=required_permission.name,
                result="success" if allowed else "denied",
            )
            
            if not allowed:
                category, policy = guard_instance.classifier.classify(str(path))
                if policy == Policy.ALLOW_WITH_CONFIRM:
                    raise PathRequiresConfirmationError(
                        f"Path '{path}' requires user confirmation",
                        path=str(path),
                        operation=operation,
                        category=category.value,
                    )
                raise WorkspaceAccessDeniedError(
                    f"[Workspace Guard] {error}",
                    path=str(path),
                    operation=operation,
                )
            
            return func(*args, **kwargs)
        
        # Return appropriate wrapper based on function type
        import asyncio
        if asyncio.iscoroutinefunction(func):
            return async_wrapper  # type: ignore
        else:
            return sync_wrapper  # type: ignore
    
    return decorator


# Global guard instance
_workspace_guard: Optional[WorkspaceGuard] = None


def get_workspace_guard() -> WorkspaceGuard:
    """Get the global workspace guard instance.
    
    Returns:
        WorkspaceGuard instance
    """
    global _workspace_guard
    if _workspace_guard is None:
        _workspace_guard = WorkspaceGuard()
    return _workspace_guard
