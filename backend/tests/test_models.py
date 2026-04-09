"""Tests for Message Models

This module contains unit tests for the message model implementations.
"""

import json
import time
from datetime import datetime, timezone
from typing import Any

import pytest
from pydantic import ValidationError

from src.bus.models import (
    AggregationPolicy,
    Event,
    Message,
    MessageType,
    Query,
    QueryResponse,
    Target,
    TargetType,
    TaskRequest,
    TaskResponse,
)


class TestMessageType:
    """Test cases for MessageType enum."""

    def test_message_type_values(self) -> None:
        """Test that all message types have correct string values."""
        assert MessageType.TASK_REQUEST.value == "TaskRequest"
        assert MessageType.TASK_RESPONSE.value == "TaskResponse"
        assert MessageType.EVENT.value == "Event"
        assert MessageType.COMMAND.value == "Command"
        assert MessageType.QUERY.value == "Query"
        assert MessageType.QUERY_RESPONSE.value == "QueryResponse"

    def test_message_type_from_string(self) -> None:
        """Test creating MessageType from string."""
        assert MessageType("TaskRequest") == MessageType.TASK_REQUEST
        assert MessageType("Event") == MessageType.EVENT


class TestTargetType:
    """Test cases for TargetType enum."""

    def test_target_type_values(self) -> None:
        """Test that all target types have correct string values."""
        assert TargetType.AGENT.value == "agent"
        assert TargetType.TOPIC.value == "topic"
        assert TargetType.GROUP.value == "group"

    def test_target_type_from_string(self) -> None:
        """Test creating TargetType from string."""
        assert TargetType("agent") == TargetType.AGENT
        assert TargetType("topic") == TargetType.TOPIC


class TestTarget:
    """Test cases for Target model."""

    def test_target_agent(self) -> None:
        """Test creating a target for agent."""
        target = Target(type=TargetType.AGENT, value="agent-123")
        assert target.type == TargetType.AGENT
        assert target.value == "agent-123"

    def test_target_topic(self) -> None:
        """Test creating a target for topic."""
        target = Target(type=TargetType.TOPIC, value="task.progress")
        assert target.type == TargetType.TOPIC
        assert target.value == "task.progress"

    def test_target_group(self) -> None:
        """Test creating a target for group."""
        target = Target(type=TargetType.GROUP, value="compilers")
        assert target.type == TargetType.GROUP
        assert target.value == "compilers"

    def test_target_from_string(self) -> None:
        """Test creating Target from string shorthand."""
        agent_target = Target.from_string("agent:agent-123")
        assert agent_target.type == TargetType.AGENT
        assert agent_target.value == "agent-123"

        topic_target = Target.from_string("topic:task.progress")
        assert topic_target.type == TargetType.TOPIC
        assert topic_target.value == "task.progress"

        group_target = Target.from_string("group:compilers")
        assert group_target.type == TargetType.GROUP
        assert group_target.value == "compilers"

    def test_target_invalid_format(self) -> None:
        """Test that invalid format raises ValueError."""
        with pytest.raises(ValueError, match="Invalid target format"):
            Target.from_string("invalid")

    def test_target_invalid_type(self) -> None:
        """Test that invalid type raises ValidationError."""
        with pytest.raises(ValidationError):
            Target(type="invalid", value="test")

    def test_target_string_representation(self) -> None:
        """Test string representation of Target."""
        target = Target(type=TargetType.AGENT, value="agent-123")
        assert str(target) == "Target(agent:agent-123)"


