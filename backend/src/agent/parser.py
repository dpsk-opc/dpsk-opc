"""Markdown parser for Agent definitions.

This module parses Agent definition files in Markdown format with YAML frontmatter.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import TYPE_CHECKING, Any, Optional

import yaml

if TYPE_CHECKING:
    from src.agent.defs import AgentDef


def extract_frontmatter(content: str) -> tuple[dict[str, Any], str]:
    """Extract YAML frontmatter from Markdown content.

    Args:
        content: Full Markdown file content

    Returns:
        Tuple of (frontmatter_dict, body_content)

    Raises:
        ValueError: If frontmatter is malformed
    """
    # Match YAML frontmatter between --- markers
    pattern = r"^---\s*\n(.*?)\n---\s*\n?(.*)$"
    match = re.match(pattern, content, re.DOTALL)

    if not match:
        return {}, content

    frontmatter_text = match.group(1)
    body_content = match.group(2).strip()

    try:
        frontmatter = yaml.safe_load(frontmatter_text) or {}
    except yaml.YAMLError as e:
        raise ValueError(f"Invalid YAML frontmatter: {e}") from e

    return frontmatter, body_content


def parse_agent_def(file_path: Path) -> "AgentDef":
    """Parse a Markdown file into an AgentDef.

    Args:
        file_path: Path to the .md file

    Returns:
        AgentDef instance

    Raises:
        FileNotFoundError: If file doesn't exist
        ValueError: If file format is invalid
    """
    from src.agent.defs import AgentDef

    if not file_path.exists():
        raise FileNotFoundError(f"Agent definition file not found: {file_path}")

    content = file_path.read_text(encoding="utf-8")
    frontmatter, body = extract_frontmatter(content)

    # Validate required fields
    agent_id = file_path.stem  # Filename without extension
    name = frontmatter.get("name")
    if not name:
        raise ValueError(f"Missing required field 'name' in {file_path}")

    # Build metadata dict
    metadata = frontmatter.get("metadata", {})
    if not isinstance(metadata, dict):
        metadata = {}

    # Parse model_config
    model_config = frontmatter.get("model_config", {})
    if not isinstance(model_config, dict):
        model_config = {}

    # Parse lists with defaults
    skills = frontmatter.get("skills", [])
    if skills is None:
        skills = []
    if not isinstance(skills, list):
        skills = [skills]

    capabilities = frontmatter.get("capabilities", [])
    if capabilities is None:
        capabilities = []
    if not isinstance(capabilities, list):
        capabilities = [capabilities]

    dependencies = frontmatter.get("dependencies", [])
    if dependencies is None:
        dependencies = []
    if not isinstance(dependencies, list):
        dependencies = [dependencies]

    return AgentDef(
        agent_id=agent_id,
        name=name,
        file_path=file_path,
        skills=skills,
        capabilities=capabilities,
        team=frontmatter.get("team"),
        workspace=frontmatter.get("workspace"),
        dependencies=dependencies,
        description=body,
        model=frontmatter.get("model"),
        model_config=model_config,
        max_instances=frontmatter.get("max_instances", 1),
        queue_size=frontmatter.get("queue_size", 0),
        max_experience_entries=frontmatter.get("max_experience_entries", 1000),
        metadata=metadata,
    )


def scan_agents_dir(dir_path: Path, recursive: bool = True) -> list[Path]:
    """Scan a directory for Agent definition files.

    Args:
        dir_path: Directory to scan
        recursive: Whether to scan subdirectories

    Returns:
        List of paths to .md files
    """
    if not dir_path.exists():
        return []

    if recursive:
        pattern = "**/*.md"
    else:
        pattern = "*.md"

    return sorted(dir_path.glob(pattern))


def create_default_secretary_def(agents_root: Path) -> "AgentDef":
    """Create a default Secretary Agent definition.

    Args:
        agents_root: Root directory for agent definitions

    Returns:
        AgentDef for the default Secretary Agent
    """
    from src.agent.defs import AgentDef

    secretary_path = agents_root / "秘书.md"
    return AgentDef(
        agent_id="秘书",
        name="秘书 Agent",
        file_path=secretary_path,
        skills=["task_planning", "agent_discovery", "group_coordination"],
        capabilities=[
            "解析用户自然语言需求",
            "拆解任务为子任务",
            "调用下游 Agent 执行",
            "汇总结果并回复用户",
        ],
        team="总裁办",
        workspace="system",
        dependencies=[],
        description="# 秘书 Agent\n\n这是系统默认的秘书 Agent，负责全局任务调度。",
        model="gpt-4",
        model_config={"temperature": 0.7},
        max_instances=1,
        queue_size=10,
        max_experience_entries=1000,
        metadata={"cpu_limit": 0.5, "memory_limit": 256},
    )
