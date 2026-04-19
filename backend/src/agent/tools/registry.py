"""Tool Registry for DPSK-OPC Agent.

Manages tool registration and provides tool management capabilities.
"""

from __future__ import annotations

import logging
from typing import Any

from .base import BaseTool

logger = logging.getLogger(__name__)


class ToolRegistry:
    """Registry for managing Agent tools.

    Singleton pattern to ensure consistent tool registration across the application.
    """

    _instance: ToolRegistry | None = None

    def __init__(self) -> None:
        """Initialize the registry."""
        self._tools: dict[str, BaseTool] = {}
        self._initialized = False

    @classmethod
    def get_instance(cls) -> ToolRegistry:
        """Get the singleton instance.

        Returns:
            ToolRegistry singleton instance.
        """
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def register(self, tool: BaseTool) -> None:
        """Register a tool.

        Args:
            tool: Tool instance to register.

        Raises:
            ValueError: If a tool with the same name is already registered.
        """
        if tool.name in self._tools:
            logger.warning(f"Tool '{tool.name}' is already registered, skipping.")
            return

        self._tools[tool.name] = tool
        logger.info(f"Registered tool: {tool.name}")

    def unregister(self, name: str) -> bool:
        """Unregister a tool.

        Args:
            name: Name of the tool to unregister.

        Returns:
            True if the tool was unregistered, False if not found.
        """
        if name in self._tools:
            del self._tools[name]
            logger.info(f"Unregistered tool: {name}")
            return True
        return False

    def get(self, name: str) -> BaseTool | None:
        """Get a tool by name.

        Args:
            name: Tool name.

        Returns:
            Tool instance or None if not found.
        """
        return self._tools.get(name)

    def get_all(self) -> list[BaseTool]:
        """Get all registered tools.

        Returns:
            List of all registered tool instances.
        """
        return list(self._tools.values())

    def get_tools_for_llm(self) -> list[dict[str, Any]]:
        """Get all tools in OpenAI function calling format.

        This format is used to pass tools to the LLM for tool selection.

        Returns:
            List of tools in OpenAI format.
        """
        return [tool.to_openai_format() for tool in self._tools.values()]

    def list_tool_names(self) -> list[str]:
        """List all registered tool names.

        Returns:
            List of tool names.
        """
        return list(self._tools.keys())

    @property
    def is_initialized(self) -> bool:
        """Check if the registry has been initialized."""
        return self._initialized

    def mark_initialized(self) -> None:
        """Mark the registry as initialized."""
        self._initialized = True

    def clear(self) -> None:
        """Clear all registered tools (mainly for testing)."""
        self._tools.clear()
        self._initialized = False


# Global registry instance
registry = ToolRegistry.get_instance()
