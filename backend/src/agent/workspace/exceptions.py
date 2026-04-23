"""Workspace exceptions for DPSK-OPC Agent.

This module defines all custom exceptions used in the workspace security module.
"""

from typing import Optional, Any


class WorkspaceError(Exception):
    """Base exception for workspace errors."""
    
    ERROR_CODE = "WORKSPACE_ERROR"
    
    def __init__(
        self,
        message: str,
        code: Optional[str] = None,
        details: Optional[dict[str, Any]] = None,
    ):
        self.message = message
        self.code = code or self.ERROR_CODE
        self.details = details or {}
        super().__init__(self.message)
    
    def to_dict(self) -> dict[str, Any]:
        """Convert to error dict."""
        return {
            "success": False,
            "error": self.message,
            "code": self.code,
            "details": self.details,
        }


# =============================================================================
# Workspace Errors
# =============================================================================


class WorkspaceAccessDeniedError(WorkspaceError):
    """Access denied error."""
    ERROR_CODE = "WORKSPACE_ACCESS_DENIED"


class WorkspacePermissionDeniedError(WorkspaceError):
    """Permission denied error."""
    ERROR_CODE = "WORKSPACE_PERMISSION_DENIED"


class WorkspaceNotFoundError(WorkspaceError):
    """Workspace not found error."""
    ERROR_CODE = "WORKSPACE_NOT_FOUND"


class WorkspaceInvalidPathError(WorkspaceError):
    """Invalid path error."""
    ERROR_CODE = "WORKSPACE_INVALID_PATH"


class WorkspaceSymlinkForbiddenError(WorkspaceError):
    """Symlink crossing boundary error."""
    ERROR_CODE = "WORKSPACE_SYMLINK_FORBIDDEN"


class WorkspaceForbiddenPathError(WorkspaceError):
    """Forbidden path error."""
    ERROR_CODE = "WORKSPACE_FORBIDDEN_PATH"


# =============================================================================
# Confirmation Errors
# =============================================================================


class PathRequiresConfirmationError(WorkspaceError):
    """Path requires user confirmation."""
    ERROR_CODE = "PATH_REQUIRES_CONFIRMATION"


class ConfirmationTimeoutError(WorkspaceError):
    """Confirmation timeout error."""
    ERROR_CODE = "CONFIRMATION_TIMEOUT"


class ConfirmationRejectedError(WorkspaceError):
    """Confirmation rejected error."""
    ERROR_CODE = "CONFIRMATION_REJECTED"


# =============================================================================
# Temporary Permission Errors
# =============================================================================


class TempPermissionExpiredError(WorkspaceError):
    """Temporary permission expired error."""
    ERROR_CODE = "TEMP_PERMISSION_EXPIRED"


class TempPermissionNotFoundError(WorkspaceError):
    """Temporary permission not found error."""
    ERROR_CODE = "TEMP_PERMISSION_NOT_FOUND"


class TempPermissionRevokedError(WorkspaceError):
    """Temporary permission revoked error."""
    ERROR_CODE = "TEMP_PERMISSION_REVOKED"


# =============================================================================
# Exchange Errors
# =============================================================================


class ExchangeAccessDeniedError(WorkspaceError):
    """Exchange access denied error."""
    ERROR_CODE = "EXCHANGE_ACCESS_DENIED"


class ExchangeNotFoundError(WorkspaceError):
    """Exchange not found error."""
    ERROR_CODE = "EXCHANGE_NOT_FOUND"


class ExchangeExpiredError(WorkspaceError):
    """Exchange expired error."""
    ERROR_CODE = "EXCHANGE_EXPIRED"


# =============================================================================
# Execution Errors
# =============================================================================


class CommandDeniedError(WorkspaceError):
    """Command denied error."""
    ERROR_CODE = "COMMAND_DENIED"


class CommandNotInWhitelistError(WorkspaceError):
    """Command not in whitelist error."""
    ERROR_CODE = "COMMAND_NOT_IN_WHITELIST"


class CommandDangerousPatternError(WorkspaceError):
    """Command contains dangerous pattern error."""
    ERROR_CODE = "COMMAND_DANGEROUS_PATTERN"


class ExecutionTimeoutError(WorkspaceError):
    """Execution timeout error."""
    ERROR_CODE = "EXECUTION_TIMEOUT"


class ExecutionOutputTooLargeError(WorkspaceError):
    """Execution output too large error."""
    ERROR_CODE = "EXECUTION_OUTPUT_TOO_LARGE"


class NetworkAccessDeniedError(WorkspaceError):
    """Network access denied error."""
    ERROR_CODE = "NETWORK_ACCESS_DENIED"


# =============================================================================
# Search Errors
# =============================================================================


class SearchPathInvalidError(WorkspaceError):
    """Search path invalid error."""
    ERROR_CODE = "SEARCH_PATH_INVALID"


class SearchRootForbiddenError(WorkspaceError):
    """Search root forbidden error."""
    ERROR_CODE = "SEARCH_ROOT_FORBIDDEN"


class SearchResultsExceededError(WorkspaceError):
    """Search results exceeded error."""
    ERROR_CODE = "SEARCH_RESULTS_EXCEEDED"
