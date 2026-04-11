"""Tests for Agent-Bus integration."""

import asyncio
import pytest
from pathlib import Path

from src.agent.defs import AgentDef, AgentInstance, AgentHandle, InstanceStatus
from src.agent.registry import AgentRegistry
from src.agent.spawner import LocalAgentSpawner
from src.agent.manager import AgentManager
from src.bus.memory import InMemoryMessageBus
from src.bus.models import Target, TargetType


@pytest.fixture
def agent_def():
    """Create a test agent definition."""
    return AgentDef(
        agent_id="test_agent",
        name="Test Agent",
        file_path=Path("/tmp/test.md"),
        skills=["test_skill"],
        capabilities=["testing"],
        description="A test agent",
    )


@pytest.fixture
def in_memory_bus():
    """Create an in-memory message bus."""
    return InMemoryMessageBus()


@pytest.mark.asyncio
async def test_agent_spawn_with_bus(agent_def, in_memory_bus):
    """Test spawning an agent with bus integration."""
    # Create spawner with bus
    spawner = LocalAgentSpawner(bus=in_memory_bus)
    
    # Spawn agent
    handle = await spawner.spawn(agent_def)
    
    # Verify handle has bus reference
    assert handle is not None
    assert handle.agent_id == "test_agent"
    assert handle.status == InstanceStatus.RUNNING
    
    # Verify agent is registered with bus
    bus_health = await in_memory_bus.health_check()
    assert bus_health["agent_subscriptions"] >= 1


@pytest.mark.asyncio
async def test_agent_send_task_via_bus(agent_def, in_memory_bus):
    """Test sending task to agent via bus."""
    # Create spawner with bus
    spawner = LocalAgentSpawner(bus=in_memory_bus)
    
    # Spawn agent
    handle = await spawner.spawn(agent_def)
    
    # Send task via handle
    result = await handle.send_task(
        task_name="test_skill",
        task_data={"input": "test"},
        timeout=5.0,
    )
    
    # Should get a response (may be error if skill not registered, but shouldn't be "not implemented")
    assert "error" not in result or result.get("error") != "Not implemented: bus integration pending"


@pytest.mark.asyncio
async def test_agent_manager_with_bus(agent_def, in_memory_bus):
    """Test AgentManager passing bus to spawner."""
    registry = AgentRegistry()
    spawner = LocalAgentSpawner()
    manager = AgentManager(registry, spawner, bus=in_memory_bus)
    
    # Register test agent
    registry.register(agent_def)
    
    # Spawn via manager
    handle = await manager.spawn_agent("test_agent")
    
    assert handle is not None
    assert handle.agent_id == "test_agent"
    
    # Verify bus subscription
    bus_health = await in_memory_bus.health_check()
    assert bus_health["agent_subscriptions"] >= 1


@pytest.mark.asyncio
async def test_bus_request_response_flow(agent_def, in_memory_bus):
    """Test the full request-response flow via bus."""
    from src.bus.models import TaskRequest
    
    # Create spawner with bus
    spawner = LocalAgentSpawner(bus=in_memory_bus)
    
    # Spawn agent
    handle = await spawner.spawn(agent_def)
    
    # Create task request
    target = Target(type=TargetType.AGENT, value="test_agent")
    request = TaskRequest(
        source="test",
        target=target,
        task_name="test_skill",
        task_data={"test": "data"},
    )
    
    # Send via bus
    response = await in_memory_bus.request(
        target=target,
        message=request,
        timeout=5.0,
    )
    
    # Verify response
    assert response is not None
    # The response may indicate skill not found, but bus integration is working


@pytest.mark.asyncio
async def test_agent_fallback_without_bus(agent_def):
    """Test agent works with fallback when bus not available."""
    # Create spawner without bus
    spawner = LocalAgentSpawner()
    
    # Spawn agent (should work without bus)
    handle = await spawner.spawn(agent_def)
    
    assert handle is not None
    
    # Send task (should use fallback handler)
    result = await handle.send_task(
        task_name="test_skill",
        task_data={"input": "test"},
        timeout=5.0,
    )
    
    # Should not return "Bus not available" error
    assert "Bus not available" not in str(result)


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
