"""Message Models for DPSK-OPC Message Bus

This module defines the core message models used for agent communication.
"""

import json
import logging
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Optional, TypeVar

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class MessageType(str, Enum):
    """Enumeration of message types."""

    TASK_REQUEST = "TaskRequest"
    TASK_RESPONSE = "TaskResponse"
    EVENT = "Event"
    COMMAND = "Command"
    QUERY = "Query"
    QUERY_RESPONSE = "QueryResponse"


class TargetType(str, Enum):
    """Enumeration of target types."""

    AGENT = "agent"
    TOPIC = "topic"
    GROUP = "group"


class AggregationPolicy(str, Enum):
    """Policy for aggregating group task results."""

    ALL = "all"  # Wait for all subtasks to complete
    ANY = "any"  # Return when any subtask completes
    FIRST = "first"  # Return first response, cancel others


class Target(BaseModel):
    """Target model for message routing.
    
    Specifies the destination of a message.
    """

    type: TargetType = Field(..., description="Type of target (agent, topic, group)")
    value: str = Field(..., description="Target identifier value")

    @classmethod
    def from_string(cls, target_str: str) -> "Target":
        """Create a Target from a string shorthand format.
        
        Args:
            target_str: String in format "type:value", e.g., "agent:agent-123"
            
        Returns:
            Target instance
            
        Raises:
            ValueError: If the format is invalid
        """
        if ":" not in target_str:
            raise ValueError(f"Invalid target format: {target_str}. Expected 'type:value'")
        
        type_str, value = target_str.split(":", 1)
        try:
            target_type = TargetType(type_str)
        except ValueError:
            raise ValueError(f"Invalid target type: {type_str}")
        
        return cls(type=target_type, value=value)

    def to_shorthand(self) -> str:
        """Convert target to shorthand string format."""
        return f"{self.type.value}:{self.value}"

    def __str__(self) -> str:
        return f"Target({self.to_shorthand()})"


class Message(BaseModel):
    """Base message model for all message types.
    
    All messages in the bus follow this structure.
    """

    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    trace_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    msg_type: MessageType = Field(..., description="Type of message")
    source: str = Field(..., description="Source agent or system identifier")
    target: Target = Field(..., description="Message destination")
    payload: dict[str, Any] = Field(default_factory=dict)
    correlation_id: Optional[str] = Field(default=None, description="For matching request-response pairs")
    timestamp: int = Field(default_factory=lambda: int(time.time()))
    ttl: int = Field(default=60, ge=0, description="Time to live in seconds, 0 means no expiration")
    priority: int = Field(default=0, ge=0, description="Message priority (higher = more urgent)")

    def to_dict(self) -> dict[str, Any]:
        """Serialize message to dictionary."""
        data = self.model_dump()
        # Convert enums to values
        data["msg_type"] = self.msg_type.value
        data["target"] = {"type": self.target.type.value, "value": self.target.value}
        return data

    def to_json(self) -> str:
        """Serialize message to JSON string."""
        return json.dumps(self.to_dict(), ensure_ascii=False)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Message":
        """Deserialize message from dictionary."""
        if isinstance(data.get("msg_type"), str):
            data["msg_type"] = MessageType(data["msg_type"])
        if isinstance(data.get("target"), dict):
            data["target"]["type"] = TargetType(data["target"]["type"])
        return cls(**data)

    @classmethod
    def from_json(cls, json_str: str) -> "Message":
        """Deserialize message from JSON string."""
        return cls.from_dict(json.loads(json_str))

    def is_expired(self) -> bool:
        """Check if message has expired based on TTL."""
        if self.ttl == 0:
            return True
        return (time.time() - self.timestamp) > self.ttl

    def with_trace_id(self, trace_id: str) -> "Message":
        """Create a copy of this message with a new trace_id."""
        data = self.model_dump()
        data["trace_id"] = trace_id
        return Message.from_dict(data)

    def with_correlation_id(self, correlation_id: str) -> "Message":
        """Create a copy of this message with a correlation_id."""
        data = self.model_dump()
        data["correlation_id"] = correlation_id
        return Message.from_dict(data)


T = TypeVar("T", bound=Message)


