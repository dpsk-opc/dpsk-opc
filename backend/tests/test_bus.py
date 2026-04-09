"""Tests for Message Bus Protocol

This module contains unit tests for the bus protocol and implementations.
"""

import asyncio
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from src.bus.models import (
    AggregationPolicy,
    Message,
    MessageType,
    Target,
    TargetType,
    TaskRequest,
    TaskResponse,
)
from src.bus.protocol import (
    BusError,
    DeliveryError,
    MessageBus,
    MessageStream,
    TimeoutError,
)
from src.bus.memory import InMemoryMessageBus


class TestMessageStream:
    """Test cases for MessageStream abstract interface."""

    def test_message_stream_is_abstract(self) -> None:
        """Test that MessageStream cannot be instantiated directly."""
        with pytest.raises(TypeError, match="abstract"):
            MessageStream()

    def test_message_stream_methods_are_abstract(self) -> None:
        """Test that required methods are abstract."""
        assert hasattr(MessageStream, "subscribe")
        assert hasattr(MessageStream, "unsubscribe")
        assert hasattr(MessageStream, "is_active")


class TestBusError:
    """Test cases for BusError exception."""

    def test_bus_error_creation(self) -> None:
        """Test creating BusError."""
        error = BusError("Test error message")
        assert str(error) == "Test error message"

    def test_bus_error_with_context(self) -> None:
        """Test creating BusError with context."""
        error = BusError("Test error", details={"key": "value"})
        assert error.details == {"key": "value"}


class TestTimeoutError:
    """Test cases for TimeoutError exception."""

    def test_timeout_error_creation(self) -> None:
        """Test creating TimeoutError."""
        error = TimeoutError("Request timed out", operation="request")
        assert "Request timed out" in str(error)
        assert error.operation == "request"


class TestDeliveryError:
    """Test cases for DeliveryError exception."""

    def test_delivery_error_creation(self) -> None:
        """Test creating DeliveryError."""
        error = DeliveryError("Failed to deliver", target="agent-1")
        assert "Failed to deliver" in str(error)
        assert error.target == "agent-1"


class TestMessageBusInterface:
    """Test cases for MessageBus abstract interface."""

    def test_message_bus_is_abstract(self) -> None:
        """Test that MessageBus cannot be instantiated directly."""
        with pytest.raises(TypeError, match="abstract"):
            MessageBus()

    def test_message_bus_has_required_methods(self) -> None:
        """Test that MessageBus has all required methods."""
        required_methods = [
            "request",
            "notify",
            "group_request",
            "publish_event",
            "subscribe",
            "unsubscribe",
            "health_check",
        ]
        for method in required_methods:
            assert hasattr(MessageBus, method)


class TestInMemoryMessageBus:
    """Test cases for InMemoryMessageBus implementation."""

    @pytest.fixture
    def bus(self) -> InMemoryMessageBus:
        """Create a fresh InMemoryMessageBus instance."""
        return InMemoryMessageBus()

    @pytest.mark.asyncio
    async def test_notify_single_agent(self, bus: InMemoryMessageBus) -> None:
        """Test sending notification to a single agent."""
        received: list[Message] = []
        
        async def handler(msg: Message) -> None:
            received.append(msg)
        
        # Subscribe agent
        target = Target(type=TargetType.AGENT, value="agent-1")
        await bus.subscribe(target, handler)
        
        # Send notification
        message = Message(
            msg_type=MessageType.EVENT,
            source="gateway",
            target=target,
            payload={"event": "test"},
        )
        await bus.notify(target, message)
        
        # Give some time for async processing
        await asyncio.sleep(0.01)
        
        assert len(received) == 1
        assert received[0].payload["event"] == "test"

    @pytest.mark.asyncio
    async def test_request_response(self, bus: InMemoryMessageBus) -> None:
        """Test request-response pattern."""
        async def handler(msg: Message) -> Message:
            return TaskResponse(
                source="agent-1",
                target=msg.target,
                correlation_id=msg.id,
                result={"status": "done"},
            )
        
        # Subscribe agent
        target = Target(type=TargetType.AGENT, value="agent-1")
        await bus.subscribe(target, handler)
        
        # Send request
        request = TaskRequest(
            source="caller",
            target=target,
            task_name="test-task",
        )
        response = await bus.request(target, request, timeout=5.0)
        
        assert response is not None
        assert response.result["status"] == "done"

    @pytest.mark.asyncio
    async def test_request_timeout(self, bus: InMemoryMessageBus) -> None:
        """Test request timeout."""
        target = Target(type=TargetType.AGENT, value="non-existent")
        
        request = TaskRequest(
            source="caller",
            target=target,
            task_name="test-task",
        )
        
        with pytest.raises(TimeoutError):
            await bus.request(target, request, timeout=0.1)

    @pytest.mark.asyncio
    async def test_topic_subscription(self, bus: InMemoryMessageBus) -> None:
        """Test subscribing to a topic."""
        received: list[Message] = []
        
        async def handler(msg: Message) -> None:
            received.append(msg)
        
        # Subscribe to topic
        topic = Target(type=TargetType.TOPIC, value="events.test")
        await bus.subscribe(topic, handler)
        
        # Publish event
        message = Message(
            msg_type=MessageType.EVENT,
            source="agent-1",
            target=topic,
            payload={"data": "test-event"},
        )
        await bus.publish_event(topic.value, message)
        
        await asyncio.sleep(0.01)
        
        assert len(received) == 1
        assert received[0].payload["data"] == "test-event"

    @pytest.mark.asyncio
    async def test_multiple_subscribers_same_topic(self, bus: InMemoryMessageBus) -> None:
        """Test multiple subscribers can receive same topic message."""
        received1: list[Message] = []
        received2: list[Message] = []
        
        async def handler1(msg: Message) -> None:
            received1.append(msg)
        
        async def handler2(msg: Message) -> None:
            received2.append(msg)
        
        topic = Target(type=TargetType.TOPIC, value="broadcast")
        await bus.subscribe(topic, handler1)
        await bus.subscribe(topic, handler2)
        
        message = Message(
            msg_type=MessageType.EVENT,
            source="source",
            target=topic,
            payload={},
        )
        await bus.publish_event(topic.value, message)
        
        await asyncio.sleep(0.01)
        
        assert len(received1) == 1
        assert len(received2) == 1

    @pytest.mark.asyncio
    async def test_unsubscribe(self, bus: InMemoryMessageBus) -> None:
        """Test unsubscribing from target."""
        received: list[Message] = []
        
        async def handler(msg: Message) -> None:
            received.append(msg)
        
        target = Target(type=TargetType.AGENT, value="agent-1")
        await bus.subscribe(target, handler)
        await bus.unsubscribe(target, handler)
        
        message = Message(
            msg_type=MessageType.EVENT,
            source="gateway",
            target=target,
            payload={},
        )
        await bus.notify(target, message)
        
        await asyncio.sleep(0.01)
        
        assert len(received) == 0

    @pytest.mark.asyncio
    async def test_health_check(self, bus: InMemoryMessageBus) -> None:
        """Test health check."""
        status = await bus.health_check()
        assert status["status"] == "healthy"
        assert status["backend"] == "memory"


