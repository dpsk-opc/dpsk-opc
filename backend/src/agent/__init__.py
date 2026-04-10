"""Agent Module for DPSK-OPC

This module provides the core Agent functionality including:
- AgentDef: Static definition of an Agent
- AgentRegistry: In-memory registry for Agent definitions
- AgentSpawner: Dynamic instantiation of Agent instances
- AgentRunner: Runtime for executing Agent tasks
"""

from src.agent.defs import (
    AgentDef,
    AgentInstance,
    InstanceStatus,
)

__all__ = [
    "AgentDef",
    "AgentInstance",
    "InstanceStatus",
]
