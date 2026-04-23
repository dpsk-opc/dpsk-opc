"""PathValidator for DPSK-OPC Agent.

This module provides path validation functionality:
- Validate paths against workspace boundaries
- Detect and prevent symlink attacks
- Check path traversal attempts
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Optional

from .classifier import PathClassifier, get_path_classifier
from .exceptions import (
    WorkspaceInvalidPathError,
    WorkspaceSymlinkForbiddenError,
    WorkspaceForbiddenPathError,
)

logger = logging.getLogger(__name__)


class PathValidator:
    """Validator for paths within workspaces."""
    
    def __init__(
        self,
        classifier: Optional[PathClassifier] = None,
        allow_symlinks: bool = False,
    ):
        """Initialize the path validator.
        
        Args:
            classifier: Path classifier instance
            allow_symlinks: Whether to allow symlinks (not recommended for security)
        """
        self.classifier = classifier or get_path_classifier()
        self.allow_symlinks = allow_symlinks
    
    def validate_path(
        self,
        path: str,
        workspace_root: Path,
        shared_dirs: Optional[list[Path]] = None,
    ) -> tuple[bool, str]:
        """Validate that a path is within allowed boundaries.
        
        Args:
            path: Path to validate
            workspace_root: Workspace root directory
            shared_dirs: Additional allowed directories
            
        Returns:
            Tuple of (valid, error_message)
        """
        # Check for path traversal BEFORE expansion/normalization
        # This catches attempts like /foo/../../../etc/passwd
        if self._contains_traversal(path):
            return False, f"Path traversal not allowed: {path}"
        
        # Expand path (handles ~, environment variables, etc.)
        expanded_path = self._expand_path(path)
        
        # Resolve to real path
        try:
            real_path = self._resolve_path(expanded_path)
        except Exception as e:
            return False, f"Failed to resolve path: {e}"
        
        # Check if path is within workspace
        if self._is_within_workspace(real_path, workspace_root):
            return True, ""
        
        # Check if path is in shared directories
        if shared_dirs:
            for shared_dir in shared_dirs:
                if self._is_within_workspace(real_path, shared_dir):
                    return True, ""
        
        # Check path classification
        category, policy = self.classifier.classify(str(real_path))
        
        if policy.value == "allow":
            return True, ""
        
        if policy.value == "allow_with_confirm":
            return False, f"Path '{path}' requires user confirmation"
        
        # Forbidden path
        return False, f"Path '{path}' is in forbidden category: {category.value}"
    
    def _expand_path(self, path: str) -> str:
        """Expand path (handle ~, environment variables, etc.)."""
        # Expand environment variables
        expanded = os.path.expandvars(path)
        # Expand ~
        expanded = os.path.expanduser(expanded)
        # Normalize
        expanded = os.path.normpath(expanded)
        return expanded
    
    def _resolve_path(self, path: str) -> Path:
        """Resolve path to real path, checking for symlinks."""
        p = Path(path)
        
        # Check if path exists
        if not p.exists():
            # For non-existent paths, just return the resolved path
            return p.resolve()
        
        # Check for symlinks
        if p.is_symlink() and not self.allow_symlinks:
            # Check if symlink target is within workspace
            target = p.resolve()
            
            # For strict validation, reject all symlinks
            if not self.allow_symlinks:
                raise WorkspaceSymlinkForbiddenError(
                    f"Symlinks are not allowed: {path} -> {target}"
                )
        
        # Resolve all symlinks
        return p.resolve()
    
    def _contains_traversal(self, path: str) -> bool:
        """Check if path contains .. traversal.
        
        Checks BEFORE normalization to catch traversal attempts.
        """
        # Check for .. in the path string BEFORE any normalization
        # This catches paths like /foo/../../../etc/passwd
        import re
        # Check if path contains /../ or starts with ../ or ends with /..
        if ".." in path:
            return True
        return False
    
    def _is_within_workspace(self, path: Path, workspace_root: Path) -> bool:
        """Check if path is within workspace directory."""
        try:
            # Get the parent directories
            path = path.resolve()
            workspace_root = workspace_root.resolve()
            
            # Check if workspace root is a parent of path
            return str(path).startswith(str(workspace_root))
        except Exception as e:
            logger.warning(f"[PathValidator] Failed to check workspace boundary: {e}")
            return False
    
    def validate_read_path(
        self,
        path: str,
        workspace_root: Path,
        shared_dirs: Optional[list[Path]] = None,
    ) -> tuple[bool, str]:
        """Validate a path for reading."""
        return self.validate_path(path, workspace_root, shared_dirs)
    
    def validate_write_path(
        self,
        path: str,
        workspace_root: Path,
        shared_dirs: Optional[list[Path]] = None,
    ) -> tuple[bool, str]:
        """Validate a path for writing."""
        valid, error = self.validate_path(path, workspace_root, shared_dirs)
        
        if not valid:
            return valid, error
        
        # Additional checks for write operations
        expanded_path = self._expand_path(path)
        real_path = Path(expanded_path)
        
        # Check parent directory exists or can be created
        parent = real_path.parent
        if not parent.exists():
            # For writes, parent must be creatable within workspace
            if not self._is_within_workspace(parent, workspace_root):
                if shared_dirs:
                    within_shared = any(
                        self._is_within_workspace(parent, sd) for sd in shared_dirs
                    )
                    if not within_shared:
                        return False, f"Cannot create parent directory outside workspace: {parent}"
                else:
                    return False, f"Cannot create parent directory outside workspace: {parent}"
        
        return True, ""
    
    def validate_execute_path(
        self,
        path: str,
        workspace_root: Path,
        shared_dirs: Optional[list[Path]] = None,
    ) -> tuple[bool, str]:
        """Validate a path for command execution."""
        return self.validate_path(path, workspace_root, shared_dirs)
    
    def validate_delete_path(
        self,
        path: str,
        workspace_root: Path,
        shared_dirs: Optional[list[Path]] = None,
    ) -> tuple[bool, str]:
        """Validate a path for deletion."""
        return self.validate_path(path, workspace_root, shared_dirs)


# Global validator instance
_validator: Optional[PathValidator] = None


def get_path_validator() -> PathValidator:
    """Get the global path validator instance.
    
    Returns:
        PathValidator instance
    """
    global _validator
    if _validator is None:
        _validator = PathValidator()
    return _validator


def validate_workspace_path(
    path: str,
    workspace_root: Path,
    shared_dirs: Optional[list[Path]] = None,
) -> tuple[bool, str]:
    """Validate a path is within workspace.
    
    Args:
        path: Path to validate
        workspace_root: Workspace root
        shared_dirs: Additional allowed directories
        
    Returns:
        Tuple of (valid, error_message)
    """
    return get_path_validator().validate_path(path, workspace_root, shared_dirs)
