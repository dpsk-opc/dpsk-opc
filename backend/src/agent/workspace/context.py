"""AgentContext management for DPSK-OPC Agent.

This module provides context management for agent execution:
- Thread/coroutine local storage for current context
- Context getters and setters
- Context lifecycle management
"""

from __future__ import annotations

import logging
from contextvars import ContextVar
from pathlib import Path
from typing import Optional

from .models import AgentContext, Permission

logger = logging.getLogger(__name__)

# Global context variable (coroutine local storage)
_current_context: ContextVar[Optional[AgentContext]] = ContextVar(
    "current_context", default=None
)


def set_current_context(context: Optional[AgentContext]) -> None:
    """Set the current execution context.
    
    Args:
        context: The context to set, or None to clear
    """
    token = _current_context.set(context)
    if context:
        logger.debug(
            f"[Context] Set context for agent: {context.agent_id}, "
            f"workspace: {context.workspace_id}"
        )
    else:
        logger.debug("[Context] Cleared context")


def get_current_context() -> Optional[AgentContext]:
    """Get the current execution context.
    
    Returns:
        The current AgentContext, or None if not set
    """
    return _current_context.get()


def get_current_agent_id() -> Optional[str]:
    """Get the current agent ID.
    
    Returns:
        The current agent ID, or None if not set
    """
    context = _current_context.get()
    return context.agent_id if context else None


def get_current_workspace() -> Optional[Path]:
    """Get the current workspace root path.
    
    Returns:
        The current workspace root path, or None if not set
    """
    context = _current_context.get()
    return context.workspace_root if context else None


def get_current_permissions() -> Permission:
    """Get the current agent permissions.
    
    Returns:
        The current permissions, or Permission.NONE if not set
    """
    context = _current_context.get()
    return context.permissions if context else Permission.NONE


def get_current_session_id() -> Optional[str]:
    """Get the current session ID.
    
    Returns:
        The current session ID, or None if not set
    """
    context = _current_context.get()
    return context.session_id if context else None


def get_current_task_id() -> Optional[str]:
    """Get the current task ID.
    
    Returns:
        The current task ID, or None if not set
    """
    context = _current_context.get()
    return context.task_id if context else None


def clear_current_context() -> None:
    """Clear the current execution context."""
    _current_context.set(None)
    logger.debug("[Context] Context cleared")


def has_permission(permission: Permission) -> bool:
    """Check if current context has a specific permission.
    
    Args:
        permission: The permission to check
        
    Returns:
        True if the permission is granted, False otherwise
    """
    context = _current_context.get()
    if context is None:
        return False
    return context.has_permission(permission)


def has_any_permission(*permissions: Permission) -> bool:
    """Check if current context has any of the specified permissions.
    
    Args:
        *permissions: The permissions to check
        
    Returns:
        True if any permission is granted, False otherwise
    """
    context = _current_context.get()
    if context is None:
        return False
    return context.has_any_permission(*permissions)


def has_all_permissions(*permissions: Permission) -> bool:
    """Check if current context has all specified permissions.
    
    Args:
        *permissions: The permissions to check
        
    Returns:
        True if all permissions are granted, False otherwise
    """
    context = _current_context.get()
    if context is None:
        return False
    return context.has_all_permissions(*permissions)


class AgentContextManager:
    """Context manager for AgentContext.
    
    Usage:
        async with AgentContextManager(agent_id, workspace_root, permissions):
            # Code executed with context set
            context = get_current_context()
    """
    
    def __init__(
        self,
        agent_id: str,
        workspace_id: str,
        workspace_root: Path,
        permissions: Permission,
        session_id: Optional[str] = None,
        task_id: Optional[str] = None,
        parent_context: Optional[AgentContext] = None,
    ):
        self.agent_id = agent_id  # Store agent_id for logging
        self.context = AgentContext(
            agent_id=agent_id,
            workspace_id=workspace_id,
            workspace_root=workspace_root,
            permissions=permissions,
            session_id=session_id,
            task_id=task_id,
            parent_context=parent_context,
        )
        self._previous_context: Optional[AgentContext] = None
    
    async def __aenter__(self) -> AgentContext:
        """Enter context."""
        self._previous_context = get_current_context()
        set_current_context(self.context)
        logger.debug(f"[Context] Entered context for agent: {self.agent_id}")
        return self.context
    
    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """Exit context."""
        set_current_context(self._previous_context)
        logger.debug("[Context] Exited context")
        return None
    
    def __enter__(self) -> AgentContext:
        """Enter context (sync version)."""
        self._previous_context = get_current_context()
        set_current_context(self.context)
        logger.debug(f"[Context] Entered context for agent: {self.agent_id}")
        return self.context
    
    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        """Exit context (sync version)."""
        set_current_context(self._previous_context)
        logger.debug("[Context] Exited context")
        return None


def require_permission(permission: Permission) -> None:
    """Require a specific permission, raise if not granted.
    
    Args:
        permission: The required permission
        
    Raises:
        PermissionError: If permission is not granted
    """
    if not has_permission(permission):
        raise PermissionError(f"Required permission: {permission}")