class TestMessage:
    """Test cases for base Message model."""

    def test_message_creation(self) -> None:
        """Test creating a basic message."""
        message = Message(
            msg_type=MessageType.TASK_REQUEST,
            source="agent-1",
            target=Target(type=TargetType.AGENT, value="agent-2"),
            payload={"task": "compile", "code": "print('hello')"},
        )
        
        assert message.id is not None
        assert message.trace_id is not None
        assert message.msg_type == MessageType.TASK_REQUEST
        assert message.source == "agent-1"
        assert message.target.value == "agent-2"
        assert message.payload == {"task": "compile", "code": "print('hello')"}
        assert message.timestamp > 0
        assert message.ttl == 60  # default value
        assert message.priority == 0  # default value

    def test_message_with_custom_trace_id(self) -> None:
        """Test creating message with custom trace_id."""
        custom_trace = "custom-trace-123"
        message = Message(
            msg_type=MessageType.EVENT,
            source="agent-1",
            target=Target(type=TargetType.TOPIC, value="events"),
            payload={"event": "test"},
            trace_id=custom_trace,
        )
        assert message.trace_id == custom_trace

    def test_message_with_correlation_id(self) -> None:
        """Test message with correlation_id for request-response matching."""
        correlation_id = "corr-123"
        message = Message(
            msg_type=MessageType.TASK_RESPONSE,
            source="agent-2",
            target=Target(type=TargetType.AGENT, value="agent-1"),
            payload={"result": "success"},
            correlation_id=correlation_id,
        )
        assert message.correlation_id == correlation_id

    def test_message_to_dict(self) -> None:
        """Test message serialization to dictionary."""
        message = Message(
            msg_type=MessageType.TASK_REQUEST,
            source="agent-1",
            target=Target(type=TargetType.AGENT, value="agent-2"),
            payload={"task": "test"},
        )
        
        data = message.to_dict()
        assert isinstance(data, dict)
        assert data["msg_type"] == "TaskRequest"
        assert data["source"] == "agent-1"
        assert data["target"]["type"] == "agent"
        assert data["target"]["value"] == "agent-2"

    def test_message_to_json(self) -> None:
        """Test message serialization to JSON."""
        message = Message(
            msg_type=MessageType.EVENT,
            source="agent-1",
            target=Target(type=TargetType.TOPIC, value="events"),
            payload={"event": "test"},
        )
        
        json_str = message.to_json()
        data = json.loads(json_str)
        assert data["msg_type"] == "Event"
        assert data["payload"]["event"] == "test"

    def test_message_from_dict(self) -> None:
        """Test message deserialization from dictionary."""
        data = {
            "id": "msg-123",
            "trace_id": "trace-456",
            "msg_type": "TaskRequest",
            "source": "agent-1",
            "target": {"type": "agent", "value": "agent-2"},
            "payload": {"task": "test"},
            "correlation_id": None,
            "timestamp": int(time.time()),
            "ttl": 30,
            "priority": 1,
        }
        
        message = Message.from_dict(data)
        assert message.id == "msg-123"
        assert message.trace_id == "trace-456"
        assert message.msg_type == MessageType.TASK_REQUEST
        assert message.ttl == 30
        assert message.priority == 1

    def test_message_from_json(self) -> None:
        """Test message deserialization from JSON string."""
        json_str = json.dumps({
            "id": "msg-123",
            "trace_id": "trace-456",
            "msg_type": "TaskResponse",
            "source": "agent-2",
            "target": {"type": "agent", "value": "agent-1"},
            "payload": {"result": "ok"},
        })
        
        message = Message.from_json(json_str)
        assert message.id == "msg-123"
        assert message.msg_type == MessageType.TASK_RESPONSE

    def test_message_is_expired(self) -> None:
        """Test message expiration check."""
        # Message with TTL 60 seconds should not be expired immediately
        message = Message(
            msg_type=MessageType.TASK_REQUEST,
            source="agent-1",
            target=Target(type=TargetType.AGENT, value="agent-2"),
            payload={},
        )
        assert not message.is_expired()
        
        # Message with TTL 0 should be expired
        message_expired = Message(
            msg_type=MessageType.TASK_REQUEST,
            source="agent-1",
            target=Target(type=TargetType.AGENT, value="agent-2"),
            payload={},
            ttl=0,
        )
        assert message_expired.is_expired()


