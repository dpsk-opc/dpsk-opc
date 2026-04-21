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
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any, Optional, cast

from src.agent.defs import AgentDef, AgentHandle, AgentInstance, InstanceStatus

logger = logging.getLogger(__name__)

# Flag to track if tools have been registered
_tools_registered = False

# Function to set bus reference in dispatch_task_tool
_set_dispatch_bus = None


def _register_builtin_tools() -> None:
    """Register all built-in tools for Tool Calling mode.

    This should be called once during initialization.
    """
    global _tools_registered

    if _tools_registered:
        return

    try:
        from src.agent.tools.registry import registry
        from src.agent.tools.scan_org_chart import scan_org_chart_tool
        from src.agent.tools.list_agents_tool import list_agents_tool
        from src.agent.tools.dispatch_task_tool import dispatch_task_tool, set_bus as set_dispatch_bus
        from src.agent.tools.meeting_room_tool import meeting_room_tool
        from src.agent.tools.file_tools import FILE_TOOLS
        from src.agent.tools.base import BaseTool

        # Register scan_org_chart tool
        registry.register(scan_org_chart_tool)

        # Register list_agents tool
        registry.register(list_agents_tool)

        # Register dispatch_task tool
        registry.register(dispatch_task_tool)

        # Register meeting_room tool
        registry.register(meeting_room_tool)

        # Register file operation tools
        for tool in FILE_TOOLS:
            registry.register(tool)  # type: ignore[arg-type]

        # Store the set_bus function for later use when bus is available
        global _set_dispatch_bus
        _set_dispatch_bus = set_dispatch_bus

        _tools_registered = True
        registered_tools = [t.name for t in registry.get_all()]
        logger.info(f"Registered built-in tools: {', '.join(registered_tools)}")
    except Exception as e:
        logger.error(f"Failed to register built-in tools: {e}")


