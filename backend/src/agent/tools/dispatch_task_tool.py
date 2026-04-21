"""Dispatch Task Tool for DPSK-OPC Agent.

This tool dispatches a task to a specific agent via the Message Bus.
This is the preferred way for agents to communicate with each other,
as it provides loose coupling and better scalability.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from .base import BaseTool, ToolParameter

logger = logging.getLogger(__name__)

# Global bus reference (set during initialization)
_bus = None


def set_bus(bus: Any) -> None:
    """Set the global bus reference for dispatching tasks."""
    global _bus
    _bus = bus


class DispatchTaskTool(BaseTool):
    """Tool for dispatching tasks to specific agents via Message Bus."""

    @property
    def name(self) -> str:
        return "dispatch_task"

    @property
    def description(self) -> str:
        return "分发任务给指定的 Agent。当秘书需要协调其他专业 Agent 完成具体工作时使用此工具。消息会通过 Message Bus 异步传递。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="to_agent",
                param_type="string",
                description="目标 Agent 的 ID，如 '行政助理', '邮件助手', '文档助手'",
                required=True,
            ),
            ToolParameter(
                name="task",
                param_type="string",
                description="要分发给目标 Agent 的任务描述，应该是清晰、完整的指令",
                required=True,
            ),
            ToolParameter(
                name="timeout",
                param_type="number",
                description="等待结果的超时时间（秒），默认 600 秒（10分钟）",
                required=False,
                default=600.0,
            ),
        ]

    async def execute(self, to_agent: str, task: str, timeout: float = 600.0, **kwargs: Any) -> dict[str, Any]:
        """Dispatch a task to a specific agent via Message Bus."""
        if not to_agent:
            return {
                "success": False,
                "error": "Missing required parameter: to_agent",
                "result": None,
            }

        if not task:
            return {
                "success": False,
                "error": "Missing required parameter: task",
                "result": None,
            }

        logger.info(f"[DISPATCH] Dispatching task to agent: {to_agent}")
        logger.info(f"[DISPATCH] Task: {task[:100]}...")

        try:
            # Get bus instance
            bus = _bus

            if bus is None:
                # Try to get bus from spawner
                try:
                    from src.main import agent_manager
                    if hasattr(agent_manager, 'spawner'):
                        spawner = agent_manager.spawner
                        if hasattr(spawner, '_bus'):
                            bus = spawner._bus
                except (ImportError, AttributeError):
                    pass

            if bus is None:
                return {
                    "success": False,
                    "error": "Message Bus not available. Cannot dispatch task.",
                    "result": None,
                }

            # Import bus models
            from src.bus.models import Target, TargetType, TaskRequest, MessageType

            # Create target
            target = Target(type=TargetType.AGENT, value=to_agent)

            # Create task request
            task_request = TaskRequest(
                task_name="dispatch",
                task_data={
                    "prompt": task,
                    "task_type": "dispatched",
                    "source": "secretary",  # The caller (secretary agent)
                },
            )

            # Send request via bus and wait for response
            logger.info(f"[DISPATCH] Sending request to bus for agent: {to_agent}")
            response = await bus.request(target, task_request, timeout=timeout)

            logger.info(f"[DISPATCH] Received response from {to_agent}")

            # Extract result from response
            result_data = response.result.get("data") if response.result else None
            error = response.result.get("error") if response.result else None

            if response.success:
                return {
                    "success": True,
                    "agent_id": to_agent,
                    "task": task,
                    "result": result_data,
                }
            else:
                return {
                    "success": False,
                    "agent_id": to_agent,
                    "task": task,
                    "error": error or "Task execution failed",
                    "result": result_data,
                }

        except asyncio.TimeoutError:
            logger.error(f"[DISPATCH] Task dispatch timeout for agent: {to_agent}")
            return {
                "success": False,
                "error": f"Task execution timeout for agent: {to_agent} (timeout={timeout}s)",
                "result": None,
            }
        except Exception as e:
            logger.exception(f"[DISPATCH] Failed to dispatch task to {to_agent}: {e}")
            return {
                "success": False,
                "error": f"Failed to dispatch task: {str(e)}",
                "result": None,
            }


# Tool instance
dispatch_task_tool = DispatchTaskTool()
