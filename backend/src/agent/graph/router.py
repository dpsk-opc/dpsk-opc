"""Agent Graph Router for DPSK-OPC.

This module defines routing functions for the LangGraph workflow,
determining which node to execute next based on current state.
"""

from __future__ import annotations

from typing import Literal

from .state import AgentState


def router_react_loop(state: AgentState) -> Literal["act", "finish", "observe"]:
    """Route to next node in ReAct loop based on react_step.

    Args:
        state: Current agent state.

    Returns:
        Next node name.
    """
    react_step = state.get("react_step", "think")

    if react_step == "think":
        # After thinking, check if we have tool calls
        tool_calls = state.get("tool_calls", [])
        if tool_calls:
            return "act"
        else:
            return "finish"
    elif react_step == "act":
        return "observe"
    elif react_step == "observe":
        # After observing, check if we should continue
        if state.get("should_continue", False):
            return "think"
        else:
            return "finish"
    else:
        return "finish"


def router_should_continue(state: AgentState) -> Literal["continue", "end"]:
    """Determine if the graph should continue or end.

    Args:
        state: Current agent state.

    Returns:
        "continue" to keep going, "end" to finish.
    """
    if state.get("should_continue", False):
        return "continue"
    return "end"
