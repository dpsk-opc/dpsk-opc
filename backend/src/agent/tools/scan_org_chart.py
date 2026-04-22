"""Scan Organization Chart Tool for DPSK-OPC Agent.

This tool scans the agents directory and generates an organization chart
in a tree-like format representing the company hierarchy.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from .base import BaseTool, ToolParameter

# Default agents directory path
DEFAULT_AGENTS_PATH = "~/.dpskopc/agents"


class ScanOrgChartTool(BaseTool):
    """Tool for scanning agents directory and generating organization chart.

    This tool scans the agents directory to find all agent definition files,
    parses their team and workspace metadata, and generates a hierarchical
    organization chart.
    """

    @property
    def name(self) -> str:
        """Tool name."""
        return "scan_org_chart"

    @property
    def description(self) -> str:
        """Human-readable description of what the tool does."""
        return "扫描 agents 目录并生成公司组织架构图。当用户询问公司部门结构、Agent 分布、组织架构时请使用此工具。"

    @property
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["agent_discovery"]

    @property
    def parameters(self) -> list[ToolParameter]:
        """Tool parameters."""
        return [
            ToolParameter(
                name="agents_path",
                param_type="string",
                description="agents 目录路径，默认为 ~/.dpskopc/agents",
                required=False,
                default=DEFAULT_AGENTS_PATH,
            ),
        ]

    async def execute(self, agents_path: str | None = None, **kwargs: Any) -> dict[str, Any]:
        """Execute the tool to scan and generate org chart.

        Args:
            agents_path: Path to the agents directory.
            **kwargs: Additional parameters (ignored).

        Returns:
            Result dictionary with organization chart data.
        """
        # Use default path if not specified
        path = agents_path or DEFAULT_AGENTS_PATH
        resolved_path = Path(path).expanduser().resolve()

        if not resolved_path.exists():
            return {
                "success": False,
                "error": f"Agents directory not found: {resolved_path}",
                "chart": "",
                "data": {},
                "stats": {},
            }

        # Scan for agent files
        agent_files = list(resolved_path.glob("**/*.md"))

        if not agent_files:
            return {
                "success": True,
                "chart": "(No agents found)",
                "data": {"agents": [], "teams": {}},
                "stats": {
                    "total_agents": 0,
                    "total_teams": 0,
                    "total_workspaces": 0,
                },
            }

        # Parse each agent
        agents_data = []
        teams: dict[str, list[dict]] = {}
        workspaces: dict[str, list[str]] = {}
        ungrouped: list[dict] = []

        for file_path in agent_files:
            agent_info = self._parse_agent_file(file_path)
            if agent_info:
                agents_data.append(agent_info)

                team = agent_info.get("team", "未分组")
                workspace = agent_info.get("workspace", "default")

                if team:
                    if team not in teams:
                        teams[team] = []
                    teams[team].append(agent_info)
                else:
                    ungrouped.append(agent_info)

                if workspace:
                    if workspace not in workspaces:
                        workspaces[workspace] = []
                    workspaces[workspace].append(agent_info["agent_id"])

        # Build organization chart
        chart = self._build_org_chart(teams, ungrouped)

        self._logger.info(f"Scan completed: {len(agents_data)} agents, {len(teams)} teams")

        return {
            "success": True,
            "chart": chart,
            "data": {
                "agents": agents_data,
                "teams": teams,
                "workspaces": workspaces,
                "ungrouped": ungrouped,
            },
            "stats": {
                "total_agents": len(agents_data),
                "total_teams": len(teams),
                "total_workspaces": len(workspaces),
                "ungrouped_count": len(ungrouped),
            },
        }

    def _parse_agent_file(self, file_path: Path) -> dict[str, Any] | None:
        """Parse a single agent definition file.

        Args:
            file_path: Path to the .md file

        Returns:
            Agent info dict or None if parsing fails
        """
        try:
            content = file_path.read_text(encoding="utf-8")

            # Extract YAML frontmatter
            frontmatter = {}
            body = content

            if content.startswith("---"):
                parts = content.split("---", 2)
                if len(parts) >= 3:
                    try:
                        frontmatter = yaml.safe_load(parts[1]) or {}
                        body = parts[2].strip()
                    except yaml.YAMLError:
                        pass

            agent_id = file_path.stem
            name = frontmatter.get("name", agent_id)
            team = frontmatter.get("team")
            workspace = frontmatter.get("workspace")
            skills = frontmatter.get("skills", [])
            capabilities = frontmatter.get("capabilities", [])
            model = frontmatter.get("model", "default")
            max_instances = frontmatter.get("max_instances", 1)
            description = body[:200] + "..." if len(body) > 200 else body

            return {
                "agent_id": agent_id,
                "name": name,
                "team": team,
                "workspace": workspace,
                "skills": skills,
                "capabilities": capabilities,
                "model": model,
                "max_instances": max_instances,
                "description": description,
                "file_path": str(file_path),
            }

        except Exception:
            return None

    def _build_org_chart(
        self,
        teams: dict[str, list[dict]],
        ungrouped: list[dict],
    ) -> str:
        """Build a tree-style organization chart.

        Args:
            teams: Dict of team_name -> list of agent dicts
            ungrouped: List of ungrouped agents

        Returns:
            Formatted tree chart string
        """
        lines = []
        lines.append("🏢 公司组织架构图")
        lines.append("=" * 40)

        if not teams and not ungrouped:
            lines.append("(暂无组织架构)")
            return "\n".join(lines)

        # Sort teams alphabetically
        sorted_teams = sorted(teams.items(), key=lambda x: x[0])

        for i, (team_name, agents) in enumerate(sorted_teams):
            is_last_team = (i == len(sorted_teams) - 1) and not ungrouped

            # Team header
            team_prefix = "└── " if is_last_team else "├── "
            lines.append(f"📁 {team_prefix}{team_name} ({len(agents)}人)")

            # Agents in team
            for j, agent in enumerate(agents):
                is_last_agent = (j == len(agents) - 1)
                agent_prefix = "    " if is_last_team else "│   "

                if is_last_agent:
                    agent_connector = "└── "
                else:
                    agent_connector = "├── "

                lines.append(f"{agent_prefix}{agent_connector}🤖 {agent['name']} [{agent['agent_id']}]")

                # Show skills if any
                if agent.get("skills"):
                    skills_str = ", ".join(agent["skills"][:3])
                    if len(agent["skills"]) > 3:
                        skills_str += f"... (+{len(agent['skills']) - 3})"
                    skills_prefix = agent_prefix + ("    " if is_last_agent else "│   ")
                    lines.append(f"{skills_prefix}    └─ 💡 {skills_str}")

        # Ungrouped agents
        if ungrouped:
            prefix = "    " if not teams else ("    " if (len(sorted_teams) == 0 or not teams) else "│   ")
            lines.append(f"{prefix}├── 📦 未分组 ({len(ungrouped)}人)")

            for i, agent in enumerate(ungrouped):
                is_last = (i == len(ungrouped) - 1)
                conn = "└── " if is_last else "├── "
                lines.append(f"{prefix}│   {conn}🤖 {agent['name']} [{agent['agent_id']}]")

        lines.append("")
        return "\n".join(lines)


# Tool instance for easy import
scan_org_chart_tool = ScanOrgChartTool()