class TaskRequest(Message):
    """Task request message sent from caller to executor agent."""

    task_name: str = Field(..., description="Name/identifier of the task")
    task_data: dict[str, Any] = Field(default_factory=dict, description="Task input data")

    def __init__(self, **data: Any) -> None:
        # Set default message type
        if "msg_type" not in data:
            data["msg_type"] = MessageType.TASK_REQUEST
        super().__init__(**data)

    def to_message(self) -> Message:
        """Convert to base Message."""
        return Message(
            msg_type=self.msg_type,
            source=self.source,
            target=self.target,
            payload={
                "task_name": self.task_name,
                "task_data": self.task_data,
            },
            correlation_id=self.correlation_id,
            trace_id=self.trace_id,
            ttl=self.ttl,
            priority=self.priority,
        )


class TaskResponse(Message):
    """Task response message sent from executor back to caller."""

    success: bool = Field(default=True, description="Whether the task succeeded")
    result: dict[str, Any] = Field(default_factory=dict, description="Task result data")
    error: Optional[str] = Field(default=None, description="Error message if failed")

    def __init__(self, **data: Any) -> None:
        if "msg_type" not in data:
            data["msg_type"] = MessageType.TASK_RESPONSE
        super().__init__(**data)

    def to_message(self) -> Message:
        """Convert to base Message."""
        return Message(
            msg_type=self.msg_type,
            source=self.source,
            target=self.target,
            payload={
                "success": self.success,
                "result": self.result,
                "error": self.error,
            },
            correlation_id=self.correlation_id,
            trace_id=self.trace_id,
            ttl=self.ttl,
            priority=self.priority,
        )


class Event(Message):
    """Event message for broadcasting state changes or notifications."""

    event_type: str = Field(..., description="Type of event")
    event_data: dict[str, Any] = Field(default_factory=dict, description="Event payload")

    def __init__(self, **data: Any) -> None:
        if "msg_type" not in data:
            data["msg_type"] = MessageType.EVENT
        super().__init__(**data)

    def to_message(self) -> Message:
        """Convert to base Message."""
        return Message(
            msg_type=self.msg_type,
            source=self.source,
            target=self.target,
            payload={
                "event_type": self.event_type,
                "event_data": self.event_data,
            },
            trace_id=self.trace_id,
            ttl=self.ttl,
            priority=self.priority,
        )


class AgentEventType(str, Enum):
    """Enumeration of agent processing event types.

    These events are published during the agent's ReAct loop execution,
    allowing upstream systems (like frontend) to track progress.
    """

    # Task lifecycle events
    TASK_RECEIVED = "agent:task_received"       # Agent received a task
    TASK_STARTED = "agent:task_started"        # Agent started processing
    TASK_COMPLETED = "agent:task_completed"     # Agent completed successfully
    TASK_FAILED = "agent:task_failed"           # Agent failed with error

    # ReAct loop events
    REACT_THINK_START = "agent:react:think_start"   # Starting LLM thinking
    REACT_THINK_END = "agent:react:think_end"       # LLM thinking complete
    REACT_ACT_START = "agent:react:act_start"       # Starting tool execution
    REACT_ACT_END = "agent:react:act_end"           # Tool execution complete
    REACT_OBSERVE_END = "agent:react:observe_end"   # Observation of tool results

    # Tool-level events
    TOOL_CALL_START = "agent:tool:call_start"   # About to call a tool
    TOOL_CALL_END = "agent:tool:call_end"       # Tool execution finished

    # LLM-level events
    LLM_REQUEST_START = "agent:llm:request_start"  # LLM API request started
    LLM_REQUEST_END = "agent:llm:request_end"      # LLM API request finished