class TestTaskRequest:
    """Test cases for TaskRequest model."""

    def test_task_request_creation(self) -> None:
        """Test creating a TaskRequest."""
        request = TaskRequest(
            source="manager-agent",
            target=Target(type=TargetType.AGENT, value="code-compiler"),
            task_name="compile_code",
            task_data={"code": "print('hello')", "lang": "python"},
        )
        
        assert request.msg_type == MessageType.TASK_REQUEST
        assert request.source == "manager-agent"
        assert request.target.value == "code-compiler"
        assert request.task_name == "compile_code"
        assert request.task_data["lang"] == "python"

    def test_task_request_with_priority(self) -> None:
        """Test TaskRequest with priority."""
        request = TaskRequest(
            source="agent-1",
            target=Target(type=TargetType.AGENT, value="agent-2"),
            task_name="urgent-task",
            priority=10,
        )
        assert request.priority == 10

    def test_task_request_to_message(self) -> None:
        """Test converting TaskRequest to base Message."""
        request = TaskRequest(
            source="agent-1",
            target=Target(type=TargetType.AGENT, value="agent-2"),
            task_name="test",
        )
        
        message = request.to_message()
        assert message.msg_type == MessageType.TASK_REQUEST
        assert message.source == "agent-1"


class TestTaskResponse:
    """Test cases for TaskResponse model."""

    def test_task_response_creation(self) -> None:
        """Test creating a TaskResponse."""
        response = TaskResponse(
            source="code-compiler",
            target=Target(type=TargetType.AGENT, value="manager-agent"),
            correlation_id="req-123",
            result={"compiled": True, "output": "bytecode"},
        )
        
        assert response.msg_type == MessageType.TASK_RESPONSE
        assert response.correlation_id == "req-123"
        assert response.result["compiled"] is True

    def test_task_response_error(self) -> None:
        """Test TaskResponse with error."""
        response = TaskResponse(
            source="code-compiler",
            target=Target(type=TargetType.AGENT, value="manager-agent"),
            correlation_id="req-123",
            success=False,
            error="Compilation failed",
        )
        assert response.success is False
        assert response.error == "Compilation failed"


class TestEvent:
    """Test cases for Event model."""

    def test_event_creation(self) -> None:
        """Test creating an Event."""
        event = Event(
            source="agent-1",
            target=Target(type=TargetType.TOPIC, value="task.progress"),
            event_type="progress",
            event_data={"task_id": "task-123", "percent": 50},
        )
        
        assert event.msg_type == MessageType.EVENT
        assert event.event_type == "progress"
        assert event.event_data["percent"] == 50

    def test_event_to_message(self) -> None:
        """Test converting Event to base Message."""
        event = Event(
            source="agent-1",
            target=Target(type=TargetType.TOPIC, value="events"),
            event_type="test",
        )
        
        message = event.to_message()
        assert message.msg_type == MessageType.EVENT


class TestQuery:
    """Test cases for Query model."""

    def test_query_creation(self) -> None:
        """Test creating a Query."""
        query = Query(
            source="gateway",
            target=Target(type=TargetType.AGENT, value="context-store"),
            query_type="get_context",
            query_data={"agent_id": "agent-1"},
        )
        
        assert query.msg_type == MessageType.QUERY
        assert query.query_type == "get_context"


class TestQueryResponse:
    """Test cases for QueryResponse model."""

    def test_query_response_creation(self) -> None:
        """Test creating a QueryResponse."""
        response = QueryResponse(
            source="context-store",
            target=Target(type=TargetType.AGENT, value="gateway"),
            correlation_id="query-123",
            result={"context": {"state": "ready"}},
        )
        
        assert response.msg_type == MessageType.QUERY_RESPONSE
        assert response.correlation_id == "query-123"
        assert response.result["context"]["state"] == "ready"


class TestAggregationPolicy:
    """Test cases for AggregationPolicy enum."""

    def test_aggregation_policy_values(self) -> None:
        """Test that all policies have correct values."""
        assert AggregationPolicy.ALL.value == "all"
        assert AggregationPolicy.ANY.value == "any"
        assert AggregationPolicy.FIRST.value == "first"

    def test_aggregation_policy_from_string(self) -> None:
        """Test creating AggregationPolicy from string."""
        assert AggregationPolicy("all") == AggregationPolicy.ALL
        assert AggregationPolicy("any") == AggregationPolicy.ANY
        assert AggregationPolicy("first") == AggregationPolicy.FIRST
