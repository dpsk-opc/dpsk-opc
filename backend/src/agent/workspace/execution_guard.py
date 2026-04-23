"""ExecutionGuard for DPSK-OPC Agent.

This module provides command execution security:
- Command whitelist validation
- Dangerous pattern detection
- Environment variable sanitization
- Working directory validation
"""

from __future__ import annotations

import fnmatch
import logging
import os
import re
import subprocess
from pathlib import Path
from typing import Optional

from .context import get_current_context
from .models import (
    CommandAction,
    CommandRule,
    ExecutionConfig,
    DefaultCommandRules,
    NetworkPolicy,
)
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class ExecutionGuard:
    """Guard for command execution security."""
    
    def __init__(
        self,
        config: Optional[ExecutionConfig] = None,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the execution guard.
        
        Args:
            config: Execution configuration
            audit_logger: Audit logger instance
        """
        self.config = config or ExecutionConfig.default_config()
        self.audit_logger = audit_logger or get_audit_logger()
    
    def validate_command(
        self,
        command: str,
        agent_id: Optional[str] = None,
    ) -> tuple[bool, str]:
        """Validate a command against whitelist/blacklist.
        
        Args:
            command: Command to validate
            agent_id: Agent identifier
            
        Returns:
            Tuple of (allowed, reason)
        """
        # Extract the base command
        base_cmd = self._extract_base_command(command)
        
        # Check blacklist first (denied commands)
        for rule in self.config.command_rules:
            if rule.action == CommandAction.DENY:
                if self._matches_command(base_cmd, rule.pattern):
                    return False, f"Command denied: {rule.description or rule.pattern}"
        
        # Check whitelist (allowed commands)
        if self.config.command_rules:
            for rule in self.config.command_rules:
                if rule.action == CommandAction.ALLOW:
                    if self._matches_command(base_cmd, rule.pattern):
                        return True, f"Command allowed: {rule.description}"
        
        # If no rules configured, deny by default
        if self.config.command_rules:
            return False, f"Command not in whitelist: {base_cmd}"
        
        return True, "Command allowed by default"
    
    def validate_command_args(
        self,
        command: str,
    ) -> tuple[bool, str]:
        """Validate command arguments for dangerous patterns.
        
        Args:
            command: Full command with arguments
            
        Returns:
            Tuple of (safe, reason)
        """
        for pattern in DefaultCommandRules.DANGEROUS_PATTERNS:
            if re.search(pattern, command, re.IGNORECASE):
                return False, f"Detected dangerous pattern: {pattern}"
        
        return True, ""
    
    def validate_working_directory(
        self,
        cwd: str,
        workspace_root: Path,
    ) -> tuple[bool, str]:
        """Validate working directory is within allowed boundaries.
        
        Args:
            cwd: Current working directory
            workspace_root: Workspace root
            
        Returns:
            Tuple of (allowed, reason)
        """
        try:
            cwd_path = Path(cwd).resolve()
            workspace_path = workspace_root.resolve()
            
            if not str(cwd_path).startswith(str(workspace_path)):
                return False, f"Working directory outside workspace: {cwd}"
            
            return True, ""
        except Exception as e:
            return False, f"Invalid working directory: {e}"
    
    def sanitize_environment(
        self,
        env: dict[str, str],
        workspace_root: Optional[Path] = None,
    ) -> dict[str, str]:
        """Sanitize environment variables for subprocess.
        
        Args:
            env: Environment variables
            workspace_root: Workspace root for PATH restriction
            
        Returns:
            Sanitized environment
        """
        result = dict(env)
        
        # Remove dangerous environment variables
        for var_name in self.config.cleanup_env_vars:
            if var_name in result:
                del result[var_name]
        
        # Restrict PATH if workspace root is specified
        if workspace_root:
            # Allow basic system paths
            safe_paths = [
                "/usr/bin",
                "/usr/local/bin",
                "/bin",
            ]
            result["PATH"] = ":".join(safe_paths)
        
        # Remove potentially dangerous variables
        dangerous_vars = [
            "LD_PRELOAD",
            "LD_LIBRARY_PATH",
            "PYTHONPATH",
            "PERL5LIB",
            "RUBYLIB",
            "NODE_PATH",
            "GOPATH",
        ]
        
        for var_name in dangerous_vars:
            if var_name in result:
                del result[var_name]
        
        return result
    
    def validate_network_access(
        self,
        host: Optional[str] = None,
        port: Optional[int] = None,
    ) -> tuple[bool, str]:
        """Validate network access policy.
        
        Args:
            host: Target host
            port: Target port
            
        Returns:
            Tuple of (allowed, reason)
        """
        if self.config.network_policy == NetworkPolicy.DENY:
            return False, "Network access is disabled"
        
        if self.config.network_policy == NetworkPolicy.ASK:
            return False, "Network access requires confirmation"
        
        # Check host whitelist
        if host and self.config.allowed_hosts:
            if host not in self.config.allowed_hosts:
                return False, f"Host not in whitelist: {host}"
        
        # Check port blacklist
        if port and self.config.blocked_ports:
            for blocked in self.config.blocked_ports:
                if self._port_in_range(port, blocked):
                    return False, f"Port blocked: {port}"
        
        return True, ""
    
    def execute_command(
        self,
        command: str,
        workspace_root: Path,
        cwd: Optional[str] = None,
        timeout: Optional[int] = None,
        capture_output: bool = True,
    ) -> tuple[int, str, str]:
        """Execute a command with security checks.
        
        Args:
            command: Command to execute
            workspace_root: Workspace root
            cwd: Working directory (defaults to workspace_root)
            timeout: Timeout in seconds
            capture_output: Whether to capture output
            
        Returns:
            Tuple of (returncode, stdout, stderr)
        """
        context = get_current_context()
        agent_id = context.agent_id if context else "unknown"
        workspace_id = getattr(context, "workspace_id", "unknown") if context else "unknown"
        
        # Validate command
        allowed, reason = self.validate_command(command, agent_id)
        if not allowed:
            self.audit_logger.log_command_execution(
                agent_id=agent_id,
                workspace_id=workspace_id,
                command=command,
                allowed=False,
                reason=reason,
            )
            return -1, "", f"Command denied: {reason}"
        
        # Validate arguments
        safe, arg_reason = self.validate_command_args(command)
        if not safe:
            self.audit_logger.log_command_execution(
                agent_id=agent_id,
                workspace_id=workspace_id,
                command=command,
                allowed=False,
                reason=arg_reason,
            )
            return -1, "", f"Dangerous pattern detected: {arg_reason}"
        
        # Validate working directory
        work_dir = cwd or str(workspace_root)
        dir_allowed, dir_reason = self.validate_working_directory(work_dir, workspace_root)
        if not dir_allowed:
            self.audit_logger.log_command_execution(
                agent_id=agent_id,
                workspace_id=workspace_id,
                command=command,
                allowed=False,
                reason=dir_reason,
            )
            return -1, "", f"Invalid working directory: {dir_reason}"
        
        # Sanitize environment
        current_env = os.environ.copy()
        sanitized_env = self.sanitize_environment(current_env, workspace_root)
        
        # Set timeout
        exec_timeout = timeout or self.config.timeout_seconds
        
        # Execute command
        try:
            result = subprocess.run(
                command,
                shell=True,
                cwd=work_dir,
                capture_output=capture_output,
                text=True,
                timeout=exec_timeout,
                env=sanitized_env,
            )
            
            # Log execution
            self.audit_logger.log_command_execution(
                agent_id=agent_id,
                workspace_id=workspace_id,
                command=command,
                allowed=True,
                returncode=result.returncode,
            )
            
            return result.returncode, result.stdout, result.stderr
        
        except subprocess.TimeoutExpired:
            self.audit_logger.log_command_execution(
                agent_id=agent_id,
                workspace_id=workspace_id,
                command=command,
                allowed=True,
                returncode=-1,
                reason="timeout",
            )
            return -1, "", f"Command timed out after {exec_timeout} seconds"
        
        except Exception as e:
            self.audit_logger.log_command_execution(
                agent_id=agent_id,
                workspace_id=workspace_id,
                command=command,
                allowed=True,
                returncode=-1,
                reason=str(e),
            )
            return -1, "", str(e)
    
    def _extract_base_command(self, command: str) -> str:
        """Extract the base command (first word) from a command string."""
        parts = command.strip().split()
        if not parts:
            return ""
        
        base = parts[0]
        
        # Handle shell built-ins
        if "/" in base:
            return os.path.basename(base)
        
        return base
    
    def _matches_command(self, command: str, pattern: str) -> bool:
        """Check if a command matches a pattern.
        
        Args:
            command: Command to check
            pattern: Pattern to match (supports * wildcard)
            
        Returns:
            True if matches
        """
        return fnmatch.fnmatch(command, pattern) or fnmatch.fnmatch(command, pattern + "*")
    
    def _port_in_range(self, port: int, range_str: str) -> bool:
        """Check if a port is in a range or list.
        
        Args:
            port: Port number
            range_str: Range string (e.g., "1-1024" or "3306")
            
        Returns:
            True if port is in range
        """
        if "-" in range_str:
            try:
                start, end = range_str.split("-")
                return int(start) <= port <= int(end)
            except ValueError:
                return False
        else:
            try:
                return port == int(range_str)
            except ValueError:
                return False


# Global guard instance
_execution_guard: Optional[ExecutionGuard] = None


def get_execution_guard() -> ExecutionGuard:
    """Get the global execution guard instance.
    
    Returns:
        ExecutionGuard instance
    """
    global _execution_guard
    if _execution_guard is None:
        _execution_guard = ExecutionGuard()
    return _execution_guard
