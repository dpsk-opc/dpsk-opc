"""Dispatch Task Tool for DPSK-OPC Agent.

This tool dispatches a task to a specific agent via the Message Bus.
This is the preferred way for agents to communicate with each other,
as it provides loose coupling and better scalability.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from typing import Any

from .base import BaseTool, ToolParameter

logger = logging.getLogger(__name__)

# Global bus reference (set during initialization)
_bus = None

# Global agent manager reference (set during initialization)
_agent_manager = None

# Global event emitter reference (set during initialization)
_event_emitter = None


def set_bus(bus: Any) -> None:
    """Set the global bus reference for dispatching tasks."""
    global _bus
    _bus = bus


def set_agent_manager(agent_manager: Any) -> None:
    """Set the global agent manager reference for on-demand agent spawning."""
    global _agent_manager
    _agent_manager = agent_manager


def set_event_emitter(emitter: Any) -> None:
    """Set the global event emitter for dispatch events."""
    global _event_emitter
    _event_emitter = emitter


async def _emit_dispatch_event(
    to_agent: str,
    task_id: str,
    task: str,
    success: bool = True,
    error: str | None = None,
) -> None:
    """Emit a dispatched event to notify about task dispatching.
    
    Args:
        to_agent: Target agent ID
        task_id: Task identifier
        task: Task description
        success: Whether dispatch succeeded
        error: Error message if failed
    """
    global _event_emitter
    if _event_emitter is None:
        return
    
    try:
        from src.bus.models import AgentEventType, AgentEventData
        
        event_type = AgentEventType.DISPATCHED if success else AgentEventType.TASK_FAILED
        
        # Generate more humanized dispatch message
        task_preview = task[:30] + "..." if len(task) > 30 else task
        if success:
            event_message = f"📤 正在联系 {to_agent}，转达任务：{task_preview}"
        else:
            event_message = f"💥 任务分发失败: {error or 'Unknown error'}"
        
        data = AgentEventData(
            agent_id="secretary",
            instance_id="secretary-unknown",
            task_id=task_id,
            event_message=event_message,
            react_step="act",
            tool_name="dispatch_task",
            tool_args={"to_agent": to_agent, "task": task},
            error=error,
            extra={
                "target_agent": to_agent,
                "task_id": task_id,
                "success": success,
                "task_preview": task_preview,
            },
        )
        
        await _event_emitter.emit(event_type, data)
    except Exception as e:
        logger.warning(f"Failed to emit dispatch event: {e}")


class DispatchTaskTool(BaseTool):
    """Tool for dispatching tasks to specific agents via Message Bus."""

    @property
    def name(self) -> str:
        return "dispatch_task"

    @property
    def description(self) -> str:
        return "分发任务给指定的 Agent。当秘书需要协调其他专业 Agent 完成具体工作时使用此工具。消息会通过 Message Bus 异步传递。"

    @property
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["task_dispatch", "coordination"]

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
        import time
        
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

        # Generate task ID for tracking
        task_id = str(uuid.uuid4())
        
        try:
            # Get bus instance
            bus = _bus

            if bus is None:
                return {
                    "success": False,
                    "error": "Message Bus not available. Cannot dispatch task.",
                    "result": None,
                }

            # Import bus models
            from src.bus.models import Target, TargetType, TaskRequest, MessageType

            # Debug: log detailed info about target agent
            logger.info(f"[DISPATCH] to_agent='{to_agent}' (type={type(to_agent).__name__}, length={len(to_agent) if to_agent else 0})")
            logger.info(f"[DISPATCH] to_agent repr: {repr(to_agent)}")

            # Check if target agent is registered in bus, if not, spawn it on-demand
            registered_agents = list(getattr(bus, '_agent_handlers', {}).keys())
            logger.info(f"[DISPATCH] Currently registered agents in bus: {registered_agents}")

            if to_agent not in registered_agents:
                logger.info(f"[DISPATCH] Agent '{to_agent}' not registered, attempting to spawn on-demand...")
                # Use agent_manager to spawn the agent
                agent_manager = _agent_manager
                if agent_manager is None:
                    logger.error("[DISPATCH] Agent manager not initialized")
                    return {
                        "success": False,
                        "error": f"Agent '{to_agent}' is not running and agent manager is not available. Cannot dispatch task.",
                        "result": None,
                    }

                logger.info(f"[DISPATCH] Spawning agent '{to_agent}' on-demand via agent_manager...")
                try:
                    await agent_manager.spawn_agent(to_agent)
                    logger.info(f"[DISPATCH] Successfully spawned agent '{to_agent}'")
                    # Give the agent a moment to register
                    await asyncio.sleep(0.5)
                except ValueError as e:
                    logger.error(f"[DISPATCH] Agent definition not found for '{to_agent}': {e}")
                    return {
                        "success": False,
                        "error": f"Agent definition for '{to_agent}' not found: {str(e)}",
                        "result": None,
                    }
                except Exception as e:
                    logger.error(f"[DISPATCH] Failed to spawn agent '{to_agent}': {e}")
                    return {
                        "success": False,
                        "error": f"Failed to spawn agent '{to_agent}': {str(e)}",
                        "result": None,
                    }
            else:
                logger.info(f"[DISPATCH] Agent '{to_agent}' is already registered")

            # Emit dispatched event BEFORE sending the request
            await _emit_dispatch_event(
                to_agent=to_agent,
                task_id=task_id,
                task=task,
                success=True,
            )

            # Create target
            target = Target(type=TargetType.AGENT, value=to_agent)
            logger.info(f"[DISPATCH] Target created: type={target.type}, value='{target.value}'")

            # Create task request
            # Note: source is the calling agent, target is the destination agent
            task_request = TaskRequest(
                msg_type=MessageType.TASK_REQUEST,
                source="secretary",  # The caller agent (this could be dynamic)
                target=target,
                task_name="dispatch",
                task_data={
                    "prompt": task,
                    "task_type": "dispatched",
                },
            )

            # Send request via bus and wait for response
            logger.info(f"[DISPATCH] Sending request to bus for agent: '{to_agent}'")
            dispatch_start = time.time()
            response = await bus.request(target, task_request, timeout=timeout)
            dispatch_duration_ms = int((time.time() - dispatch_start) * 1000)

            logger.info(f"[DISPATCH] Received response from {to_agent}, duration={dispatch_duration_ms}ms")

            # Extract result from response
            result_data = response.result.get("content") if response.result else None
            error = response.result.get("error") if response.result else None

            # Emit sub-task result event with humanized message
            from src.bus.models import AgentEventType, AgentEventData
            if response.success:
                result_preview = str(result_data)[:50] + "..." if len(str(result_data)) > 50 else str(result_data)
                event_message = f"🤝 {to_agent}：任务已完成，结果：{result_preview}"
            else:
                event_message = f"😔 {to_agent}：任务执行失败 - {error or '未知错误'}"
            
            # Use global event emitter
            if _event_emitter is not None:
                event_data = AgentEventData(
                    agent_id="secretary",
                    instance_id="secretary-unknown",
                    task_id=task_id,
                    event_message=event_message,
                    extra={
                        "target_agent": to_agent,
                        "success": response.success,
                        "result_data": result_data,
                        "error": error,
                        "duration_ms": dispatch_duration_ms,
                    },
                )
                await _event_emitter.emit(AgentEventType.SUB_TASK_RESULT, event_data)

            if response.success:
                # Ensure result is not None - if no data, use a completion indicator
                task_result = result_data if result_data is not None else f"任务已在 {to_agent} 完成"
                return {
                    "success": True,
                    "agent_id": to_agent,
                    "task": task,
                    "result": task_result,
                    "duration_ms": dispatch_duration_ms,
                    "should_finish":True
                }
            else:
                return {
                    "success": False,
                    "agent_id": to_agent,
                    "task": task,
                    "error": error or "Task execution failed",
                    "result": result_data,
                    "duration_ms": dispatch_duration_ms
                }

        except asyncio.TimeoutError:
            logger.error(f"[DISPATCH] Task dispatch timeout for agent: {to_agent}")
            # Emit failure event
            await _emit_dispatch_event(
                to_agent=to_agent,
                task_id=task_id,
                task=task,
                success=False,
                error=f"Task execution timeout for agent: {to_agent}",
            )
            return {
                "success": False,
                "error": f"Task execution timeout for agent: {to_agent} (timeout={timeout}s)",
                "result": None,
            }
        except Exception as e:
            logger.exception(f"[DISPATCH] Failed to dispatch task to {to_agent}: {e}")
            # Emit failure event
            await _emit_dispatch_event(
                to_agent=to_agent,
                task_id=task_id,
                task=task,
                success=False,
                error=str(e),
            )
            return {
                "success": False,
                "error": f"Failed to dispatch task: {str(e)}",
                "result": None,
            }


# Tool instance
dispatch_task_tool = DispatchTaskTool()
