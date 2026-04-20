"""Agent Runner for DPSK-OPC.

This module provides the runtime framework for executing Agent tasks using LangGraph.
It runs inside the sandbox and connects to the message bus.

Pure Tool Calling Architecture:
- LLM decides which tool to use based on available tools
- ReAct loop: think → act → observe → think → ... → finish
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import time
from pathlib import Path
from typing import Any, Optional, TypedDict

logger = logging.getLogger(__name__)

# Global LLM client (injected at runtime)
_llm_client: Optional[Any] = None

# Global event emitter for observability
_event_emitter: Optional[Any] = None

# Configuration from environment
AGENT_ID = os.environ.get("AGENT_ID", "unknown")
MESSAGE_BUS_ADDRESS = os.environ.get("MESSAGE_BUS_ADDRESS", "memory")
TEMP_TOKEN = os.environ.get("TEMP_TOKEN", "")


class AgentState(TypedDict, total=False):
    """State for the Agent workflow graph."""

    # Input fields
    task_name: str
    task_data: dict[str, Any]
    agent_info: Optional[dict[str, Any]]
    experience_db: Optional[Any]

    # Messages for LLM conversation
    messages: list[dict[str, Any]]

    # Tool Calling state (ReAct loop)
    tool_calls: list[dict[str, Any]]
    tool_results: list[dict[str, Any]]
    react_step: str  # "think", "act", "observe", "finish"
    iterations: int
    max_iterations: int

    # Execution metadata
    llm_generated: bool

    # Output fields
    result: dict[str, Any]
    error: Optional[str]
    duration_ms: int
    start_time: float


def set_llm_client(client: Any) -> None:
    """Set the global LLM client.

    Args:
        client: LLM client instance
    """
    global _llm_client
    _llm_client = client
    logger.info(f"LLM client set: {client.__class__.__name__}")


def get_llm_client() -> Optional[Any]:
    """Get the global LLM client."""
    return _llm_client


def set_event_emitter(emitter: Any) -> None:
    """Set the global event emitter for observability.

    Args:
        emitter: Event emitter instance (AgentEventEmitter)
    """
    global _event_emitter
    _event_emitter = emitter
    logger.info(f"Event emitter configured: {emitter.__class__.__name__}")


def get_event_emitter() -> Optional[Any]:
    """Get the global event emitter."""
    return _event_emitter


async def _emit_event(
    event_type: str,
    agent_id: str,
    instance_id: str,
    message: str,
    task_id: str | None = None,
    correlation_id: str | None = None,
    react_step: str | None = None,
    react_iteration: int = 0,
    tool_name: str | None = None,
    tool_args: dict | None = None,
    tool_result: dict | None = None,
    duration_ms: int = 0,
    error: str | None = None,
    llm_model: str | None = None,
) -> None:
    """Emit an agent processing event.

    This is a helper function that wraps the global event emitter.

    Args:
        event_type: AgentEventType value
        agent_id: Agent identifier
        instance_id: Agent instance identifier
        message: Human-readable message
        task_id: Optional task identifier for tracing
        correlation_id: Optional correlation ID for request tracking
        react_step: Current ReAct step (think/act/observe/finish)
        react_iteration: Current iteration count
        tool_name: Tool being executed (if applicable)
        tool_args: Tool arguments (if applicable)
        tool_result: Tool result (if applicable)
        duration_ms: Duration of this step in milliseconds
        error: Error message if failed
    """
    emitter = get_event_emitter()
    if emitter is None:
        return

    try:
        from src.bus.models import AgentEventData, AgentEventType

        # Convert string to enum if needed
        if isinstance(event_type, str):
            try:
                event_type_enum = AgentEventType(event_type)
            except ValueError:
                # Use string directly if not a valid enum
                event_type_enum = event_type

        data = AgentEventData(
            agent_id=agent_id,
            instance_id=instance_id,
            task_id=task_id,
            correlation_id=correlation_id,
            event_message=message,
            react_step=react_step,
            react_iteration=react_iteration,
            tool_name=tool_name,
            tool_args=tool_args,
            tool_result=tool_result,
            llm_model=llm_model,
            duration_ms=duration_ms,
            error=error,
        )

        await emitter.emit(event_type_enum, data)
    except Exception as e:
        logger.warning(f"Failed to emit event {event_type}: {e}")


# =============================================================================
# Tool Management
# =============================================================================

def _register_builtin_tools() -> None:
    """Register built-in tools on startup."""
    logger.info("[INIT] Registering built-in tools...")
    try:
        from .tools.registry import registry
        from .tools.scan_org_chart import scan_org_chart_tool
        from .tools.list_agents_tool import list_agents_tool
        from .tools.dispatch_task_tool import dispatch_task_tool
        from .tools.file_tools import FILE_TOOLS
        from .tools.meeting_room_tool import meeting_room_tool

        # Register scan_org_chart tool
        registry.register(scan_org_chart_tool)
        logger.info("[INIT] Registered: scan_org_chart")

        # Register list_agents tool
        registry.register(list_agents_tool)
        logger.info("[INIT] Registered: list_agents")

        # Register dispatch_task tool
        registry.register(dispatch_task_tool)
        logger.info("[INIT] Registered: dispatch_task")

        # Register file operation tools
        for tool in FILE_TOOLS:
            registry.register(tool)
            logger.info(f"[INIT] Registered: {tool.name}")

        # Register meeting_room tool
        registry.register(meeting_room_tool)
        logger.info("[INIT] Registered: meeting_room")

        logger.info(f"[INIT] Total tools registered: {len(registry.get_all())}")
    except Exception as e:
        logger.error(f"[INIT] Failed to register built-in tools: {e}")
        import traceback
        logger.error(f"[INIT] Traceback:\n{traceback.format_exc()}")


def get_tools_for_llm() -> list[dict[str, Any]]:
    """Get all registered tools in OpenAI format for LLM."""
    try:
        from .tools.registry import registry
        tools = registry.get_tools_for_llm()
        tool_names = [t.get("function", {}).get("name") for t in tools]
        logger.info(f"[TOOLS] Found {len(tools)} tools: {tool_names}")
        return tools
    except Exception as e:
        logger.error(f"[TOOLS] Failed to get tools: {e}")
        return []


# =============================================================================
# LangGraph Nodes (ReAct Loop)
# =============================================================================

async def node_think(state: AgentState) -> AgentState:
    """Think node: LLM decides what to do (call tool or respond directly)."""
    from .graph.nodes import DebugConfig
    from src.bus.models import AgentEventType

    client = get_llm_client()
    agent_info = state.get("agent_info") or {}
    task_data = state.get("task_data", {})
    iterations = state.get("iterations", 0)
    messages = state.get("messages", [])

    # Get agent identifiers
    agent_id = agent_info.get("agent_id", AGENT_ID)
    instance_id = f"{agent_id}-unknown"  # Will be set by spawner

    if DebugConfig.REACT_VERBOSE:
        logger.info(f"[REACT] ═══════════════════════════════════════")
        logger.info(f"[REACT] ITERATION {iterations}")
        logger.info(f"[REACT] ═══════════════════════════════════════")

    # Emit think start event
    await _emit_event(
        event_type=AgentEventType.REACT_THINK_START,
        agent_id=agent_id,
        instance_id=instance_id,
        message="正在思考...",
        react_step="think",
        react_iteration=iterations,
    )

    if client is None:
        logger.error("[THINK] LLM client not available")
        state["error"] = "LLM client not initialized"
        state["react_step"] = "finish"
        await _emit_event(
            event_type=AgentEventType.TASK_FAILED,
            agent_id=agent_id,
            instance_id=instance_id,
            message="LLM 客户端未初始化",
            error="LLM client not initialized",
        )
        return state

    # Get system prompt and tools
    system_prompt = agent_info.get("description", "") or ""
    tools = get_tools_for_llm()

    # Build messages for LLM
    # In first iteration, create user message from task_data
    # In subsequent iterations, use existing messages from state
    if iterations == 0:
        user_prompt = ""
        if isinstance(task_data, dict):
            user_prompt = task_data.get("instruction", "") or task_data.get("input", "") or str(task_data)
        else:
            user_prompt = str(task_data)

        messages = [
            {"role": "user", "content": user_prompt},
        ]
        logger.info(f"[REACT] [THINK] iter=0, created new messages from task_data")
    else:
        messages = state.get("messages", [])
        logger.info(f"[REACT] [THINK] iter={iterations}, using existing messages: {len(messages)}")

    # Log messages before sending to LLM
    for i, msg in enumerate(messages):
        role = msg.get("role", "?")
        has_tc = "tool_calls" in msg
        has_tcid = "tool_call_id" in msg
        tc_names = []
        if "tool_calls" in msg:
            for tc in msg.get("tool_calls", []):
                tc_names.append(tc.get("function", {}).get("name", "?"))
        logger.info(f"[REACT] [THINK]   msg[{i}]: role={role}, has_tool_calls={has_tc}, tool_names={tc_names}")

    # Build system prompt with tools
    system_content = _build_system_prompt(system_prompt, tools)
    chat_messages = [{"role": "system", "content": system_content}] + messages

    # Call LLM
    try:
        # Check if we have tool messages in history (second call onwards)
        has_tool_messages = any(msg.get("role") == "tool" for msg in chat_messages)

        # If we already have tool results, don't pass tools parameter
        # LLM should respond to the tool results
        should_pass_tools = bool(tools) and not has_tool_messages

        logger.info(f"[REACT] [THINK] iter={iterations}, {len(chat_messages)} messages, tools={len(tools)}, has_tool_msgs={has_tool_messages}, passing_tools={should_pass_tools}")

        # Log full message sequence with details
        for i, msg in enumerate(chat_messages):
            role = msg.get("role", "?")
            has_tc = "tool_calls" in msg
            has_tcid = "tool_call_id" in msg
            tc_id = msg.get("tool_call_id", "N/A")
            content_preview = str(msg.get("content", ""))[:30]
            # Get tool_call id if present
            tc_ids = []
            if "tool_calls" in msg:
                for tc in msg.get("tool_calls", []):
                    tc_ids.append(tc.get("id", "?"))
            logger.info(f"[REACT] [THINK]   [{i}] role={role}, has_tool_calls={has_tc}, has_tool_id={has_tcid}, tool_call_ids={tc_ids}, content={content_preview}...")

        response = await client.chat(messages=chat_messages, tools=tools if should_pass_tools else None)

        assistant_message = response.get("message", {})
        content = assistant_message.get("content", "")
        tool_calls = assistant_message.get("tool_calls", [])

        # Emit LLM request end event
        await _emit_event(
            event_type=AgentEventType.REACT_THINK_END,
            agent_id=agent_id,
            instance_id=instance_id,
            message="思考完成" if not tool_calls else f"决定调用 {len(tool_calls)} 个工具",
            react_step="think",
            react_iteration=iterations,
            llm_model=getattr(client, 'model', None),
        )

        if DebugConfig.REACT_VERBOSE:
            logger.info(f"[REACT] [THINK] LLM response: has_tool_calls={bool(tool_calls)}, content_len={len(content) if content else 0}")

        if DebugConfig.REACT_VERBOSE:
            logger.info(f"[REACT] [THINK] LLM response: has_tool_calls={bool(tool_calls)}, content_len={len(content) if content else 0}")

        if DebugConfig.REACT_VERBOSE:
            if content:
                logger.info(f"[REACT] [THINK] LLM text: {content[:100]}...")
            logger.info(f"[REACT] [THINK] Tool calls: {len(tool_calls)}")

        if tool_calls:
            # LLM wants to call tools
            state["tool_calls"] = tool_calls
            state["react_step"] = "act"
            # Ensure assistant_message has tool_calls field
            # This is critical for message sequence validation in node_act
            if "tool_calls" not in assistant_message:
                assistant_message["tool_calls"] = tool_calls
            # Update messages: append assistant response with tool_calls
            state["messages"] = messages + [assistant_message]
            for tc in tool_calls:
                func = tc.get("function", {})
                tc_id = tc.get("id", "MISSING_ID")
                tool_name = func.get("name", "?")
                logger.info(f"[REACT] [THINK] → Will call: {tool_name}, id={tc_id}")
                # Emit tool call start event
                tool_args = {}
                try:
                    tool_args = json.loads(func.get("arguments", "{}")) if isinstance(func.get("arguments"), str) else func.get("arguments", {})
                except json.JSONDecodeError:
                    pass
                await _emit_event(
                    event_type=AgentEventType.TOOL_CALL_START,
                    agent_id=agent_id,
                    instance_id=instance_id,
                    message=f"准备调用工具: {tool_name}",
                    react_step="act",
                    react_iteration=iterations,
                    tool_name=tool_name,
                    tool_args=tool_args,
                )
        else:
            # Direct response
            state["result"] = {
                "success": True,
                "content": content,
                "type": "text",
            }
            state["llm_generated"] = True
            state["react_step"] = "finish"
            # Emit task completed event for direct responses
            await _emit_event(
                event_type=AgentEventType.TASK_COMPLETED,
                agent_id=agent_id,
                instance_id=instance_id,
                message="任务完成（直接回复）",
                react_step="finish",
            )
            # Still update messages for history
            state["messages"] = messages + [assistant_message]
            if DebugConfig.REACT_VERBOSE:
                logger.info("[REACT] [THINK] → Direct response (finish)")

    except Exception as e:
        logger.exception(f"[THINK] LLM call failed: {e}")
        state["error"] = str(e)
        state["react_step"] = "finish"

    return state


async def node_act(state: AgentState) -> AgentState:
    """Act node: Execute tools requested by LLM."""
    from .graph.nodes import DebugConfig
    from src.bus.models import AgentEventType

    # Get agent identifiers
    agent_info = state.get("agent_info") or {}
    agent_id = agent_info.get("agent_id", AGENT_ID)
    instance_id = f"{agent_id}-unknown"
    iterations = state.get("iterations", 0)

    # DEBUG: Check incoming messages
    existing_messages = state.get("messages", [])
    logger.info(f"[REACT] [ACT] Entering node_act, messages count: {len(existing_messages)}")
    if existing_messages:
        logger.info(f"[REACT] [ACT] Last message role: {existing_messages[-1].get('role')}")
        logger.info(f"[REACT] [ACT] Last message has_tool_calls: {'tool_calls' in existing_messages[-1]}")

    # Emit act start event
    await _emit_event(
        event_type=AgentEventType.REACT_ACT_START,
        agent_id=agent_id,
        instance_id=instance_id,
        message="开始执行工具...",
        react_step="act",
        react_iteration=iterations,
    )

    tool_calls = state.get("tool_calls", [])

    if not tool_calls:
        logger.warning("[ACT] No tool calls to execute")
        state["react_step"] = "finish"
        return state

    if DebugConfig.REACT_VERBOSE:
        logger.info(f"[REACT] [ACT] Executing {len(tool_calls)} tool(s)")

    try:
        from .tools.registry import registry

        results = []
        for i, tool_call in enumerate(tool_calls):
            func = tool_call.get("function", {})
            tool_name = func.get("name", "unknown")
            arguments_str = func.get("arguments", "{}")

            if DebugConfig.REACT_VERBOSE:
                logger.info(f"[REACT] [ACT] Tool {i+1}: {tool_name}")
                logger.info(f"[REACT] [ACT]   Args: {arguments_str[:200]}...")

            # Parse arguments for event
            tool_args = {}
            try:
                tool_args = json.loads(arguments_str) if isinstance(arguments_str, str) else arguments_str
            except json.JSONDecodeError:
                pass

            # Emit tool call start event
            await _emit_event(
                event_type=AgentEventType.TOOL_CALL_START,
                agent_id=agent_id,
                instance_id=instance_id,
                message=f"正在调用工具: {tool_name}",
                react_step="act",
                react_iteration=iterations,
                tool_name=tool_name,
                tool_args=tool_args,
            )

            tool_start_time = time.time()

            # Get tool
            tool = registry.get(tool_name)
            if not tool:
                result = {"success": False, "error": f"Tool '{tool_name}' not found", "tool_name": tool_name}
            else:
                # Parse arguments
                try:
                    arguments = tool_args
                except json.JSONDecodeError:
                    result = {"success": False, "error": f"Invalid JSON: {arguments_str}", "tool_name": tool_name}
                else:
                    # Execute
                    try:
                        result = await tool.run(arguments)
                        result["tool_name"] = tool_name
                    except Exception as e:
                        result = {"success": False, "error": str(e), "tool_name": tool_name}

            # Calculate duration
            duration_ms = int((time.time() - tool_start_time) * 1000)

            # Emit tool call end event
            await _emit_event(
                event_type=AgentEventType.TOOL_CALL_END,
                agent_id=agent_id,
                instance_id=instance_id,
                message=f"工具 {tool_name} 执行完成",
                react_step="act",
                react_iteration=iterations,
                tool_name=tool_name,
                tool_result=result,
                duration_ms=duration_ms,
            )

            results.append(result)

            if DebugConfig.REACT_VERBOSE:
                success = result.get("success", False)
                logger.info(f"[REACT] [ACT]   Result: success={success}")
                if success and "chart" in result:
                    logger.info(f"[REACT] [ACT]   Chart preview:\n{result.get('chart', '')[:200]}...")

        # Emit act end event
        await _emit_event(
            event_type=AgentEventType.REACT_ACT_END,
            agent_id=agent_id,
            instance_id=instance_id,
            message=f"已完成 {len(tool_calls)} 个工具调用",
            react_step="act",
            react_iteration=iterations,
        )

        # Add tool results as messages
        # IMPORTANT: DeepSeek requires tool_call_id to match the assistant's tool_calls
        tool_result_messages = []
        for i, (tool_call, result) in enumerate(zip(tool_calls, results)):
            tool_call_id = tool_call.get("id", "")
            tool_result_messages.append({
                "role": "tool",
                "tool_call_id": tool_call_id,
                "content": json.dumps(result, ensure_ascii=False),
            })
            logger.info(f"[REACT] [ACT] Tool {i+1} result: name={tool_call.get('function', {}).get('name')}, tool_call_id={tool_call_id}")

        # Append tool messages to existing messages
        # After this, messages should be: [user, assistant(with tool_calls), tool, ...]
        existing_messages = state.get("messages", [])

        # Validate: the last message before tool should be assistant with tool_calls
        if existing_messages:
            last_msg = existing_messages[-1]
            if last_msg.get("role") != "assistant" or "tool_calls" not in last_msg:
                logger.error(f"[REACT] [ACT] ERROR: Last message before tool is not assistant with tool_calls!")
                logger.error(f"[REACT] [ACT] Last message: role={last_msg.get('role')}, has_tool_calls={'tool_calls' in last_msg}")
                # Don't append tool messages if sequence is invalid
                state["error"] = "Invalid message sequence: tool message without preceding assistant tool_calls"
                state["react_step"] = "finish"
                return state

        state["messages"] = existing_messages + tool_result_messages
        state["tool_results"] = results
        state["react_step"] = "observe"

        logger.info(f"[REACT] [ACT] → Executed {len(results)} tool(s), messages count: {len(state['messages'])}")

        # Log the message sequence after appending tool results
        for i, msg in enumerate(state["messages"]):
            role = msg.get("role", "?")
            has_tc = "tool_calls" in msg
            has_tcid = "tool_call_id" in msg
            logger.info(f"[REACT] [ACT]   state.msg[{i}]: role={role}, has_tool_calls={has_tc}, has_tool_id={has_tcid}")

    except Exception as e:
        logger.exception(f"[ACT] Tool execution failed: {e}")
        state["error"] = str(e)
        state["react_step"] = "finish"

    return state


async def node_observe(state: AgentState) -> AgentState:
    """Observe node: Process results and decide next step."""
    from .graph.nodes import DebugConfig
    from src.bus.models import AgentEventType

    # Get agent identifiers
    agent_info = state.get("agent_info") or {}
    agent_id = agent_info.get("agent_id", AGENT_ID)
    instance_id = f"{agent_id}-unknown"

    iterations = state.get("iterations", 0) + 1
    max_iterations = state.get("max_iterations", 10)

    state["iterations"] = iterations
    state["react_step"] = "think"

    # Emit observe end event
    await _emit_event(
        event_type=AgentEventType.REACT_OBSERVE_END,
        agent_id=agent_id,
        instance_id=instance_id,
        message=f"已收到工具结果，继续思考...",
        react_step="observe",
        react_iteration=iterations - 1,  # Report previous iteration
    )

    if DebugConfig.REACT_VERBOSE:
        tool_results = state.get("tool_results", [])
        for i, result in enumerate(tool_results):
            logger.info(f"[REACT] [OBSERVE] Tool {i+1}: success={result.get('success')}")

    # Check iteration limit
    if iterations >= max_iterations:
        logger.warning(f"[REACT] [OBSERVE] Max iterations ({max_iterations}) reached")
        state["react_step"] = "finish"
        state["error"] = f"Maximum iterations ({max_iterations}) reached"
        await _emit_event(
            event_type=AgentEventType.TASK_FAILED,
            agent_id=agent_id,
            instance_id=instance_id,
            message=f"达到最大迭代次数 ({max_iterations})",
            error=f"Maximum iterations ({max_iterations}) reached",
        )
        return state

    if DebugConfig.REACT_VERBOSE:
        logger.info(f"[REACT] [OBSERVE] → Continuing to think (iter {iterations}/{max_iterations})")

    return state


async def node_finish(state: AgentState) -> AgentState:
    """Finish node: Generate final response."""
    from .graph.nodes import DebugConfig
    from src.bus.models import AgentEventType

    # Get agent identifiers
    agent_info = state.get("agent_info") or {}
    agent_id = agent_info.get("agent_id", AGENT_ID)
    instance_id = f"{agent_id}-unknown"
    iterations = state.get("iterations", 0)

    # Calculate duration
    start_time = state.get("start_time", time.time())
    duration_ms = int((time.time() - start_time) * 1000)

    # Check if we have a result already (direct LLM response)
    if "result" in state:
        state["result"]["duration_ms"] = duration_ms
        if DebugConfig.REACT_VERBOSE:
            logger.info(f"[REACT] [FINISH] Direct response, duration={duration_ms}ms")
        # Emit task completed event
        await _emit_event(
            event_type=AgentEventType.TASK_COMPLETED,
            agent_id=agent_id,
            instance_id=instance_id,
            message="任务完成",
            react_step="finish",
            react_iteration=iterations,
            duration_ms=duration_ms,
        )
        return state

    # Summarize tool results
    tool_results = state.get("tool_results", [])

    if tool_results:
        summaries = []
        for result in tool_results:
            tool_name = result.get("tool_name", "unknown")

            if result.get("success"):
                if "chart" in result:
                    summaries.append(f"【{tool_name}】\n{result['chart']}")
                elif "content" in result:
                    summaries.append(f"【{tool_name}】\n{result['content']}")
                else:
                    summaries.append(f"【{tool_name}】执行成功")
            else:
                error = result.get("error", "Unknown error")
                summaries.append(f"【{tool_name}】执行失败: {error}")

        state["result"] = {
            "success": True,
            "content": "\n\n".join(summaries),
            "type": "tool_result",
            "tool_results": tool_results,
            "duration_ms": duration_ms,
        }

        if DebugConfig.REACT_VERBOSE:
            logger.info(f"[REACT] [FINISH] Summarized {len(tool_results)} tool results, duration={duration_ms}ms")

        # Emit task completed event
        await _emit_event(
            event_type=AgentEventType.TASK_COMPLETED,
            agent_id=agent_id,
            instance_id=instance_id,
            message=f"任务完成，共执行 {len(tool_results)} 个工具",
            react_step="finish",
            react_iteration=iterations,
            duration_ms=duration_ms,
        )
    else:
        error_msg = state.get("error", "No result generated")
        state["result"] = {
            "success": False,
            "error": error_msg,
            "duration_ms": duration_ms,
        }

        if DebugConfig.REACT_VERBOSE:
            logger.info(f"[REACT] [FINISH] No results, error={state.get('error')}")

        # Emit task failed event
        await _emit_event(
            event_type=AgentEventType.TASK_FAILED,
            agent_id=agent_id,
            instance_id=instance_id,
            message=f"任务失败: {error_msg}",
            react_step="finish",
            react_iteration=iterations,
            duration_ms=duration_ms,
            error=error_msg,
        )

    return state


def _build_system_prompt(base_prompt: str, tools: list[dict[str, Any]]) -> str:
    """Build system prompt with tool descriptions."""
    prompt_parts = [base_prompt] if base_prompt else []
    prompt_parts.append("\n\nYou have access to the following tools:")

    for tool in tools:
        func = tool.get("function", {})
        name = func.get("name", "unknown")
        desc = func.get("description", "No description")
        params = func.get("parameters", {}).get("properties", {})

        prompt_parts.append(f"\n## {name}")
        prompt_parts.append(f"Description: {desc}")

        if params:
            prompt_parts.append("Parameters:")
            for param_name, param_info in params.items():
                param_type = param_info.get("type", "any")
                param_desc = param_info.get("description", "")
                prompt_parts.append(f"  - {param_name} ({param_type}): {param_desc}")

    prompt_parts.append("\n## Instructions")
    prompt_parts.append("- Use tools when needed to help the user")
    prompt_parts.append("- If no tool is needed, respond directly")

    return "\n".join(prompt_parts)


# =============================================================================
# Build LangGraph
# =============================================================================

def build_agent_graph() -> Any:
    """Build and compile the ReAct agent graph."""
    try:
        from langgraph.graph import StateGraph, END

        workflow = StateGraph(AgentState)

        # ReAct nodes
        workflow.add_node("think", node_think)
        workflow.add_node("act", node_act)
        workflow.add_node("observe", node_observe)
        workflow.add_node("finish", node_finish)

        # Entry point
        workflow.set_entry_point("think")

        # ReAct loop edges
        workflow.add_conditional_edges(
            "think",
            lambda s: "act" if s.get("tool_calls") else "finish",
            {
                "act": "act",
                "finish": "finish",
            }
        )

        workflow.add_edge("act", "observe")

        workflow.add_conditional_edges(
            "observe",
            lambda s: "think" if not s.get("error") else "finish",
            {
                "think": "think",
                "finish": "finish",
            }
        )

        workflow.add_edge("finish", END)

        compiled = workflow.compile()
        logger.info("[GRAPH] Agent graph compiled (ReAct mode)")
        return compiled

    except ImportError as e:
        logger.error(f"[GRAPH] LangGraph not available: {e}")
        return None


# Global compiled graph
_agent_graph: Optional[Any] = None


def get_agent_graph() -> Any:
    """Get the compiled agent graph."""
    global _agent_graph
    if _agent_graph is None:
        _agent_graph = build_agent_graph()
    return _agent_graph


# =============================================================================
# Main Task Handler
# =============================================================================

async def handle_task_request(
    task_name: str,
    task_data: dict[str, Any],
    experience_db: Optional[Any] = None,
    agent_info: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    """Handle a task request using ReAct workflow.

    Args:
        task_name: Name of the task
        task_data: Task input data
        experience_db: Optional experience database
        agent_info: Agent information including system prompt

    Returns:
        Task response dictionary
    """
    from src.bus.models import AgentEventType

    start_time = time.time()
    
    # Get agent identifiers for events
    agent_id = (agent_info or {}).get("agent_id", AGENT_ID)
    instance_id = f"{agent_id}-unknown"  # Will be set by spawner

    # Emit task received event
    await _emit_event(
        event_type=AgentEventType.TASK_RECEIVED,
        agent_id=agent_id,
        instance_id=instance_id,
        message="收到任务请求",
        task_id=task_name,
    )

    # Emit task started event
    await _emit_event(
        event_type=AgentEventType.TASK_STARTED,
        agent_id=agent_id,
        instance_id=instance_id,
        message="开始处理任务...",
        task_id=task_name,
    )

    initial_state: AgentState = {
        "task_name": task_name,
        "task_data": task_data,
        "agent_info": agent_info,
        "experience_db": experience_db,
        "messages": [],
        "tool_calls": [],
        "tool_results": [],
        "react_step": "think",
        "iterations": 0,
        "max_iterations": 10,
        "llm_generated": False,
        "result": {},
        "error": None,
        "duration_ms": 0,
        "start_time": start_time,
    }

    graph = get_agent_graph()
    if graph is None:
        raise RuntimeError("LangGraph not available")

    try:
        logger.info(f"[HANDLER] Executing task: {task_name}")
        final_state = await graph.ainvoke(initial_state)
        logger.info(f"[HANDLER] Task complete: {task_name}")
        return final_state.get("result", {})

    except Exception as e:
        logger.exception(f"[HANDLER] Task failed: {task_name}")
        # Emit task failed event
        await _emit_event(
            event_type=AgentEventType.TASK_FAILED,
            agent_id=agent_id,
            instance_id=instance_id,
            message=f"任务执行异常: {str(e)}",
            task_id=task_name,
            error=str(e),
        )
        raise


# =============================================================================
# Legacy Compatibility (kept minimal)
# =============================================================================

async def call_security_operation(operation: str, params: dict[str, Any]) -> dict[str, Any]:
    """Call a security operation via message bus."""
    logger.warning(f"Security operation requested: {operation}")
    return {"error": "Bus integration pending"}


async def call_llm(
    prompt: str,
    model: Optional[str] = None,
    system_prompt: Optional[str] = None,
    messages: Optional[list[dict[str, str]]] = None,
    **kwargs: Any,
) -> dict[str, Any]:
    """Call LLM using the global LLM client."""
    client = get_llm_client()

    if client is None:
        return {"error": "LLM client not initialized", "content": ""}

    try:
        response = await client.complete(
            prompt=prompt,
            system_prompt=system_prompt,
            messages=messages,
            **kwargs,
        )

        if response.success:
            return {
                "content": response.content,
                "model": response.model,
                "usage": response.usage,
                "finish_reason": response.finish_reason,
                "error": None,
            }
        else:
            return {"content": "", "error": response.error}

    except Exception as e:
        logger.exception(f"[LLM] Call failed: {e}")
        return {"content": "", "error": str(e)}


# =============================================================================
# Runner Loop
# =============================================================================

async def run(skills_dir: Optional[Path] = None) -> None:
    """Main runner loop."""
    logger.info(f"Starting Agent Runner for {AGENT_ID}")
    logger.info(f"Bus address: {MESSAGE_BUS_ADDRESS}")

    # Register built-in tools
    _register_builtin_tools()

    # Pre-build the LangGraph
    get_agent_graph()

    logger.info(f"Agent Runner ready (ReAct mode)")

    # Keep running
    while True:
        await asyncio.sleep(60)


def main() -> None:
    """Entry point for agent_runner."""
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    )

    skills_dir = os.environ.get("SKILLS_DIR", "/app/skills")

    try:
        asyncio.run(run(Path(skills_dir)))
    except KeyboardInterrupt:
        logger.info(f"Agent {AGENT_ID} shutting down")


if __name__ == "__main__":
    main()
