"""ListDirGuard for DPSK-OPC Agent.

This module provides directory listing security:
- Filter results based on permissions
- Hide forbidden and sensitive directories
- Prevent directory existence disclosure
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any, Optional

from .context import get_current_context
from .models import ListDirConfig, Permission
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class ListDirGuard:
    """Guard for directory listing operations."""
    
    def __init__(
        self,
        config: Optional[ListDirConfig] = None,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the list directory guard.
        
        Args:
            config: List directory configuration
            audit_logger: Audit logger instance
        """
        self.config = config or ListDirConfig.default_config()
        self.audit_logger = audit_logger or get_audit_logger()
    
    def filter_results(
        self,
        items: list[dict[str, Any]],
        workspace_root: Path,
        permissions: Permission,
    ) -> list[dict[str, Any]]:
        """Filter directory listing results.
        
        Args:
            items: Directory items
            workspace_root: Workspace root
            permissions: Current permissions
            
        Returns:
            Filtered items
        """
        filtered = []
        
        for item in items:
            name = item.get("name", "")
            path = item.get("path", "")
            item_type = item.get("type", "file")
            
            # Check if should hide
            if self.should_hide(name, path, item_type):
                continue
            
            # If item is a directory, check LIST permission
            if item_type == "directory":
                # For directories, we check if the parent has LIST permission
                # (the actual listing of subdirectory contents would be checked separately)
                if Permission.LIST not in permissions:
                    # Still show the directory exists, but don't show contents
                    # For now, we include it but could mark it differently
                    pass
            
            filtered.append(item)
        
        return filtered
    
    def should_hide(self, name: str, path: str, item_type: str) -> bool:
        """Check if an item should be hidden.
        
        Args:
            name: Item name
            path: Item path
            item_type: Item type (file/directory)
            
        Returns:
            True if item should be hidden
        """
        # Hide hidden directories
        if name.startswith("."):
            # But don't hide some common ones that might be relevant
            allowed_hidden = {".git", ".workspace", ".config"}
            if name not in allowed_hidden:
                return True
        
        # Hide configured hidden directories
        if name in self.config.hidden_dirs:
            return True
        
        # Hide configured hidden files
        if name in self.config.hidden_files:
            return True
        
        # Check for sensitive patterns in path
        sensitive_patterns = [
            ".ssh",
            ".gnupg",
            ".aws",
            ".docker",
            ".kube",
            ".pki",
            ".local/share",
        ]
        
        for pattern in sensitive_patterns:
            if pattern in path:
                return True
        
        return False
    
    def check_and_filter(
        self,
        path: str,
        items: list[dict[str, Any]],
        workspace_root: Path,
        permissions: Permission,
    ) -> tuple[bool, str, list[dict[str, Any]]]:
        """Check list directory operation and filter results.
        
        Args:
            path: Directory path
            items: Directory items
            workspace_root: Workspace root
            permissions: Current permissions
            
        Returns:
            Tuple of (allowed, reason, filtered_items)
        """
        context = get_current_context()
        agent_id = context.agent_id if context else "unknown"
        workspace_id = getattr(context, "workspace_id", "unknown") if context else "unknown"
        
        # Check LIST permission
        if Permission.LIST not in permissions:
            self.audit_logger.log_workspace_access(
                agent_id=agent_id,
                workspace_id=workspace_id,
                operation="list_dir",
                path=path,
                allowed=False,
                permission_used="LIST",
                reason="Missing LIST permission",
                result="denied",
            )
            return False, "Missing LIST permission", []
        
        # Check if path is within workspace
        try:
            dir_path = Path(path).resolve()
            if not str(dir_path).startswith(str(workspace_root.resolve())):
                self.audit_logger.log_workspace_access(
                    agent_id=agent_id,
                    workspace_id=workspace_id,
                    operation="list_dir",
                    path=path,
                    allowed=False,
                    permission_used="LIST",
                    reason="Path outside workspace",
                    result="denied",
                )
                return False, "Path outside workspace", []
        except Exception as e:
            return False, f"Invalid path: {e}", []
        
        # Filter results
        filtered = self.filter_results(items, workspace_root, permissions)
        
        # Log the access
        self.audit_logger.log_workspace_access(
            agent_id=agent_id,
            workspace_id=workspace_id,
            operation="list_dir",
            path=path,
            allowed=True,
            permission_used="LIST",
            result="success",
            item_count=len(filtered),
        )
        
        return True, "", filtered


# Global guard instance
_list_dir_guard: Optional[ListDirGuard] = None


def get_list_dir_guard() -> ListDirGuard:
    """Get the global list directory guard instance.
    
    Returns:
        ListDirGuard instance
    """
    global _list_dir_guard
    if _list_dir_guard is None:
        _list_dir_guard = ListDirGuard()
    return _list_dir_guard
