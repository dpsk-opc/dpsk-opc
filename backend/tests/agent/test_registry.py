"""Tests for Agent Registry (registry.py)."""

import os
import tempfile
from pathlib import Path

import pytest

from src.agent.defs import AgentDef
from src.agent.registry import AgentRegistry


class TestAgentRegistry:
    """Tests for AgentRegistry class."""

    def test_empty_registry(self):
        """Test empty registry."""
        registry = AgentRegistry()
        assert registry.count() == 0
        assert registry.is_loaded() is False
        assert registry.get("any") is None
        assert registry.list_all() == []

    def test_load_from_empty_dir(self):
        """Test loading from empty directory."""
        with tempfile.TemporaryDirectory() as tmpdir:
            registry = AgentRegistry()
            registry.load_from_dir(Path(tmpdir))
            assert registry.count() == 1  # Default secretary
            assert registry.is_loaded() is True
            assert registry.get("秘书") is not None

    def test_load_from_nonexistent_dir(self):
        """Test loading from nonexistent directory."""
        with tempfile.TemporaryDirectory() as tmpdir:
            nonexistent = Path(tmpdir) / "nonexistent"
            registry = AgentRegistry()
            registry.load_from_dir(nonexistent)
            # Should create directory and add default secretary
            assert nonexistent.exists()
            assert registry.count() == 1

    def test_load_single_agent(self):
        """Test loading a single agent definition."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "test-agent.md").write_text("""---
name: Test Agent
team: engineering
skills:
  - skill1
---
# Test Agent
""", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            assert registry.count() == 1
            agent = registry.get("test-agent")
            assert agent is not None
            assert agent.name == "Test Agent"
            assert "skill1" in agent.skills

    def test_load_multiple_agents(self):
        """Test loading multiple agent definitions."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "agent1.md").write_text("---\nname: Agent 1\n---\n", encoding="utf-8")
            (agents_dir / "agent2.md").write_text("---\nname: Agent 2\n---\n", encoding="utf-8")
            (agents_dir / "agent3.md").write_text("---\nname: Agent 3\n---\n", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            assert registry.count() == 3
            agents = registry.list_all()
            assert len(agents) == 3

    def test_load_nested_agents(self):
        """Test loading agents from nested directories."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "root.md").write_text("---\nname: Root Agent\n---\n", encoding="utf-8")
            (agents_dir / "subteam").mkdir()
            (agents_dir / "subteam" / "nested.md").write_text("---\nname: Nested Agent\n---\n", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            assert registry.count() == 2
            assert registry.get("root") is not None
            assert registry.get("nested") is not None

    def test_load_skips_invalid_files(self):
        """Test that invalid files are skipped with warning."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "valid-agent.md").write_text("---\nname: Valid\n---\n", encoding="utf-8")
            (agents_dir / "invalid-agent.md").write_text("No frontmatter name", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            assert registry.count() == 1
            assert registry.get("valid-agent") is not None
            assert registry.get("invalid-agent") is None

    def test_register_and_unregister(self):
        """Test registering and unregistering agents."""
        registry = AgentRegistry()

        agent = AgentDef(
            agent_id="new-agent",
            name="New Agent",
            file_path=Path("/tmp/new.md"),
        )
        registry.register(agent)
        assert registry.count() == 1
        assert registry.get("new-agent") is not None

        result = registry.unregister("new-agent")
        assert result is True
        assert registry.count() == 0
        assert registry.get("new-agent") is None

        # Unregister non-existent
        result = registry.unregister("nonexistent")
        assert result is False

    def test_get_by_team(self):
        """Test filtering agents by team."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "agent1.md").write_text("---\nname: Agent 1\nteam: engineering\n---\n", encoding="utf-8")
            (agents_dir / "agent2.md").write_text("---\nname: Agent 2\nteam: design\n---\n", encoding="utf-8")
            (agents_dir / "agent3.md").write_text("---\nname: Agent 3\nteam: engineering\n---\n", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            eng_agents = registry.get_by_team("engineering")
            assert len(eng_agents) == 2

            design_agents = registry.get_by_team("design")
            assert len(design_agents) == 1

            other_agents = registry.get_by_team("other")
            assert len(other_agents) == 0

    def test_get_by_workspace(self):
        """Test filtering agents by workspace."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "agent1.md").write_text("---\nname: Agent 1\nworkspace: dev\n---\n", encoding="utf-8")
            (agents_dir / "agent2.md").write_text("---\nname: Agent 2\nworkspace: prod\n---\n", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            dev_agents = registry.get_by_workspace("dev")
            assert len(dev_agents) == 1
            assert dev_agents[0].name == "Agent 1"

    def test_get_skills(self):
        """Test getting skills for an agent."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "test.md").write_text("---\nname: Test\nskills:\n  - skill1\n  - skill2\n---\n", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            skills = registry.get_skills("test")
            assert len(skills) == 2
            assert "skill1" in skills

            # Non-existent agent
            skills = registry.get_skills("nonexistent")
            assert skills == []

    def test_has_agent(self):
        """Test checking if agent exists."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "test.md").write_text("---\nname: Test\n---\n", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            assert registry.has_agent("test") is True
            assert registry.has_agent("nonexistent") is False

    def test_auto_create_secretary(self):
        """Test automatic Secretary creation."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            # No Secretary.md file

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=True)

            secretary = registry.get("秘书")
            assert secretary is not None
            assert secretary.name == "秘书 Agent"

    def test_no_auto_create_secretary(self):
        """Test no automatic Secretary creation when disabled."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=False)

            assert registry.get("秘书") is None
            assert registry.count() == 0

    def test_existing_secretary_preserved(self):
        """Test that existing Secretary is not overwritten."""
        with tempfile.TemporaryDirectory() as tmpdir:
            agents_dir = Path(tmpdir)
            (agents_dir / "秘书.md").write_text("""---
name: My Secretary
team: custom-team
---
# Custom Secretary
""", encoding="utf-8")

            registry = AgentRegistry()
            registry.load_from_dir(agents_dir, auto_create_secretary=True)

            secretary = registry.get("秘书")
            assert secretary.name == "My Secretary"
            assert secretary.team == "custom-team"
