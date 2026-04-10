"""Message Bus Protocol for DPSK-OPC

This module defines the abstract interfaces for the message bus system.
"""

from abc import ABC, abstractmethod
from typing import Any, AsyncIterator, Optional, Union

from src.bus.models import (
    AggregationPolicy,
    GroupTask,
    Message,
    Target,
    TaskRequest,
    TaskResponse,
)


class BusError(Exception):
    """Base exception for bus-related errors."""

    def __init__(self, message: str, details: Optional[dict[str, Any]] = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details or {}


class TimeoutError(BusError):
    """Exception raised when an operation times out."""

    def __init__(self, message: str, operation: str = "unknown") -> None:
        super().__init__(message)
        self.operation = operation


class DeliveryError(BusError):
    """Exception raised when message delivery fails."""

    def __init__(self, message: str, target: str = "unknown") -> None:
        super().__init__(message)
        self.target = target


class MessageStream(ABC):
    """Abstract interface for message streams.
    
    A message stream represents an async iterator of messages,
    typically used for subscribing to topics or agents.
    """

    @abstractmethod
    async def subscribe(self) -> None:
        """Start the subscription."""
        raise NotImplementedError

    @abstractmethod
    async def unsubscribe(self) -> None:
        """Stop the subscription."""
        raise NotImplementedError

    @abstractmethod
    def is_active(self) -> bool:
        """Check if the subscription is active."""
        raise NotImplementedError

    async def __aiter__(self) -> AsyncIterator[Message]:
        """Iterate over messages asynchronously."""
        raise NotImplementedError


class MessageHandler:
    """Type alias for message handlers.
    
    A message handler is a callable that takes a Message and returns
    either None (for fire-and-forget) or a Message (for responses).
    """

    HandlerResult = Optional[Message]

    def __call__(self, message: Message) -> HandlerResult:
        raise NotImplementedError


class MessageBus(ABC):
    """Abstract interface for the message bus.
    
    This defines the core operations supported by all message bus
    implementations.
    """

    # === Point-to-Point Communication ===

    @abstractmethod
    async def request(
        self,
        target: Target,
        message: TaskRequest,
        timeout: float,
    ) -> TaskResponse:
        """Send a request and wait for a response.
        
        Args:
            target: Target agent or group
            message: Request message
            timeout: Timeout in seconds
            
        Returns:
            Response message
            
        Raises:
            TimeoutError: If the request times out
            DeliveryError: If the message cannot be delivered
        """
        raise NotImplementedError

    @abstractmethod
    async def notify(
        self,
        target: Target,
        message: Message,
    ) -> None:
        """Send a notification without waiting for response.
        
        Args:
            target: Target agent, topic, or group
            message: Message to send
        """
        raise NotImplementedError

    # === Group Task Operations ===

    @abstractmethod
    async def group_request(
        self,
        group: Target,
        subtasks: list[dict[str, Any]],
        policy: AggregationPolicy,
        timeout: float,
    ) -> list[TaskResponse]:
        """Send multiple subtasks to a group and aggregate results.
        
        Args:
            group: Target group
            subtasks: List of subtask data
            policy: Aggregation policy (ALL, ANY, FIRST)
            timeout: Timeout in seconds
            
        Returns:
            List of responses based on aggregation policy
            
        Raises:
            TimeoutError: If the operation times out
        """
        raise NotImplementedError

    # === Publish-Subscribe ===

    @abstractmethod
    async def publish_event(
        self,
        topic: str,
        message: Message,
    ) -> None:
        """Publish an event to a topic.
        
        All subscribers to the topic will receive the message.
        
        Args:
            topic: Topic name
            message: Event message
        """
        raise NotImplementedError

    @abstractmethod
    async def subscribe(
        self,
        target: Target,
        handler: MessageHandler,
    ) -> None:
        """Subscribe to messages for a target.
        
        Args:
            target: Target (agent, topic, or group) to subscribe to
            handler: Callback function to handle messages
        """
        raise NotImplementedError

    @abstractmethod
    async def unsubscribe(
        self,
        target: Target,
        handler: MessageHandler,
    ) -> None:
        """Unsubscribe from a target.
        
        Args:
            target: Target to unsubscribe from
            handler: Handler to remove
        """
        raise NotImplementedError

    # === Health and Management ===

    @abstractmethod
    async def health_check(self) -> dict[str, Any]:
        """Check the health of the bus and its connections.
        
        Returns:
            Health status dictionary
        """
        raise NotImplementedError


class StreamMessageBus(MessageBus):
    """MessageBus implementation that also supports streaming subscriptions.
    
    This extends the base MessageBus with the ability to create
    message streams for reactive programming patterns.
    """

    @abstractmethod
    async def create_stream(
        self,
        target: Target,
    ) -> MessageStream:
        """Create a message stream for a target.
        
        Args:
            target: Target to create stream for
            
        Returns:
            MessageStream instance
        """
        raise NotImplementedError

    @abstractmethod
    async def send_to_stream(
        self,
        stream_id: str,
        message: Message,
    ) -> None:
        """Send a message to a specific stream.
        
        Args:
            stream_id: Stream identifier
            message: Message to send
        """
        raise NotImplementedError
