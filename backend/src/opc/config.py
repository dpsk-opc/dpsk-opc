"""OPC-Client Configuration."""

from __future__ import annotations

import uuid
from typing import Any, Optional

from pydantic import BaseModel, Field


class FailureStrategy(str):
    """Task failure handling strategy."""

    STOP_ON_FAILURE = "stop_on_failure"
    SKIP_DEPENDENTS = "skip_dependents"
    RETRY_N_TIMES = "retry_n_times"


class OPCConfig(BaseModel):
    """Configuration for OPC-Client."""

    max_execution_time_secs: int = Field(
        default=300,
        ge=1,
        description="Maximum workflow execution time in seconds",
    )

    default_task_timeout_secs: int = Field(
        default=300,
        ge=1,
        description="Default timeout for individual task execution",
    )

    failure_strategy: str = Field(
        default="stop_on_failure",
        description="Strategy when task fails: stop_on_failure, skip_dependents, retry_n_times",
    )

    max_retries: int = Field(
        default=3,
        ge=0,
        description="Maximum retry attempts for failed tasks",
    )

    max_parallel_tasks: int = Field(
        default=20,
        ge=1,
        description="Maximum number of tasks to execute in parallel",
    )

    enable_progress_streaming: bool = Field(
        default=True,
        description="Enable streaming progress events",
    )

    llm_temperature: float = Field(
        default=0.7,
        ge=0,
        le=2,
        description="LLM temperature for workflow generation",
    )

    llm_max_tokens: int = Field(
        default=4096,
        ge=1,
        description="Maximum tokens for LLM workflow generation",
    )

    llm_retry_times: int = Field(
        default=3,
        ge=0,
        description="Number of retries for LLM workflow generation",
    )

    static_workflow_dir: Optional[str] = Field(
        default=None,
        description="Directory for static workflow files",
    )

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary."""
        return self.model_dump()

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "OPCConfig":
        """Create from dictionary."""
        return cls(**data)


class WorkflowContext:
    """Context passed during workflow generation and execution."""

    def __init__(
        self,
        trace_id: Optional[str] = None,
        user_request: str = "",
        available_agents: Optional[list[dict[str, Any]]] = None,
        metadata: Optional[dict[str, Any]] = None,
    ) -> None:
        self.trace_id = trace_id or str(uuid.uuid4())
        self.user_request = user_request
        self.available_agents = available_agents or []
        self.metadata = metadata or {}

    def get_agent_summary(self) -> str:
        """Get a summary of available agents for LLM prompt."""
        if not self.available_agents:
            return "No agents available"

        lines = []
        for agent in self.available_agents:
            agent_id = agent.get("agent_id", "unknown")
            name = agent.get("name", agent_id)
            capabilities = agent.get("capabilities", [])
            skills = agent.get("skills", [])

            lines.append(f"- {name} ({agent_id})")
            if capabilities:
                lines.append(f"  Capabilities: {', '.join(capabilities[:3])}")
            if skills:
                lines.append(f"  Skills: {', '.join(skills[:3])}")

        return "\n".join(lines)

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary."""
        return {
            "trace_id": self.trace_id,
            "user_request": self.user_request,
            "available_agents": self.available_agents,
            "metadata": self.metadata,
        }
