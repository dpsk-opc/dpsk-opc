"""OPC-Client Module for DPSK-OPC.

OPC-Client (Orchestration Pipeline Client) is the orchestration kernel
that coordinates agents to execute user requests through dynamically
generated workflows.

Version: 1.0.2
"""

from src.opc.config import OPCConfig, WorkflowContext
from src.opc.errors import (
    OPCError,
    WorkflowValidationError,
    CyclicDependencyError,
    AgentNotFoundError,
    WorkflowGenerationError,
    TaskExecutionError,
    WorkflowTimeoutError,
)
from src.opc.models.workflow import TaskStatus, WorkflowTask, Workflow
from src.opc.models.result import TaskResult, FinalResult, WorkflowStatus
from src.opc.models.events import EventType, ProgressEvent
from src.opc.client import OPCClient
from src.opc.providers import WorkflowProvider, StaticFileWorkflowProvider, LLMWorkflowProvider
from src.opc.invokers import AgentInvoker, LocalAgentInvoker
from src.opc.strategies import ExecutionStrategy, ParallelDependencyStrategy
from src.opc.api import router as api_router

__version__ = "1.0.2"

__all__ = [
    # Config
    "OPCConfig",
    "WorkflowContext",
    # Errors
    "OPCError",
    "WorkflowValidationError",
    "CyclicDependencyError",
    "AgentNotFoundError",
    "WorkflowGenerationError",
    "TaskExecutionError",
    "WorkflowTimeoutError",
    # Models
    "TaskStatus",
    "WorkflowTask",
    "Workflow",
    "TaskResult",
    "FinalResult",
    "WorkflowStatus",
    "EventType",
    "ProgressEvent",
    # Client
    "OPCClient",
    # Providers
    "WorkflowProvider",
    "StaticFileWorkflowProvider",
    "LLMWorkflowProvider",
    # Invokers
    "AgentInvoker",
    "LocalAgentInvoker",
    # Strategies
    "ExecutionStrategy",
    "ParallelDependencyStrategy",
    # API
    "api_router",
]