@dataclass
class QueuedTask:
    """Represents a queued task waiting for an available instance."""
    agent_def: AgentDef
    task_context: Optional[dict[str, Any]] = None
    future: asyncio.Future[Any] = field(default_factory=lambda: asyncio.Future())
    queued_at: float = field(default_factory=time.time)
    task_name: str = "execute"
    task_data: dict[str, Any] = field(default_factory=dict)


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
        self._instance_handlers: dict[str, Any] = {}  # instance_id -> bus handler (for routing)
        
        # Queue processing state
        self._queued_tasks: dict[str, deque[QueuedTask]] = {}  # agent_id -> queued tasks with futures
        self._queue_processors: dict[str, asyncio.Task[Any]] = {}  # agent_id -> processor task
        self._started = False
        
        # Register built-in tools
        _register_builtin_tools()
        
    def set_bus(self, bus: Any) -> None:
        """Set the message bus instance."""
        self._bus = bus
        
        # Also set bus reference in dispatch_task_tool
        global _set_dispatch_bus
        if _set_dispatch_bus is not None:
            _set_dispatch_bus(bus)
            logger.info("Set bus reference in dispatch_task_tool")
        
        # Also configure the event emitter for observability
        try:
            from src.bus.models import agent_event_emitter
            agent_event_emitter.set_bus(bus)
            logger.info("Set bus reference in agent_event_emitter")
            
            # Also wire up the runner's event emitter reference
            from src.agent.runner import set_event_emitter
            set_event_emitter(agent_event_emitter)
            logger.info("Configured runner's event emitter reference")
        except Exception as e:
            logger.warning(f"Failed to configure agent_event_emitter: {e}")
    
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
        """Spawn a new Agent instance.
        
        If all instances are at max capacity, the request will be queued and
        processed automatically when an instance becomes available.
        """
        # Start queue processor if not already running
        await self._ensure_queue_processor_started()
        
        async with self._lock:
            agent_id = agent_def.agent_id

            # Initialize counters if needed
            if agent_id not in self._instance_counts:
                self._instance_counts[agent_id] = 0
            if agent_id not in self._queues:
                self._queues[agent_id] = deque()
            if agent_id not in self._queued_tasks:
                self._queued_tasks[agent_id] = deque()

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
                
                # Queue the task and return a handle that waits for processing
                queued_task = QueuedTask(
                    agent_def=agent_def,
                    task_context=task_context,
                )
                self._queued_tasks[agent_id].append(queued_task)
                logger.info(f"Task queued for {agent_id}, queue size: {len(self._queued_tasks[agent_id])}")
                
                # Create a placeholder handle for the queued task
                # This handle will resolve when the task is processed
                instance_id = f"{agent_id}-queued-{uuid.uuid4().hex[:8]}"
                sandbox_id = f"sandbox-{instance_id}"
                now = time.time()
                
                # Create a special "queued" instance
                queued_instance = AgentInstance(
                    instance_id=instance_id,
                    agent_id=agent_id,
                    sandbox_id=sandbox_id,
                    status=InstanceStatus.QUEUED,  # Special queued status
                    created_at=now,
                    last_heartbeat=now,
                )
                
                handle = AgentHandle(instance=queued_instance, agent_def=agent_def)
                # Set the queued task future so send_task can wait on it
                handle.set_queued_task(queued_task, task_context)
                
                logger.info(f"Created queued handle for {agent_id}, waiting for instance availability")
                return handle

            # Create new instance
            return await self._spawn_instance(agent_def, task_context)

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
            agent_id = instance.agent_id
            logger.info(f"Stopping instance {instance_id}")

            # Simulate graceful shutdown
            await asyncio.sleep(0.1)

            instance.status = InstanceStatus.STOPPED
            self._instance_counts[agent_id] -= 1

            # Cleanup
            if instance_id in self._handlers:
                del self._handlers[instance_id]

        # Unregister from bus (outside lock to avoid deadlock)
        if self._bus is not None:
            try:
                await self._unregister_from_bus(instance_id)
            except Exception as e:
                logger.warning(f"[_UNREGISTER] Error during unregistration: {e}")

        # Process next in queue
        await self._process_queue(agent_id)

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
            if instance_id in self._handlers:
                del self._handlers[instance_id]

            logger.info(f"Destroyed instance {instance_id}")

        # Unregister from bus (outside lock to avoid deadlock)
        if self._bus is not None:
            try:
                await self._unregister_from_bus(instance_id)
            except Exception as e:
                logger.warning(f"[_UNREGISTER] Error during unregistration: {e}")

        # Process next in queue
        await self._process_queue(agent_id)

    async def get_queue_size(self, agent_id: str) -> int:
        """Get the number of pending tasks in queue."""
        return len(self._queued_tasks.get(agent_id, deque()))

    async def _spawn_instance(
        self,
        agent_def: AgentDef,
        task_context: Optional[dict[str, Any]] = None,
    ) -> AgentHandle:
        """Spawn a new agent instance.
        
        Args:
            agent_def: Agent definition
            task_context: Optional context for the task
            
        Returns:
            AgentHandle for the spawned instance
        """
        agent_id = agent_def.agent_id
        
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
            logger.info(f"[SPAWN] About to register {agent_id} with bus")
            try:
                await self._register_with_bus(handle, task_handler)
                handle.set_bus(self._bus)
                logger.info(f"[SPAWN] Successfully registered agent {agent_id} with message bus")
            except Exception as e:
                logger.exception(f"[SPAWN] Failed to register {agent_id} with bus: {e}")
        else:
            logger.warning(f"Message bus not available, agent {agent_id} running in standalone mode")

        logger.info(f"Spawned instance {instance_id} for {agent_id}")

        return handle

    async def _ensure_queue_processor_started(self) -> None:
        """Ensure the global queue processor is running."""
        if self._started:
            return
        self._started = True
        # Start a background task to process queues
        asyncio.create_task(self._queue_processor_loop())

    async def _queue_processor_loop(self) -> None:
        """Background loop that processes queued tasks when instances become available."""
        logger.info("[QUEUE_PROC] Queue processor loop started")
        while True:
            try:
                await asyncio.sleep(0.5)  # Check every 500ms
                await self._process_all_queues()
            except asyncio.CancelledError:
                logger.info("[QUEUE_PROC] Queue processor loop cancelled")
                break
            except Exception as e:
                logger.exception(f"[QUEUE_PROC] Error in queue processor: {e}")

    async def _process_all_queues(self) -> None:
        """Process all agent queues."""
        async with self._lock:
            for agent_id, queue in list(self._queued_tasks.items()):
                if queue:
                    await self._process_next_in_queue(agent_id)

    async def _process_next_in_queue(self, agent_id: str) -> None:
        """Process the next task in the queue for an agent.
        
        This method checks if there's capacity and processes a queued task.
        """
        agent_def = None
        queued_task = None
        
        # Get the first queued task
        queue = self._queued_tasks.get(agent_id)
        if not queue:
            return
            
        # Peek at the first queued task to get agent_def
        queued_task = queue[0]
        agent_def = queued_task.agent_def
        
        # Check if we have capacity
        current_count = self._instance_counts.get(agent_id, 0)
        if current_count >= agent_def.max_instances:
            # No capacity available yet
            logger.debug(f"[QUEUE_PROC] No capacity for {agent_id}, instances={current_count}/{agent_def.max_instances}")
            return
        
        # We have capacity! Process the queued task
        queue.popleft()
        logger.info(f"[QUEUE_PROC] Processing queued task for {agent_id}, remaining in queue: {len(queue)}")
        
        try:
            # Spawn a new instance
            handle = await self._spawn_instance(agent_def, queued_task.task_context)
            
            # Execute the queued task on the new instance
            handler = handle._task_handler
            if handler:
                logger.info(f"[QUEUE_PROC] Executing queued task on instance {handle.instance_id}")
                # Cast to the expected callable type since the linter doesn't know it's async
                task_callable = cast(Callable[..., Awaitable[Any]], handler)
                result = await task_callable(queued_task.task_name, queued_task.task_data)
                
                # Resolve the future with the result
                if not queued_task.future.done():
                    queued_task.future.set_result(result)
                    
                logger.info(f"[QUEUE_PROC] Queued task completed for {agent_id}")
            else:
                logger.warning(f"[QUEUE_PROC] No task handler on handle for {agent_id}")
                
        except Exception as e:
            logger.exception(f"[QUEUE_PROC] Error processing queued task for {agent_id}: {e}")
            if queued_task and not queued_task.future.done():
                queued_task.future.set_result({
                    "success": False,
                    "error": str(e),
                    "result": {},
                })

    async def _process_queue(self, agent_id: str) -> None:
        """Process next task from queue (legacy compatibility).
        
        Called when an instance becomes free (stopped or destroyed).
        This triggers queue processing for the agent.
        """
        queue = self._queued_tasks.get(agent_id)
        if not queue:
            return
            
        if len(queue) > 0:
            logger.info(f"[PROCESS_QUEUE] Instance freed for {agent_id}, {len(queue)} tasks pending")
            await self._process_next_in_queue(agent_id)

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
                logger.error(f"[HANDLER] Instance {instance_id} not found")
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
                logger.info(
                    f"[HANDLER] Using LLM client: model={client.model} for agent {agent_def.agent_id}",
                    extra={"task_name": task_name, "instance_id": instance_id},
                )
            else:
                logger.warning(f"[HANDLER] No LLM client available for agent {agent_def.agent_id}")

            logger.info(
                f"[HANDLER] Task STARTED: '{task_name}' on agent {agent_def.agent_id}",
                extra={
                    "task_name": task_name,
                    "instance_id": instance_id,
                    "agent_id": agent_def.agent_id,
                },
            )

            try:
                # Execute the task with agent context
                result = await handle_task_request(
                    task_name=task_name,
                    task_data=task_data,
                    agent_info=agent_info,
                )
                logger.info(
                    f"[HANDLER] Task COMPLETED: '{task_name}' success={result.get('success', False)}",
                    extra={
                        "task_name": task_name,
                        "instance_id": instance_id,
                        "success": result.get("success", False),
                        "error": result.get("error"),
                    },
                )
                return result
            except Exception as e:
                logger.exception(f"[HANDLER] Task FAILED: {task_name}")
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
        """Register an agent instance with the message bus.

        Each instance subscribes to its unique instance_id, and the spawner
        routes messages to the appropriate handler based on instance_id.

        Args:
            handle: Agent handle
            task_handler: Task handler function
        """
        from src.bus.models import Target, TargetType, MessageType, TaskResponse
        import uuid

        instance_id = handle.instance_id
        agent_id = handle.agent_id
        
        logger.info(f"[_REGISTER] Starting registration for instance {instance_id} (agent: {agent_id})")

        # Store the handler for this instance
        self._instance_handlers[instance_id] = task_handler
        
        async def bus_handler(message: Any) -> Any:
            """Handle incoming bus messages for this instance.

            Args:
                message: Incoming message

            Returns:
                Response message if needed
            """
            try:
                # Extract task info
                task_name = getattr(message, 'task_name', None) or 'execute'
                task_data = getattr(message, 'task_data', None) or {}
                
                # Get handler for this instance
                handler = self._instance_handlers.get(instance_id)
                if handler is None:
                    logger.warning(f"[_REGISTER] No handler found for instance {instance_id}")
                    return None
                
                # Execute task via handler
                result = await handler(task_name, task_data)
                
                # Build response
                correlation_id = getattr(message, 'correlation_id', None) or getattr(message, 'id', None)
                
                response = TaskResponse(
                    source=instance_id,  # Use instance_id as source
                    target=Target(type=TargetType.AGENT, value=message.source) if hasattr(message, 'source') else message.target,
                    success=result.get("success", False),
                    result=result,  # Use the entire result as the output
                    error=result.get("error"),
                    correlation_id=correlation_id,
                    trace_id=getattr(message, 'trace_id', ''),
                )
                response.id = str(uuid.uuid4())
                
                return response
                
            except Exception as e:
                logger.exception(f"[_REGISTER] Bus handler error for {instance_id}: {e}")
                # Return error response
                return TaskResponse(
                    source=instance_id,
                    target=message.target if hasattr(message, 'target') else Target(type=TargetType.AGENT, value="unknown"),
                    success=False,
                    result={},
                    error=str(e),
                    correlation_id=getattr(message, 'correlation_id', None) or getattr(message, 'id', None),
                    trace_id=getattr(message, 'trace_id', ''),
                )

        # Subscribe to bus using both instance_id AND agent_id
        # This allows both instance-specific routing (keep_fit-abc123) 
        # and type-based routing (keep_fit) to work
        agent_def = handle.agent_def  # Get agent_def from handle
        targets_to_subscribe = [
            (instance_id, f"instance {instance_id}"),
            (agent_def.agent_id, f"agent type {agent_def.agent_id}"),
        ]
        
        for target_value, desc in targets_to_subscribe:
            target = Target(type=TargetType.AGENT, value=target_value)
            logger.info(f"[_REGISTER] About to subscribe {desc} to bus")
            try:
                await self._bus.subscribe(target, bus_handler)
                logger.info(f"[_REGISTER] Successfully subscribed {desc} to bus")
            except Exception as e:
                logger.error(f"[_REGISTER] Failed to subscribe {desc} to bus: {e}")
                raise
        
        logger.info(f"[_REGISTER] Registration complete for instance {instance_id} (with agent_id alias)")
    
    async def _unregister_from_bus(self, instance_id: str) -> None:
        """Unregister an agent instance from the message bus.

        Args:
            instance_id: Instance identifier
        """
        from src.bus.models import Target, TargetType
        
        logger.info(f"[_UNREGISTER] Starting unregistration for instance {instance_id}")
        
        # Get the handler for this instance
        bus_handler = self._instance_handlers.pop(instance_id, None)
        
        if bus_handler is None:
            logger.warning(f"[_UNREGISTER] No handler found for instance {instance_id}")
            return
        
        # Get agent_id for unregistering the alias
        agent_id = None
        instance = self._instances.get(instance_id)
        if instance:
            agent_id = instance.agent_id
        
        # Unsubscribe from bus using both instance_id AND agent_id
        targets_to_unsubscribe = [
            (instance_id, f"instance {instance_id}"),
        ]
        if agent_id:
            targets_to_unsubscribe.append((agent_id, f"agent type {agent_id}"))
        
        for target_value, desc in targets_to_unsubscribe:
            target = Target(type=TargetType.AGENT, value=target_value)
            try:
                await self._bus.unsubscribe(target, bus_handler)
                logger.info(f"[_UNREGISTER] Successfully unsubscribed {desc} from bus")
            except Exception as e:
                logger.warning(f"[_UNREGISTER] Failed to unsubscribe {desc}: {e}")
        
        logger.info(f"[_UNREGISTER] Unregistration complete for instance {instance_id}")
