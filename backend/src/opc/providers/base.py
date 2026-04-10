"""Workflow Provider Base.

This module defines the abstract interface for workflow generation.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from src.opc.config import WorkflowContext
    from src.opc.models.workflow import Workflow


class WorkflowProvider(ABC):
    """Abstract base class for workflow generation.

    A WorkflowProvider is responsible for generating workflows based on
    user requests and available context.
    """

    @abstractmethod
    async def generate(
        self,
        user_request: str,
        context: "WorkflowContext",
    ) -> "Workflow":
        """Generate a workflow from user request.

        Args:
            user_request: The user's natural language request
            context: Workflow generation context containing available agents etc.

        Returns:
            Generated Workflow object

        Raises:
            WorkflowGenerationError: If workflow generation fails
        """
        ...
