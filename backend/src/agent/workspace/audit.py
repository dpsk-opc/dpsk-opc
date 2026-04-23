"""Audit logger for DPSK-OPC Agent.

This module provides audit logging functionality:
- Log all file operations
- Log permission checks and failures
- Log cross-agent file exchanges
- Support JSON Lines format
"""

from __future__ import annotations

import json
import logging
import os
import re
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

logger = logging.getLogger(__name__)


class AuditLogger:
    """Audit logger for workspace operations."""
    
    def __init__(
        self,
        log_path: Optional[Path] = None,
        format: str = "jsonl",
        include_successful: bool = True,
        include_denied: bool = True,
        include_commands: bool = True,
        masking_enabled: bool = True,
    ):
        """Initialize the audit logger.
        
        Args:
            log_path: Path for audit logs
            format: Log format (jsonl, json, plain)
            include_successful: Log successful operations
            include_denied: Log denied operations
            include_commands: Log command executions
            masking_enabled: Enable sensitive data masking
        """
        self.log_path = Path(log_path) if log_path else Path("/tmp/dpsk_audit")
        self.format = format
        self.include_successful = include_successful
        self.include_denied = include_denied
        self.include_commands = include_commands
        self.masking_enabled = masking_enabled
        
        # Ensure log directory exists
        try:
            self.log_path.mkdir(parents=True, exist_ok=True)
        except PermissionError:
            # Fall back to tmp directory
            self.log_path = Path("/tmp/dpsk_audit")
            self.log_path.mkdir(parents=True, exist_ok=True)
        
        # Sensitive data patterns for masking
        self._sensitive_patterns = [
            (r"(?i)(password|passwd|pwd)[=:]\S+", "***MASKED***"),
            (r"(?i)(api[_-]?key|apikey)[=:]\S+", "***MASKED***"),
            (r"(?i)(token|bearer|auth)[=:]\S+", "***MASKED***"),
            (r"(?i)secret[=:]\S+", "***MASKED***"),
        ]
    
    def _get_log_file(self) -> Path:
        """Get the current log file path."""
        date_str = datetime.now().strftime("%Y-%m-%d")
        return self.log_path / f"audit_{date_str}.log"
    
    def _mask_sensitive_data(self, data: str) -> str:
        """Mask sensitive data in a string."""
        if not self.masking_enabled:
            return data
        
        result = str(data)
        for pattern, replacement in self._sensitive_patterns:
            result = re.sub(pattern, replacement, result)
        
        return result
    
    def _write_log(self, entry: dict[str, Any]) -> None:
        """Write a log entry to file."""
        try:
            log_file = self._get_log_file()
            
            with open(log_file, "a", encoding="utf-8") as f:
                if self.format == "jsonl":
                    f.write(json.dumps(entry, ensure_ascii=False) + "\n")
                elif self.format == "json":
                    f.write(json.dumps(entry, ensure_ascii=False) + "\n")
                else:  # plain
                    timestamp = entry.get("timestamp", "")
                    msg = entry.get("message", "")
                    f.write(f"[{timestamp}] {msg}\n")
        except Exception as e:
            logger.error(f"[AuditLogger] Failed to write log: {e}")
    
    def log_workspace_access(
        self,
        agent_id: str,
        workspace_id: str,
        operation: str,
        path: str,
        allowed: bool,
        permission_used: Optional[str] = None,
        reason: Optional[str] = None,
        result: str = "success",
        **extra: Any,
    ) -> None:
        """Log a workspace access event.
        
        Args:
            agent_id: Agent identifier
            workspace_id: Workspace identifier
            operation: Operation type (read_file, write_to_file, etc.)
            path: Accessed path
            allowed: Whether access was allowed
            permission_used: Permission that was used
            reason: Reason for denial if not allowed
            result: Operation result
        """
        # Check if we should log this
        if allowed and not self.include_successful:
            return
        if not allowed and not self.include_denied:
            return
        
        entry = {
            "timestamp": datetime.now().isoformat() + "Z",
            "event_type": "workspace_access",
            "agent_id": agent_id,
            "workspace_id": workspace_id,
            "operation": operation,
            "path": self._mask_sensitive_data(path),
            "allowed": allowed,
            "permission_used": permission_used,
            "result": result,
            **extra,
        }
        
        if reason:
            entry["reason"] = reason
        
        self._write_log(entry)
    
    def log_command_execution(
        self,
        agent_id: str,
        workspace_id: str,
        command: str,
        allowed: bool,
        reason: Optional[str] = None,
        returncode: Optional[int] = None,
        duration_ms: Optional[int] = None,
        **extra: Any,
    ) -> None:
        """Log a command execution event.
        
        Args:
            agent_id: Agent identifier
            workspace_id: Workspace identifier
            command: Executed command
            allowed: Whether execution was allowed
            reason: Reason for denial if not allowed
            returncode: Command return code
            duration_ms: Execution duration in milliseconds
        """
        if not self.include_commands:
            return
        
        entry = {
            "timestamp": datetime.now().isoformat() + "Z",
            "event_type": "command_execution",
            "agent_id": agent_id,
            "workspace_id": workspace_id,
            "command": self._mask_sensitive_data(command),
            "allowed": allowed,
            "returncode": returncode,
            "duration_ms": duration_ms,
            **extra,
        }
        
        if reason:
            entry["reason"] = reason
        
        self._write_log(entry)
    
    def log_permission_grant(
        self,
        agent_id: str,
        granted_by: str,
        permission: str,
        path: str,
        scope: str,
        expires_at: Optional[float] = None,
        **extra: Any,
    ) -> None:
        """Log a permission grant event.
        
        Args:
            agent_id: Agent identifier
            granted_by: User who granted the permission
            permission: Granted permission
            path: Path for the permission
            scope: Permission scope
            expires_at: Expiration timestamp
        """
        entry = {
            "timestamp": datetime.now().isoformat() + "Z",
            "event_type": "permission_grant",
            "agent_id": agent_id,
            "granted_by": granted_by,
            "permission": permission,
            "path": self._mask_sensitive_data(path),
            "scope": scope,
            "expires_at": expires_at,
            **extra,
        }
        
        self._write_log(entry)
    
    def log_permission_revoke(
        self,
        permission_id: str,
        agent_id: str,
        revoked_by: str,
        reason: Optional[str] = None,
        **extra: Any,
    ) -> None:
        """Log a permission revocation event.
        
        Args:
            permission_id: Permission identifier
            agent_id: Agent identifier
            revoked_by: User who revoked the permission
            reason: Revocation reason
        """
        entry = {
            "timestamp": datetime.now().isoformat() + "Z",
            "event_type": "permission_revoke",
            "permission_id": permission_id,
            "agent_id": agent_id,
            "revoked_by": revoked_by,
            **extra,
        }
        
        if reason:
            entry["reason"] = reason
        
        self._write_log(entry)
    
    def log_exchange_operation(
        self,
        exchange_id: str,
        from_agent_id: str,
        to_agent_id: str,
        operation: str,
        filename: str,
        allowed: bool,
        reason: Optional[str] = None,
        **extra: Any,
    ) -> None:
        """Log a cross-agent exchange operation.
        
        Args:
            exchange_id: Exchange identifier
            from_agent_id: Sender agent
            to_agent_id: Receiver agent
            operation: Operation (push/pull)
            filename: Original filename
            allowed: Whether operation was allowed
            reason: Reason if not allowed
        """
        entry = {
            "timestamp": datetime.now().isoformat() + "Z",
            "event_type": "exchange_operation",
            "exchange_id": exchange_id,
            "from_agent_id": from_agent_id,
            "to_agent_id": to_agent_id,
            "operation": operation,
            "filename": filename,
            "allowed": allowed,
            **extra,
        }
        
        if reason:
            entry["reason"] = reason
        
        self._write_log(entry)
    
    def log_security_event(
        self,
        event_type: str,
        agent_id: str,
        description: str,
        severity: str = "medium",
        **extra: Any,
    ) -> None:
        """Log a security event.
        
        Args:
            event_type: Type of security event
            agent_id: Agent identifier
            description: Event description
            severity: Event severity (low, medium, high, critical)
        """
        entry = {
            "timestamp": datetime.now().isoformat() + "Z",
            "event_type": f"security_{event_type}",
            "agent_id": agent_id,
            "description": description,
            "severity": severity,
            **extra,
        }
        
        self._write_log(entry)


