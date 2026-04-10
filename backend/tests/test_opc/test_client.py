"""Tests for OPCClient."""

from __future__ import annotations

import pytest

from src.opc.client import OPCClient
from src.opc.config import OPCConfig
from src.opc.models.workflow import WorkflowStatus
from src.opc.models.workflow import TaskStatus


class TestOPCClient:
    """Test OPCClient main class."""

    @pytest.mark.asyncio
    async def test_run_simple_request(
        self, mock_provider, mock_invoker, mock_execution_strategy, mock_registry, config
    ):
        """Test running a simple request."""
        client = OPCClient(
            workflow_provider=mock_provider,
            agent_invoker=mock_invoker,
            execution_strategy=mock_execution_strategy,
            registry=mock_registry,
            config=config,
        )

        result = await client.run("Test request")

        assert result.status == WorkflowStatus.COMPLETED
        assert result.request == "Test request"
        assert result.trace_id != ""

    @pytest.mark.asyncio
    async def test_run_with_custom_workflow(
        self, mock_invoker, mock_execution_strategy, mock_registry, sample_workflow, config
    ):
        """Test running with a custom workflow."""
        from src.opc.providers.static_provider import StaticFileWorkflowProvider

        provider = StaticFileWorkflowProvider(available_agents=["agent_a", "agent_b"])

        client = OPCClient(
            workflow_provider=provider,
            agent_invoker=mock_invoker,
            execution_strategy=mock_execution_strategy,
            registry=mock_registry,
            config=config,
        )

        # Manually set workflow (normally would be loaded)
        client.workflow_provider.workflow = sample_workflow

        result = await client.run("Sequential workflow test")

        assert result.status == WorkflowStatus.COMPLETED
        assert len(result.results) == 3

    @pytest.mark.asyncio
    async def test_run_validation_error(
        self, mock_invoker, mock_execution_strategy, mock_registry, cyclic_workflow, config
    ):
        """Test that cyclic workflow is rejected."""
        from src.opc.providers.static_provider import StaticFileWorkflowProvider

        provider = StaticFileWorkflowProvider(available_agents=["agent_a", "agent_b"])
        provider.workflow = cyclic_workflow

        client = OPCClient(
            workflow_provider=provider,
            agent_invoker=mock_invoker,
            execution_strategy=mock_execution_strategy,
            registry=mock_registry,
            config=config,
        )

        result = await client.run("Cyclic workflow test")

        # Should fail validation
        assert result.status == WorkflowStatus.FAILED
        assert result.error is not None
        assert "Cyclic" in result.error or "validation" in result.error.lower()

    @pytest.mark.asyncio
    async def test_run_with_progress_streaming(
        self, mock_provider, mock_invoker, mock_execution_strategy, mock_registry, config
    ):
        """Test streaming progress events."""
        client = OPCClient(
            workflow_provider=mock_provider,
            agent_invoker=mock_invoker,
            execution_strategy=mock_execution_strategy,
            registry=mock_registry,
            config=config,
        )

        events = []
        async for event in client.run_with_progress("Streaming test"):
            events.append(event)
            if event.type == "workflow_complete" or event.type == "workflow_failed":
                break

        assert len(events) > 0
        assert events[0].type == "workflow_start"

    @pytest.mark.asyncio
    async def test_get_available_agents(
        self, mock_provider, mock_invoker, mock_execution_strategy, mock_registry, config
    ):
        """Test getting available agents from registry."""
        client = OPCClient(
            workflow_provider=mock_provider,
            agent_invoker=mock_invoker,
            execution_strategy=mock_execution_strategy,
            registry=mock_registry,
            config=config,
        )

        agents = await client._get_available_agents()
        assert len(agents) == 3

    def test_determine_workflow_status_all_complete(self):
        """Test status determination when all tasks complete."""
        from src.opc.models.result import TaskResult

        client = OPCClient.__new__(OPCClient)

        results = {
            "task_1": TaskResult(task_id="task_1", status=TaskStatus.COMPLETED),
            "task_2": TaskResult(task_id="task_2", status=TaskStatus.COMPLETED),
        }

        status = client._determine_workflow_status(results)
        assert status == WorkflowStatus.COMPLETED

    def test_determine_workflow_status_with_failure(self):
        """Test status determination when any task fails."""
        from src.opc.models.result import TaskResult

        client = OPCClient.__new__(OPCClient)

        results = {
            "task_1": TaskResult(task_id="task_1", status=TaskStatus.COMPLETED),
            "task_2": TaskResult(task_id="task_2", status=TaskStatus.FAILED),
        }

        status = client._determine_workflow_status(results)
        assert status == WorkflowStatus.FAILED

    def test_determine_workflow_status_with_skipped(self):
        """Test status determination when tasks are skipped."""
        from src.opc.models.result import TaskResult

        client = OPCClient.__new__(OPCClient)

        results = {
            "task_1": TaskResult(task_id="task_1", status=TaskStatus.COMPLETED),
            "task_2": TaskResult(task_id="task_2", status=TaskStatus.SKIPPED),
        }

        status = client._determine_workflow_status(results)
        assert status == WorkflowStatus.COMPLETED
