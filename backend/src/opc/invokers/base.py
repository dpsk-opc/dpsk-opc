"""Agent Invoker Base.

This module defines the abstract interface for agent invocation.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Any, Dict

if TYPE_CHECKING:
    from src.opc.models.result import TaskResult


class AgentInvoker(ABC):
    """Abstract base class for agent invocation.

    An AgentInvoker is responsible for sending tasks to agents
    and retrieving results.
    """

    @abstractmethod
    async def invoke(
        self,
        agent_id: str,
        task: str,
        context: Dict[str, Any],
    ) -> "TaskResult":
        """Invoke an agent to execute a task.

        Args:
            agent_id: Target agent identifier
            task: Task description/instruction
            context: Execution context (previous task results, etc.)

        Returns:
            TaskResult containing execution outcome
        """
        ...
