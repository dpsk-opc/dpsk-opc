"""Agent Graph Nodes for DPSK-OPC.

This module defines the nodes used in the LangGraph workflow:
- think: LLM decides what to do (with optional tool calls)
- act: Execute the selected tool
- observe: Process tool result and decide next step
- finish: Generate final response
"""

from __future__ import annotations

import json
import logging
import os
from typing import Any

from ..tools.registry import registry as tool_registry
from .state import AgentState

logger = logging.getLogger(__name__)


# =============================================================================
# Debug Configuration
# =============================================================================

class DebugConfig:
    """Debug configuration for ReAct agent.

    Can be set programmatically or via environment variables.
    """

    # Enable verbose ReAct logging (default: True for development)
    REACT_VERBOSE: bool = os.environ.get("REACT_VERBOSE", "true").lower() == "true"

    # Enable LangGraph debug mode
    LANGGRAPH_DEBUG: bool = os.environ.get("LANGGRAPH_DEBUG", "false").lower() == "true"

    # Print state transitions
    PRINT_STATE: bool = os.environ.get("REACT_PRINT_STATE", "false").lower() == "true"

    @classmethod
    def enable_all(cls) -> None:
        """Enable all debug options."""
        cls.REACT_VERBOSE = True
        cls.LANGGRAPH_DEBUG = True
        cls.PRINT_STATE = True

    @classmethod
    def disable_all(cls) -> None:
        """Disable all debug options."""
        cls.REACT_VERBOSE = False
        cls.LANGGRAPH_DEBUG = False
        cls.PRINT_STATE = False

    @classmethod
    def set_verbose(cls, enabled: bool = True) -> None:
        """Enable or disable verbose logging.

        Args:
            enabled: True to enable, False to disable
        """
        cls.REACT_VERBOSE = enabled
        logger.info(f"ReAct verbose logging: {'enabled' if enabled else 'disabled'}")


# Convenience alias
_REACT_VERBOSE = True  # Will be checked via DebugConfig.REACT_VERBOSE

# Tool call limits to prevent runaway loops
MAX_TOOL_CALLS_PER_ITERATION = 10  # Max tool calls in a single LLM response
MAX_REPEATED_TOOL_NAME = 3  # Max times the same tool can be called consecutively


def _log_state(state: AgentState, node_name: str) -> None:
    """Log current state for debugging.

    Args:
        state: Current agent state
        node_name: Name of the current node
    """
    if not _REACT_VERBOSE:
        return

    logger.debug(f"[{node_name}] State keys: {list(state.keys())}")
    logger.debug(f"[{node_name}] react_step: {state.get('react_step')}")
    logger.debug(f"[{node_name}] iterations: {state.get('iterations')}")
    logger.debug(f"[{node_name}] tool_calls: {state.get('tool_calls')}")
    logger.debug(f"[{node_name}] messages: {len(state.get('messages', []))}")


