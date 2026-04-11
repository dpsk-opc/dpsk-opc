"""Tests for LLM integration with Agent."""

import pytest
from pathlib import Path

from src.agent.defs import AgentDef
from src.agent.registry import AgentRegistry
from src.agent.spawner import LocalAgentSpawner
from src.agent.manager import AgentManager
from src.bus.memory import InMemoryMessageBus


class MockLLMClient:
    """Mock LLM client for testing."""
    
    def __init__(self, model: str = "mock-model"):
        self.model = model
    
    async def complete(self, prompt: str, system_prompt=None, messages=None, **kwargs):
        from src.llm.base import LLMResponse
        return LLMResponse(
            content=f"Mock response for: {prompt[:50]}...",
            model=self.model,
            usage={"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30},
            finish_reason="stop",
        )
    
    async def stream_complete(self, prompt: str, system_prompt=None, messages=None, **kwargs):
        response = f"Mock streaming response for: {prompt[:30]}..."
        for char in response:
            yield char


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
def agent_def_with_custom_llm():
    """Create an agent definition with custom LLM config."""
    return AgentDef(
        agent_id="custom_llm_agent",
        name="Custom LLM Agent",
        file_path=Path("/tmp/custom.md"),
        skills=["test_skill"],
        capabilities=["testing with custom LLM"],
        description="An agent with custom LLM",
        model="claude-3",
        model_config={
            "temperature": 0.5,
            "max_tokens": 2000,
        },
    )


@pytest.fixture
def default_llm_client():
    """Create a mock default LLM client."""
    return MockLLMClient(model="gpt-3.5-turbo")


@pytest.fixture
def custom_llm_client():
    """Create a mock custom LLM client."""
    return MockLLMClient(model="claude-3")


@pytest.mark.asyncio
async def test_agent_spawn_with_default_llm(
    agent_def, 
    default_llm_client,
):
    """Test spawning agent with default LLM client."""
    spawner = LocalAgentSpawner(
        default_llm_client=default_llm_client,
    )
    
    handle = await spawner.spawn(agent_def)
    
    assert handle is not None
    assert handle.agent_id == "test_agent"


@pytest.mark.asyncio
async def test_agent_llm_selection_priority(
    agent_def,
    agent_def_with_custom_llm,
    default_llm_client,
    custom_llm_client,
):
    """Test that custom LLM takes priority over default."""
    from src.llm.registry import LLMRegistry
    
    registry = LLMRegistry()
    registry.set_default_client(default_llm_client)
    
    # Add custom client to registry
    registry.get_client = lambda model, model_config=None: custom_llm_client if model == "claude-3" else None
    
    spawner = LocalAgentSpawner(
        llm_registry=registry,
        default_llm_client=default_llm_client,
    )
    
    # Spawn agent with custom LLM
    handle1 = await spawner.spawn(agent_def)  # Should use default
    
    # Check that spawner has correct config
    client_for_agent = spawner._get_llm_client_for_agent(agent_def_with_custom_llm)
    assert client_for_agent == custom_llm_client, "Should use custom LLM for agent with custom model"


@pytest.mark.asyncio
async def test_agent_manager_passes_llm_to_spawner(
    agent_def,
    default_llm_client,
):
    """Test that AgentManager passes LLM to spawner."""
    from src.llm.registry import LLMRegistry
    
    registry = AgentRegistry()
    spawner = LocalAgentSpawner()
    agent_manager = AgentManager(
        registry,
        spawner,
        default_llm_client=default_llm_client,
    )
    
    assert agent_manager._default_llm_client == default_llm_client
    assert spawner._default_llm_client == default_llm_client


@pytest.mark.asyncio
async def test_llm_client_fallback():
    """Test fallback behavior when no custom model available."""
    from src.llm.registry import LLMRegistry
    
    default_client = MockLLMClient(model="gpt-4")
    
    registry = LLMRegistry()
    registry.set_default_client(default_client)
    
    spawner = LocalAgentSpawner(
        llm_registry=registry,
        default_llm_client=default_client,
    )
    
    # Agent without custom model should use default
    agent_def = AgentDef(
        agent_id="default_agent",
        name="Default Agent",
        file_path=Path("/tmp/default.md"),
        skills=[],
        capabilities=[],
        description="Agent using default LLM",
    )
    
    client = spawner._get_llm_client_for_agent(agent_def)
    assert client == default_client, "Should use default LLM client"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
