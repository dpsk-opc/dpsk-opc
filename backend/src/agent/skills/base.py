"""Base skill class for DPSK-OPC Agents.

This module provides the base class and utilities for implementing skills.
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from typing import Any

logger = logging.getLogger(__name__)


class BaseSkill(ABC):
    """Abstract base class for Agent skills.

    Skills should inherit from this class and implement the run method.
    """

    name: str = "base_skill"
    description: str = "Base skill"

    @abstractmethod
    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        """Execute the skill.

        Args:
            params: Input parameters for the skill

        Returns:
            Result dictionary with skill output
        """
        raise NotImplementedError

    async def validate_params(self, params: dict[str, Any]) -> bool:
        """Validate input parameters.

        Args:
            params: Input parameters

        Returns:
            True if valid
        """
        return True

    async def cleanup(self) -> None:
        """Cleanup resources after skill execution."""
        pass


class EchoSkill(BaseSkill):
    """Simple echo skill for testing."""

    name = "echo"
    description = "Echoes back the input parameters"

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        """Echo back the input."""
        return {"echoed": params}


class HealthCheckSkill(BaseSkill):
    """Health check skill for agent status."""

    name = "health_check"
    description = "Returns the agent's health status"

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        """Return health status."""
        return {
            "status": "healthy",
            "skills": [self.name],
        }
