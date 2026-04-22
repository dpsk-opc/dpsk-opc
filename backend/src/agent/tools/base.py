"""Base Tool class for DPSK-OPC Agent.

This module defines the abstract interface for Agent tools,
compatible with OpenAI's Function Calling format.
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from typing import Any

logger = logging.getLogger(__name__)


class ToolParameter:
    """Definition of a tool parameter."""

    def __init__(
        self,
        name: str,
        param_type: str,
        description: str = "",
        required: bool = True,
        default: Any = None,
        enum: list[str] | None = None,
    ) -> None:
        """Initialize a tool parameter.

        Args:
            name: Parameter name
            param_type: Parameter type (string, integer, number, boolean, object, array)
            description: Human-readable description
            required: Whether this parameter is required
            default: Default value if not required
            enum: List of allowed values (for enum type)
        """
        self.name = name
        self.param_type = param_type
        self.description = description
        self.required = required
        self.default = default
        self.enum = enum

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary."""
        result: dict[str, Any] = {
            "type": self.param_type,
            "description": self.description,
        }
        if self.enum:
            result["enum"] = self.enum
        return result


class BaseTool(ABC):
    """Abstract base class for Agent tools.

    All tools must inherit from this class and implement the execute method.
    Tools are automatically converted to OpenAI's function calling format.
    """

    def __init__(self) -> None:
        """Initialize the tool."""
        self._logger = logging.getLogger(self.__class__.__name__)

    @property
    @abstractmethod
    def name(self) -> str:
        """Tool name (must be unique, used for identification)."""
        pass

    @property
    @abstractmethod
    def description(self) -> str:
        """Human-readable description of what the tool does."""
        pass

    @property
    def parameters(self) -> list[ToolParameter]:
        """List of tool parameters.

        Returns:
            List of ToolParameter objects defining the tool's input schema.
        """
        return []

    @property
    def skills(self) -> list[str]:
        """Skills/capabilities this tool belongs to.

        This is used to determine which agents can use this tool.
        Each agent has a list of skills in its definition, and only tools
        with matching skills will be provided to that agent's LLM.

        Returns:
            List of skill identifiers (e.g., ["task_planning", "agent_discovery"]).
            Empty list means the tool is available to all agents.
        """
        return []

    @abstractmethod
    async def execute(self, **kwargs: Any) -> dict[str, Any]:
        """Execute the tool with given arguments.

        Args:
            **kwargs: Arguments matching the tool's parameter definitions.

        Returns:
            Result dictionary with execution output.
        """
        pass

    def to_openai_format(self) -> dict[str, Any]:
        """Convert to OpenAI function calling format.

        Returns:
            Dictionary in OpenAI's function calling format.
        """
        properties = {}
        required = []

        for param in self.parameters:
            properties[param.name] = param.to_dict()
            if param.required:
                required.append(param.name)

        result: dict[str, Any] = {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                },
            },
        }

        if required:
            result["function"]["parameters"]["required"] = required

        return result

    def validate_params(self, **kwargs: Any) -> tuple[bool, str | None]:
        """Validate the input parameters.

        Args:
            **kwargs: Input parameters to validate.

        Returns:
            Tuple of (is_valid, error_message).
        """
        for param in self.parameters:
            if param.required and param.name not in kwargs:
                return False, f"Missing required parameter: {param.name}"

        return True, None

    async def run(self, params: Any) -> dict[str, Any]:
        """Run the tool with flexible input.

        This is a convenience method that accepts either a dict or
        a simple value as input.

        Args:
            params: Input parameters (dict or simple value).

        Returns:
            Result dictionary.
        """
        if isinstance(params, dict):
            # Validate
            is_valid, error = self.validate_params(**params)
            if not is_valid:
                return {"success": False, "error": error}
            return await self.execute(**params)
        else:
            # Simple value - pass as-is for single-parameter tools
            return await self.execute(input=params)
