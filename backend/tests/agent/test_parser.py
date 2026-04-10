"""Tests for Markdown parser (parser.py)."""

import os
import tempfile
from pathlib import Path

import pytest

from src.agent.parser import (
    create_default_secretary_def,
    extract_frontmatter,
    parse_agent_def,
    scan_agents_dir,
)


class TestExtractFrontmatter:
    """Tests for extract_frontmatter function."""

    def test_extract_valid_frontmatter(self):
        """Test extracting valid frontmatter."""
        content = """---
agent_id: test
name: Test Agent
---
# Test Body
This is the body.
"""
        frontmatter, body = extract_frontmatter(content)
        assert frontmatter["agent_id"] == "test"
        assert frontmatter["name"] == "Test Agent"
        assert "# Test Body" in body
        assert "This is the body." in body

    def test_no_frontmatter(self):
        """Test content without frontmatter."""
        content = "# Just a header\n\nSome content."
        frontmatter, body = extract_frontmatter(content)
        assert frontmatter == {}
        assert body == content

    def test_empty_frontmatter(self):
        """Test empty frontmatter markers."""
        content = "---\n---\n# Body"
        frontmatter, body = extract_frontmatter(content)
        assert frontmatter == {}
        assert "# Body" in body

    def test_complex_frontmatter(self):
        """Test complex YAML in frontmatter."""
        content = """---
name: Complex Agent
skills:
  - skill1
  - skill2
metadata:
  cpu_limit: 0.5
  memory_limit: 256
model_config:
  temperature: 0.7
  top_p: 0.9
---
# Body
"""
        frontmatter, body = extract_frontmatter(content)
        assert frontmatter["name"] == "Complex Agent"
        assert len(frontmatter["skills"]) == 2
        assert frontmatter["metadata"]["cpu_limit"] == 0.5
        assert frontmatter["model_config"]["temperature"] == 0.7

    def test_invalid_yaml_raises_error(self):
        """Test that invalid YAML raises ValueError."""
        content = """---
invalid: yaml: content:
---
Body
"""
        with pytest.raises(ValueError, match="Invalid YAML"):
            extract_frontmatter(content)


