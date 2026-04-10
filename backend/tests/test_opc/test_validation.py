"""Tests for workflow validation."""

from __future__ import annotations

import pytest

from src.opc.models.workflow import TaskStatus, Workflow, WorkflowTask
from src.opc.validation import (
    validate_workflow,
    topological_sort,
    get_ready_tasks,
    get_tasks_to_skip,
)
from src.opc.errors import CyclicDependencyError


class TestWorkflowValidation:
    """Test workflow validation."""

    def test_validate_simple_workflow(self):
        """Test validation of a simple valid workflow."""
        workflow = Workflow(
            tasks=[
                WorkflowTask(id="task_1", agent="agent_a", task="Task 1"),
                WorkflowTask(id="task_2", agent="agent_b", task="Task 2", depends_on=["task_1"]),
            ]
        )

        errors = validate_workflow(workflow)
        assert len(errors) == 0

    def test_validate_duplicate_task_ids(self):
        """Test detection of duplicate task IDs."""
        workflow = Workflow(
            tasks=[
                WorkflowTask(id="task_1", agent="agent_a", task="Task 1"),
                WorkflowTask(id="task_1", agent="agent_b", task="Task 2"),  # Duplicate
            ]
        )

        errors = validate_workflow(workflow)
        assert len(errors) > 0
        assert any("Duplicate" in e for e in errors)

    def test_validate_invalid_reference(self):
        """Test detection of invalid task references."""
        workflow = Workflow(
            tasks=[
                WorkflowTask(id="task_1", agent="agent_a", task="Task 1", depends_on=["task_999"]),
            ]
        )

        errors = validate_workflow(workflow)
        assert len(errors) > 0
        assert any("non-existent" in e for e in errors)

    def test_validate_agent_not_found(self):
        """Test validation when agent not in registry."""
        workflow = Workflow(
            tasks=[
                WorkflowTask(id="task_1", agent="unknown_agent", task="Task 1"),
            ]
        )

        errors = validate_workflow(workflow, available_agents=["agent_a", "agent_b"])
        assert len(errors) > 0
        assert any("not found" in e for e in errors)


class TestCyclicDependencyDetection:
    """Test cyclic dependency detection."""

    def test_detect_simple_cycle(self):
        """Test detection of a simple cycle: A -> B -> C -> A."""
        workflow = Workflow(
            tasks=[
                WorkflowTask(id="task_a", agent="agent_a", task="Task A", depends_on=["task_c"]),
                WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
                WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
            ]
        )

        errors = validate_workflow(workflow)
        assert len(errors) > 0
        assert any("Cyclic" in e for e in errors)

    def test_no_cycle_in_valid_workflow(self):
        """Test that valid sequential workflows pass."""
        workflow = Workflow(
            tasks=[
                WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
                WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
                WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
            ]
        )

        errors = validate_workflow(workflow)
        assert len(errors) == 0

    def test_topological_sort_simple(self):
        """Test topological sort of simple chain."""
        tasks = [
            WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
        ]

        sorted_ids = topological_sort(tasks)
        assert sorted_ids.index("task_a") < sorted_ids.index("task_b")
        assert sorted_ids.index("task_b") < sorted_ids.index("task_c")

    def test_topological_sort_parallel(self):
        """Test topological sort with parallel branches."""
        tasks = [
            WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_a"]),
            WorkflowTask(id="task_d", agent="agent_b", task="Task D", depends_on=["task_b", "task_c"]),
        ]

        sorted_ids = topological_sort(tasks)
        # A must come before B and C
        assert sorted_ids.index("task_a") < sorted_ids.index("task_b")
        assert sorted_ids.index("task_a") < sorted_ids.index("task_c")
        # D must come after B and C
        assert sorted_ids.index("task_b") < sorted_ids.index("task_d")
        assert sorted_ids.index("task_c") < sorted_ids.index("task_d")


class TestReadyTasks:
    """Test getting ready tasks."""

    def test_get_ready_tasks_initial(self):
        """Test getting initial ready tasks (no dependencies)."""
        tasks = [
            WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B"),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_a"]),
        ]

        ready = get_ready_tasks(tasks, completed=set(), skipped=set())
        assert len(ready) == 2
        ready_ids = {t.id for t in ready}
        assert ready_ids == {"task_a", "task_b"}

    def test_get_ready_tasks_after_completion(self):
        """Test getting ready tasks after some completion."""
        tasks = [
            WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_a"]),
        ]

        ready = get_ready_tasks(tasks, completed={"task_a"}, skipped=set())
        assert len(ready) == 1
        assert ready[0].id == "task_b"

    def test_skip_tasks_on_failure(self):
        """Test getting tasks to skip when dependency fails."""
        tasks = [
            WorkflowTask(id="task_a", agent="agent_a", task="Task A"),
            WorkflowTask(id="task_b", agent="agent_b", task="Task B", depends_on=["task_a"]),
            WorkflowTask(id="task_c", agent="agent_a", task="Task C", depends_on=["task_b"]),
        ]

        to_skip = get_tasks_to_skip(tasks, "task_a", completed=set(), skipped=set())
        assert "task_b" in to_skip
        assert "task_c" in to_skip