class TestGroupRequest:
    """Test cases for group request functionality."""

    @pytest.fixture
    def bus(self) -> InMemoryMessageBus:
        """Create a fresh InMemoryMessageBus instance."""
        return InMemoryMessageBus()

    @pytest.mark.asyncio
    async def test_group_request_all_policy(self, bus: InMemoryMessageBus) -> None:
        """Test group request with ALL policy."""
        async def handler(msg: Message) -> Message:
            # Handle TaskRequest specially since task_data is a field
            if isinstance(msg, TaskRequest):
                task_data = msg.task_data
            else:
                task_data = msg.payload.get("task_data", {})
            lang = task_data.get("lang", "unknown")
            return TaskResponse(
                source="compiler",
                target=msg.target,
                correlation_id=msg.id,
                result={"compiled": True, "lang": lang},
            )
        
        # Subscribe to group
        group = Target(type=TargetType.GROUP, value="compilers")
        await bus.subscribe(group, handler)
        
        # Send group request
        subtasks = [
            {"task_data": {"lang": "python"}},
            {"task_data": {"lang": "rust"}},
        ]
        
        results = await bus.group_request(
            group=group,
            subtasks=subtasks,
            policy=AggregationPolicy.ALL,
            timeout=5.0,
        )
        
        assert len(results) == 2
        langs = {r.result.get("lang") for r in results if isinstance(r, TaskResponse)}
        assert langs == {"python", "rust"}

    @pytest.mark.asyncio
    async def test_group_request_any_policy(self, bus: InMemoryMessageBus) -> None:
        """Test group request with ANY policy."""
        call_count = 0
        
        async def handler(msg: Message) -> Message:
            nonlocal call_count
            call_count += 1
            task_data = msg.payload.get("task_data", {})
            delay = task_data.get("delay", 0)
            await asyncio.sleep(delay)
            return TaskResponse(
                source="worker",
                target=msg.target,
                correlation_id=msg.id,
                result={"delay": delay},
            )
        
        group = Target(type=TargetType.GROUP, value="workers")
        await bus.subscribe(group, handler)
        
        subtasks = [
            {"task_data": {"delay": 0.1}},
            {"task_data": {"delay": 0.05}},
            {"task_data": {"delay": 0.15}},
        ]
        
        results = await bus.group_request(
            group=group,
            subtasks=subtasks,
            policy=AggregationPolicy.ANY,
            timeout=5.0,
        )
        
        # Should return only first one
        assert len(results) == 1


class TestMessageCorrelation:
    """Test cases for message correlation."""

    @pytest.fixture
    def bus(self) -> InMemoryMessageBus:
        """Create a fresh InMemoryMessageBus instance."""
        return InMemoryMessageBus()

    @pytest.mark.asyncio
    async def test_request_response_correlation(self, bus: InMemoryMessageBus) -> None:
        """Test that request and response are properly correlated."""
        received_request_id: str | None = None
        
        async def handler(msg: Message) -> Message:
            nonlocal received_request_id
            received_request_id = msg.id
            return TaskResponse(
                source="agent",
                target=msg.target,
                correlation_id=msg.id,  # Echo the request ID
                result={"received": True},
            )
        
        target = Target(type=TargetType.AGENT, value="agent-1")
        await bus.subscribe(target, handler)
        
        request = TaskRequest(
            source="caller",
            target=target,
            task_name="test",
        )
        original_id = request.id
        
        response = await bus.request(target, request, timeout=5.0)
        
        assert received_request_id == original_id
        assert response.correlation_id == original_id
