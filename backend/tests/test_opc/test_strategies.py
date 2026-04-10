"""Tests for execution strategies."""

from __future__ import annotations

import asyncio
from typing import Any, Dict

import pytest

from src.opc.config import OPCConfig
from src.opc.models.result import TaskResult
from src.opc.models.workflow import TaskStatus, Workflow, WorkflowTask
from src.opc.strategies.parallel_strategy import ParallelDependencyStrategy


class TestParallelDependencyStrategy:
    """Test parallel execution strategy."""

    @pytest.mark.asyncio
    async def test_execute_simple_sequential(self, mock_invoker, config):
        """Test execution of simple sequential workflow."""
        strategy = ParallelDependencyStrategy(config)

        tasks = [
            WorkflowTask(id="task_1", agent="agent_a", task="Task 1"),
            WorkflowTask(id="task_2", agent="agent_b", task="Task 2", depends_on=["task_1"]),
            WorkflowTask(id="task_3", agent="agent_a", task="Task 3", depends_on=["task_2"]),
        ]

        results = await strategy.execute(
            tasks,
            mock_invoker,
            {"trace_id": "test-123"},
        )

        assert len(results) == 3
        assert all(r.status == TaskStatus.COMPLETED for r in results.values())

    @pytest.mark.asyncio
    async def test_execute_parallel_tasks(self, mock_invoker, config):
        """Test that parallel-ready tasks execute concurrently."""
        strategy = ParallelDependencyStrategy(config)

        tasks = [
            WorkflowTask(id="task_1", agent="agent_a", task="Task 1"),
            WorkflowTask(id="task_2", agent="agent_b", task="Task 2"),
            WorkflowTask(id="task_3", agent="agent_a", task="Task 3", depends_on=["task_1", "task_2"]),
        ]

        results = await strategy.execute(
            tasks,
            mock_invoker,
            {"trace_id": "test-123"},
        )

        assert len(results) == 3
        # task_1 and task_2 should execute first in parallel
        assert results["task_1"].status == TaskStatus.COMPLETED
        assert results["task_2"].status == TaskStatus.COMPLETED

    @pytest.mark.asyncio
    async def test_execute_with_failure_stop(self, mock_invoker, config):
        """Test that failure stops execution when strategy is stop_on_failure."""
        config.failure_strategy = "stop_on_failure"

        # Make task_1 always fail
        mock_invoker.results["task_1"] = TaskResult(
            task_id="task_1",
            status=TaskStatus.FAILED,
            error="Task failed",
        )

        strategy = ParallelDependencyStrategy(config)

        tasks = [
            WorkflowTask(id="task_1", agent="agent_a", task="Task 1"),
            WorkflowTask(id="task_2", agent="agent_b", task="Task 2", depends_on=["task_1"]),
            WorkflowTask(id="task_3", agent="agent_a", task="Task 3", depends_on=["task_2"]),
        ]

        results = await strategy.execute(
            tasks,
            mock_invoker,
            {"trace_id": "test-123"},
        )

        assert results["task_1"].status == TaskStatus.FAILED
        # task_2 and task_3 should be skipped
        assert results["task_2"].status == TaskStatus.SKIPPED
        assert results["task_3"].status == TaskStatus.SKIPPED

    @pytest.mark.asyncio
    async def test_execute_all_complete(self, mock_invoker, config):
        """Test that all tasks complete when successful."""
        strategy = ParallelDependencyStrategy(config)

        tasks = [
            WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
            WorkflowTask(id="task_d", agent="agent_b", task="Task D"),
            WorkflowTask(id="task_e", agent="agent_a", task="Task E", depends_on=["task_c", "task_d"]),
        ]

        results = await strategy.execute(
            tasks,
            mock_invoker,
            {"trace_id": "test-123"},
        )

        assert len(results) == 5
        assert all(r.status == TaskStatus.COMPLETED for r in results.values())

    @pytest.mark.asyncio
    async def test_call_history_order(self, mock_invoker, config):
        """Test that task invocation order respects dependencies."""
        strategy = ParallelDependencyStrategy(config)

        tasks = [
            WorkflowTask(id="task_1", agent="agent_a", task="Task 1"),
            WorkflowTask(id="task_2", agent="agent_b", task="Task 2", depends_on=["task_1"]),
        ]

        await strategy.execute(
            tasks,
            mock_invoker,
            {"trace_id": "test-123"},
        )

        # task_1 should be called before task_2
        assert len(mock_invoker.call_history) == 2
        assert mock_invoker.call_history[0][0] == "agent_a"  # task_1 agent
        assert mock_invoker.call_history[1][0] == "agent_b"  # task_2 agent


class TestTaskExecution:
    """Test individual task execution."""

    @pytest.mark.asyncio
    async def test_task_timeout_handling(self):
        """Test that task timeout is handled properly."""
        from src.opc.config import OPCConfig
        from src.opc.invokers.local_invoker import LocalAgentInvoker

        # Create a mock that simulates timeout
        async def slow_invoke(*args, **kwargs):
            await asyncio.sleep(10)  # Simulate slow task
            return TaskResult(
                task_id="test",
                status=TaskStatus.COMPLETED,
            )

        mock_manager = MagicMock()
        mock_manager.list_instances = AsyncMock(return_value=[])

        invoker = LocalAgentInvoker(mock_manager, config=OPCConfig(default_task_timeout_secs=1))

        result = await invoker.invoke(
            "test_agent",
            "slow_task",
            {"task_id": "test"},
        )

        # Should timeout and fail
        assert result.status == TaskStatus.FAILED
        assert "timed out" in result.error.lower()
