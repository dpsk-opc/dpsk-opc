"""Agent Graph State for DPSK-OPC.

This module defines the state structure used throughout the agent graph,
including messages, tool calls, and execution state.
"""

from __future__ import annotations

from typing import Any, Literal, TypedDict

from langgraph.graph import add_messages


class AgentState(TypedDict, total=False):
    """State for the Agent graph.

    This state is passed between nodes in the LangGraph workflow and tracks
    all relevant information for the agent's execution.
    """

    # Messages exchanged with the LLM (includes tool call responses)
    messages: list[dict[str, Any]]

    # User input message
    input_message: str

    # Conversation ID for context management
    conversation_id: str | None

    # User ID
    user_id: str | None

    # Tool calls requested by LLM
    tool_calls: list[dict[str, Any]]

    # Tool call results
    tool_results: list[dict[str, Any]]

    # Current step in ReAct loop (think, act, observe)
    react_step: Literal["think", "act", "observe", "finish"]

    # Number of tool call iterations (to prevent infinite loops)
    iterations: int

    # Maximum iterations allowed
    max_iterations: int

    # Final response to return
    response: dict[str, Any]

    # Error message if any
    error: str | None

    # Whether the agent should continue
    should_continue: bool


def create_initial_state(
    input_message: str,
    conversation_id: str | None = None,
    user_id: str | None = None,
    max_iterations: int = 10,
) -> AgentState:
    """Create an initial state for the agent graph.

    Args:
        input_message: The user's input message.
        conversation_id: Optional conversation ID for context.
        user_id: Optional user ID.
        max_iterations: Maximum number of tool call iterations.

    Returns:
        Initial AgentState dictionary.
    """
    return AgentState(
        messages=[],
        input_message=input_message,
        conversation_id=conversation_id,
        user_id=user_id,
        tool_calls=[],
        tool_results=[],
        react_step="think",
        iterations=0,
        max_iterations=max_iterations,
        response={},
        error=None,
        should_continue=True,
    )
