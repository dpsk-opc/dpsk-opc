"""Agent definitions for DPSK-OPC.

This module defines the core data structures for Agent management.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Optional

from pydantic import BaseModel, Field


class InstanceStatus(str, Enum):
    """Status of an Agent instance."""

    CREATING = "creating"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    FAILED = "failed"


class AgentMetadata(BaseModel):
    """Metadata for Agent resource limits and configuration."""

    cpu_limit: Optional[float] = Field(default=None, ge=0, description="CPU limit (cores)")
    memory_limit: Optional[int] = Field(default=None, ge=0, description="Memory limit (MB)")
    timeout: Optional[int] = Field(default=300, ge=0, description="Default task timeout (seconds)")

    class Config:
        extra = "allow"  # Allow additional metadata fields


class ModelConfig(BaseModel):
    """Configuration for the LLM model used by an Agent."""

    model: str = Field(default="gpt-4", description="Model identifier")
    temperature: float = Field(default=0.7, ge=0, le=2, description="Sampling temperature")
    max_tokens: Optional[int] = Field(default=None, ge=1, description="Maximum tokens to generate")
    top_p: Optional[float] = Field(default=None, ge=0, le=1, description="Nucleus sampling parameter")
    timeout: Optional[int] = Field(default=60, ge=1, description="Request timeout (seconds)")

    class Config:
        extra = "allow"  # Allow additional model config fields


@dataclass
class AgentDef:
    """Static definition of an Agent.

    An AgentDef is typically loaded from a Markdown file with YAML frontmatter.
    It represents the static configuration of an Agent, not a running instance.
    """

    agent_id: str  # Unique identifier, matches filename without .md
    name: str  # Display name
    file_path: Path  # Source file path
    skills: list[str] = field(default_factory=list)  # List of skill identifiers
    capabilities: list[str] = field(default_factory=list)  # Natural language capabilities
    team: Optional[str] = None  # Team/group this Agent belongs to
    workspace: Optional[str] = None  # Workspace/business unit
    dependencies: list[str] = field(default_factory=list)  # Agent IDs this depends on
    description: str = ""  # Markdown body for LLM understanding
    model: Optional[str] = None  # Model ID (e.g., "gpt-4", "claude-3-opus")
    model_config: dict[str, Any] = field(default_factory=dict)  # Model configuration
    max_instances: int = 1  # Maximum concurrent instances
    queue_size: int = 0  # Task queue size (0 = no queuing)
    max_experience_entries: int = 1000  # Max entries in experience pool
    metadata: dict[str, Any] = field(default_factory=dict)  # cpu_limit, memory_limit, etc.

    def get_cpu_limit(self) -> Optional[float]:
        """Get CPU limit from metadata."""
        return self.metadata.get("cpu_limit")

    def get_memory_limit(self) -> Optional[int]:
        """Get memory limit (MB) from metadata."""
        return self.metadata.get("memory_limit")

    def get_timeout(self) -> int:
        """Get default task timeout from metadata."""
        return self.metadata.get("timeout", 300)

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "agent_id": self.agent_id,
            "name": self.name,
            "file_path": str(self.file_path),
            "skills": self.skills,
            "capabilities": self.capabilities,
            "team": self.team,
            "workspace": self.workspace,
            "dependencies": self.dependencies,
            "description": self.description,
            "model": self.model,
            "model_config": self.model_config,
            "max_instances": self.max_instances,
            "queue_size": self.queue_size,
            "max_experience_entries": self.max_experience_entries,
            "metadata": self.metadata,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> AgentDef:
        """Deserialize from dictionary."""
        if "file_path" in data and isinstance(data["file_path"], str):
            data["file_path"] = Path(data["file_path"])
        return cls(**data)


@dataclass
class AgentInstance:
    """Dynamic instance of an Agent.

    Represents a running Agent process inside a sandbox.
    """

    instance_id: str  # Unique instance ID
    agent_id: str  # Corresponding AgentDef ID
    sandbox_id: str  # Sandbox ID
    status: InstanceStatus  # Current status
    created_at: float  # Creation timestamp
    last_heartbeat: float  # Last heartbeat timestamp
    task_count: int = 0  # Number of tasks processed
    task_queue_size: int = 0  # Current queue size (for monitoring)

    @property
    def is_alive(self) -> bool:
        """Check if instance is alive based on status."""
        return self.status == InstanceStatus.RUNNING

    def update_heartbeat(self) -> None:
        """Update last heartbeat timestamp."""
        self.last_heartbeat = time.time()

    def increment_task_count(self) -> None:
        """Increment processed task count."""
        self.task_count += 1

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "instance_id": self.instance_id,
            "agent_id": self.agent_id,
            "sandbox_id": self.sandbox_id,
            "status": self.status.value,
            "created_at": self.created_at,
            "last_heartbeat": self.last_heartbeat,
            "task_count": self.task_count,
            "task_queue_size": self.task_queue_size,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> AgentInstance:
        """Deserialize from dictionary."""
        if isinstance(data.get("status"), str):
            data["status"] = InstanceStatus(data["status"])
        return cls(**data)


@dataclass
class AgentHandle:
    """Handle to a running Agent instance.

    Provides a high-level interface for interacting with an Agent instance.
    """

    instance: AgentInstance
    agent_def: AgentDef
    _bus = None  # Reference to message bus (injected later)
    _task_handler = None  # Task handler function (injected later)

    @property
    def instance_id(self) -> str:
        """Get instance ID."""
        return self.instance.instance_id

    @property
    def agent_id(self) -> str:
        """Get agent ID."""
        return self.instance.agent_id

    @property
    def status(self) -> InstanceStatus:
        """Get current status."""
        return self.instance.status

    def set_bus(self, bus: Any) -> None:
        """Set the message bus reference."""
        self._bus = bus

    def set_task_handler(self, handler: Any) -> None:
        """Set the task handler function."""
        self._task_handler = handler

    async def send_task(
        self,
        task_name: str,
        task_data: dict[str, Any],
        timeout: float = 60.0,
    ) -> dict[str, Any]:
        """Send a task to this Agent instance via message bus.

        Args:
            task_name: Name of the task to execute
            task_data: Task input data
            timeout: Request timeout in seconds

        Returns:
            Task result dictionary
        """
        from src.bus.models import MessageType, Target, TargetType
        from src.bus.protocol import TimeoutError as BusTimeoutError

        if self._bus is None:
            # Fallback to direct handler call for backward compatibility
            if self._task_handler is not None:
                return await self._execute_via_handler(task_name, task_data)
            return {"error": "Bus not available and no task handler set"}

        target = Target(type=TargetType.AGENT, value=self.agent_id)
        
        # Create task request
        from src.bus.models import TaskRequest, Message
        import uuid
        
        request = TaskRequest(
            source="system",
            target=target,
            task_name=task_name,
            task_data=task_data,
        )
        request.id = str(uuid.uuid4())
        request.correlation_id = request.id
        
        try:
            # Send via bus and wait for response
            response = await self._bus.request(
                target=target,
                message=request,
                timeout=timeout,
            )
            
            # Convert response to dict format
            if hasattr(response, 'success'):
                return {
                    "success": response.success,
                    "result": response.result if hasattr(response, 'result') else {},
                    "error": response.error if hasattr(response, 'error') else None,
                }
            return {"result": response}
            
        except BusTimeoutError as e:
            return {
                "success": False,
                "error": f"Task timeout: {e}",
                "result": {},
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
                "result": {},
            }

    async def _execute_via_handler(
        self,
        task_name: str,
        task_data: dict[str, Any],
    ) -> dict[str, Any]:
        """Execute task via direct handler call (fallback).

        Args:
            task_name: Name of the task
            task_data: Task input data

        Returns:
            Task result dictionary
        """
        try:
            if asyncio.iscoroutinefunction(self._task_handler):
                result = await self._task_handler(task_name, task_data)
            else:
                result = self._task_handler(task_name, task_data)
            return {"success": True, "result": result, "error": None}
        except Exception as e:
            return {"success": False, "result": {}, "error": str(e)}

    async def stop(self, timeout: float = 10.0) -> None:
        """Gracefully stop the Agent instance."""
        self.instance.status = InstanceStatus.STOPPING
        # TODO: Send stop command via bus
        self.instance.status = InstanceStatus.STOPPED

    async def destroy(self, force: bool = False) -> None:
        """Destroy the Agent instance."""
        if force:
            self.instance.status = InstanceStatus.FAILED
        else:
            self.instance.status = InstanceStatus.STOPPING
        # TODO: Call security module to destroy sandbox
        self.instance.status = InstanceStatus.STOPPED
