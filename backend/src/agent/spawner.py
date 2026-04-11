"""Agent Spawner for DPSK-OPC.

This module provides the AgentSpawner class for creating and managing Agent instances.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from abc import ABC, abstractmethod
from collections import deque
from typing import Any, Optional

from src.agent.defs import AgentDef, AgentHandle, AgentInstance, InstanceStatus

logger = logging.getLogger(__name__)


class AgentSpawner(ABC):
    """Abstract base class for Agent spawning.

    Implementations should integrate with the security module for sandbox creation.
    """

    @abstractmethod
    async def spawn(
        self,
        agent_def: AgentDef,
        task_context: Optional[dict[str, Any]] = None,
        reuse_existing: bool = False,
        ttl_seconds: Optional[float] = None,
    ) -> AgentHandle:
        """Spawn a new Agent instance.

        Args:
            agent_def: Agent definition
            task_context: Optional context for the task
            reuse_existing: Whether to reuse an existing instance
            ttl_seconds: Time-to-live for the instance

        Returns:
            AgentHandle for the spawned instance

        Raises:
            RuntimeError: If spawning fails
        """
        raise NotImplementedError

    @abstractmethod
    async def get_instance(self, instance_id: str) -> Optional[AgentInstance]:
        """Get an instance by ID.

        Args:
            instance_id: Instance identifier

        Returns:
            AgentInstance or None
        """
        raise NotImplementedError

    @abstractmethod
    async def list_instances(self, agent_id: Optional[str] = None) -> list[AgentInstance]:
        """List all instances.

        Args:
            agent_id: Optional filter by agent_id

        Returns:
            List of AgentInstance
        """
        raise NotImplementedError

    @abstractmethod
    async def stop(self, instance_id: str) -> None:
        """Stop an instance gracefully.

        Args:
            instance_id: Instance identifier
        """
        raise NotImplementedError

    @abstractmethod
    async def destroy(self, instance_id: str) -> None:
        """Destroy an instance forcefully.

        Args:
            instance_id: Instance identifier
        """
        raise NotImplementedError


class LocalAgentSpawner(AgentSpawner):
    """Local implementation of AgentSpawner.

    This implementation creates in-process agent instances for testing.
    In production, this would integrate with the security module.
    """

    def __init__(
        self,
        bus: Any = None,
        llm_registry: Any = None,
        default_llm_client: Any = None,
    ) -> None:
        """Initialize the spawner.

        Args:
            bus: Message bus instance for agent communication
            llm_registry: LLM registry for getting LLM clients
            default_llm_client: Default LLM client for agents without custom model
        """
        self._instances: dict[str, AgentInstance] = {}
        self._handles: dict[str, AgentHandle] = {}
        self._queues: dict[str, deque[dict[str, Any]]] = {}  # agent_id -> pending tasks
        self._instance_counts: dict[str, int] = {}  # agent_id -> current count
        self._lock = asyncio.Lock()
        self._bus = bus
        self._llm_registry = llm_registry
        self._default_llm_client = default_llm_client
        self._handlers: dict[str, Any] = {}  # instance_id -> handler function
        
    def set_bus(self, bus: Any) -> None:
        """Set the message bus instance."""
        self._bus = bus
    
    def set_llm_registry(self, registry: Any) -> None:
        """Set the LLM registry.
        
        Args:
            registry: LLM registry instance
        """
        self._llm_registry = registry
    
    def set_default_llm_client(self, client: Any) -> None:
        """Set the default LLM client.
        
        Args:
            client: Default LLM client
        """
        self._default_llm_client = client

    async def spawn(
        self,
        agent_def: AgentDef,
        task_context: Optional[dict[str, Any]] = None,
        reuse_existing: bool = False,
        ttl_seconds: Optional[float] = None,
    ) -> AgentHandle:
        """Spawn a new Agent instance."""
        async with self._lock:
            agent_id = agent_def.agent_id

            # Initialize counters if needed
            if agent_id not in self._instance_counts:
                self._instance_counts[agent_id] = 0
            if agent_id not in self._queues:
                self._queues[agent_id] = deque()

            # Check concurrent instance limit
            current_count = self._instance_counts[agent_id]
            if current_count >= agent_def.max_instances:
                # Check queue
                queue = self._queues[agent_id]
                if len(queue) >= agent_def.queue_size:
                    raise RuntimeError(
                        f"Agent {agent_id} at max capacity: "
                        f"{current_count} instances, queue full ({agent_def.queue_size})"
                    )
                # Queue the task
                queue.append({"agent_def": agent_def, "context": task_context})
                logger.info(f"Task queued for {agent_id}, queue size: {len(queue)}")
                raise RuntimeError(f"Task queued for {agent_id}")

            # Create new instance
            instance_id = f"{agent_id}-{uuid.uuid4().hex[:8]}"
            sandbox_id = f"sandbox-{instance_id}"
            now = time.time()

            instance = AgentInstance(
                instance_id=instance_id,
                agent_id=agent_id,
                sandbox_id=sandbox_id,
                status=InstanceStatus.CREATING,
                created_at=now,
                last_heartbeat=now,
            )

            # Simulate sandbox creation
            await asyncio.sleep(0.1)  # Simulate async operation

            instance.status = InstanceStatus.RUNNING
            self._instances[instance_id] = instance
            handle = AgentHandle(instance=instance, agent_def=agent_def)
            self._handles[instance_id] = handle
            self._instance_counts[agent_id] += 1

            # Create task handler for this instance
            task_handler = self._create_task_handler(instance_id, agent_def)
            self._handlers[instance_id] = task_handler
            
            # Set handler on handle for fallback
            handle.set_task_handler(task_handler)
            
            # Register with bus if available
            if self._bus is not None:
                await self._register_with_bus(handle, task_handler)
                handle.set_bus(self._bus)
                logger.info(f"Registered agent {agent_id} with message bus")
            else:
                logger.warning(f"Message bus not available, agent {agent_id} running in standalone mode")

            logger.info(f"Spawned instance {instance_id} for {agent_id}")

            return handle

    async def get_instance(self, instance_id: str) -> Optional[AgentInstance]:
        """Get an instance by ID."""
        return self._instances.get(instance_id)

    async def list_instances(self, agent_id: Optional[str] = None) -> list[AgentInstance]:
        """List all instances."""
        if agent_id:
            return [inst for inst in self._instances.values() if inst.agent_id == agent_id]
        return list(self._instances.values())

    async def stop(self, instance_id: str) -> None:
        """Stop an instance gracefully."""
        async with self._lock:
            instance = self._instances.get(instance_id)
            if not instance:
                raise ValueError(f"Instance not found: {instance_id}")

            instance.status = InstanceStatus.STOPPING
            logger.info(f"Stopping instance {instance_id}")

            # Simulate graceful shutdown
            await asyncio.sleep(0.1)

            instance.status = InstanceStatus.STOPPED
            self._instance_counts[instance.agent_id] -= 1

            # Process next in queue
            await self._process_queue(instance.agent_id)

    async def destroy(self, instance_id: str) -> None:
        """Destroy an instance forcefully."""
        async with self._lock:
            instance = self._instances.get(instance_id)
            if not instance:
                raise ValueError(f"Instance not found: {instance_id}")

            agent_id = instance.agent_id
            instance.status = InstanceStatus.FAILED
            self._instance_counts[agent_id] -= 1

            # Cleanup
            if instance_id in self._instances:
                del self._instances[instance_id]
            if instance_id in self._handles:
                del self._handles[instance_id]

            logger.info(f"Destroyed instance {instance_id}")

            # Process next in queue
            await self._process_queue(agent_id)

    async def get_queue_size(self, agent_id: str) -> int:
        """Get the number of pending tasks in queue."""
        return len(self._queues.get(agent_id, deque()))

    async def _process_queue(self, agent_id: str) -> None:
        """Process next task from queue."""
        queue = self._queues.get(agent_id)
        if not queue or not queue:
            return

        # This would be called when an instance becomes free
        # For now, just log
        logger.debug(f"Queue for {agent_id} has {len(queue)} pending tasks")

    def _create_task_handler(
        self,
        instance_id: str,
        agent_def: AgentDef,
    ) -> Any:
        """Create a task handler function for an agent instance.

        Args:
            instance_id: The instance identifier
            agent_def: The agent definition

        Returns:
            Async task handler function
        """
        # Get LLM client for this agent
        llm_client = self._get_llm_client_for_agent(agent_def)
        
        # Extract agent info for LLM calls
        agent_info = {
            "agent_id": agent_def.agent_id,
            "name": agent_def.name,
            "description": agent_def.description,  # System prompt
            "model": agent_def.model,
            "model_config": agent_def.model_config,
        }
        
        async def handle_task(task_name: str, task_data: dict[str, Any]) -> dict[str, Any]:
            """Handle incoming task requests.

            Args:
                task_name: Name of the task to execute
                task_data: Task input data

            Returns:
                Task execution result
            """
            from src.agent.runner import handle_task_request, set_llm_client

            instance = self._instances.get(instance_id)
            if instance is None:
                return {
                    "success": False,
                    "error": f"Instance {instance_id} not found",
                    "result": {},
                }

            # Update instance state
            instance.increment_task_count()
            instance.update_heartbeat()

            # Set LLM client for this agent
            client = llm_client or self._default_llm_client
            if client is not None:
                set_llm_client(client)
                logger.debug(f"Using LLM client: {client.model} for agent {agent_def.agent_id}")
            else:
                logger.warning(f"No LLM client available for agent {agent_def.agent_id}")

            logger.info(
                f"Executing task '{task_name}' on agent {agent_def.agent_id} (instance: {instance_id})",
                extra={"task_name": task_name, "instance_id": instance_id}
            )

            try:
                # Execute the task with agent context
                result = await handle_task_request(
                    task_name=task_name,
                    task_data=task_data,
                    agent_info=agent_info,
                )
                return result
            except Exception as e:
                logger.exception(f"Task execution failed: {task_name}")
                return {
                    "success": False,
                    "error": str(e),
                    "result": {},
                }

        return handle_task
    
    def _get_llm_client_for_agent(self, agent_def: AgentDef) -> Any:
        """Get the appropriate LLM client for an agent.
        
        Priority:
        1. Agent's custom model (from agent_def.model)
        2. Default LLM client
        
        Args:
            agent_def: Agent definition
            
        Returns:
            LLM client or None
        """
        # If agent has a custom model, try to get a client for it
        if agent_def.model:
            if self._llm_registry:
                client = self._llm_registry.get_client(
                    model=agent_def.model,
                    model_config=agent_def.model_config,
                )
                if client:
                    logger.info(f"Using custom model '{agent_def.model}' for agent {agent_def.agent_id}")
                    return client
                else:
                    logger.warning(
                        f"Could not get LLM client for custom model '{agent_def.model}', "
                        f"falling back to default"
                    )
            else:
                logger.warning(f"No LLM registry available for agent {agent_def.agent_id} with custom model")
        
        # Return default client
        return self._default_llm_client

    async def _register_with_bus(
        self,
        handle: AgentHandle,
        task_handler: Any,
    ) -> None:
        """Register an agent with the message bus.

        Args:
            handle: Agent handle
            task_handler: Task handler function
        """
        from src.bus.models import Target, TargetType, MessageType, TaskResponse
        import uuid

        agent_id = handle.agent_id
        target = Target(type=TargetType.AGENT, value=agent_id)

        async def bus_handler(message: Any) -> Any:
            """Handle incoming bus messages.

            Args:
                message: Incoming message

            Returns:
                Response message if needed
            """
            try:
                # Extract task info from message
                task_name = getattr(message, 'task_name', None) or message.payload.get('task_name', 'execute')
                task_data = getattr(message, 'task_data', None) or message.payload.get('task_data', {})
                
                # Execute task via handler
                result = await task_handler(task_name, task_data)
                
                # Build response
                response = TaskResponse(
                    source=agent_id,
                    target=Target(type=TargetType.AGENT, value=message.source) if hasattr(message, 'source') else message.target,
                    success=result.get("success", False),
                    result=result.get("result", {}),
                    error=result.get("error"),
                    correlation_id=getattr(message, 'correlation_id', None) or getattr(message, 'id', None),
                    trace_id=getattr(message, 'trace_id', ''),
                )
                response.id = str(uuid.uuid4())
                
                return response
                
            except Exception as e:
                logger.exception(f"Error in bus handler for {agent_id}")
                # Return error response
                return TaskResponse(
                    source=agent_id,
                    target=message.target if hasattr(message, 'target') else Target(type=TargetType.AGENT, value="unknown"),
                    success=False,
                    result={},
                    error=str(e),
                    correlation_id=getattr(message, 'correlation_id', None),
                    trace_id=getattr(message, 'trace_id', ''),
                )

        # Subscribe to bus
        try:
            await self._bus.subscribe(target, bus_handler)
            logger.info(f"Agent {agent_id} subscribed to bus at {target}")
        except Exception as e:
            logger.error(f"Failed to subscribe agent {agent_id} to bus: {e}")