# Global audit logger instance
_audit_logger: Optional[AuditLogger] = None


def get_audit_logger() -> AuditLogger:
    """Get the global audit logger instance.
    
    Returns:
        AuditLogger instance
    """
    global _audit_logger
    if _audit_logger is None:
        import os
        log_path = os.environ.get("DPSK_AUDIT_PATH", "/tmp/dpsk_audit")
        format = os.environ.get("DPSK_AUDIT_FORMAT", "jsonl")
        
        _audit_logger = AuditLogger(
            log_path=Path(log_path),
            format=format,
        )
    
    return _audit_logger


def audit_log(**kwargs) -> None:
    """Quick audit logging function.
    
    Args:
        **kwargs: Log entry fields
    """
    logger = get_audit_logger()
    
    event_type = kwargs.get("event_type", "unknown")
    
    if event_type == "workspace_access":
        logger.log_workspace_access(**kwargs)
    elif event_type == "command_execution":
        logger.log_command_execution(**kwargs)
    elif event_type == "permission_grant":
        logger.log_permission_grant(**kwargs)
    elif event_type == "permission_revoke":
        logger.log_permission_revoke(**kwargs)
    elif event_type == "exchange_operation":
        logger.log_exchange_operation(**kwargs)
    elif event_type.startswith("security_"):
        logger.log_security_event(
            event_type=event_type.replace("security_", ""),
            **kwargs,
        )
    else:
        logger.log_security_event(
            event_type=event_type,
            description=str(kwargs),
        )
