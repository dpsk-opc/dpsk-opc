"""WorkspaceManager for DPSK-OPC Agent.

This module provides workspace management functionality:
- Create and manage agent workspaces
- Validate path access
- Handle workspace lifecycle (archive, delete)
"""

from __future__ import annotations

import logging
import shutil
import time
from pathlib import Path
from typing import Optional

from .models import (
    Permission,
    Workspace,
    WorkspaceStatus,
    SharedAccessRequest,
    SharedAccessStatus,
)
from .validator import PathValidator, get_path_validator
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class WorkspaceManager:
    """Manager for agent workspaces."""
    
    def __init__(
        self,
        workspace_root: Path,
        exchange_path: Optional[Path] = None,
        shared_path: Optional[Path] = None,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the workspace manager.
        
        Args:
            workspace_root: Root directory for all workspaces
            exchange_path: Path for cross-agent file exchange
            shared_path: Path for shared directories
            audit_logger: Audit logger instance
        """
        self.workspace_root = Path(workspace_root)
        self.exchange_path = Path(exchange_path) if exchange_path else self.workspace_root.parent / "exchange"
        self.shared_path = Path(shared_path) if shared_path else self.workspace_root.parent / "shared"
        self.audit_logger = audit_logger or get_audit_logger()
        self.validator = get_path_validator()
        
        # In-memory workspace store (in production, use database)
        self._workspaces: dict[str, Workspace] = {}
        
        # Ensure directories exist
        self._ensure_directories()
    
    def _ensure_directories(self) -> None:
        """Ensure required directories exist."""
        try:
            self.workspace_root.mkdir(parents=True, exist_ok=True)
            self.exchange_path.mkdir(parents=True, exist_ok=True)
            self.shared_path.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            logger.error(f"[WorkspaceManager] Failed to create directories: {e}")
    
    def create_workspace(
        self,
        agent_id: str,
        permissions: Permission = Permission.READ | Permission.LIST,
        parent_workspace_id: Optional[str] = None,
    ) -> Workspace:
        """Create a new workspace for an agent.
        
        Args:
            agent_id: Agent identifier
            permissions: Initial permissions
            parent_workspace_id: Parent workspace ID for hierarchy
            
        Returns:
            Created Workspace object
        """
        workspace_id = f"ws_{agent_id}"
        
        # Check if workspace already exists
        if workspace_id in self._workspaces:
            workspace = self._workspaces[workspace_id]
            if workspace.status == WorkspaceStatus.DELETED:
                # Reactivate deleted workspace
                workspace.status = WorkspaceStatus.ACTIVE
                workspace.updated_at = time.time()
                logger.info(f"[WorkspaceManager] Reactivated workspace: {workspace_id}")
                return workspace
            raise ValueError(f"Workspace already exists: {workspace_id}")
        
        # Create workspace directory
        workspace_path = self.workspace_root / agent_id
        try:
            workspace_path.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            logger.error(f"[WorkspaceManager] Failed to create workspace directory: {e}")
            raise
        
        # Create workspace object
        workspace = Workspace.create(
            agent_id=agent_id,
            root_path=workspace_path,
            permissions=permissions,
            parent_workspace_id=parent_workspace_id,
        )
        
        # Handle parent workspace
        if parent_workspace_id:
            parent = self._workspaces.get(parent_workspace_id)
            if parent:
                parent.child_workspace_ids.append(workspace_id)
        
        # Store workspace
        self._workspaces[workspace_id] = workspace
        
        logger.info(f"[WorkspaceManager] Created workspace: {workspace_id} at {workspace_path}")
        
        return workspace
    
    def get_workspace(self, agent_id: str) -> Optional[Workspace]:
        """Get workspace for an agent.
        
        Args:
            agent_id: Agent identifier
            
        Returns:
            Workspace object or None if not found
        """
        workspace_id = f"ws_{agent_id}"
        workspace = self._workspaces.get(workspace_id)
        
        if workspace and workspace.status != WorkspaceStatus.DELETED:
            return workspace
        
        return None
    
    def get_workspace_by_id(self, workspace_id: str) -> Optional[Workspace]:
        """Get workspace by workspace ID.
        
        Args:
            workspace_id: Workspace identifier
            
        Returns:
            Workspace object or None if not found
        """
        workspace = self._workspaces.get(workspace_id)
        if workspace and workspace.status != WorkspaceStatus.DELETED:
            return workspace
        return None
    
    def validate_path(
        self,
        agent_id: str,
        requested_path: str,
        required_permission: Permission,
    ) -> tuple[bool, str]:
        """Validate that an agent can access a path with given permission.
        
        Args:
            agent_id: Agent identifier
            requested_path: Path to validate
            required_permission: Required permission
            
        Returns:
            Tuple of (allowed, error_message)
        """
        workspace = self.get_workspace(agent_id)
        
        if not workspace:
            return False, f"Workspace not found for agent: {agent_id}"
        
        if not workspace.is_active():
            return False, f"Workspace is not active: {workspace.workspace_id}"
        
        # Check required permission
        required_val = required_permission.value if hasattr(required_permission, 'value') else required_permission
        if not workspace.has_permission(required_val):
            return False, f"Agent lacks required permission: {required_permission}"
        
        # Validate path is within workspace
        valid, error = self.validator.validate_path(
            path=requested_path,
            workspace_root=workspace.root_path,
            shared_dirs=workspace.shared_dirs,
        )
        
        if not valid:
            return False, error
        
        return True, ""
    
    def is_path_within_workspace(
        self,
        agent_id: str,
        path: str,
    ) -> bool:
        """Check if a path is within an agent's workspace.
        
        Args:
            agent_id: Agent identifier
            path: Path to check
            
        Returns:
            True if path is within workspace
        """
        workspace = self.get_workspace(agent_id)
        if not workspace:
            return False
        
        valid, _ = self.validator.validate_path(
            path=path,
            workspace_root=workspace.root_path,
            shared_dirs=workspace.shared_dirs,
        )
        return valid
    
    def update_permissions(
        self,
        agent_id: str,
        permissions: Permission,
    ) -> bool:
        """Update agent workspace permissions.
        
        Args:
            agent_id: Agent identifier
            permissions: New permissions
            
        Returns:
            True if updated successfully
        """
        workspace = self.get_workspace(agent_id)
        if not workspace:
            return False
        
        workspace.permissions = permissions
        workspace.updated_at = time.time()
        
        logger.info(f"[WorkspaceManager] Updated permissions for {agent_id}: {permissions}")
        return True
    
    def add_shared_directory(
        self,
        agent_id: str,
        shared_path: Path,
    ) -> bool:
        """Add a shared directory to agent's workspace.
        
        Args:
            agent_id: Agent identifier
            shared_path: Path to add to whitelist
            
        Returns:
            True if added successfully
        """
        workspace = self.get_workspace(agent_id)
        if not workspace:
            return False
        
        workspace.add_shared_dir(shared_path)
        logger.info(f"[WorkspaceManager] Added shared dir to {agent_id}: {shared_path}")
        return True
    
    def archive_workspace(self, agent_id: str) -> bool:
        """Archive an agent's workspace.
        
        Args:
            agent_id: Agent identifier
            
        Returns:
            True if archived successfully
        """
        workspace = self.get_workspace(agent_id)
        if not workspace:
            return False
        
        workspace.archive()
        logger.info(f"[WorkspaceManager] Archived workspace: {workspace.workspace_id}")
        return True
    
    def delete_workspace(self, agent_id: str, delete_files: bool = False) -> bool:
        """Delete an agent's workspace.
        
        Args:
            agent_id: Agent identifier
            delete_files: Whether to delete physical files
            
        Returns:
            True if deleted successfully
        """
        workspace = self.get_workspace(agent_id)
        if not workspace:
            return False
        
        workspace.delete()
        
        # Optionally delete physical files
        if delete_files:
            try:
                if workspace.root_path.exists():
                    shutil.rmtree(workspace.root_path)
                logger.info(f"[WorkspaceManager] Deleted workspace files: {workspace.root_path}")
            except Exception as e:
                logger.error(f"[WorkspaceManager] Failed to delete workspace files: {e}")
        
        logger.info(f"[WorkspaceManager] Deleted workspace: {workspace.workspace_id}")
        return True
    
    def list_workspaces(
        self,
        status: Optional[WorkspaceStatus] = None,
    ) -> list[Workspace]:
        """List all workspaces.
        
        Args:
            status: Filter by status (optional)
            
        Returns:
            List of workspaces
        """
        workspaces = list(self._workspaces.values())
        
        if status:
            workspaces = [ws for ws in workspaces if ws.status == status]
        
        return workspaces
    
    def get_workspace_stats(self, agent_id: str) -> Optional[dict]:
        """Get workspace statistics.
        
        Args:
            agent_id: Agent identifier
            
        Returns:
            Dictionary with workspace stats
        """
        workspace = self.get_workspace(agent_id)
        if not workspace:
            return None
        
        try:
            total_size = 0
            file_count = 0
            dir_count = 0
            
            if workspace.root_path.exists():
                for item in workspace.root_path.rglob("*"):
                    if item.is_file():
                        total_size += item.stat().st_size
                        file_count += 1
                    elif item.is_dir():
                        dir_count += 1
            
            return {
                "workspace_id": workspace.workspace_id,
                "agent_id": workspace.agent_id,
                "root_path": str(workspace.root_path),
                "permissions": str(workspace.permissions),
                "status": workspace.status.value,
                "created_at": workspace.created_at,
                "updated_at": workspace.updated_at,
                "total_size_bytes": total_size,
                "file_count": file_count,
                "directory_count": dir_count,
                "shared_dirs": [str(d) for d in workspace.shared_dirs],
            }
        except Exception as e:
            logger.error(f"[WorkspaceManager] Failed to get workspace stats: {e}")
            return None


# Global workspace manager instance
_workspace_manager: Optional[WorkspaceManager] = None


def get_workspace_manager() -> WorkspaceManager:
    """Get the global workspace manager instance.
    
    Returns:
        WorkspaceManager instance
    """
    global _workspace_manager
    if _workspace_manager is None:
        # Import settings
        import os
        workspace_root = Path(os.environ.get("DPSK_WORKSPACE_ROOT", "/storage/ws"))
        exchange_path = Path(os.environ.get("DPSK_EXCHANGE_PATH", "/storage/exchange"))
        shared_path = Path(os.environ.get("DPSK_SHARED_PATH", "/storage/shared"))
        
        _workspace_manager = WorkspaceManager(
            workspace_root=workspace_root,
            exchange_path=exchange_path,
            shared_path=shared_path,
        )
    
    return _workspace_manager


def create_workspace_for_agent(
    agent_id: str,
    permissions: Permission = Permission.READ | Permission.LIST,
) -> Workspace:
    """Create a workspace for an agent.
    
    Args:
        agent_id: Agent identifier
        permissions: Initial permissions
        
    Returns:
        Created Workspace
    """
    return get_workspace_manager().create_workspace(agent_id, permissions)


def get_agent_workspace(agent_id: str) -> Optional[Workspace]:
    """Get workspace for an agent.
    
    Args:
        agent_id: Agent identifier
        
    Returns:
        Workspace or None
    """
    return get_workspace_manager().get_workspace(agent_id)
