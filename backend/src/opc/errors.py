"""OPC-Client Errors.

This module defines custom exceptions for the OPC-Client module.
"""

from __future__ import annotations


class OPCError(Exception):
    """Base exception for OPC-Client errors."""

    def __init__(self, message: str, trace_id: str = "") -> None:
        self.message = message
        self.trace_id = trace_id
        super().__init__(message)

    def to_dict(self) -> dict[str, str]:
        """Convert to dictionary for serialization."""
        return {
            "error": self.__class__.__name__,
            "message": self.message,
            "trace_id": self.trace_id,
        }


class WorkflowValidationError(OPCError):
    """Raised when workflow validation fails."""

    pass


class CyclicDependencyError(WorkflowValidationError):
    """Raised when a cyclic dependency is detected in workflow."""

    def __init__(self, cycle: list[str], trace_id: str = "") -> None:
        self.cycle = cycle
        message = f"Cyclic dependency detected: {' -> '.join(cycle + [cycle[0]])}"
        super().__init__(message, trace_id)


class DuplicateTaskIdError(WorkflowValidationError):
    """Raised when duplicate task IDs are found."""

    def __init__(self, duplicate_ids: set[str], trace_id: str = "") -> None:
        self.duplicate_ids = duplicate_ids
        message = f"Duplicate task IDs found: {duplicate_ids}"
        super().__init__(message, trace_id)


class InvalidTaskReferenceError(WorkflowValidationError):
    """Raised when a task references a non-existent task ID."""

    def __init__(self, task_id: str, reference: str, trace_id: str = "") -> None:
        self.task_id = task_id
        self.reference = reference
        message = f"Task '{task_id}' references non-existent task '{reference}'"
        super().__init__(message, trace_id)


class AgentNotFoundError(OPCError):
    """Raised when a referenced agent is not found in registry."""

    def __init__(self, agent_id: str, trace_id: str = "") -> None:
        self.agent_id = agent_id
        message = f"Agent not found in registry: {agent_id}"
        super().__init__(message, trace_id)


class WorkflowGenerationError(OPCError):
    """Raised when workflow generation fails."""

    pass


class LLMParseError(WorkflowGenerationError):
    """Raised when LLM output cannot be parsed."""

    def __init__(self, reason: str, trace_id: str = "") -> None:
        self.reason = reason
        message = f"Failed to parse LLM output: {reason}"
        super().__init__(message, trace_id)


class TaskExecutionError(OPCError):
    """Raised when task execution fails."""

    def __init__(self, task_id: str, reason: str, trace_id: str = "") -> None:
        self.task_id = task_id
        self.reason = reason
        message = f"Task '{task_id}' execution failed: {reason}"
        super().__init__(message, trace_id)


class SecurityValidationError(OPCError):
    """Raised when security validation fails."""

    def __init__(self, task_id: str, reason: str, trace_id: str = "") -> None:
        self.task_id = task_id
        self.reason = reason
        message = f"Security validation failed for task '{task_id}': {reason}"
        super().__init__(message, trace_id)


class WorkflowTimeoutError(OPCError):
    """Raised when workflow execution times out."""

    def __init__(self, timeout_secs: int, trace_id: str = "") -> None:
        self.timeout_secs = timeout_secs
        message = f"Workflow execution timed out after {timeout_secs} seconds"
        super().__init__(message, trace_id)


class TaskTimeoutError(TaskExecutionError):
    """Raised when individual task execution times out."""

    def __init__(self, task_id: str, timeout_secs: int, trace_id: str = "") -> None:
        self.timeout_secs = timeout_secs
        message = f"Task '{task_id}' timed out after {timeout_secs} seconds"
        super().__init__(task_id, message, trace_id)


class DependencyNotMetError(OPCError):
    """Raised when a task's dependencies are not met."""

    def __init__(self, task_id: str, unmet_deps: list[str], trace_id: str = "") -> None:
        self.task_id = task_id
        self.unmet_deps = unmet_deps
        message = f"Task '{task_id}' has unmet dependencies: {unmet_deps}"
        super().__init__(message, trace_id)


class WorkflowCancelledError(OPCError):
    """Raised when workflow execution is cancelled."""

    def __init__(self, reason: str = "Workflow was cancelled", trace_id: str = "") -> None:
        self.reason = reason
        super().__init__(reason, trace_id)
