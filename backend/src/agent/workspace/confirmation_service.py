"""ConfirmationService for DPSK-OPC Agent.

This module provides user confirmation functionality:
- Request confirmation for path access
- Approve or reject confirmation requests
- Support different permission scopes
- Timeout handling
"""

from __future__ import annotations

import asyncio
import logging
import time
from pathlib import Path
from typing import Any, Callable, Optional

from .models import (
    ConfirmationStatus,
    PendingConfirmation,
    Permission,
    TempPermissionScope,
    TemporaryPermission,
)
from .temp_permission import TemporaryPermissionService, get_temp_permission_service
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class ConfirmationService:
    """Service for user confirmation of path access."""
    
    def __init__(
        self,
        temp_permission_service: Optional[TemporaryPermissionService] = None,
        event_emitter: Optional[Any] = None,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the confirmation service.
        
        Args:
            temp_permission_service: Temporary permission service
            event_emitter: Event emitter for pushing confirmations
            audit_logger: Audit logger instance
        """
        self.temp_permission_service = temp_permission_service or get_temp_permission_service()
        self.event_emitter = event_emitter
        self.audit_logger = audit_logger or get_audit_logger()
        
        # Store pending confirmations
        self._pending_confirmations: dict[str, PendingConfirmation] = {}
        
        # Callbacks for confirmation events
        self._confirmation_callbacks: list[Callable] = []
    
    def add_confirmation_callback(self, callback: Callable) -> None:
        """Add a callback for confirmation events.
        
        Args:
            callback: Callback function (request_id, confirmed, scope) -> None
        """
        self._confirmation_callbacks.append(callback)
    
    async def request_confirmation(
        self,
        agent_id: str,
        path: str,
        operation: str,
        reason: str,
        task_id: Optional[str] = None,
        timeout_seconds: float = 120.0,
    ) -> PendingConfirmation:
        """Request user confirmation for path access.
        
        Args:
            agent_id: Agent requesting confirmation
            path: Path to access
            operation: Operation type
            reason: Reason for access
            task_id: Associated task ID
            timeout_seconds: Timeout for confirmation
            
        Returns:
            PendingConfirmation object
        """
        # Check if there's already a pending request
        for pending in self._pending_confirmations.values():
            if (pending.agent_id == agent_id and 
                pending.path == path and 
                pending.operation == operation and
                pending.is_pending()):
                logger.info(f"[Confirmation] Reusing pending request: {pending.request_id}")
                return pending
        
        # Create new request
        confirmation = PendingConfirmation.create(
            agent_id=agent_id,
            path=path,
            operation=operation,
            reason=reason,
            task_id=task_id,
        )
        confirmation.timeout_seconds = timeout_seconds
        
        self._pending_confirmations[confirmation.request_id] = confirmation
        
        # Emit event for frontend
        if self.event_emitter:
            await self._emit_confirmation_event(confirmation)
        
        # Trigger callbacks
        for callback in self._confirmation_callbacks:
            try:
                callback(confirmation.request_id, None, None)
            except Exception as e:
                logger.warning(f"[Confirmation] Callback error: {e}")
        
        logger.info(f"[Confirmation] Requested for {agent_id}: {path} ({operation})")
        
        return confirmation
    
    async def approve(
        self,
        request_id: str,
        approver_id: str,
        scope: TempPermissionScope,
        permanent_path: bool = False,
    ) -> Optional[TemporaryPermission]:
        """Approve a confirmation request.
        
        Args:
            request_id: Request identifier
            approver_id: User approving the request
            scope: Permission scope to grant
            permanent_path: Whether to add path to permanent whitelist
            
        Returns:
            TemporaryPermission if granted, None if not found
        """
        confirmation = self._pending_confirmations.get(request_id)
        
        if not confirmation:
            logger.warning(f"[Confirmation] Request not found: {request_id}")
            return None
        
        if not confirmation.is_pending():
            logger.warning(f"[Confirmation] Request not pending: {request_id}")
            return None
        
        confirmation.approve()
        
        # Get required permission
        permission = self._get_permission_for_operation(confirmation.operation)
        
        # Grant temporary permission
        temp_perm = await self.temp_permission_service.grant_permission(
            agent_id=confirmation.agent_id,
            path=confirmation.path,
            permission=permission,
            scope=scope,
            granted_by=approver_id,
            task_id=confirmation.task_id,
        )
        
        # Handle permanent whitelist
        if permanent_path:
            # Add to workspace manager's shared dirs
            pass  # TODO: integrate with workspace manager
        
        # Trigger callbacks
        for callback in self._confirmation_callbacks:
            try:
                callback(request_id, True, scope)
            except Exception as e:
                logger.warning(f"[Confirmation] Callback error: {e}")
        
        logger.info(f"[Confirmation] Approved: {request_id} -> {scope.value}")
        
        return temp_perm
    
    async def reject(
        self,
        request_id: str,
        rejector_id: str,
        reason: Optional[str] = None,
    ) -> bool:
        """Reject a confirmation request.
        
        Args:
            request_id: Request identifier
            rejector_id: User rejecting the request
            reason: Rejection reason
            
        Returns:
            True if rejected successfully
        """
        confirmation = self._pending_confirmations.get(request_id)
        
        if not confirmation:
            return False
        
        confirmation.reject()
        
        # Trigger callbacks
        for callback in self._confirmation_callbacks:
            try:
                callback(request_id, False, None)
            except Exception as e:
                logger.warning(f"[Confirmation] Callback error: {e}")
        
        logger.info(f"[Confirmation] Rejected: {request_id}")
        
        return True
    
    async def wait_for_confirmation(
        self,
        request_id: str,
        timeout_seconds: float = 120.0,
    ) -> ConfirmationStatus:
        """Wait for a confirmation to complete.
        
        Args:
            request_id: Request identifier
            timeout_seconds: Timeout in seconds
            
        Returns:
            Final confirmation status
        """
        start_time = time.time()
        
        while time.time() - start_time < timeout_seconds:
            confirmation = self._pending_confirmations.get(request_id)
            
            if not confirmation:
                return ConfirmationStatus.REJECTED
            
            if not confirmation.is_pending():
                return confirmation.status
            
            await asyncio.sleep(0.5)
        
        # Timeout
        confirmation = self._pending_confirmations.get(request_id)
        if confirmation:
            confirmation.timeout()
        
        return ConfirmationStatus.TIMEOUT
    
    def get_pending_requests(
        self,
        agent_id: Optional[str] = None,
    ) -> list[PendingConfirmation]:
        """Get pending confirmation requests.
        
        Args:
            agent_id: Filter by agent (optional)
            
        Returns:
            List of pending confirmations
        """
        result = []
        
        for confirmation in self._pending_confirmations.values():
            if not confirmation.is_pending():
                continue
            
            if agent_id and confirmation.agent_id != agent_id:
                continue
            
            result.append(confirmation)
        
        return result
    
    async def cleanup_timeouted(self) -> int:
        """Clean up timed out confirmations.
        
        Returns:
            Number of confirmations cleaned up
        """
        now = time.time()
        cleaned = 0
        
        for confirmation in list(self._pending_confirmations.values()):
            if not confirmation.is_pending():
                continue
            
            if now - confirmation.created_at > confirmation.timeout_seconds:
                confirmation.timeout()
                cleaned += 1
        
        return cleaned
    
    async def _emit_confirmation_event(self, confirmation: PendingConfirmation) -> None:
        """Emit a confirmation event for frontend."""
        if not self.event_emitter:
            return
        
        try:
            # Create confirmation event data
            event_data = {
                "type": "confirmation_request",
                "request_id": confirmation.request_id,
                "agent_id": confirmation.agent_id,
                "path": confirmation.path,
                "operation": confirmation.operation,
                "reason": confirmation.reason,
                "task_id": confirmation.task_id,
                "timeout_seconds": confirmation.timeout_seconds,
                "created_at": confirmation.created_at,
            }
            
            # Emit via event emitter (implementation depends on event system)
            if hasattr(self.event_emitter, "emit"):
                await self.event_emitter.emit("confirmation", event_data)
        
        except Exception as e:
            logger.warning(f"[Confirmation] Failed to emit event: {e}")
    
    def _get_permission_for_operation(self, operation: str) -> Permission:
        """Get permission required for an operation."""
        mapping = {
            "read_file": Permission.READ,
            "write_to_file": Permission.WRITE,
            "replace_in_file": Permission.WRITE,
            "delete_file": Permission.DELETE,
            "list_dir": Permission.LIST,
            "execute_command": Permission.EXECUTE,
            "search_file": Permission.READ,
            "search_content": Permission.READ,
        }
        return mapping.get(operation, Permission.READ)


_confirmation_service: Optional[ConfirmationService] = None

def get_confirmation_service() -> ConfirmationService:
    """Get the global confirmation service instance."""
    global _confirmation_service
    if _confirmation_service is None:
        _confirmation_service = ConfirmationService()
    return _confirmation_service
