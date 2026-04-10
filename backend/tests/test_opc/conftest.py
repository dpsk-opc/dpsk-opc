"""Test fixtures for OPC-Client tests."""

from __future__ import annotations

import asyncio
from typing import Any, Dict, Optional
from unittest.mock import AsyncMock, MagicMock

import pytest

from src.opc.config import OPCConfig, WorkflowContext
from src.opc.models.workflow import TaskStatus, Workflow, WorkflowTask
from src.opc.models.result import TaskResult
from src.opc.providers.base import WorkflowProvider
from src.opc.invokers.base import AgentInvoker
from src.opc.strategies.base import ExecutionStrategy


class MockAgentInvoker(AgentInvoker):
    """Mock agent invoker for testing."""

    def __init__(self, results: Optional[Dict[str, TaskResult]] = None) -> None:
        self.results = results or {}
        self.call_history: list[tuple[str, str, Dict]] = []

    async def invoke(
        self,
        agent_id: str,
        task: str,
        context: Dict[str, Any],
    ) -> TaskResult:
        task_id = context.get("task_id", "")
        self.call_history.append((agent_id, task, context))

        if task_id in self.results:
            return self.results[task_id]

        # Default success result
        return TaskResult(
            task_id=task_id,
            status=TaskStatus.COMPLETED,
            output={"result": f"Completed {task_id}"},
            duration_ms=100,
        )


class MockWorkflowProvider(WorkflowProvider):
    """Mock workflow provider for testing."""

    def __init__(self, workflow: Optional[Workflow] = None) -> None:
        self.workflow = workflow
        self.generate_count = 0

    async def generate(
        self,
        user_request: str,
        context: WorkflowContext,
    ) -> Workflow:
        self.generate_count += 1
        if self.workflow:
            return self.workflow
        # Default simple workflow
        return Workflow(
            tasks=[
                WorkflowTask(
                    id="task_1",
                    agent="test_agent",
                    task=user_request,
                )
            ]
        )


class MockRegistry:
    """Mock agent registry for testing."""

    def __init__(self, agents: Optional[list[Dict[str, Any]]] = None) -> None:
        self.agents = agents or [
            {"agent_id": "agent_a", "name": "Agent A"},
            {"agent_id": "agent_b", "name": "Agent B"},
            {"agent_id": "秘书", "name": "Secretary"},
        ]

    def list_all(self):
        return self.agents

    def get(self, agent_id: str):
        for agent in self.agents:
            if agent.get("agent_id") == agent_id:
                return MagicMock(**agent)
        return None


@pytest.fixture
def mock_invoker():
    """Create a mock agent invoker."""
    return MockAgentInvoker()


@pytest.fixture
def mock_provider():
    """Create a mock workflow provider."""
    return MockWorkflowProvider()


@pytest.fixture
def mock_registry():
    """Create a mock agent registry."""
    return MockRegistry()


@pytest.fixture
def mock_execution_strategy():
    """Create a mock execution strategy."""
    from src.opc.strategies import ParallelDependencyStrategy

    return ParallelDependencyStrategy()


@pytest.fixture
def sample_workflow():
    """Create a sample sequential workflow."""
    return Workflow(
        tasks=[
            WorkflowTask(id="task_a", agent="agent_a", task="Task A", depends_on=[]),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
        ]
    )


@pytest.fixture
def parallel_workflow():
    """Create a sample parallel workflow."""
    return Workflow(
        tasks=[
            WorkflowTask(id="task_a", agent="agent_a", task="Task A", depends_on=[]),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=[]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_a", "task_b"]),
        ]
    )


@pytest.fixture
def cyclic_workflow():
    """Create a workflow with cyclic dependency."""
    return Workflow(
        tasks=[
            WorkflowTask(id="task_a", agent="agent_a", task="Task A", depends_on=["task_c"]),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
        ]
    )


@pytest.fixture
def config():
    """Create default config."""
    return OPCConfig()
