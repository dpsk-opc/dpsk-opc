"""Agent Graph Builder for DPSK-OPC.

This module builds the LangGraph workflow for the agent.
"""

from __future__ import annotations

import logging
from typing import Any

from langgraph.graph import END, StateGraph

from .nodes import node_act, node_finish, node_observe, node_think
from .router import router_react_loop, router_should_continue
from .state import AgentState, create_initial_state

logger = logging.getLogger(__name__)


def build_agent_graph() -> StateGraph:
    """Build the agent graph.

    The graph implements a ReAct loop:
    1. think: LLM decides action (with optional tool calls)
    2. act: Execute tool if requested
    3. observe: Process result and decide next step
    4. finish: Generate final response

    Returns:
        Compiled StateGraph ready for execution.
    """
    # Create the graph
    graph = StateGraph(AgentState)

    # Add nodes
    graph.add_node("think", node_think)
    graph.add_node("act", node_act)
    graph.add_node("observe", node_observe)
    graph.add_node("finish", node_finish)

    # Set entry point
    graph.set_entry_point("think")

    # Add conditional edges
    # From think: go to act (if tools) or finish (if no tools)
    graph.add_conditional_edges(
        "think",
        router_react_loop,
        {
            "act": "act",
            "finish": "finish",
        },
    )

    # From act: always go to observe
    graph.add_edge("act", "observe")

    # From observe: go to think (if continue) or finish (if done)
    graph.add_conditional_edges(
        "observe",
        router_react_loop,
        {
            "think": "think",
            "finish": "finish",
        },
    )

    # From finish: end
    graph.add_edge("finish", END)

    # Compile the graph
    compiled = graph.compile()
    logger.info("Agent graph compiled successfully")

    return compiled


# Global compiled graph instance
_compiled_graph: Any = None


def get_agent_graph() -> Any:
    """Get the compiled agent graph (singleton).

    Returns:
        Compiled LangGraph.
    """
    global _compiled_graph
    if _compiled_graph is None:
        _compiled_graph = build_agent_graph()
    return _compiled_graph


async def run_agent(
    input_message: str,
    conversation_id: str | None = None,
    user_id: str | None = None,
    max_iterations: int = 10,
) -> dict[str, Any]:
    """Run the agent with given input.

    Args:
        input_message: User's input message.
        conversation_id: Optional conversation ID.
        user_id: Optional user ID.
        max_iterations: Maximum ReAct loop iterations.

    Returns:
        Final response dictionary.
    """
    graph = get_agent_graph()

    # Create initial state
    initial_state = create_initial_state(
        input_message=input_message,
        conversation_id=conversation_id,
        user_id=user_id,
        max_iterations=max_iterations,
    )

    # Add user message to history
    initial_state["messages"] = [{
        "role": "user",
        "content": input_message,
    }]

    # Run the graph
    logger.info(f"Running agent with input: {input_message[:100]}...")
    final_state = await graph.ainvoke(initial_state)

    # Extract response
    response = final_state.get("response", {})
    logger.info(f"Agent completed with response type: {response.get('type', 'unknown')}")

    return response


# Singleton graph instance
_agent_graph = build_agent_graph()
