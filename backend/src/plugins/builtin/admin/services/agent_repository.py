"""Agent repository for file-based agent definition storage.

Handles all file I/O operations for agent .md files.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Optional

logger = logging.getLogger(__name__)


class AgentRepositoryError(Exception):
    """Base exception for agent repository errors."""
    pass


class AgentNotFoundError(AgentRepositoryError):
    """Raised when an agent is not found."""
    pass


class TeamNotFoundError(AgentRepositoryError):
    """Raised when a team directory is not found."""
    pass


class AgentRepository:
    """Repository for managing agent definition files on disk.

    Responsibilities:
    - Read/write agent .md files (as raw markdown text)
    - Create/delete team directories
    - Validate file paths

    File structure:
        agents_root/
        ├── 秘书.md
        ├── engineering/
        │   ├── 前端.md
        │   └── 后端.md
        └── design/
            └── UI.md
    """

    def __init__(self, agents_root: Path):
        """Initialize repository.

        Args:
            agents_root: Root directory containing agent definitions.
        """
        self.agents_root = Path(agents_root).expanduser().resolve()
        self._ensure_agents_root()

    def _ensure_agents_root(self) -> None:
        """Ensure agents root directory exists."""
        self.agents_root.mkdir(parents=True, exist_ok=True)
        logger.debug(f"Agents root: {self.agents_root}")

    def _get_agent_file_path(self, agent_id: str) -> Path:
        """Get the file path for an agent.

        Args:
            agent_id: Agent identifier (can include team prefix like "engineering/前端").

        Returns:
            Path to the agent's .md file.
        """
        if "/" in agent_id:
            base_path = "/".join(agent_id.split("/")[:-1])
            filename = agent_id.split("/")[-1]
            return self.agents_root / base_path / f"{filename}.md"
        else:
            return self.agents_root / f"{agent_id}.md"

    def _get_team_path(self, team: str) -> Path:
        """Get the directory path for a team."""
        return self.agents_root / team

    # ==================== Agent Operations ====================

    def get_agent(self, agent_id: str) -> dict[str, Any]:
        """Read an agent definition from disk.

        Args:
            agent_id: Agent identifier (e.g., "秘书" or "engineering/前端").

        Returns:
            Dict containing 'agent_id' and 'markdown' (raw file content).

        Raises:
            AgentNotFoundError: If agent file doesn't exist.
        """
        file_path = self._get_agent_file_path(agent_id)

        if not file_path.exists():
            raise AgentNotFoundError(f"Agent not found: {agent_id}")

        markdown = file_path.read_text(encoding="utf-8")

        return {
            "agent_id": agent_id,
            "markdown": markdown,
        }

    def list_agents(self, team: Optional[str] = None) -> list[dict[str, Any]]:
        """List all agents, optionally filtered by team.

        Args:
            team: Optional team name to filter by.

        Returns:
            List of agent info dicts (id and name only).
        """
        import re

        search_root = self._get_team_path(team) if team else self.agents_root

        if team and not search_root.exists():
            raise TeamNotFoundError(f"Team not found: {team}")

        agents = []

        for md_file in search_root.rglob("*.md"):
            # Get relative path from agents_root
            rel_path = md_file.relative_to(self.agents_root)
            agent_id = str(rel_path.with_suffix("")).replace("\\", "/")

            # Extract name from frontmatter for display
            name = agent_id.split("/")[-1]
            try:
                content = md_file.read_text(encoding="utf-8")
                match = re.match(r"^---\s*\n(.*?)\n---", content, re.DOTALL)
                if match:
                    import yaml
                    fm = yaml.safe_load(match.group(1))
                    if fm and isinstance(fm, dict):
                        name = fm.get("name", name)
            except Exception:
                pass

            agents.append({
                "agent_id": agent_id,
                "name": name,
                "team": str(rel_path.parent) if rel_path.parent != Path(".") else "",
            })

        return agents

    def create_agent(self, agent_id: str, markdown: str) -> dict[str, Any]:
        """Create a new agent definition file.

        Args:
            agent_id: Agent identifier (e.g., "engineering/前端").
            markdown: Raw markdown content (should include frontmatter).

        Returns:
            Created agent data.

        Raises:
            AgentRepositoryError: If agent already exists or creation fails.
        """
        file_path = self._get_agent_file_path(agent_id)

        if file_path.exists():
            raise AgentRepositoryError(f"Agent already exists: {agent_id}")

        # Ensure parent directory exists
        file_path.parent.mkdir(parents=True, exist_ok=True)

        # Write file
        try:
            file_path.write_text(markdown, encoding="utf-8")
            logger.info(f"Created agent: {agent_id}")

            return {
                "agent_id": agent_id,
                "markdown": markdown,
            }
        except Exception as e:
            raise AgentRepositoryError(f"Failed to create agent: {e}") from e

    def update_agent(self, agent_id: str, markdown: str) -> dict[str, Any]:
        """Update an existing agent definition.

        Args:
            agent_id: Agent identifier.
            markdown: New raw markdown content.

        Returns:
            Updated agent data.

        Raises:
            AgentNotFoundError: If agent doesn't exist.
        """
        file_path = self._get_agent_file_path(agent_id)

        if not file_path.exists():
            raise AgentNotFoundError(f"Agent not found: {agent_id}")

        file_path.write_text(markdown, encoding="utf-8")
        logger.info(f"Updated agent: {agent_id}")

        return {
            "agent_id": agent_id,
            "markdown": markdown,
        }

    def delete_agent(self, agent_id: str) -> bool:
        """Delete an agent definition file.

        Args:
            agent_id: Agent identifier.

        Returns:
            True if deleted.

        Raises:
            AgentNotFoundError: If agent doesn't exist.
        """
        file_path = self._get_agent_file_path(agent_id)

        if not file_path.exists():
            raise AgentNotFoundError(f"Agent not found: {agent_id}")

        file_path.unlink()
        logger.info(f"Deleted agent: {agent_id}")

        # Clean up empty parent directories
        self._cleanup_empty_dirs(file_path.parent)

        return True

    # ==================== Team (Directory) Operations ====================

    def list_teams(self) -> list[dict[str, Any]]:
        """List all teams (subdirectories).

        Returns:
            List of team info dicts.
        """
        teams = []

        if not self.agents_root.exists():
            return teams

        for item in self.agents_root.iterdir():
            if item.is_dir() and not item.name.startswith("."):
                agent_count = len(list(item.rglob("*.md")))
                teams.append({
                    "name": item.name,
                    "path": str(item.relative_to(self.agents_root)),
                    "agent_count": agent_count,
                })

        return sorted(teams, key=lambda t: t["name"])

    def create_team(self, team_name: str) -> dict[str, Any]:
        """Create a new team directory.

        Args:
            team_name: Team name (can be nested like "engineering/backend").

        Returns:
            Created team info.

        Raises:
            AgentRepositoryError: If team already exists.
        """
        team_path = self._get_team_path(team_name)

        if team_path.exists():
            raise AgentRepositoryError(f"Team already exists: {team_name}")

        team_path.mkdir(parents=True, exist_ok=True)
        logger.info(f"Created team: {team_name}")

        return {
            "name": team_name.split("/")[-1],
            "path": team_name,
            "agent_count": 0,
        }

    def delete_team(self, team_name: str, force: bool = False) -> bool:
        """Delete a team directory.

        Args:
            team_name: Team name.
            force: If True, delete even if not empty.

        Returns:
            True if deleted.

        Raises:
            TeamNotFoundError: If team doesn't exist.
        """
        team_path = self._get_team_path(team_name)

        if not team_path.exists():
            raise TeamNotFoundError(f"Team not found: {team_name}")

        # Check if empty
        agents = list(team_path.rglob("*.md"))
        if agents and not force:
            raise AgentRepositoryError(
                f"Team '{team_name}' is not empty ({len(agents)} agents). "
                "Use force=true to delete anyway."
            )

        # Delete directory
        if force:
            import shutil
            shutil.rmtree(team_path)
        else:
            team_path.rmdir()

        logger.info(f"Deleted team: {team_name}")
        return True

    # ==================== Helper Methods ====================

    def _cleanup_empty_dirs(self, path: Path) -> None:
        """Remove empty parent directories up to agents_root."""
        current = path

        while current != self.agents_root and current.exists():
            if current.is_dir() and not any(current.iterdir()):
                current.rmdir()
                logger.debug(f"Removed empty directory: {current}")
                current = current.parent
            else:
                break
