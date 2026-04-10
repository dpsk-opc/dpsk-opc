"""Event Models for OPC-Client.

This module defines progress event structures for streaming execution.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional


class EventType(str, Enum):
    """Types of progress events."""

    WORKFLOW_START = "workflow_start"
    WORKFLOW_COMPLETE = "workflow_complete"
    WORKFLOW_FAILED = "workflow_failed"
    TASK_START = "task_start"
    TASK_COMPLETE = "task_complete"
    TASK_FAILED = "task_failed"
    TASK_SKIPPED = "task_skipped"
    PROGRESS = "progress"


@dataclass
class ProgressEvent:
    """Progress event for workflow execution.

    Attributes:
        type: Event type
        task_id: Associated task ID (if applicable)
        status: Status description
        data: Additional event data
        timestamp: Unix timestamp
    """

    type: str
    task_id: Optional[str] = None
    status: Optional[str] = None
    data: Optional[dict[str, Any]] = None
    timestamp: float = field(default_factory=time.time)

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "type": self.type,
            "task_id": self.task_id,
            "status": self.status,
            "data": self.data,
            "timestamp": self.timestamp,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ProgressEvent":
        """Deserialize from dictionary."""
        return cls(
            type=data["type"],
            task_id=data.get("task_id"),
            status=data.get("status"),
            data=data.get("data"),
            timestamp=data.get("timestamp", time.time()),
        )

    @classmethod
    def workflow_start(cls, workflow_id: str, trace_id: str) -> "ProgressEvent":
        """Create a workflow start event."""
        return cls(
            type=EventType.WORKFLOW_START,
            status="started",
            data={"workflow_id": workflow_id, "trace_id": trace_id},
        )

    @classmethod
    def workflow_complete(cls, duration_ms: int, trace_id: str) -> "ProgressEvent":
        """Create a workflow complete event."""
        return cls(
            type=EventType.WORKFLOW_COMPLETE,
            status="completed",
            data={"duration_ms": duration_ms, "trace_id": trace_id},
        )

    @classmethod
    def workflow_failed(cls, error: str, trace_id: str) -> "ProgressEvent":
        """Create a workflow failed event."""
        return cls(
            type=EventType.WORKFLOW_FAILED,
            status="failed",
            data={"error": error, "trace_id": trace_id},
        )

    @classmethod
    def task_start(cls, task_id: str, agent: str) -> "ProgressEvent":
        """Create a task start event."""
        return cls(
            type=EventType.TASK_START,
            task_id=task_id,
            status="running",
            data={"agent": agent},
        )

    @classmethod
    def task_complete(cls, task_id: str, duration_ms: int, output: Any = None) -> "ProgressEvent":
        """Create a task complete event."""
        return cls(
            type=EventType.TASK_COMPLETE,
            task_id=task_id,
            status="completed",
            data={"duration_ms": duration_ms, "output": output},
        )

    @classmethod
    def task_failed(cls, task_id: str, error: str) -> "ProgressEvent":
        """Create a task failed event."""
        return cls(
            type=EventType.TASK_FAILED,
            task_id=task_id,
            status="failed",
            data={"error": error},
        )

    @classmethod
    def task_skipped(cls, task_id: str, reason: str) -> "ProgressEvent":
        """Create a task skipped event."""
        return cls(
            type=EventType.TASK_SKIPPED,
            task_id=task_id,
            status="skipped",
            data={"reason": reason},
        )

    @classmethod
    def progress(cls, message: str, completed: int, total: int) -> "ProgressEvent":
        """Create a progress update event."""
        return cls(
            type=EventType.PROGRESS,
            status="in_progress",
            data={
                "message": message,
                "completed": completed,
                "total": total,
                "percent": int((completed / total) * 100) if total > 0 else 0,
            },
        )
