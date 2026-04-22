"""List Agents Tool for DPSK-OPC Agent.

This tool lists all available agents and their capabilities.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from .base import BaseTool, ToolParameter


class ListAgentsTool(BaseTool):
    """Tool for listing all available agents."""

    @property
    def name(self) -> str:
        return "list_agents"

    @property
    def description(self) -> str:
        return "列出所有可用的 Agent 及其能力介绍。当秘书需要了解有哪些专业 Agent 可以协作时使用此工具。"

    @property
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["agent_discovery"]

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="agents_path",
                param_type="string",
                description="agents 目录路径，默认为 ~/.dpskopc/agents",
                required=False,
                default="~/.dpskopc/agents",
            ),
        ]

    async def execute(self, agents_path: str = "~/.dpskopc/agents", **kwargs: Any) -> dict[str, Any]:
        """List all available agents."""
        import logging
        import yaml

        logger = logging.getLogger(__name__)

        agents_path = Path(agents_path).expanduser().resolve()
        if not agents_path.exists():
            return {
                "success": False,
                "error": f"Agents directory not found: {agents_path}",
                "agents": [],
            }

        # Scan for agent files
        agent_files = list(agents_path.glob("**/*.md"))
        agents = []

        for file_path in agent_files:
            agent_info = self._parse_agent_file(file_path)
            if agent_info:
                agents.append(agent_info)

        # Group by team
        teams: dict[str, list[dict[str, Any]]] = {}
        for agent in agents:
            team = agent.get("team", "未分组")
            if team not in teams:
                teams[team] = []
            teams[team].append(agent)

        logger.info(f"List agents completed: {len(agents)} agents, {len(teams)} teams")

        return {
            "success": True,
            "agents": agents,
            "teams": teams,
            "count": len(agents),
            "team_count": len(teams),
        }

    def _parse_agent_file(self, file_path: Path) -> dict[str, Any] | None:
        """Parse a single agent definition file."""
        try:
            import yaml

            content = file_path.read_text(encoding="utf-8")

            # Extract YAML frontmatter
            frontmatter = {}
            if content.startswith("---"):
                parts = content.split("---", 2)
                if len(parts) >= 3:
                    try:
                        frontmatter = yaml.safe_load(parts[1]) or {}
                    except yaml.YAMLError:
                        pass

            agent_id = file_path.stem
            name = frontmatter.get("name", agent_id)
            team = frontmatter.get("team")
            skills = frontmatter.get("skills", [])
            capabilities = frontmatter.get("capabilities", [])
            max_instances = frontmatter.get("max_instances", 1)

            return {
                "agent_id": agent_id,
                "name": name,
                "team": team,
                "skills": skills,
                "capabilities": capabilities,
                "max_instances": max_instances,
            }

        except Exception:
            return None


# Tool instance
list_agents_tool = ListAgentsTool()
