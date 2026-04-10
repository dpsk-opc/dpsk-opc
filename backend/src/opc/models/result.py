"""Result Models for OPC-Client.

This module defines the result data structures for workflow execution.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional

from src.opc.models.workflow import TaskStatus, Workflow


class WorkflowStatus(str, Enum):
    """Overall workflow execution status."""

    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"
    TIMEOUT = "timeout"


@dataclass
class TaskResult:
    """Result of a single task execution.

    Attributes:
        task_id: Corresponding task ID
        status: Execution status
        output: Task output data
        error: Error message if failed
        duration_ms: Execution duration in milliseconds
    """

    task_id: str
    status: TaskStatus
    output: Any = None
    error: Optional[str] = None
    duration_ms: int = 0

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "task_id": self.task_id,
            "status": self.status.value if isinstance(self.status, TaskStatus) else self.status,
            "output": self.output,
            "error": self.error,
            "duration_ms": self.duration_ms,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "TaskResult":
        """Deserialize from dictionary."""
        status = data["status"]
        if isinstance(status, str):
            status = TaskStatus(status)
        return cls(
            task_id=data["task_id"],
            status=status,
            output=data.get("output"),
            error=data.get("error"),
            duration_ms=data.get("duration_ms", 0),
        )

    def is_success(self) -> bool:
        """Check if task completed successfully."""
        return self.status == TaskStatus.COMPLETED


@dataclass
class FinalResult:
    """Final result of workflow execution.

    Attributes:
        request: Original user request
        workflow: The generated/executed workflow
        results: Map of task_id to TaskResult
        status: Overall workflow status
        total_duration_ms: Total execution duration
        trace_id: Trace ID for request tracking
        error: Overall error message if workflow failed
    """

    request: str
    workflow: Workflow
    results: dict[str, TaskResult] = field(default_factory=dict)
    status: WorkflowStatus = WorkflowStatus.PENDING
    total_duration_ms: int = 0
    trace_id: str = ""
    error: Optional[str] = None

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "request": self.request,
            "workflow": self.workflow.to_dict() if self.workflow else None,
            "results": {k: v.to_dict() for k, v in self.results.items()},
            "status": self.status.value if isinstance(self.status, WorkflowStatus) else self.status,
            "total_duration_ms": self.total_duration_ms,
            "trace_id": self.trace_id,
            "error": self.error,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "FinalResult":
        """Deserialize from dictionary."""
        workflow = Workflow.from_dict(data["workflow"]) if data.get("workflow") else None
        status = data["status"]
        if isinstance(status, str):
            status = WorkflowStatus(status)
        results = {k: TaskResult.from_dict(v) for k, v in data.get("results", {}).items()}
        return cls(
            request=data["request"],
            workflow=workflow,
            results=results,
            status=status,
            total_duration_ms=data.get("total_duration_ms", 0),
            trace_id=data.get("trace_id", ""),
            error=data.get("error"),
        )

    def get_successful_tasks(self) -> list[str]:
        """Get list of successful task IDs."""
        return [tid for tid, r in self.results.items() if r.is_success()]

    def get_failed_tasks(self) -> list[str]:
        """Get list of failed task IDs."""
        return [tid for tid, r in self.results.items() if r.status == TaskStatus.FAILED]

    def is_success(self) -> bool:
        """Check if workflow completed successfully."""
        return self.status == WorkflowStatus.COMPLETED
