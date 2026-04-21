"""In-Memory Message Bus Implementation

This module provides an in-memory implementation of the message bus,
suitable for development and testing.
"""

from __future__ import annotations

import asyncio
import uuid
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

from src.bus.models import (
    AggregationPolicy,
    GroupTask,
    Message,
    Target,
    TargetType,
    TaskRequest,
    TaskResponse,
)
from src.bus.protocol import (
    BusError,
    DeliveryError,
    MessageBus,
    MessageHandler,
    MessageStream,
    TimeoutError,
)
from src.utils.logging import get_logger

logger = get_logger(__name__)


@dataclass
class PendingRequest:
    """Represents a pending request waiting for response."""

    request: TaskRequest
    future: asyncio.Future[TaskResponse]
    timeout_handle: Optional[asyncio.TimerHandle] = None


@dataclass
class Subscription:
    """Represents a subscription to a target."""

    target: Target
    handler: MessageHandler
    active: bool = True


class InMemoryMessageStream(MessageStream):
    """In-memory implementation of MessageStream."""

    def __init__(self, queue: asyncio.Queue[Message]) -> None:
        """Initialize the stream.
        
        Args:
            queue: Queue to pull messages from
        """
        self._queue = queue
        self._active = False

    async def subscribe(self) -> None:
        """Start the subscription."""
        self._active = True

    async def unsubscribe(self) -> None:
        """Stop the subscription."""
        self._active = False

    def is_active(self) -> bool:
        """Check if subscription is active."""
        return self._active

    async def __aiter__(self) -> Any:
        """Iterate over messages."""
        while self._active:
            try:
                message = await asyncio.wait_for(
                    self._queue.get(),
                    timeout=1.0,
                )
                yield message
            except asyncio.TimeoutError:
                continue


