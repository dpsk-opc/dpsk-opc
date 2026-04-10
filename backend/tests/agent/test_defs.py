"""Tests for Agent definitions (defs.py)."""

import time
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from src.agent.defs import (
    AgentDef,
    AgentInstance,
    AgentMetadata,
    AgentHandle,
    InstanceStatus,
    ModelConfig,
)


class TestInstanceStatus:
    """Tests for InstanceStatus enum."""

    def test_status_values(self):
        """Test all status values exist."""
        assert InstanceStatus.CREATING.value == "creating"
        assert InstanceStatus.RUNNING.value == "running"
        assert InstanceStatus.STOPPING.value == "stopping"
        assert InstanceStatus.STOPPED.value == "stopped"
        assert InstanceStatus.FAILED.value == "failed"

    def test_status_from_string(self):
        """Test creating status from string."""
        assert InstanceStatus("running") == InstanceStatus.RUNNING


class TestAgentMetadata:
    """Tests for AgentMetadata model."""

    def test_default_values(self):
        """Test default metadata values."""
        meta = AgentMetadata()
        assert meta.cpu_limit is None
        assert meta.memory_limit is None
        assert meta.timeout == 300

    def test_custom_values(self):
        """Test custom metadata values."""
        meta = AgentMetadata(cpu_limit=0.5, memory_limit=256, timeout=600)
        assert meta.cpu_limit == 0.5
        assert meta.memory_limit == 256
        assert meta.timeout == 600

    def test_extra_fields(self):
        """Test extra fields are allowed."""
        meta = AgentMetadata(custom_field="value")
        assert meta.custom_field == "value"


class TestModelConfig:
    """Tests for ModelConfig."""

    def test_default_values(self):
        """Test default model config."""
        config = ModelConfig()
        assert config.model == "gpt-4"
        assert config.temperature == 0.7
        assert config.max_tokens is None

    def test_custom_values(self):
        """Test custom model config."""
        config = ModelConfig(
            model="claude-3-opus",
            temperature=0.9,
            max_tokens=1000,
            top_p=0.95,
        )
        assert config.model == "claude-3-opus"
        assert config.temperature == 0.9
        assert config.max_tokens == 1000
        assert config.top_p == 0.95

    def test_temperature_bounds(self):
        """Test temperature validation."""
        with pytest.raises(ValueError):
            ModelConfig(temperature=-0.1)
        with pytest.raises(ValueError):
            ModelConfig(temperature=2.5)


class TestAgentDef:
    """Tests for AgentDef dataclass."""

    def test_create_agent_def(self):
        """Test creating an AgentDef."""
        agent = AgentDef(
            agent_id="test-agent",
            name="Test Agent",
            file_path=Path("/tmp/test.md"),
        )
        assert agent.agent_id == "test-agent"
        assert agent.name == "Test Agent"
        assert agent.file_path == Path("/tmp/test.md")
        assert agent.skills == []
        assert agent.max_instances == 1
        assert agent.queue_size == 0

    def test_agent_def_with_all_fields(self):
        """Test AgentDef with all fields."""
        agent = AgentDef(
            agent_id="full-agent",
            name="Full Agent",
            file_path=Path("/tmp/full.md"),
            skills=["skill1", "skill2"],
            capabilities=["capability1"],
            team="engineering",
            workspace="dev",
            dependencies=["dep1"],
            description="A full agent",
            model="gpt-4",
            model_config={"temperature": 0.8},
            max_instances=3,
            queue_size=10,
            max_experience_entries=500,
            metadata={"cpu_limit": 1.0, "memory_limit": 512},
        )
        assert len(agent.skills) == 2
        assert agent.team == "engineering"
        assert agent.workspace == "dev"
        assert agent.max_instances == 3
        assert agent.queue_size == 10

    def test_get_cpu_limit(self):
        """Test getting CPU limit from metadata."""
        agent = AgentDef(
            agent_id="test",
            name="Test",
            file_path=Path("/tmp/test.md"),
            metadata={"cpu_limit": 0.5},
        )
        assert agent.get_cpu_limit() == 0.5

    def test_get_memory_limit(self):
        """Test getting memory limit from metadata."""
        agent = AgentDef(
            agent_id="test",
            name="Test",
            file_path=Path("/tmp/test.md"),
            metadata={"memory_limit": 1024},
        )
        assert agent.get_memory_limit() == 1024

    def test_get_timeout(self):
        """Test getting timeout from metadata."""
        agent = AgentDef(
            agent_id="test",
            name="Test",
            file_path=Path("/tmp/test.md"),
        )
        assert agent.get_timeout() == 300

        agent2 = AgentDef(
            agent_id="test2",
            name="Test2",
            file_path=Path("/tmp/test2.md"),
            metadata={"timeout": 600},
        )
        assert agent2.get_timeout() == 600

    def test_to_dict(self):
        """Test serialization to dictionary."""
        agent = AgentDef(
            agent_id="test",
            name="Test",
            file_path=Path("/tmp/test.md"),
            skills=["skill1"],
        )
        data = agent.to_dict()
        assert data["agent_id"] == "test"
        assert data["name"] == "Test"
        assert data["skills"] == ["skill1"]
        assert isinstance(data["file_path"], str)

    def test_from_dict(self):
        """Test deserialization from dictionary."""
        data = {
            "agent_id": "test",
            "name": "Test",
            "file_path": "/tmp/test.md",
            "skills": ["skill1", "skill2"],
        }
        agent = AgentDef.from_dict(data)
        assert agent.agent_id == "test"
        assert agent.file_path == Path("/tmp/test.md")
        assert len(agent.skills) == 2