class TestParseAgentDef:
    """Tests for parse_agent_def function."""

    def test_parse_basic_agent(self):
        """Test parsing a basic agent definition."""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".md", delete=False, encoding="utf-8"
        ) as f:
            f.write("""---
name: Basic Agent
team: engineering
workspace: dev
skills:
  - skill1
capabilities:
  - Does something
---
# Basic Agent
This agent does something useful.
""")
            f.flush()
            file_path = Path(f.name)

        try:
            agent = parse_agent_def(file_path)
            assert agent.agent_id == Path(f.name).stem
            assert agent.name == "Basic Agent"
            assert agent.team == "engineering"
            assert agent.workspace == "dev"
            assert len(agent.skills) == 1
            assert "Does something" in agent.description
        finally:
            os.unlink(f.name)

    def test_parse_full_agent(self):
        """Test parsing a complete agent definition."""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".md", delete=False, encoding="utf-8"
        ) as f:
            f.write("""---
name: Full Agent
team: backend
workspace: production
skills:
  - debug_js
  - code_review
capabilities:
  - Debug JavaScript code
  - Review code changes
dependencies:
  - base-agent
model: gpt-4
model_config:
  temperature: 0.8
  max_tokens: 2000
max_instances: 3
queue_size: 10
max_experience_entries: 500
metadata:
  cpu_limit: 1.0
  memory_limit: 512
  timeout: 600
---
# Full Agent
A comprehensive agent with all features.
""")
            f.flush()
            file_path = Path(f.name)

        try:
            agent = parse_agent_def(file_path)
            assert agent.name == "Full Agent"
            assert len(agent.skills) == 2
            assert len(agent.capabilities) == 2
            assert "base-agent" in agent.dependencies
            assert agent.model == "gpt-4"
            assert agent.model_config["temperature"] == 0.8
            assert agent.max_instances == 3
            assert agent.queue_size == 10
            assert agent.max_experience_entries == 500
            assert agent.get_cpu_limit() == 1.0
            assert agent.get_memory_limit() == 512
        finally:
            os.unlink(f.name)

    def test_parse_missing_name_raises(self):
        """Test that missing name raises ValueError."""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".md", delete=False, encoding="utf-8"
        ) as f:
            f.write("""---
team: engineering
---
# No name
""")
            f.flush()
            file_path = Path(f.name)

        try:
            with pytest.raises(ValueError, match="Missing required field 'name'"):
                parse_agent_def(file_path)
        finally:
            os.unlink(f.name)

    def test_parse_nonexistent_file_raises(self):
        """Test that nonexistent file raises FileNotFoundError."""
        with pytest.raises(FileNotFoundError):
            parse_agent_def(Path("/nonexistent/file.md"))

    def test_parse_secretary_agent(self):
        """Test parsing Secretary Agent."""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".md", delete=False, encoding="utf-8"
        ) as f:
            f.write("""---
name: 秘书 Agent
team: 总裁办
workspace: system
skills:
  - task_planning
  - agent_discovery
  - group_coordination
capabilities:
  - 解析用户自然语言需求
  - 拆解任务为子任务
model: gpt-4
max_instances: 1
---
# 秘书 Agent
系统核心调度 Agent。
""")
            f.flush()
            file_path = Path(f.name)

        try:
            agent = parse_agent_def(file_path)
            assert agent.agent_id == Path(f.name).stem
            assert agent.name == "秘书 Agent"
            assert agent.team == "总裁办"
            assert agent.workspace == "system"
        finally:
            os.unlink(f.name)


class TestScanAgentsDir:
    """Tests for scan_agents_dir function."""

    def test_scan_empty_dir(self):
        """Test scanning empty directory."""
        with tempfile.TemporaryDirectory() as tmpdir:
            files = scan_agents_dir(Path(tmpdir))
            assert files == []

    def test_scan_with_md_files(self):
        """Test scanning directory with .md files."""
        with tempfile.TemporaryDirectory() as tmpdir:
            (Path(tmpdir) / "agent1.md").write_text("---")
            (Path(tmpdir) / "agent2.md").write_text("---")
            (Path(tmpdir) / "readme.txt").write_text("README")

            files = scan_agents_dir(Path(tmpdir))
            assert len(files) == 2
            file_names = [f.name for f in files]
            assert "agent1.md" in file_names
            assert "agent2.md" in file_names
            assert "readme.txt" not in file_names

    def test_scan_recursive(self):
        """Test recursive directory scanning."""
        with tempfile.TemporaryDirectory() as tmpdir:
            base = Path(tmpdir)
            (base / "root.md").write_text("---")
            (base / "subdir").mkdir()
            (base / "subdir" / "nested.md").write_text("---")
            (base / "subdir" / "readme.txt").write_text("README")

            files = scan_agents_dir(base, recursive=True)
            assert len(files) == 2

            files_flat = scan_agents_dir(base, recursive=False)
            assert len(files_flat) == 1
            assert files_flat[0].name == "root.md"


class TestCreateDefaultSecretaryDef:
    """Tests for create_default_secretary_def function."""

    def test_create_default_secretary(self):
        """Test creating default Secretary Agent."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_root = Path(tmpdir)
            secretary = create_default_secretary_def(agents_root)

            assert secretary.agent_id == "秘书"
            assert secretary.name == "秘书 Agent"
            assert secretary.team == "总裁办"
            assert secretary.workspace == "system"
            assert "task_planning" in secretary.skills
            assert "agent_discovery" in secretary.skills
            assert secretary.max_instances == 1
            assert secretary.queue_size == 10
            assert secretary.file_path == agents_root / "秘书.md"
