"""SearchGuard for DPSK-OPC Agent.

This module provides search operation security:
- Path validation for searches
- Result filtering
- Ignore patterns for sensitive directories
"""

from __future__ import annotations

import logging
import os
import fnmatch
from pathlib import Path
from typing import Any, Optional

from .models import SearchConfig
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class SearchGuard:
    """Guard for search operations."""
    
    def __init__(
        self,
        config: Optional[SearchConfig] = None,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the search guard.
        
        Args:
            config: Search configuration
            audit_logger: Audit logger instance
        """
        self.config = config or SearchConfig.default_config()
        self.audit_logger = audit_logger or get_audit_logger()
    
    def validate_search_path(
        self,
        path: str,
        workspace_root: Path,
    ) -> tuple[bool, str]:
        """Validate a search path.
        
        Args:
            path: Search root path
            workspace_root: Workspace root
            
        Returns:
            Tuple of (allowed, reason)
        """
        # Expand path
        if path.startswith("~"):
            path = os.path.expanduser(path)
        
        path = os.path.normpath(path)
        
        # Check for root path
        if path == "/" or path == "~":
            return False, "Cannot search from root directory"
        
        # Resolve paths
        try:
            search_path = Path(path).resolve()
            workspace_path = workspace_root.resolve()
            
            # Check if search path is within workspace
            if not str(search_path).startswith(str(workspace_path)):
                return False, f"Search path must be within workspace: {workspace_root}"
        
        except Exception as e:
            return False, f"Invalid search path: {e}"
        
        return True, ""
    
    def filter_results(
        self,
        results: list[Any],
        workspace_root: Path,
    ) -> list[Any]:
        """Filter search results.
        
        Args:
            results: Search results
            workspace_root: Workspace root
            
        Returns:
            Filtered results
        """
        filtered = []
        
        for result in results:
            if len(filtered) >= self.config.max_results:
                break
            
            # Get the file path from result
            file_path = self._get_file_path(result)
            
            if file_path:
                # Check if within workspace
                try:
                    resolved = Path(file_path).resolve()
                    if not str(resolved).startswith(str(workspace_root)):
                        continue
                    
                    # Check if should be ignored
                    if self.should_ignore(resolved):
                        continue
                except Exception:
                    continue
            
            filtered.append(result)
        
        return filtered
    
    def should_ignore(self, path: Path) -> bool:
        """Check if a path should be ignored.
        
        Args:
            path: Path to check
            
        Returns:
            True if path should be ignored
        """
        # Check ignored directories
        for part in path.parts:
            if part in self.config.ignored_dirs:
                return True
        
        # Check ignored patterns
        name = path.name
        for pattern in self.config.ignored_patterns:
            if fnmatch.fnmatch(name, pattern):
                return True
        
        return False
    
    def is_binary_file(self, path: Path) -> bool:
        """Check if a file is binary.
        
        Args:
            path: File path
            
        Returns:
            True if file is binary
        """
        suffix = path.suffix.lower()
        return suffix in self.config.binary_extensions
    
    def _get_file_path(self, result: Any) -> Optional[str]:
        """Extract file path from search result.
        
        Args:
            result: Search result
            
        Returns:
            File path or None
        """
        if isinstance(result, dict):
            return result.get("file") or result.get("path")
        elif isinstance(result, str):
            return result
        elif isinstance(result, Path):
            return str(result)
        
        return None
    
    def check_and_filter(
        self,
        path: str,
        results: list[Any],
        workspace_root: Path,
    ) -> tuple[bool, str, list[Any]]:
        """Check search operation and filter results.
        
        Args:
            path: Search path
            results: Search results
            workspace_root: Workspace root
            
        Returns:
            Tuple of (allowed, reason, filtered_results)
        """
        # Validate path
        allowed, reason = self.validate_search_path(path, workspace_root)
        
        if not allowed:
            return False, reason, []
        
        # Filter results
        filtered = self.filter_results(results, workspace_root)
        
        return True, "", filtered


# Global guard instance
_search_guard: Optional[SearchGuard] = None


def get_search_guard() -> SearchGuard:
    """Get the global search guard instance.
    
    Returns:
        SearchGuard instance
    """
    global _search_guard
    if _search_guard is None:
        _search_guard = SearchGuard()
    return _search_guard
