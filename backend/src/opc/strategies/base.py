"""Execution Strategy Base.

This module defines the abstract interface for task execution strategies.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Any, Dict

if TYPE_CHECKING:
    from src.opc.models.result import TaskResult
    from src.opc.models.workflow import WorkflowTask


class ExecutionStrategy(ABC):
    """Abstract base class for task execution strategies.

    An ExecutionStrategy determines how tasks are scheduled and executed
    based on their dependencies.
    """

    @abstractmethod
    async def execute(
        self,
        tasks: list["WorkflowTask"],
        invoker: Any,
        context: Dict[str, Any],
    ) -> Dict[str, "TaskResult"]:
        """Execute tasks according to the strategy.

        Args:
            tasks: List of tasks to execute
            invoker: Agent invoker for executing tasks
            context: Execution context (trace_id, previous results, etc.)

        Returns:
            Dictionary mapping task_id to TaskResult
        """
        ...
