"""Workflow Validation.

This module provides validation functions for workflow structures.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any, Optional

if TYPE_CHECKING:
    from src.opc.models.workflow import Workflow, WorkflowTask
    from src.opc.errors import WorkflowValidationError


def validate_workflow(
    workflow: "Workflow",
    available_agents: Optional[list[str]] = None,
    trace_id: str = "",
) -> list[str]:
    """Validate a workflow.

    Performs the following validations:
    1. Task ID uniqueness
    2. No cyclic dependencies (topological sort)
    3. Agent existence in registry
    4. Dependency references validity

    Args:
        workflow: The workflow to validate
        available_agents: Optional list of available agent IDs
        trace_id: Trace ID for error reporting

    Returns:
        List of validation error messages. Empty if valid.

    Raises:
        WorkflowValidationError: If validation fails
    """
    from src.opc.errors import (
        AgentNotFoundError,
        CyclicDependencyError,
        DuplicateTaskIdError,
        InvalidTaskReferenceError,
    )

    errors: list[str] = []
    task_ids = {task.id for task in workflow.tasks}

    # 1. Check for duplicate task IDs
    duplicate_ids = _find_duplicate_ids(workflow.tasks)
    if duplicate_ids:
        errors.append(f"Duplicate task IDs: {duplicate_ids}")

    # 2. Check for cyclic dependencies
    cycle = _detect_cycle(workflow.tasks)
    if cycle:
        cycle_path = " -> ".join(cycle + [cycle[0]])
        errors.append(f"Cyclic dependency detected: {cycle_path}")

    # 3. Check agent existence
    if available_agents is not None:
        agent_set = set(available_agents)
        for task in workflow.tasks:
            if task.agent not in agent_set:
                errors.append(f"Agent '{task.agent}' not found in registry (task: {task.id})")

    # 4. Check dependency references
    for task in workflow.tasks:
        for dep_id in task.depends_on:
            if dep_id not in task_ids:
                errors.append(f"Task '{task.id}' references non-existent task '{dep_id}'")

    return errors


def _find_duplicate_ids(tasks: list["WorkflowTask"]) -> set[str]:
    """Find duplicate task IDs."""
    seen: set[str] = set()
    duplicates: set[str] = set()

    for task in tasks:
        if task.id in seen:
            duplicates.add(task.id)
        seen.add(task.id)

    return duplicates


def _detect_cycle(tasks: list["WorkflowTask"]) -> list[str] | None:
    """Detect cyclic dependencies using DFS.

    Returns:
        List representing the cycle path if detected, None otherwise.
    """
    # Build adjacency list
    graph: dict[str, list[str]] = {task.id: list(task.depends_on) for task in tasks}

    visited: set[str] = set()
    rec_stack: set[str] = set()
    path: list[str] = []

    def dfs(node: str) -> list[str] | None:
        visited.add(node)
        rec_stack.add(node)
        path.append(node)

        for neighbor in graph.get(node, []):
            if neighbor not in visited:
                result = dfs(neighbor)
                if result:
                    return result
            elif neighbor in rec_stack:
                # Found cycle
                cycle_start = path.index(neighbor)
                return path[cycle_start:]

        path.pop()
        rec_stack.remove(node)
        return None

    # Check all nodes
    for task_id in graph:
        if task_id not in visited:
            result = dfs(task_id)
            if result:
                return result

    return None


def topological_sort(tasks: list["WorkflowTask"]) -> list[str]:
    """Perform topological sort on tasks.

    Returns tasks in dependency order (dependencies first).

    Args:
        tasks: List of workflow tasks

    Returns:
        List of task IDs in topological order

    Raises:
        CyclicDependencyError: If cyclic dependency detected
    """
    from src.opc.errors import CyclicDependencyError

    graph: dict[str, list[str]] = {task.id: list(task.depends_on) for task in tasks}
    in_degree: dict[str, int] = {task.id: len(task.depends_on) for task in tasks}

    # Initialize queue with nodes that have no dependencies
    queue: list[str] = [task_id for task_id, degree in in_degree.items() if degree == 0]
    result: list[str] = []

    while queue:
        node = queue.pop(0)
        result.append(node)

        # Reduce in-degree for dependent nodes
        for task in tasks:
            if node in task.depends_on:
                task_id = task.id
                in_degree[task_id] -= 1
                if in_degree[task_id] == 0:
                    queue.append(task_id)

    # Check for cycle (if not all nodes are in result)
    if len(result) != len(tasks):
        cycle = _detect_cycle(tasks)
        if cycle:
            raise CyclicDependencyError(cycle)

    return result


def get_ready_tasks(
    tasks: list["WorkflowTask"],
    completed: set[str],
    skipped: set[str],
) -> list["WorkflowTask"]:
    """Get tasks that are ready to execute.

    A task is ready if:
    1. It is not already completed or skipped
    2. All its dependencies are completed

    Args:
        tasks: List of all workflow tasks
        completed: Set of completed task IDs
        skipped: Set of skipped task IDs

    Returns:
        List of ready tasks
    """
    finished = completed | skipped
    ready: list["WorkflowTask"] = []

    for task in tasks:
        if task.id in finished:
            continue
        # Check if all dependencies are met
        if all(dep_id in completed for dep_id in task.depends_on):
            ready.append(task)

    return ready


def get_tasks_to_skip(
    tasks: list["WorkflowTask"],
    failed_task_id: str,
    completed: set[str],
    skipped: set[str],
) -> set[str]:
    """Get task IDs that should be skipped due to failed dependency.

    Args:
        tasks: List of all workflow tasks
        failed_task_id: ID of the failed task
        completed: Set of completed task IDs
        skipped: Set of already skipped task IDs

    Returns:
        Set of task IDs to skip
    """
    to_skip: set[str] = set()
    finished = completed | skipped | {failed_task_id}

    # Find all tasks that depend on the failed task
    queue = [failed_task_id]
    while queue:
        current = queue.pop(0)
        for task in tasks:
            if task.id in finished:
                continue
            if current in task.depends_on:
                to_skip.add(task.id)
                queue.append(task.id)
                finished.add(task.id)

    return to_skip
