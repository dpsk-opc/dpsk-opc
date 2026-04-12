"""Local Agent Invoker.

This module provides an agent invoker that executes tasks locally.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import TYPE_CHECKING, Any, Dict, Optional

if TYPE_CHECKING:
    from src.opc.config import OPCConfig
    from src.opc.models.result import TaskResult

logger = logging.getLogger(__name__)


class LocalAgentInvoker:
    """Agent invoker that executes tasks via local agent manager.

    This invoker directly calls the AgentManager to execute tasks
    on local agent instances.
    """

    def __init__(
        self,
        agent_manager: Any,
        security_module: Optional[Any] = None,
        config: Optional["OPCConfig"] = None,
    ) -> None:
        """Initialize the local agent invoker.

        Args:
            agent_manager: Agent manager instance for getting agent handles
            security_module: Optional security module for validation
            config: OPC configuration
        """
        self.agent_manager = agent_manager
        self.security_module = security_module
        self.config = config
        
        # Handle both dict and Pydantic model config
        if config is None:
            self._timeout = 300
        elif hasattr(config, "default_task_timeout_secs"):
            # Pydantic model
            self._timeout = config.default_task_timeout_secs
        elif isinstance(config, dict):
            # Dictionary
            self._timeout = config.get("default_task_timeout_secs", 300)
        else:
            self._timeout = 300

    async def invoke(
        self,
        agent_id: str,
        task: str,
        context: Dict[str, Any],
    ) -> "TaskResult":
        """Invoke an agent to execute a task locally.

        Args:
            agent_id: Target agent identifier
            task: Task description/instruction
            context: Execution context

        Returns:
            TaskResult containing execution outcome
        """
        from src.opc.models.result import TaskResult
        from src.opc.models.workflow import TaskStatus
        from src.opc.errors import SecurityValidationError, TaskExecutionError

        start_time = time.time()
        trace_id = context.get("trace_id", "")

        # Security validation
        if self.security_module:
            try:
                await self._validate_security(agent_id, task, context, trace_id)
            except SecurityValidationError as e:
                logger.warning(
                    f"Security validation failed for agent {agent_id}",
                    extra={"trace_id": trace_id},
                )
                return TaskResult(
                    task_id=context.get("task_id", ""),
                    status=TaskStatus.FAILED,
                    error=str(e),
                    duration_ms=int((time.time() - start_time) * 1000),
                )

        # Get agent handle
        try:
            handle = await self._get_agent_handle(agent_id, trace_id)
        except Exception as e:
            logger.error(
                f"Failed to get agent handle for {agent_id}: {e}",
                extra={"trace_id": trace_id},
            )
            return TaskResult(
                task_id=context.get("task_id", ""),
                status=TaskStatus.FAILED,
                error=f"Failed to get agent handle: {e}",
                duration_ms=int((time.time() - start_time) * 1000),
            )

        # Execute task with timeout
        try:
            result_data = await asyncio.wait_for(
                self._execute_task(handle, task, context, trace_id),
                timeout=self._timeout,
            )

            logger.info(
                f"Task completed successfully for agent {agent_id}",
                extra={
                    "trace_id": trace_id,
                    "task_id": context.get("task_id"),
                    "duration_ms": int((time.time() - start_time) * 1000),
                },
            )

            return TaskResult(
                task_id=context.get("task_id", ""),
                status=TaskStatus.COMPLETED,
                output=result_data,
                duration_ms=int((time.time() - start_time) * 1000),
            )

        except asyncio.TimeoutError:
            logger.warning(
                f"Task timed out for agent {agent_id}",
                extra={
                    "trace_id": trace_id,
                    "task_id": context.get("task_id"),
                    "timeout_secs": self._timeout,
                },
            )
            return TaskResult(
                task_id=context.get("task_id", ""),
                status=TaskStatus.FAILED,
                error=f"Task timed out after {self._timeout} seconds",
                duration_ms=int((time.time() - start_time) * 1000),
            )

        except Exception as e:
            logger.exception(
                f"Task execution failed for agent {agent_id}",
                extra={
                    "trace_id": trace_id,
                    "task_id": context.get("task_id"),
                },
            )
            return TaskResult(
                task_id=context.get("task_id", ""),
                status=TaskStatus.FAILED,
                error=str(e),
                duration_ms=int((time.time() - start_time) * 1000),
            )

    async def _validate_security(
        self,
        agent_id: str,
        task: str,
        context: Dict[str, Any],
        trace_id: str,
    ) -> None:
        """Validate task invocation with security module.

        Args:
            agent_id: Target agent ID
            task: Task description
            context: Execution context
            trace_id: Trace ID

        Raises:
            SecurityValidationError: If validation fails
        """
        from src.opc.errors import SecurityValidationError

        if self.security_module is None:
            return

        # Call security module validation
        # The security module should implement validate_invocation
        if hasattr(self.security_module, "validate_invocation"):
            result = await self.security_module.validate_invocation(
                agent_id=agent_id,
                task=task,
                context=context,
                trace_id=trace_id,
            )
            if not result.get("allowed", True):
                reason = result.get("reason", "Security validation failed")
                raise SecurityValidationError(
                    task_id=context.get("task_id", ""),
                    reason=reason,
                    trace_id=trace_id,
                )

    async def _get_agent_handle(self, agent_id: str, trace_id: str) -> Any:
        """Get an agent handle for invocation.

        Args:
            agent_id: Agent identifier
            trace_id: Trace ID

        Returns:
            AgentHandle object

        Raises:
            ValueError: If agent not found
        """
        # Always spawn a new instance for task execution
        try:
            handle = await self.agent_manager.spawn_agent(
                agent_id,
                task_context={"trace_id": trace_id},
            )
            return handle
        except Exception as e:
            raise ValueError(f"Failed to spawn agent {agent_id}: {e}")

    async def _execute_task(
        self,
        handle: Any,
        task: str,
        context: Dict[str, Any],
        trace_id: str,
    ) -> Dict[str, Any]:
        """Execute task on agent handle.

        Args:
            handle: Agent handle
            task: Task description
            context: Execution context
            trace_id: Trace ID

        Returns:
            Task execution result
        """
        # Extract task data from context
        task_data = context.get("task_data", {})
        task_data["instruction"] = task

        # Include previous results in context
        if "previous_results" in context:
            task_data["context"] = context["previous_results"]

        # Send task to agent
        result = await handle.send_task(
            task_name="execute",
            task_data=task_data,
            timeout=float(self._timeout),
        )

        return result
