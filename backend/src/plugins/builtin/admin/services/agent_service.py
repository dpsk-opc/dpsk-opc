"""Agent administration service.

Business logic layer for agent management, sitting between
API handlers and the repository.

This service works directly with raw markdown content, keeping
the API simple and flexible.
"""

from __future__ import annotations

import logging
import re
from pathlib import Path
from typing import Any, Optional

from src.plugins.builtin.admin.services.agent_repository import (
    AgentNotFoundError,
    AgentRepository,
    AgentRepositoryError,
    TeamNotFoundError,
)

logger = logging.getLogger(__name__)


class AgentServiceError(Exception):
    """Base exception for agent service errors."""
    pass


class ValidationError(AgentServiceError):
    """Raised when validation fails."""
    pass


class AgentService:
    """Service for managing agents.

    This service provides business logic for agent CRUD operations,
    independent of whether it's called from API or CLI.

    Design philosophy:
    - Work directly with raw markdown content
    - Keep API simple: just agent_id + markdown
    - Validation is minimal, letting users control their own format

    Usage:
        ```python
        # With default agents root
        service = AgentService()

        # List agents
        agents = service.list_agents()

        # Get single agent (returns raw markdown)
        data = service.get_agent("engineering/前端")
        print(data["markdown"])

        # Create agent (accepts raw markdown)
        service.create_agent(
            agent_id="engineering/frontend",
            markdown='''---
name: 前端工程师
skills:
  - react
---

# 前端工程师

你是一个专业的前端工程师...
'''
        )
        ```
    """

    def __init__(
        self,
        agents_root: Optional[Path] = None,
        repository: Optional[AgentRepository] = None,
    ):
        """Initialize service.

        Args:
            agents_root: Root directory for agent definitions.
                         Defaults to ~/.dpskopc/agents.
            repository: Optional repository instance (for testing).
        """
        if repository:
            self._repo = repository
        elif agents_root:
            self._repo = AgentRepository(agents_root)
        else:
            default_root = Path("~/.dpskopc/agents").expanduser()
            self._repo = AgentRepository(default_root)

    # ==================== Agent Operations ====================

    def list_agents(
        self,
        team: Optional[str] = None,
    ) -> list[dict[str, Any]]:
        """List all agents or agents in a specific team.

        Args:
            team: Optional team name to filter by.

        Returns:
            List of agent info dicts (id, name, team).
        """
        return self._repo.list_agents(team)

    def get_agent(self, agent_id: str) -> dict[str, Any]:
        """Get a single agent by ID.

        Args:
            agent_id: Agent identifier.

        Returns:
            Dict with agent_id and markdown content.

        Raises:
            AgentServiceError: If agent not found.
        """
        try:
            return self._repo.get_agent(agent_id)
        except AgentNotFoundError:
            raise AgentServiceError(f"Agent not found: {agent_id}") from None

    def create_agent(
        self,
        agent_id: str,
        markdown: str,
    ) -> dict[str, Any]:
        """Create a new agent.

        Args:
            agent_id: Unique identifier for the agent.
                     Can include team prefix (e.g., "engineering/frontend").
            markdown: Raw markdown content (should include frontmatter).

        Returns:
            Created agent info.

        Raises:
            ValidationError: If agent_id is invalid.
            AgentServiceError: If agent already exists.
        """
        self._validate_agent_id(agent_id)

        if not markdown or not markdown.strip():
            raise ValidationError("Markdown content cannot be empty")

        try:
            result = self._repo.create_agent(agent_id, markdown)
            logger.info(f"Created agent: {agent_id}")
            return {
                "agent_id": result["agent_id"],
                "message": f"Agent '{agent_id}' created successfully",
            }
        except AgentRepositoryError as e:
            raise AgentServiceError(str(e)) from e

    def update_agent(
        self,
        agent_id: str,
        markdown: str,
    ) -> dict[str, Any]:
        """Update an existing agent.

        Args:
            agent_id: Agent identifier.
            markdown: New raw markdown content.

        Returns:
            Updated agent info.

        Raises:
            AgentServiceError: If agent not found.
        """
        if not markdown or not markdown.strip():
            raise ValidationError("Markdown content cannot be empty")

        try:
            result = self._repo.update_agent(agent_id, markdown)
            logger.info(f"Updated agent: {agent_id}")
            return {
                "agent_id": result["agent_id"],
                "message": f"Agent '{agent_id}' updated successfully",
            }
        except AgentNotFoundError:
            raise AgentServiceError(f"Agent not found: {agent_id}") from None

    def delete_agent(self, agent_id: str) -> dict[str, Any]:
        """Delete an agent.

        Args:
            agent_id: Agent identifier.

        Returns:
            Deletion result.

        Raises:
            AgentServiceError: If agent not found.
        """
        try:
            self._repo.delete_agent(agent_id)
            logger.info(f"Deleted agent: {agent_id}")
            return {
                "agent_id": agent_id,
                "message": f"Agent '{agent_id}' deleted successfully",
            }
        except AgentNotFoundError:
            raise AgentServiceError(f"Agent not found: {agent_id}") from None

    # ==================== Team Operations ====================

    def list_teams(self) -> list[dict[str, Any]]:
        """List all teams.

        Returns:
            List of team info dicts.
        """
        return self._repo.list_teams()

    def create_team(self, team_name: str) -> dict[str, Any]:
        """Create a new team directory.

        Args:
            team_name: Team name (can be nested like "engineering/backend").

        Returns:
            Created team info.

        Raises:
            ValidationError: If team name is invalid.
            AgentServiceError: If team already exists.
        """
        self._validate_team_name(team_name)

        try:
            result = self._repo.create_team(team_name)
            logger.info(f"Created team: {team_name}")
            return {
                "team": result["name"],
                "path": result["path"],
                "message": f"Team '{result['name']}' created successfully",
            }
        except AgentRepositoryError as e:
            raise AgentServiceError(str(e)) from e

    def delete_team(self, team_name: str, force: bool = False) -> dict[str, Any]:
        """Delete a team directory.

        Args:
            team_name: Team name.
            force: If True, delete even if not empty.

        Returns:
            Deletion result.

        Raises:
            AgentServiceError: If team not found or not empty (without force).
        """
        try:
            self._repo.delete_team(team_name, force=force)
            logger.info(f"Deleted team: {team_name}")
            return {
                "team": team_name,
                "message": f"Team '{team_name}' deleted successfully",
            }
        except TeamNotFoundError:
            raise AgentServiceError(f"Team not found: {team_name}") from None
        except AgentRepositoryError as e:
            raise AgentServiceError(str(e)) from e

    # ==================== Validation ====================

    def _validate_agent_id(self, agent_id: str) -> None:
        """Validate agent ID format.

        Args:
            agent_id: Agent identifier to validate.

        Raises:
            ValidationError: If invalid.
        """
        if not agent_id:
            raise ValidationError("Agent ID cannot be empty")

        # Allow alphanumeric, Chinese characters, hyphens, underscores, slashes
        pattern = r"^[\w\u4e00-\u9fff-]+(/[\w\u4e00-\u9fff-]+)*$"
        if not re.match(pattern, agent_id):
            raise ValidationError(
                f"Invalid agent ID format: '{agent_id}'. "
                "Use alphanumeric characters, hyphens, underscores, "
                "and optionally slash for team prefix."
            )

    def _validate_team_name(self, team_name: str) -> None:
        """Validate team name format.

        Args:
            team_name: Team name to validate.

        Raises:
            ValidationError: If invalid.
        """
        if not team_name:
            raise ValidationError("Team name cannot be empty")

        pattern = r"^[\w\u4e00-\u9fff-]+(/[\w\u4e00-\u9fff-]+)*$"
        if not re.match(pattern, team_name):
            raise ValidationError(
                f"Invalid team name format: '{team_name}'. "
                "Use alphanumeric characters, hyphens, underscores."
            )
