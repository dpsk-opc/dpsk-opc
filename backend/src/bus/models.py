"""Message Models for DPSK-OPC Message Bus

This module defines the core message models used for agent communication.
"""

import json
import time
import uuid
from datetime import datetime
from enum import Enum
from typing import Any, Generic, Optional, TypeVar, Union

from pydantic import BaseModel, Field, field_validator


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
