"""TemporaryPermissionService for DPSK-OPC Agent.

This module provides temporary permission management:
- Grant time-limited access permissions
- Check and validate temporary permissions
- Support different permission scopes (one-time, task, session)
- Automatic cleanup of expired permissions
"""

from __future__ import annotations

import asyncio
import logging
import time
from pathlib import Path
from typing import Optional

from .models import (
    Permission,
    TemporaryPermission,
    TempPermissionScope,
    TempPermissionStatus,
)
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class TemporaryPermissionService:
    """Service for managing temporary permissions."""
    
    def __init__(
        self,
        audit_logger: Optional[AuditLogger] = None,
        check_interval: float = 300.0,  # 5 minutes
    ):
        """Initialize the temporary permission service.
        
        Args:
            audit_logger: Audit logger instance
            check_interval: Interval for cleanup checks (seconds)
        """
        self.audit_logger = audit_logger or get_audit_logger()
        self.check_interval = check_interval
        
        # In-memory permission store (in production, use database)
        self._permissions: dict[str, TemporaryPermission] = {}
        
        # Index by agent_id for quick lookup
        self._agent_permissions: dict[str, set[str]] = {}
        
        # Background cleanup task
        self._cleanup_task: Optional[asyncio.Task] = None
    
    async def start_cleanup_task(self) -> None:
        """Start the background cleanup task."""
        if self._cleanup_task is None or self._cleanup_task.done():
            self._cleanup_task = asyncio.create_task(self._cleanup_loop())
            logger.info("[TempPermission] Started cleanup task")
    
    async def stop_cleanup_task(self) -> None:
        """Stop the background cleanup task."""
        if self._cleanup_task:
            self._cleanup_task.cancel()
            try:
                await self._cleanup_task
            except asyncio.CancelledError:
                pass
            logger.info("[TempPermission] Stopped cleanup task")
    
    async def _cleanup_loop(self) -> None:
        """Background cleanup loop."""
        while True:
            try:
                await asyncio.sleep(self.check_interval)
                cleaned = await self.cleanup_expired()
                if cleaned > 0:
                    logger.info(f"[TempPermission] Cleaned up {cleaned} expired permissions")
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"[TempPermission] Cleanup error: {e}")
    
    async def grant_permission(
        self,
        agent_id: str,
        path: str,
        permission: Permission,
        scope: TempPermissionScope,
        granted_by: str,
        task_id: Optional[str] = None,
        session_id: Optional[str] = None,
        ttl_seconds: Optional[int] = None,
    ) -> TemporaryPermission:
        """Grant a temporary permission.
        
        Args:
            agent_id: Agent identifier
            path: Authorized path
            permission: Permission to grant
            scope: Permission scope
            granted_by: User who granted the permission
            task_id: Associated task ID (for TASK_SCOPE)
            session_id: Associated session ID (for SESSION_SCOPE)
            ttl_seconds: Time to live in seconds
            
        Returns:
            Created TemporaryPermission
        """
        temp_perm = TemporaryPermission.create(
            agent_id=agent_id,
            path=Path(path),
            permission=permission,
            scope=scope,
            granted_by=granted_by,
            task_id=task_id,
            session_id=session_id,
            ttl_seconds=ttl_seconds,
        )
        
        # Store permission
        self._permissions[temp_perm.permission_id] = temp_perm
        
        # Index by agent
        if agent_id not in self._agent_permissions:
            self._agent_permissions[agent_id] = set()
        self._agent_permissions[agent_id].add(temp_perm.permission_id)
        
        # Audit log
        self.audit_logger.log_permission_grant(
            agent_id=agent_id,
            granted_by=granted_by,
            permission=permission.name,
            path=path,
            scope=scope.value,
            permission_id=temp_perm.permission_id,
            expires_at=temp_perm.expires_at,
        )
        
        logger.info(
            f"[TempPermission] Granted {permission.name} to {agent_id} "
            f"for {path} (scope={scope.value}, expires={temp_perm.expires_at})"
        )
        
        return temp_perm
    
    async def check_permission(
        self,
        agent_id: str,
        path: str,
        required_permission: Permission,
        task_id: Optional[str] = None,
        session_id: Optional[str] = None,
    ) -> tuple[bool, str]:
        """Check if an agent has a temporary permission for a path.
        
        Args:
            agent_id: Agent identifier
            path: Path to check
            required_permission: Required permission
            task_id: Current task ID (for TASK_SCOPE)
            session_id: Current session ID (for SESSION_SCOPE)
            
        Returns:
            Tuple of (has_permission, reason)
        """
        agent_perms = self._agent_permissions.get(agent_id, set())
        
        for perm_id in agent_perms:
            perm = self._permissions.get(perm_id)
            if not perm:
                continue
            
            # Check if permission is active
            if not perm.is_active():
                continue
            
            # Check path matches
            perm_path = str(perm.path)
            requested_path = str(Path(path))
            
            if not (requested_path == perm_path or requested_path.startswith(perm_path + "/")):
                continue
            
            # Check permission type
            if perm.permission != required_permission:
                continue
            
            # Check scope constraints
            if perm.scope == TempPermissionScope.TASK_SCOPE:
                if task_id and perm.task_id != task_id:
                    continue
            
            if perm.scope == TempPermissionScope.SESSION_SCOPE:
                if session_id and perm.session_id != session_id:
                    continue
            
            # Permission found and valid
            # For ONE_TIME scope, mark as consumed
            if perm.scope == TempPermissionScope.ONE_TIME:
                perm.consume()
                self._remove_permission(perm)
            
            return True, f"Temporary permission: {perm.permission_id}"
        
        return False, "No valid temporary permission found"
    
    async def revoke_permission(self, permission_id: str) -> bool:
        """Revoke a temporary permission.
        
        Args:
            permission_id: Permission to revoke
            
        Returns:
            True if revoked successfully
        """
        perm = self._permissions.get(permission_id)
        if not perm:
            return False
        
        perm.revoke()
        self._remove_permission(perm)
        
        self.audit_logger.log_permission_revoke(
            permission_id=permission_id,
            agent_id=perm.agent_id,
            revoked_by="system",  # Could be improved with actual revoker
        )
        
        logger.info(f"[TempPermission] Revoked permission: {permission_id}")
        return True
    
    async def cleanup_expired(self) -> int:
        """Clean up expired permissions.
        
        Returns:
            Number of permissions cleaned up
        """
        now = time.time()
        cleaned = 0
        
        expired_ids = [
            perm_id for perm_id, perm in self._permissions.items()
            if perm.expires_at and now > perm.expires_at
        ]
        
        for perm_id in expired_ids:
            perm = self._permissions[perm_id]
            perm.status = TempPermissionStatus.EXPIRED
            self._remove_permission(perm)
            cleaned += 1
        
        return cleaned
    
    def _remove_permission(self, perm: TemporaryPermission) -> None:
        """Remove a permission from storage."""
        if perm.permission_id in self._permissions:
            del self._permissions[perm.permission_id]
        
        if perm.agent_id in self._agent_permissions:
            self._agent_permissions[perm.agent_id].discard(perm.permission_id)
    
    async def get_active_permissions(
        self,
        agent_id: Optional[str] = None,
    ) -> list[TemporaryPermission]:
        """Get active temporary permissions.
        
        Args:
            agent_id: Filter by agent ID (optional)
            
        Returns:
            List of active permissions
        """
        now = time.time()
        result = []
        
        if agent_id:
            perm_ids = self._agent_permissions.get(agent_id, set())
            for perm_id in perm_ids:
                perm = self._permissions.get(perm_id)
                if perm and perm.is_active():
                    result.append(perm)
        else:
            for perm in self._permissions.values():
                if perm.is_active():
                    result.append(perm)
        
        return result
    
    async def revoke_all_for_agent(self, agent_id: str) -> int:
        """Revoke all permissions for an agent.
        
        Args:
            agent_id: Agent identifier
            
        Returns:
            Number of permissions revoked
        """
        agent_perms = list(self._agent_permissions.get(agent_id, set()))
        count = 0
        
        for perm_id in agent_perms:
            if await self.revoke_permission(perm_id):
                count += 1
        
        return count
    
    async def revoke_all_for_path(self, path: str) -> int:
        """Revoke all permissions for a path.
        
        Args:
            path: Path to revoke
            
        Returns:
            Number of permissions revoked
        """
        path_str = str(Path(path))
        count = 0
        
        for perm_id, perm in list(self._permissions.items()):
            perm_path = str(perm.path)
            if perm_path == path_str or perm_path.startswith(path_str + "/"):
                if await self.revoke_permission(perm_id):
                    count += 1
        
        return count


# Global service instance
_temp_permission_service: Optional[TemporaryPermissionService] = None


def get_temp_permission_service() -> TemporaryPermissionService:
    """Get the global temporary permission service instance.
    
    Returns:
        TemporaryPermissionService instance
    """
    global _temp_permission_service
    if _temp_permission_service is None:
        _temp_permission_service = TemporaryPermissionService()
    
    return _temp_permission_service