async def node_think(state: AgentState) -> AgentState:
    """Think node: LLM decides what to do.

    The LLM is given the conversation history and decides whether to:
    1. Call a tool (with arguments)
    2. Respond directly to the user

    Args:
        state: Current agent state.

    Returns:
        Updated state with LLM's decision.
    """
    from ..llm.openai_client import OpenAIClient

    messages = state.get("messages", [])
    tools = tool_registry.get_tools_for_llm()
    iterations = state.get("iterations", 0)

    # Log entry
    if DebugConfig.REACT_VERBOSE:
        logger.info(f"[REACT] ═══════════════════════════════════════")
        logger.info(f"[REACT] ITERATION {iterations}")
        logger.info(f"[REACT] ═══════════════════════════════════════")
        logger.info(f"[REACT] [THINK] Entering think node")
        logger.info(f"[REACT] [THINK] Messages: {len(messages)}, Tools: {len(tools)}")

    # Prepare system prompt with available tools
    system_prompt = _build_system_prompt(tools)

    # Build chat messages for LLM
    chat_messages = [{"role": "system", "content": system_prompt}]

    # Add conversation history (skip the system prompt we just added)
    for msg in messages:
        if msg.get("role") != "system":
            chat_messages.append(msg)

    if DebugConfig.REACT_VERBOSE:
        # Log last user message
        for msg in reversed(chat_messages):
            if msg.get("role") == "user":
                content = msg.get("content", "")
                logger.info(f"[REACT] [THINK] User input: {content[:200]}...")
                break

    # Call LLM
    client = OpenAIClient.get_instance()
    response = await client.chat(messages=chat_messages, tools=tools if tools else None)

    # Parse LLM response
    assistant_message = response.get("message", {})
    content = assistant_message.get("content", "")
    tool_calls = assistant_message.get("tool_calls", [])

    if DebugConfig.REACT_VERBOSE:
        if content:
            logger.info(f"[REACT] [THINK] LLM text response: {content[:200]}...")
        logger.info(f"[REACT] [THINK] Tool calls requested: {len(tool_calls)}")

    # =========================================================================
    # PROTECTION: Detect abnormal number of tool calls (runaway loop)
    # =========================================================================
    if len(tool_calls) > MAX_TOOL_CALLS_PER_ITERATION:
        logger.warning(
            f"[REACT] [THINK] ABORT: Got {len(tool_calls)} tool calls, "
            f"max allowed is {MAX_TOOL_CALLS_PER_ITERATION}. "
            f"This appears to be a runaway loop."
        )
        # Log sample of tool calls for debugging
        sample_names = [tc.get("function", {}).get("name") for tc in tool_calls[:5]]
        logger.warning(f"[REACT] [THINK]   First 5 tool names: {sample_names}")
        for i, tc in enumerate(tool_calls[:3]):
            func = tc.get("function", {})
            args = func.get("arguments", "{}")
            logger.warning(f"[REACT] [THINK]   tc[{i}]: name={func.get('name')}, args={args}")

        # Add failure message and finish
        error_msg = (
            f"ERROR: LLM generated {len(tool_calls)} tool calls in one response (max: {MAX_TOOL_CALLS_PER_ITERATION}). "
            f"This appears to be a runaway loop. Stopping execution."
        )
        new_messages = messages + [assistant_message]
        state["messages"] = new_messages
        state["tool_calls"] = []
        state["react_step"] = "finish"
        state["response"] = {
            "success": False,
            "error": error_msg,
            "type": "error",
            "tool_calls_count": len(tool_calls),
        }
        state["should_continue"] = False
        return state

    # Check for repeated tool names (potential loop)
    tool_name_counts: dict[str, int] = {}
    empty_args_calls = []
    for i, tc in enumerate(tool_calls):
        func = tc.get("function", {})
        tool_name = func.get("name", "unknown")
        tool_name_counts[tool_name] = tool_name_counts.get(tool_name, 0) + 1

        args = func.get("arguments", "{}")
        try:
            parsed_args = json.loads(args) if isinstance(args, str) else args
            if not parsed_args or len(parsed_args) == 0:
                empty_args_calls.append((i, tool_name))
        except json.JSONDecodeError:
            empty_args_calls.append((i, tool_name))

    # Log warning if same tool called multiple times or with empty args
    for tool_name, count in tool_name_counts.items():
        if count > MAX_REPEATED_TOOL_NAME:
            logger.warning(
                f"[REACT] [THINK] WARNING: Tool '{tool_name}' called {count} times. "
                f"This may indicate a loop."
            )

    if empty_args_calls:
        logger.warning(
            f"[REACT] [THINK] WARNING: {len(empty_args_calls)} tool calls have empty arguments"
        )

    # Add assistant message to history (do this before updating tool_calls)
    new_messages = messages + [assistant_message]
    state["messages"] = new_messages

    # CRITICAL: Always update state["tool_calls"] explicitly to ensure LangGraph
    # sees the new value. Simply doing `tool_calls = new_value` does NOT update
    # state["tool_calls"] if tool_calls was already a key in state (Python list
    # assignment `a = b` rebinds the local variable, not the state dict).
    if tool_calls:
        state["tool_calls"] = tool_calls
        state["react_step"] = "act"
        for tc in tool_calls:
            func = tc.get("function", {})
            logger.info(f"[REACT] [THINK] → Will call tool: {func.get('name')}")
    else:
        # Explicitly set empty list so the graph router sees [] (falsy)
        state["tool_calls"] = []
        # No tool call, directly respond to user
        state["react_step"] = "finish"
        state["response"] = {
            "success": True,
            "content": content,
            "type": "text",
        }
        state["should_continue"] = False
        logger.info("[REACT] [THINK] → Direct response, no tools needed")
        logger.info("LLM responded directly without tool call")

    return state