@dataclass
class AgentEventData:
    """Data payload for agent events."""

    # Basic info
    agent_id: str
    instance_id: str
    task_id: Optional[str] = None

    # Correlation for linking to original request
    correlation_id: Optional[str] = None

    # Event-specific data
    event_message: str = ""                                    # Human-readable message
    react_step: Optional[str] = None                           # Current ReAct step
    react_iteration: int = 0                                   # Current iteration count
    tool_name: Optional[str] = None                            # Tool being executed
    tool_args: Optional[dict[str, Any]] = None                # Tool arguments
    tool_result: Optional[dict[str, Any]] = None               # Tool result (success/failure)
    llm_model: Optional[str] = None                             # LLM model used
    token_usage: Optional[dict[str, int]] = None              # Token usage stats
    duration_ms: int = 0                                       # Duration of this step
    error: Optional[str] = None                                # Error message if failed

    # Additional context
    extra: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary for serialization."""
        result = {
            "agent_id": self.agent_id,
            "instance_id": self.instance_id,
            "message": self.event_message,
        }

        # Add optional fields if present
        if self.task_id:
            result["task_id"] = self.task_id
        if self.correlation_id:
            result["correlation_id"] = self.correlation_id
        if self.react_step:
            result["react_step"] = self.react_step
        if self.react_iteration > 0:
            result["react_iteration"] = self.react_iteration
        if self.tool_name:
            result["tool_name"] = self.tool_name
        if self.tool_args:
            result["tool_args"] = self.tool_args
        if self.tool_result:
            result["tool_result"] = self.tool_result
        if self.llm_model:
            result["llm_model"] = self.llm_model
        if self.token_usage:
            result["token_usage"] = self.token_usage
        if self.duration_ms > 0:
            result["duration_ms"] = self.duration_ms
        if self.error:
            result["error"] = self.error
        if self.extra:
            result["extra"] = self.extra

        return result


# Global event emitter for agent events
class AgentEventEmitter:
    """Emits agent processing events to the message bus.

    This singleton manages event emission during agent execution.
    It publishes events to a dedicated topic that consumers (like ChatService)
    can subscribe to for tracking progress.
    """

    def __init__(self) -> None:
        """Initialize the event emitter."""
        self._bus: Any = None  # type: ignore[assignment]
        self._topic: str = "agent.events"
        self._enabled: bool = True

    def set_bus(self, bus: Any) -> None:  # type: ignore[type-arg]
        """Set the message bus for event publishing."""
        self._bus = bus
        logger.info(f"[EVENT_EMITTER] Message bus configured, topic={self._topic}")

    def disable(self) -> None:
        """Disable event emission (useful for testing)."""
        self._enabled = False

    def enable(self) -> None:
        """Enable event emission."""
        self._enabled = True

    async def emit(
        self,
        event_type: AgentEventType,
        data: AgentEventData,
    ) -> None:
        """Emit an agent event to the message bus.

        Args:
            event_type: Type of event
            data: Event data payload
        """
        if not self._enabled:
            return

        if self._bus is None:
            logger.warning(f"[EVENT_EMITTER] No bus configured, skipping event: {event_type}")
            return

        try:
            event = Event(
                source=data.instance_id,
                target=Target(type=TargetType.TOPIC, value=self._topic),
                event_type=event_type.value,
                event_data=data.to_dict(),
                correlation_id=data.correlation_id,
                trace_id=data.task_id or "",
            )

            await self._bus.publish_event(topic=self._topic, message=event)
            logger.debug(f"[EVENT_EMITTER] Emitted: {event_type.value} for {data.agent_id}")

        except Exception as e:
            logger.error(f"[EVENT_EMITTER] Failed to emit event {event_type}: {e}")


# Global singleton instance
agent_event_emitter = AgentEventEmitter()


class Command(Message):
    """Command message for control instructions (pause, cancel, etc.)."""

    command: str = Field(..., description="Command name")
    command_data: dict[str, Any] = Field(default_factory=dict, description="Command parameters")

    def __init__(self, **data: Any) -> None:
        if "msg_type" not in data:
            data["msg_type"] = MessageType.COMMAND
        super().__init__(**data)


class Query(Message):
    """Query message for requesting information."""

    query_type: str = Field(..., description="Type of query")
    query_data: dict[str, Any] = Field(default_factory=dict, description="Query parameters")

    def __init__(self, **data: Any) -> None:
        if "msg_type" not in data:
            data["msg_type"] = MessageType.QUERY
        super().__init__(**data)


class QueryResponse(Message):
    """Response to a Query message."""

    success: bool = Field(default=True, description="Whether the query succeeded")
    result: dict[str, Any] = Field(default_factory=dict, description="Query result")
    error: Optional[str] = Field(default=None, description="Error message if failed")

    def __init__(self, **data: Any) -> None:
        if "msg_type" not in data:
            data["msg_type"] = MessageType.QUERY_RESPONSE
        super().__init__(**data)


class GroupTask(BaseModel):
    """Group task for distributing subtasks to multiple agents."""

    group_id: str = Field(..., description="Group identifier")
    subtasks: list[dict[str, Any]] = Field(default_factory=list, description="List of subtasks")
    policy: AggregationPolicy = Field(default=AggregationPolicy.ALL, description="Aggregation policy")
    timeout: int = Field(default=60, description="Timeout in seconds")
