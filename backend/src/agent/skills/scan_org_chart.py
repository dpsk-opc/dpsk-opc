"""Scan Organization Chart skill for DPSK-OPC Agents.

This skill scans the agents directory and generates an organization chart
in a tree-like format representing the company hierarchy.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any


# Default agents directory path
DEFAULT_AGENTS_PATH = "~/.dpskopc/agents"


async def run(params: Any) -> dict[str, Any]:
    """Scan agents directory and generate organization chart.

    This skill scans the agents directory to find all agent definition files,
    parses their team and workspace metadata, and generates a hierarchical
    organization chart.

    Args:
        params: Input parameters. Can be:
            - dict with 'agents_path' key: path to agents directory
            - dict with 'agents' key: list of agent definitions
            - string: path to agents directory
            - None: use default path

    Returns:
        dict with organization chart data:
            - success: bool
            - chart: str (formatted tree chart)
            - data: dict (structured data for rendering)
            - stats: dict (statistics about agents)
    """
    import logging
    logger = logging.getLogger(__name__)

    # Parse input parameters
    agents_path = _parse_agents_path(params)

    if agents_path is None:
        return {
            "success": False,
            "error": "No agents path provided and default path does not exist",
            "chart": "",
            "data": {},
            "stats": {},
        }

    agents_path = Path(agents_path).expanduser().resolve()

    if not agents_path.exists():
        return {
            "success": False,
            "error": f"Agents directory not found: {agents_path}",
            "chart": "",
            "data": {},
            "stats": {},
        }

    # Scan for all subdirectories (departments) and agent files
    # Step 1: Discover all departments (subdirectories) under agents_path
    departments: dict[str, dict[str, Any]] = {}  # dept_name -> {"path": Path, "agents": [], "is_empty": True}
    _scan_departments(agents_path, departments)

    # Step 2: Discover agent files and assign them to departments
    agent_files = list(agents_path.glob("**/*.md"))
    agents_data: list[dict[str, Any]] = []
    ungrouped: list[dict[str, Any]] = []

    for file_path in agent_files:
        agent_info = _parse_agent_file(file_path)
        if agent_info:
            agents_data.append(agent_info)

            team = agent_info.get("team")
            workspace = agent_info.get("workspace", "default")

            if team and team in departments:
                departments[team]["agents"].append(agent_info)
                departments[team]["is_empty"] = False
            else:
                ungrouped.append(agent_info)

            # Also track by workspace
            if workspace not in departments:
                departments[workspace] = {"path": agents_path, "agents": [], "is_empty": False}
            if agent_info not in departments[workspace]["agents"]:
                departments[workspace]["agents"].append(agent_info)

    # Build organization chart
    chart = _build_org_chart(departments, ungrouped)

    logger.info(f"Scan completed: {len(agents_data)} agents, {len(departments)} departments")

    return {
        "success": True,
        "chart": chart,
        "data": {
            "agents": agents_data,
            "departments": departments,
            "ungrouped": ungrouped,
        },
        "stats": {
            "total_agents": len(agents_data),
            "total_departments": len(departments),
            "empty_departments": sum(1 for d in departments.values() if d["is_empty"]),
            "ungrouped_count": len(ungrouped),
        },
    }


def _scan_departments(base_path: Path, departments: dict[str, dict[str, Any]]) -> None:
    """Recursively scan directory structure to discover all departments.

    Each subdirectory under base_path is treated as a department.
    Departments are initialized as empty and marked populated when agents are added.

    Args:
        base_path: Root agents directory
        departments: Dict to populate with department info
    """
    import logging
    logger = logging.getLogger(__name__)

    try:
        for item in base_path.iterdir():
            if item.is_dir():
                dept_name = item.name
                # Skip hidden directories and common non-department folders
                if dept_name.startswith(".") or dept_name in (".git", "__pycache__", "node_modules"):
                    continue

                departments[dept_name] = {
                    "path": item,
                    "agents": [],
                    "is_empty": True,
                    "sub_departments": [],
                }
                logger.debug(f"Discovered department: {dept_name}")

                # Recursively scan sub-directories for nested departments
                _scan_departments(item, departments)
    except PermissionError:
        logger.warning(f"Permission denied scanning: {base_path}")
    except Exception as e:
        logger.warning(f"Error scanning {base_path}: {e}")


def _parse_agents_path(params: Any) -> str | None:
    """Parse agents path from input parameters.

    Args:
        params: Input parameters of various types

    Returns:
        Agents path string or None
    """
    if params is None:
        return DEFAULT_AGENTS_PATH

    if isinstance(params, str):
        return params if params else DEFAULT_AGENTS_PATH

    if isinstance(params, dict):
        # Check various possible keys
        for key in ["agents_path", "path", "root", "dir"]:
            if key in params and params[key]:
                return str(params[key])
        # If params contains 'agents' key with list, use default path
        if "agents" in params:
            return DEFAULT_AGENTS_PATH

    return DEFAULT_AGENTS_PATH


def _parse_agent_file(file_path: Path) -> dict[str, Any] | None:
    """Parse a single agent definition file.

    Args:
        file_path: Path to the .md file

    Returns:
        Agent info dict or None if parsing fails
    """
    try:
        import yaml

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
    departments: dict[str, dict[str, Any]],
    ungrouped: list[dict[str, Any]]
) -> str:
    """Build a tree-style organization chart.

    Args:
        departments: Dict of dept_name -> {"path": Path, "agents": [], "is_empty": bool}
        ungrouped: List of ungrouped agents

    Returns:
        Formatted tree chart string
    """
    lines = []
    lines.append("🏢 公司组织架构图")
    lines.append("=" * 40)

    if not departments and not ungrouped:
        lines.append("(暂无组织架构)")
        return "\n".join(lines)

    # Separate populated and empty departments
    populated_depts = {k: v for k, v in departments.items() if not v["is_empty"]}
    empty_depts = {k: v for k, v in departments.items() if v["is_empty"]}

    # Sort alphabetically
    sorted_populated = sorted(populated_depts.items(), key=lambda x: x[0])
    sorted_empty = sorted(empty_depts.items(), key=lambda x: x[0])
    all_depts = sorted_populated + sorted_empty

    total_depts = len(all_depts)
    for i, (dept_name, dept_info) in enumerate(all_depts):
        is_last_dept = (i == total_depts - 1) and not ungrouped
        is_empty = dept_info["is_empty"]
        agents = dept_info["agents"]

        # Department header
        dept_prefix = "└── " if is_last_dept else "├── "
        if is_empty:
            lines.append(f"📁 {dept_prefix}{dept_name} (空部门)")
        else:
            lines.append(f"📁 {dept_prefix}{dept_name} ({len(agents)}人)")

        # Agents in department
        for j, agent in enumerate(agents):
            is_last_agent = (j == len(agents) - 1)
            agent_prefix = "    " if is_last_dept else "│   "

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
        prefix = "    " if not departments else ("    " if (total_depts == 0) else "│   ")
        lines.append(f"{prefix}├── 📦 未分组 ({len(ungrouped)}人)")

        for i, agent in enumerate(ungrouped):
            is_last = (i == len(ungrouped) - 1)
            conn = "└── " if is_last else "├── "
            lines.append(f"{prefix}│   {conn}🤖 {agent['name']} [{agent['agent_id']}]")

    lines.append("")
    return "\n".join(lines)


# Expose skill metadata
name = "scan_org_chart"
description = "扫描 agents 目录并生成公司组织架构图（含空部门）"
version = "1.1.0"