class TestAgentInstance:
    """Tests for AgentInstance dataclass."""

    def test_create_instance(self):
        """Test creating an AgentInstance."""
        now = time.time()
        instance = AgentInstance(
            instance_id="inst-1",
            agent_id="test-agent",
            sandbox_id="sandbox-1",
            status=InstanceStatus.RUNNING,
            created_at=now,
            last_heartbeat=now,
        )
        assert instance.instance_id == "inst-1"
        assert instance.agent_id == "test-agent"
        assert instance.status == InstanceStatus.RUNNING
        assert instance.task_count == 0

    def test_is_alive(self):
        """Test is_alive property."""
        instance = AgentInstance(
            instance_id="inst-1",
            agent_id="test",
            sandbox_id="sandbox-1",
            status=InstanceStatus.RUNNING,
            created_at=time.time(),
            last_heartbeat=time.time(),
        )
        assert instance.is_alive is True

        instance.status = InstanceStatus.STOPPED
        assert instance.is_alive is False

    def test_update_heartbeat(self):
        """Test heartbeat update."""
        old_time = time.time() - 100
        instance = AgentInstance(
            instance_id="inst-1",
            agent_id="test",
            sandbox_id="sandbox-1",
            status=InstanceStatus.RUNNING,
            created_at=old_time,
            last_heartbeat=old_time,
        )
        instance.update_heartbeat()
        assert instance.last_heartbeat > old_time

    def test_increment_task_count(self):
        """Test task count increment."""
        instance = AgentInstance(
            instance_id="inst-1",
            agent_id="test",
            sandbox_id="sandbox-1",
            status=InstanceStatus.RUNNING,
            created_at=time.time(),
            last_heartbeat=time.time(),
        )
        assert instance.task_count == 0
        instance.increment_task_count()
        assert instance.task_count == 1
        instance.increment_task_count()
        assert instance.task_count == 2

    def test_to_dict(self):
        """Test serialization."""
        now = time.time()
        instance = AgentInstance(
            instance_id="inst-1",
            agent_id="test",
            sandbox_id="sandbox-1",
            status=InstanceStatus.RUNNING,
            created_at=now,
            last_heartbeat=now,
        )
        data = instance.to_dict()
        assert data["instance_id"] == "inst-1"
        assert data["status"] == "running"

    def test_from_dict(self):
        """Test deserialization."""
        data = {
            "instance_id": "inst-1",
            "agent_id": "test",
            "sandbox_id": "sandbox-1",
            "status": "running",
            "created_at": time.time(),
            "last_heartbeat": time.time(),
        }
        instance = AgentInstance.from_dict(data)
        assert instance.instance_id == "inst-1"
        assert instance.status == InstanceStatus.RUNNING


class TestAgentHandle:
    """Tests for AgentHandle."""

    def test_handle_properties(self):
        """Test handle properties."""
        agent = AgentDef(
            agent_id="test",
            name="Test",
            file_path=Path("/tmp/test.md"),
        )
        instance = AgentInstance(
            instance_id="inst-1",
            agent_id="test",
            sandbox_id="sandbox-1",
            status=InstanceStatus.RUNNING,
            created_at=time.time(),
            last_heartbeat=time.time(),
        )
        handle = AgentHandle(instance=instance, agent_def=agent)
        assert handle.instance_id == "inst-1"
        assert handle.agent_id == "test"
        assert handle.status == InstanceStatus.RUNNING
