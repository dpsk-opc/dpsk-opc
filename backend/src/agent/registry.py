"""Agent Registry for DPSK-OPC.

This module provides the AgentRegistry class for managing Agent definitions.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from src.agent.defs import AgentDef

logger = logging.getLogger(__name__)


class AgentRegistry:
    """In-memory registry for Agent definitions.

    The registry loads and manages AgentDef objects loaded from Markdown files.
    """

    def __init__(self) -> None:
        """Initialize the registry."""
        self._agents: dict[str, "AgentDef"] = {}
        self._loaded = False

    def load_from_dir(self, dir_path: Path, auto_create_secretary: bool = True) -> None:
        """Load all Agent definitions from a directory.

        Args:
            dir_path: Directory containing .md files
            auto_create_secretary: If True, create default Secretary if not found
        """
        from src.agent.parser import create_default_secretary_def, parse_agent_def, scan_agents_dir

        if not dir_path.exists():
            logger.warning(f"Agent directory does not exist: {dir_path}")
            # Create directory if it doesn't exist
            dir_path.mkdir(parents=True, exist_ok=True)

        # Scan for .md files
        files = scan_agents_dir(dir_path)
        logger.info(f"Found {len(files)} Agent definition files")

        secretary_found = False

        for file_path in files:
            try:
                agent_def = parse_agent_def(file_path)
                self._agents[agent_def.agent_id] = agent_def
                logger.debug(f"Loaded Agent: {agent_def.agent_id}")

                if agent_def.agent_id == "秘书":
                    secretary_found = True
            except Exception as e:
                logger.error(f"Failed to parse {file_path}: {e}")

        # Auto-create Secretary Agent if not found
        if not secretary_found and auto_create_secretary:
            logger.info("Secretary Agent not found, creating default")
            secretary_def = create_default_secretary_def(dir_path)
            self._agents[secretary_def.agent_id] = secretary_def

        self._loaded = True
        logger.info(f"AgentRegistry loaded {len(self._agents)} definitions")

    def get(self, agent_id: str) -> Optional["AgentDef"]:
        """Get an Agent definition by ID.

        Args:
            agent_id: Agent identifier

        Returns:
            AgentDef or None if not found
        """
        return self._agents.get(agent_id)

    def list_all(self) -> list["AgentDef"]:
        """List all registered Agent definitions.

        Returns:
            List of AgentDef objects
        """
        return list(self._agents.values())

    def register(self, agent_def: "AgentDef") -> None:
        """Register a new Agent definition.

        Args:
            agent_def: Agent definition to register
        """
        self._agents[agent_def.agent_id] = agent_def
        logger.debug(f"Registered Agent: {agent_def.agent_id}")

    def unregister(self, agent_id: str) -> bool:
        """Unregister an Agent definition.

        Args:
            agent_id: Agent identifier

        Returns:
            True if removed, False if not found
        """
        if agent_id in self._agents:
            del self._agents[agent_id]
            logger.debug(f"Unregistered Agent: {agent_id}")
            return True
        return False

    def is_loaded(self) -> bool:
        """Check if registry has been loaded."""
        return self._loaded

    def get_by_team(self, team: str) -> list["AgentDef"]:
        """Get all Agents belonging to a team.

        Args:
            team: Team identifier

        Returns:
            List of matching AgentDefs
        """
        return [agent for agent in self._agents.values() if agent.team == team]

    def get_by_workspace(self, workspace: str) -> list["AgentDef"]:
        """Get all Agents in a workspace.

        Args:
            workspace: Workspace identifier

        Returns:
            List of matching AgentDefs
        """
        return [agent for agent in self._agents.values() if agent.workspace == workspace]

    def get_skills(self, agent_id: str) -> list[str]:
        """Get skills for an Agent.

        Args:
            agent_id: Agent identifier

        Returns:
            List of skill names, empty list if Agent not found
        """
        agent = self.get(agent_id)
        if agent:
            return agent.skills
        return []

    def has_agent(self, agent_id: str) -> bool:
        """Check if an Agent exists in the registry.

        Args:
            agent_id: Agent identifier

        Returns:
            True if exists
        """
        return agent_id in self._agents

    def count(self) -> int:
        """Get the number of registered Agents."""
        return len(self._agents)