class InMemoryMessageBus(MessageBus):
    """In-memory message bus implementation.
    
    This implementation provides a simple message bus that works
    entirely in memory. It's suitable for:
    - Development and testing
    - Single-process deployments
    - Low-throughput scenarios
    
    For production use, consider using the Redis or Kafka backend.
    """

    def __init__(self) -> None:
        """Initialize the in-memory message bus."""
        # Agent subscriptions: agent_id -> list of handlers
        self._agent_handlers: dict[str, list[MessageHandler]] = defaultdict(list)
        
        # Topic subscriptions: topic -> list of handlers
        self._topic_handlers: dict[str, list[MessageHandler]] = defaultdict(list)
        
        # Group subscriptions: group_id -> list of handlers
        self._group_handlers: dict[str, list[MessageHandler]] = defaultdict(list)
        
        # Pending requests: correlation_id -> PendingRequest
        self._pending_requests: dict[str, PendingRequest] = {}
        
        # Message queues for streaming
        self._queues: dict[str, asyncio.Queue[Message]] = defaultdict(
            lambda: asyncio.Queue(maxsize=1000)
        )
        
        # Lock for thread-safe operations
        self._lock = asyncio.Lock()
        
        logger.info("In-memory message bus initialized")

    async def request(
        self,
        target: Target,
        message: TaskRequest,
        timeout: float,
    ) -> TaskResponse:
        """Send a request and wait for a response."""
        if target.type != TargetType.AGENT:
            raise BusError(f"Request only supports AGENT target, got {target.type}")

        logger.info(
            f"[BUS] Sending request to {target.value}",
            extra={
                "message_id": message.id,
                "task_name": message.task_name,
                "timeout": timeout,
            },
        )

        # Create future for response
        future: asyncio.Future[TaskResponse] = asyncio.get_event_loop().create_future()
        
        # Store pending request
        pending = PendingRequest(
            request=message,
            future=future,
        )
        
        async with self._lock:
            self._pending_requests[message.id] = pending

        try:
            # Deliver the message
            await self._deliver_to_agent(target.value, message)
            
            # Wait for response
            try:
                logger.debug(f"[BUS] Waiting for response: {message.id}")
                # response = await asyncio.wait_for(future, timeout=timeout)
                response = await asyncio.wait_for(future, timeout=600)
                logger.info(
                    f"[BUS] Received response from {target.value}",
                    extra={"message_id": message.id, "success": response.success},
                )
                return response
            except asyncio.TimeoutError:
                logger.warning(
                    f"[BUS] Request timed out: {target.value}",
                    extra={"message_id": message.id, "timeout": timeout},
                )
                raise TimeoutError(
                    f"Request to {target.value} timed out after {timeout}s",
                    operation="request",
                )

        finally:
            # Clean up
            async with self._lock:
                self._pending_requests.pop(message.id, None)

    async def notify(
        self,
        target: Target,
        message: Message,
    ) -> None:
        """Send a notification without waiting for response."""
        await self._deliver_to_target(target, message)

    async def group_request(
        self,
        group: Target,
        subtasks: list[dict[str, Any]],
        policy: AggregationPolicy,
        timeout: float,
    ) -> list[TaskResponse]:
        """Send multiple subtasks to a group and aggregate results."""
        if group.type != TargetType.GROUP:
            raise BusError(f"Group request only supports GROUP target, got {group.type}")

        if not subtasks:
            return []

        # Create subtask messages
        task_messages = []
        for i, subtask in enumerate(subtasks):
            msg = TaskRequest(
                source="group-coordinator",
                target=group,
                task_name=f"subtask-{i}",
                task_data=subtask.get("task_data", subtask),
            )
            task_messages.append(msg)

        # Create futures for responses
        loop = asyncio.get_event_loop()
        futures: list[asyncio.Future[TaskResponse]] = [
            loop.create_future() for _ in task_messages
        ]
        completed: list[TaskResponse] = []
        pending_count = len(task_messages)

        async with self._lock:
            for msg, future in zip(task_messages, futures):
                self._pending_requests[msg.id] = PendingRequest(
                    request=msg,
                    future=future,
                )

        # Deliver all subtasks
        for msg in task_messages:
            await self._deliver_to_group(group.value, msg)

        # Wait for responses based on policy
        async def wait_for_responses() -> None:
            nonlocal pending_count
            
            # Wait for any future to complete
            while pending_count > 0:
                # Find completed futures
                done, _ = await asyncio.wait(
                    futures,
                    timeout=0.01,
                    return_when=asyncio.FIRST_COMPLETED,
                )
                
                # Also check for already-done futures not in 'done' set
                for future in futures:
                    if future.done() and future not in done:
                        done.add(future)
                
                # Process only the newly completed futures this iteration
                newly_done_count = len(done)
                for future in done:
                    pending_count -= 1
                    try:
                        response = future.result()
                        completed.append(response)
                        
                        if policy == AggregationPolicy.ANY:
                            # Cancel remaining and return
                            for f in futures:
                                if not f.done():
                                    f.cancel()
                            return
                        elif policy == AggregationPolicy.FIRST:
                            # Return immediately with first response
                            for f in futures:
                                if not f.done():
                                    f.cancel()
                            return
                    except Exception as e:
                        # Handle failed futures
                        if policy == AggregationPolicy.ALL:
                            # Continue waiting
                            pass
                        else:
                            # For ANY/FIRST, include error responses
                            pass
                
                # Clear done set for next iteration (don't re-process)
                done.clear()

        try:
            await asyncio.wait_for(wait_for_responses(), timeout=timeout)
        except asyncio.TimeoutError:
            # Cancel remaining futures
            for future in futures:
                if not future.done():
                    future.cancel()

        # Clean up pending requests
        async with self._lock:
            for msg in task_messages:
                self._pending_requests.pop(msg.id, None)

        return completed

    async def publish_event(
        self,
        topic: str,
        message: Message,
    ) -> None:
        """Publish an event to a topic."""
        target = Target(type=TargetType.TOPIC, value=topic)
        message.target = target
        await self._deliver_to_topic(topic, message)

    async def subscribe(
        self,
        target: Target,
        handler: MessageHandler,
    ) -> None:
        """Subscribe to messages for a target."""
        async with self._lock:
            if target.type == TargetType.AGENT:
                self._agent_handlers[target.value].append(handler)
                logger.debug(f"Subscribed handler to agent: {target.value}")
            elif target.type == TargetType.TOPIC:
                self._topic_handlers[target.value].append(handler)
                logger.debug(f"Subscribed handler to topic: {target.value}")
            elif target.type == TargetType.GROUP:
                self._group_handlers[target.value].append(handler)
                logger.debug(f"Subscribed handler to group: {target.value}")

    async def unsubscribe(
        self,
        target: Target,
        handler: MessageHandler,
    ) -> None:
        """Unsubscribe from a target."""
        async with self._lock:
            if target.type == TargetType.AGENT:
                handlers = self._agent_handlers.get(target.value, [])
                if handler in handlers:
                    handlers.remove(handler)
            elif target.type == TargetType.TOPIC:
                handlers = self._topic_handlers.get(target.value, [])
                if handler in handlers:
                    handlers.remove(handler)
            elif target.type == TargetType.GROUP:
                handlers = self._group_handlers.get(target.value, [])
                if handler in handlers:
                    handlers.remove(handler)

    async def health_check(self) -> dict[str, Any]:
        """Check the health of the bus."""
        async with self._lock:
            return {
                "status": "healthy",
                "backend": "memory",
                "agent_subscriptions": sum(
                    len(h) for h in self._agent_handlers.values()
                ),
                "topic_subscriptions": sum(
                    len(h) for h in self._topic_handlers.values()
                ),
                "group_subscriptions": sum(
                    len(h) for h in self._group_handlers.values()
                ),
                "pending_requests": len(self._pending_requests),
            }

    # === Internal Methods ===

    async def _deliver_to_target(self, target: Target, message: Message) -> None:
        """Deliver a message to the appropriate target."""
        if target.type == TargetType.AGENT:
            await self._deliver_to_agent(target.value, message)
        elif target.type == TargetType.TOPIC:
            await self._deliver_to_topic(target.value, message)
        elif target.type == TargetType.GROUP:
            await self._deliver_to_group(target.value, message)

    async def _deliver_to_agent(self, agent_id: str, message: Message) -> None:
        """Deliver a message to an agent."""
        handlers = self._agent_handlers.get(agent_id, [])
        
        if not handlers:
            logger.warning(f"[BUS] No handlers for agent: {agent_id}")
            return

        logger.info(
            f"[BUS] Delivering message to agent {agent_id}",
            extra={
                "message_id": message.id,
                "handler_count": len(handlers),
            },
        )

        # Execute handlers
        for i, handler in enumerate(handlers):
            logger.info(f"[BUS] Executing handler {i+1}/{len(handlers)} for agent {agent_id}")
            logger.info(f"[BUS] Handler type: {type(handler)}, is_coroutine: {asyncio.iscoroutinefunction(handler)}")
            try:
                if asyncio.iscoroutinefunction(handler):
                    logger.info(f"[BUS] Calling async handler...")
                    result = await handler(message)
                else:
                    logger.info(f"[BUS] Calling sync handler...")
                    result = handler(message)
                
                logger.info(f"[BUS] Handler {i+1} returned: type={type(result)}, is_message={isinstance(result, Message) if result else False}")
                
                # If handler returns a response, handle it
                if result is not None and isinstance(result, Message):
                    logger.info(f"[BUS] Handling response from handler {i+1}")
                    await self._handle_response(result)
                else:
                    logger.warning(f"[BUS] Handler {i+1} returned non-Message or None, not handling as response")
                    
            except Exception as e:
                logger.error(f"[BUS] Handler {i+1} error: {e}", exc_info=True)

    async def _deliver_to_topic(self, topic: str, message: Message) -> None:
        """Deliver a message to a topic (broadcast)."""
        handlers = self._topic_handlers.get(topic, [])
        
        if not handlers:
            logger.debug(f"No subscribers for topic: {topic}")
            return

        # Deliver to all subscribers
        for handler in handlers:
            try:
                if asyncio.iscoroutinefunction(handler):
                    await handler(message)
                else:
                    handler(message)
            except Exception as e:
                logger.error(f"Topic handler error: {e}", exc_info=True)

    async def _deliver_to_group(self, group_id: str, message: Message) -> None:
        """Deliver a message to a group (load balanced)."""
        handlers = self._group_handlers.get(group_id, [])
        
        if not handlers:
            logger.warning(f"No handlers for group: {group_id}")
            if isinstance(message, TaskRequest):
                error_response = TaskResponse(
                    source=group_id,
                    target=message.target,
                    correlation_id=message.id,
                    success=False,
                    error=f"No handler registered for group: {group_id}",
                )
                await self._handle_response(error_response)
            return

        # Load balance: pick one handler (round-robin would be better in production)
        handler = handlers[0]
        
        try:
            if asyncio.iscoroutinefunction(handler):
                result = await handler(message)
            else:
                result = handler(message)
            
            if result is not None and isinstance(result, Message):
                await self._handle_response(result)
        except Exception as e:
            logger.error(f"Group handler error: {e}", exc_info=True)

    async def _handle_response(self, response: Message) -> None:
        """Handle a response message by matching it to a pending request."""
        logger.info(f"[_HANDLE_RESPONSE] Processing response: correlation_id={response.correlation_id}")
        
        if response.correlation_id is None:
            logger.warning("[_HANDLE_RESPONSE] Response without correlation_id received")
            return

        async with self._lock:
            pending = self._pending_requests.get(response.correlation_id)
        
        if pending is None:
            logger.warning(f"[_HANDLE_RESPONSE] No pending request for correlation_id: {response.correlation_id}")
            logger.info(f"[_HANDLE_RESPONSE] Available pending requests: {list(self._pending_requests.keys())}")
            return

        if not pending.future.done():
            logger.info(f"[_HANDLE_RESPONSE] Setting result for correlation_id: {response.correlation_id}")
            if isinstance(response, TaskResponse):
                pending.future.set_result(response)
            else:
                pending.future.set_exception(
                    BusError(f"Unexpected response type: {type(response)}")
                )
        else:
            logger.warning(f"[_HANDLE_RESPONSE] Future already done for correlation_id: {response.correlation_id}")