async def node_act(state: AgentState) -> AgentState:
    """Act node: Execute the selected tool.

    Args:
        state: Current agent state with tool_calls.

    Returns:
        Updated state with tool execution results.
    """
    tool_calls = state.get("tool_calls", [])

    if DebugConfig.REACT_VERBOSE:
        logger.info("[REACT] [ACT] Entering act node")

    if not tool_calls:
        logger.warning("[REACT] [ACT] No tool calls to execute")
        state["react_step"] = "finish"
        state["response"] = {
            "success": True,
            "content": "No tools to execute.",
            "type": "text",
        }
        state["should_continue"] = False
        return state

    results = []
    for i, tool_call in enumerate(tool_calls):
        func = tool_call.get("function", {})
        tool_name = func.get("name", "unknown")
        arguments = func.get("arguments", "{}")

        if DebugConfig.REACT_VERBOSE:
            logger.info(f"[REACT] [ACT] Executing tool {i+1}: {tool_name}")
            logger.info(f"[REACT] [ACT]   Args: {arguments[:200]}...")

        result = await _execute_tool_call(tool_call)
        results.append(result)

        if DebugConfig.REACT_VERBOSE:
            success = result.get("success", False)
            has_chart = "chart" in result
            logger.info(f"[REACT] [ACT]   Result: success={success}, has_chart={has_chart}")
            if success and "chart" in result:
                chart_preview = result.get("chart", "")[:200]
                logger.info(f"[REACT] [ACT]   Chart preview:\n{chart_preview}...")

    # Add tool results as messages
    tool_result_messages = []
    for i, (tool_call, result) in enumerate(zip(tool_calls, results)):
        tool_result_messages.append({
            "role": "tool",
            "tool_call_id": tool_call.get("id", f"call_{i}"),
            "tool_name": tool_call.get("function", {}).get("name", "unknown"),
            "content": json.dumps(result, ensure_ascii=False),
        })

    state["messages"] = state["messages"] + tool_result_messages
    state["tool_results"] = results
    state["react_step"] = "observe"

    logger.info(f"[REACT] [ACT] → Executed {len(results)} tool(s), moving to observe")

    return state


async def node_observe(state: AgentState) -> AgentState:
    """Observe node: Process tool results and decide next step.

    Args:
        state: Current agent state with tool_results.

    Returns:
        Updated state with next step decision.
    """
    iterations = state.get("iterations", 0) + 1
    max_iterations = state.get("max_iterations", 10)
    tool_results = state.get("tool_results", [])

    if DebugConfig.REACT_VERBOSE:
        logger.info("[REACT] [OBSERVE] Entering observe node")
        for i, result in enumerate(tool_results):
            tool_name = result.get("tool_name", "unknown")
            success = result.get("success", False)
            logger.info(f"[REACT] [OBSERVE] Tool {i+1} ({tool_name}): success={success}")

    state["iterations"] = iterations
    state["react_step"] = "think"

    # Check iteration limit
    if iterations >= max_iterations:
        logger.warning(f"[REACT] [OBSERVE] Max iterations ({max_iterations}) reached")
        state["react_step"] = "finish"
        state["response"] = {
            "success": False,
            "error": f"Maximum iterations ({max_iterations}) reached",
            "type": "error",
        }
        state["should_continue"] = False
        return state

    # Continue to think for more tool calls or finish
    state["should_continue"] = True

    logger.info(f"[REACT] [OBSERVE] → Continuing to think (iter {iterations}/{max_iterations})")

    return state


async def node_finish(state: AgentState) -> AgentState:
    """Finish node: Generate final response.

    Args:
        state: Current agent state.

    Returns:
        Updated state with final response.
    """
    # If we have tool results but no explicit response, summarize them
    response = state.get("response", {})
    tool_results = state.get("tool_results", [])

    if not response.get("content") and tool_results:
        # Generate response from tool results
        summary = _summarize_tool_results(tool_results)
        response = {
            "success": True,
            "content": summary,
            "type": "tool_result",
            "tool_results": tool_results,
        }
        state["response"] = response

    state["should_continue"] = False
    state["react_step"] = "finish"

    logger.info("Finish: generating final response")

    return state


