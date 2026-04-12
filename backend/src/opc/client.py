"""OPC-Client Main Entry.

This module provides the main OPCClient class that orchestrates
workflow execution.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from typing import Any, AsyncIterator

from src.opc.config import OPCConfig, WorkflowContext
from src.opc.errors import (
    OPCError,
    WorkflowCancelledError,
    WorkflowTimeoutError,
    WorkflowValidationError,
)
from src.opc.models.events import EventType, ProgressEvent
from src.opc.models.result import FinalResult, TaskResult, WorkflowStatus
from src.opc.models.workflow import TaskStatus, Workflow, WorkflowTask
from src.opc.providers.base import WorkflowProvider
from src.opc.strategies.base import ExecutionStrategy
from src.opc.validation import validate_workflow

logger = logging.getLogger(__name__)


class OPCClient:
    """Main orchestration client for DPSK-OPC.

    OPCClient provides the unified entry point for user requests,
    coordinating workflow generation, security validation,
    and task execution.
    """

    def __init__(
        self,
        workflow_provider: WorkflowProvider,
        agent_invoker: Any,
        execution_strategy: ExecutionStrategy,
        registry: Any,
        security_module: Any | None = None,
        logger_instance: Any | None = None,
        config: OPCConfig | None = None,
    ) -> None:
        """Initialize OPC-Client.

        Args:
            workflow_provider: Provider for generating workflows
            agent_invoker: Invoker for executing agent tasks
            execution_strategy: Strategy for executing tasks
            registry: Agent registry for listing available agents
            security_module: Optional security module for validation
            logger_instance: Optional logger instance
            config: Optional configuration
        """
        self.workflow_provider = workflow_provider
        self.agent_invoker = agent_invoker
        self.execution_strategy = execution_strategy
        self.registry = registry
        self.security_module = security_module
        self.logger = logger_instance or logging.getLogger(__name__)
        self.config = config or OPCConfig()

    async def run(self, user_request: str) -> FinalResult:
        """Execute user request and return final result.

        This is the main entry point for synchronous workflow execution.

        Args:
            user_request: User's natural language request

        Returns:
            FinalResult containing all task results and workflow status
        """
        trace_id = str(uuid.uuid4())
        start_time = time.time()

        self.logger.info(
            f"Starting workflow execution",
            extra={"trace_id": trace_id, "request": user_request},
        )

        try:
            # Phase 1: Get available agents from registry
            available_agents = self._get_available_agents()
            agent_ids = [a["agent_id"] if isinstance(a, dict) else a for a in available_agents]

            self.logger.debug(
                f"Found {len(available_agents)} available agents",
                extra={"trace_id": trace_id},
            )

            # Build workflow context
            context: WorkflowContext = WorkflowContext(
                trace_id=trace_id,
                user_request=user_request,
                available_agents=available_agents,
            )

            # Phase 2: Generate workflow
            self.logger.debug(
                "Generating workflow",
                extra={"trace_id": trace_id},
            )
            workflow: Workflow = await self.workflow_provider.generate(
                user_request,
                context,
            )

            self.logger.info(
                f"Generated workflow with {len(workflow.tasks)} tasks",
                extra={"trace_id": trace_id},
            )

            # Phase 3: Validate workflow
            validation_errors = validate_workflow(workflow, agent_ids, trace_id)
            if validation_errors:
                raise WorkflowValidationError(
                    f"Workflow validation failed: {'; '.join(validation_errors)}",
                    trace_id=trace_id,
                )

            # Phase 4: Execute workflow
            self.logger.debug(
                "Starting workflow execution",
                extra={"trace_id": trace_id},
            )

            execution_context: dict[str, Any] = {
                "trace_id": trace_id,
                "request": user_request,
            }

            results = await self._execute_with_timeout(
                workflow,
                execution_context,
            )

            # Phase 5: Aggregate results
            final_status = self._determine_workflow_status(results)

            total_duration_ms = int((time.time() - start_time) * 1000)

            self.logger.info(
                f"Workflow execution completed",
                extra={
                    "trace_id": trace_id,
                    "status": final_status.value,
                    "duration_ms": total_duration_ms,
                },
            )

            return FinalResult(
                request=user_request,
                workflow=workflow,
                results=results,
                status=final_status,
                total_duration_ms=total_duration_ms,
                trace_id=trace_id,
            )

        except WorkflowTimeoutError as e:
            return FinalResult(
                request=user_request,
                workflow=Workflow(),  # Empty workflow on timeout
                results={},
                status=WorkflowStatus.TIMEOUT,
                total_duration_ms=int((time.time() - start_time) * 1000),
                trace_id=trace_id,
                error=str(e),
            )

        except WorkflowCancelledError as e:
            return FinalResult(
                request=user_request,
                workflow=Workflow(),
                results={},
                status=WorkflowStatus.CANCELLED,
                total_duration_ms=int((time.time() - start_time) * 1000),
                trace_id=trace_id,
                error=str(e),
            )

        except OPCError as e:
            self.logger.error(
                f"OPC execution error: {e}",
                extra={"trace_id": trace_id},
            )
            return FinalResult(
                request=user_request,
                workflow=Workflow(),
                results={},
                status=WorkflowStatus.FAILED,
                total_duration_ms=int((time.time() - start_time) * 1000),
                trace_id=trace_id,
                error=str(e),
            )

        except Exception as e:
            self.logger.exception(
                f"Unexpected error during execution",
                extra={"trace_id": trace_id},
            )
            return FinalResult(
                request=user_request,
                workflow=Workflow(),
                results={},
                status=WorkflowStatus.FAILED,
                total_duration_ms=int((time.time() - start_time) * 1000),
                trace_id=trace_id,
                error=f"Unexpected error: {e}",
            )

    async def run_with_progress(
        self,
        user_request: str,
    ) -> AsyncIterator[ProgressEvent]:
        """Execute user request with progress streaming.

        Args:
            user_request: User's natural language request

        Yields:
            ProgressEvent objects for each significant event
        """
        trace_id = str(uuid.uuid4())
        start_time = time.time()

        yield ProgressEvent.workflow_start(
            workflow_id=trace_id,
            trace_id=trace_id,
        )

        try:
            # Phase 1: Get available agents
            available_agents = self._get_available_agents()
            agent_ids = available_agents  # Already a list of agent ID strings

            # Build context
            context = WorkflowContext(
                trace_id=trace_id,
                user_request=user_request,
                available_agents=available_agents,
            )

            # Phase 2: Generate workflow
            yield ProgressEvent(
                type=EventType.PROGRESS,
                status="generating",
                data={"message": "Generating workflow..."},
            )

            workflow: Workflow = await self.workflow_provider.generate(user_request, context)

            yield ProgressEvent(
                type=EventType.PROGRESS,
                status="generated",
                data={
                    "message": f"Generated workflow with {len(workflow.tasks)} tasks",
                    "task_count": len(workflow.tasks),
                },
            )

            # Phase 3: Validate
            validation_errors = validate_workflow(workflow, agent_ids, trace_id)
            if validation_errors:
                yield ProgressEvent.workflow_failed(
                    f"Validation failed: {'; '.join(validation_errors)}",
                    trace_id=trace_id,
                )
                return

            # Phase 4: Execute with progress
            execution_context: dict[str, Any] = {
                "trace_id": trace_id,
                "request": user_request,
                "progress_callback": lambda event: None,
            }

            results = await self._execute_with_timeout(
                workflow,
                execution_context,
            )

            # Phase 5: Final status
            final_status = self._determine_workflow_status(results)
            total_duration_ms = int((time.time() - start_time) * 1000)

            if final_status == WorkflowStatus.COMPLETED:
                # Extract content from results for response
                output_content = self._extract_content_from_results(results)
                yield ProgressEvent.workflow_complete(total_duration_ms, trace_id, output=output_content)
            else:
                yield ProgressEvent.workflow_failed(
                    f"Workflow ended with status: {final_status.value}",
                    trace_id=trace_id,
                )

        except Exception as e:
            yield ProgressEvent.workflow_failed(str(e), trace_id=trace_id)

    def _get_available_agents(self) -> list[str]:
        """Get list of available agent IDs from registry.

        Returns:
            List of agent IDs
        """
        if self.registry is None:
            return []

        if hasattr(self.registry, "list_all"):
            agents = self.registry.list_all()
            return [
                agent["agent_id"] if isinstance(agent, dict) else agent
                for agent in agents
            ]

        return []

    async def _execute_with_timeout(
        self,
        workflow: Workflow,
        context: dict[str, Any],
    ) -> dict[str, TaskResult]:
        """Execute workflow with timeout.

        Args:
            workflow: Workflow to execute
            context: Execution context

        Returns:
            Task results dictionary

        Raises:
            WorkflowTimeoutError: If execution times out
        """
        timeout = self.config.max_execution_time_secs

        try:
            return await asyncio.wait_for(
                self.execution_strategy.execute(
                    workflow.tasks,
                    self.agent_invoker,
                    context,
                ),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise WorkflowTimeoutError(timeout, context.get("trace_id", ""))

    def _determine_workflow_status(
        self,
        results: dict[str, TaskResult],
    ) -> WorkflowStatus:
        """Determine overall workflow status from task results.

        Args:
            results: Task results dictionary

        Returns:
            Overall workflow status
        """
        if not results:
            return WorkflowStatus.FAILED

        statuses = [r.status for r in results.values()]

        # If any task failed, workflow failed
        if TaskStatus.FAILED in statuses:
            return WorkflowStatus.FAILED

        # If all completed, workflow completed
        if all(s == TaskStatus.COMPLETED for s in statuses):
            return WorkflowStatus.COMPLETED

        # If any skipped, might still be completed
        if all(s in (TaskStatus.COMPLETED, TaskStatus.SKIPPED) for s in statuses):
            return WorkflowStatus.COMPLETED

        return WorkflowStatus.FAILED

    def _extract_content_from_results(self, results: dict[str, Any]) -> str:
        """Extract readable content from task results.

        Args:
            results: Task results dictionary

        Returns:
            Extracted content string
        """
        if not results:
            return ""

        def extract_from_output(output: Any) -> str:
            """Recursively extract content from output."""
            if not output:
                return ""
            if isinstance(output, str):
                return output
            if isinstance(output, dict):
                # Direct content
                if "content" in output:
                    content = output.get("content", "")
                    if content:
                        return str(content)
                # Nested result structure: {'success': ..., 'result': {'success': ..., 'content': ...}}
                if "result" in output:
                    result = output.get("result", {})
                    if isinstance(result, dict):
                        if "content" in result:
                            content = result.get("content", "")
                            if content:
                                return str(content)
                        # Double nested
                        if "result" in result:
                            inner = result.get("result", {})
                            if isinstance(inner, dict) and "content" in inner:
                                content = inner.get("content", "")
                                if content:
                                    return str(content)
                    elif isinstance(result, str) and result:
                        return result
            return ""

        contents = []
        for task_id, task_result in results.items():
            if hasattr(task_result, 'output') and task_result.output:
                output = task_result.output
                content = extract_from_output(output)
                if content:
                    contents.append(content)

        return "\n".join(contents) if contents else ""
