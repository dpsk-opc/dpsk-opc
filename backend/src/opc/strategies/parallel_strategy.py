"""Parallel Dependency Execution Strategy.

This module provides a task execution strategy that executes
parallel-ready tasks concurrently while respecting dependencies.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any, Dict, Optional

from src.opc.config import OPCConfig
from src.opc.models.result import TaskResult
from src.opc.models.workflow import TaskStatus, WorkflowTask
from src.opc.strategies.base import ExecutionStrategy
from src.opc.validation import get_ready_tasks, get_tasks_to_skip

logger = logging.getLogger(__name__)


class ParallelDependencyStrategy(ExecutionStrategy):
    """Execution strategy that runs parallel-ready tasks concurrently.

    This strategy:
    1. Identifies all tasks whose dependencies are satisfied
    2. Executes them in parallel
    3. Waits for completion, then identifies next batch
    4. Continues until all tasks are complete or failed
    """

    def __init__(self, config: Optional[OPCConfig] = None) -> None:
        """Initialize the parallel execution strategy.

        Args:
            config: OPC configuration for failure handling
        """
        self.config = config or OPCConfig()
        self._failure_strategy = self.config.failure_strategy
        self._max_parallel = self.config.max_parallel_tasks

    async def execute(
        self,
        tasks: list[WorkflowTask],
        invoker: Any,
        context: Dict[str, Any],
    ) -> Dict[str, TaskResult]:
        """Execute tasks with parallel dependency strategy.

        Args:
            tasks: List of workflow tasks to execute
            invoker: Agent invoker for task execution
            context: Execution context with trace_id, etc.

        Returns:
            Dictionary mapping task_id to TaskResult
        """
        trace_id = context.get("trace_id", "")

        logger.info(
            f"Starting parallel execution of {len(tasks)} tasks",
            extra={"trace_id": trace_id},
        )

        results: Dict[str, TaskResult] = {}
        completed: set[str] = set()
        skipped: set[str] = set()
        failed_task_id: Optional[str] = None

        # Track running tasks
        running: Dict[str, asyncio.Task] = {}

        while len(completed) + len(skipped) < len(tasks):
            # Check if we should stop due to failure
            if failed_task_id and self._failure_strategy == "stop_on_failure":
                # Mark remaining as skipped
                for task in tasks:
                    if task.id not in completed and task.id not in skipped:
                        skipped.add(task.id)
                        results[task.id] = TaskResult(
                            task_id=task.id,
                            status=TaskStatus.SKIPPED,
                            error="Skipped due to upstream failure",
                        )
                break

            # Get ready tasks
            ready_tasks = get_ready_tasks(tasks, completed, skipped)

            # Handle skipped tasks
            if failed_task_id:
                to_skip = get_tasks_to_skip(tasks, failed_task_id, completed, skipped)
                for task_id in to_skip:
                    if task_id not in skipped:
                        skipped.add(task_id)
                        results[task_id] = TaskResult(
                            task_id=task_id,
                            status=TaskStatus.SKIPPED,
                            error="Skipped due to dependency failure",
                        )
                ready_tasks = [t for t in ready_tasks if t.id not in to_skip]

            # No ready tasks means we're stuck (possible cycle)
            if not ready_tasks and not running:
                remaining = [t.id for t in tasks if t.id not in completed and t.id not in skipped]
                logger.warning(
                    f"No ready tasks and no running tasks. Remaining: {remaining}",
                    extra={"trace_id": trace_id},
                )
                break

            # Limit parallel execution
            available_slots = self._max_parallel - len(running)
            tasks_to_run = ready_tasks[:available_slots]

            # Start executing ready tasks
            for task in tasks_to_run:
                logger.debug(
                    f"Starting task {task.id}",
                    extra={"trace_id": trace_id, "agent": task.agent},
                )

                # Build task context
                task_context = {
                    **context,
                    "task_id": task.id,
                    "task": task.task,
                    "previous_results": {tid: results[tid].output for tid in completed if tid in results},
                }

                # Create async task for parallel execution
                coro = self._execute_single_task(task, invoker, task_context)
                running_task = asyncio.create_task(coro)
                running[task.id] = running_task

            # Wait for at least one task to complete
            if running:
                done, pending = await asyncio.wait(
                    running.values(),
                    return_when=asyncio.FIRST_COMPLETED,
                )

                # Process completed tasks
                for done_task in done:
                    task_id = None
                    for tid, t in running.items():
                        if t == done_task:
                            task_id = tid
                            break

                    if task_id is None:
                        continue

                    result = done_task.result()
                    results[task_id] = result
                    del running[task_id]

                    if result.status == TaskStatus.COMPLETED:
                        completed.add(task_id)
                        logger.info(
                            f"Task {task_id} completed",
                            extra={
                                "trace_id": trace_id,
                                "duration_ms": result.duration_ms,
                            },
                        )
                    elif result.status == TaskStatus.FAILED:
                        failed_task_id = task_id
                        logger.warning(
                            f"Task {task_id} failed: {result.error}",
                            extra={"trace_id": trace_id},
                        )

        logger.info(
            f"Parallel execution finished. "
            f"Completed: {len(completed)}, Skipped: {len(skipped)}, "
            f"Failed: {failed_task_id is not None}",
            extra={"trace_id": trace_id},
        )

        return results

    async def _execute_single_task(
        self,
        task: WorkflowTask,
        invoker: Any,
        context: Dict[str, Any],
    ) -> TaskResult:
        """Execute a single task.

        Args:
            task: Task to execute
            invoker: Agent invoker
            context: Execution context

        Returns:
            Task result
        """
        start_time = time.time()
        trace_id = context.get("trace_id", "")

        try:
            result = await invoker.invoke(
                agent_id=task.agent,
                task=task.task,
                context={
                    **context,
                    "context_hint": task.context_hint,
                },
            )

            # Ensure duration is set
            if result.duration_ms == 0:
                result.duration_ms = int((time.time() - start_time) * 1000)

            return result

        except Exception as e:
            logger.exception(
                f"Unexpected error executing task {task.id}",
                extra={"trace_id": trace_id},
            )
            return TaskResult(
                task_id=task.id,
                status=TaskStatus.FAILED,
                error=str(e),
                duration_ms=int((time.time() - start_time) * 1000),
            )
