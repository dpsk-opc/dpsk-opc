"""Workflow Models for OPC-Client.

This module defines the core data structures for workflow orchestration.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional


class TaskStatus(str, Enum):
    """Status of a workflow task."""

    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"


@dataclass
class WorkflowTask:
    """A single task in a workflow.

    Attributes:
        id: Unique identifier for this task within the workflow
        agent: Target Agent ID to execute this task
        task: Task description/instruction for the agent
        depends_on: List of task IDs that must complete before this task runs
        context_hint: Optional hint for context passing
    """

    id: str
    agent: str
    task: str
    depends_on: list[str] = field(default_factory=list)
    context_hint: Optional[str] = None

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "id": self.id,
            "agent": self.agent,
            "task": self.task,
            "depends_on": self.depends_on,
            "context_hint": self.context_hint,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "WorkflowTask":
        """Deserialize from dictionary."""
        return cls(
            id=data["id"],
            agent=data["agent"],
            task=data["task"],
            depends_on=data.get("depends_on", []),
            context_hint=data.get("context_hint"),
        )


@dataclass
class Workflow:
    """A workflow consisting of multiple dependent tasks.

    Attributes:
        tasks: List of tasks in this workflow
        final_aggregator: Optional Agent ID for result aggregation
    """

    tasks: list[WorkflowTask] = field(default_factory=list)
    final_aggregator: Optional[str] = None

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "tasks": [task.to_dict() for task in self.tasks],
            "final_aggregator": self.final_aggregator,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Workflow":
        """Deserialize from dictionary."""
        tasks = [WorkflowTask.from_dict(t) for t in data.get("tasks", [])]
        return cls(
            tasks=tasks,
            final_aggregator=data.get("final_aggregator"),
        )

    def get_task(self, task_id: str) -> Optional[WorkflowTask]:
        """Get a task by ID."""
        for task in self.tasks:
            if task.id == task_id:
                return task
        return None

    def validate(self) -> list[str]:
        """Validate the workflow.

        Returns:
            List of validation error messages. Empty if valid.
        """
        errors: list[str] = []

        # Check for duplicate task IDs
        task_ids = [t.id for t in self.tasks]
        if len(task_ids) != len(set(task_ids)):
            duplicates = [tid for tid in task_ids if task_ids.count(tid) > 1]
            errors.append(f"Duplicate task IDs found: {set(duplicates)}")

        # Check for empty task ID
        for task in self.tasks:
            if not task.id.strip():
                errors.append("Task ID cannot be empty")

        return errors