async def _execute_tool_call(tool_call: dict[str, Any]) -> dict[str, Any]:
    """Execute a single tool call.

    Args:
        tool_call: Tool call specification from LLM.

    Returns:
        Execution result.
    """
    function = tool_call.get("function", {})
    tool_name = function.get("name", "")
    arguments_str = function.get("arguments", "{}")

    logger.info(f"Executing tool: {tool_name}")

    # Get tool from registry
    tool = tool_registry.get(tool_name)
    if not tool:
        return {
            "success": False,
            "error": f"Tool '{tool_name}' not found",
            "tool_name": tool_name,
        }

    # Parse arguments
    try:
        arguments = json.loads(arguments_str) if isinstance(arguments_str, str) else arguments_str
    except json.JSONDecodeError:
        return {
            "success": False,
            "error": f"Invalid JSON arguments: {arguments_str}",
            "tool_name": tool_name,
        }

    # Execute tool
    try:
        result = await tool.run(arguments)
        result["tool_name"] = tool_name
        return result
    except Exception as e:
        logger.exception(f"Tool execution failed: {tool_name}")
        return {
            "success": False,
            "error": str(e),
            "tool_name": tool_name,
        }


def _build_system_prompt(tools: list[dict[str, Any]]) -> str:
    """Build system prompt with available tools.

    Args:
        tools: List of tools in OpenAI format.

    Returns:
        System prompt string.
    """
    prompt_parts = [
        "You are a helpful AI Agent assistant.",
        "You have access to the following tools:",
    ]

    for tool in tools:
        func = tool.get("function", {})
        name = func.get("name", "unknown")
        desc = func.get("description", "No description")
        params = func.get("parameters", {}).get("properties", {})
        required_params = func.get("parameters", {}).get("required", [])

        prompt_parts.append(f"\n## {name}")
        prompt_parts.append(f"Description: {desc}")

        if params:
            prompt_parts.append("Parameters:")
            for param_name, param_info in params.items():
                param_type = param_info.get("type", "any")
                param_desc = param_info.get("description", "")
                required_marker = " [REQUIRED]" if param_name in required_params else ""
                prompt_parts.append(f"  - {param_name} ({param_type}): {param_desc}{required_marker}")

    prompt_parts.append("\n## Instructions")
    prompt_parts.append("- Use tools when needed to help the user")
    prompt_parts.append("- If no tool is needed, respond directly")
    prompt_parts.append("- When calling a tool, you MUST provide ALL required parameters with VALID values")
    prompt_parts.append("- NEVER call a tool with empty arguments {} - this will cause errors")
    prompt_parts.append("- If you don't know the required parameter values, ask the user instead of guessing")
    prompt_parts.append("- Call ONE tool at a time, then wait for the result before deciding next action")
    prompt_parts.append("- Be concise and helpful in your responses")

    return "\n".join(prompt_parts)


def _summarize_tool_results(tool_results: list[dict[str, Any]]) -> str:
    """Summarize tool execution results for user.

    Args:
        tool_results: List of tool execution results.

    Returns:
        Human-readable summary.
    """
    if not tool_results:
        return "No results available."

    summaries = []
    for result in tool_results:
        tool_name = result.get("tool_name", "unknown")

        if result.get("success"):
            # Check for chart or specific content
            if "chart" in result:
                summaries.append(f"【{tool_name}】\n{result['chart']}")
            elif "content" in result:
                summaries.append(f"【{tool_name}】\n{result['content']}")
            else:
                # Generic success
                data_keys = [k for k in result.keys() if k not in ("success", "tool_name")]
                if data_keys:
                    summaries.append(f"【{tool_name}】执行成功")
                else:
                    summaries.append(f"【{tool_name}】已完成")
        else:
            error = result.get("error", "Unknown error")
            summaries.append(f"【{tool_name}】执行失败: {error}")

    return "\n\n".join(summaries)
