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

    def __init__(self) -> None:
        """Initialize the spawner."""
        self._instances: dict[str, AgentInstance] = {}
        self._handles: dict[str, AgentHandle] = {}
        self._queues: dict[str, deque[dict[str, Any]]] = {}  # agent_id -> pending tasks
        self._instance_counts: dict[str, int] = {}  # agent_id -> current count
        self._lock = asyncio.Lock()

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
            self._handles[instance_id] = AgentHandle(instance=instance, agent_def=agent_def)
            self._instance_counts[agent_id] += 1

            logger.info(f"Spawned instance {instance_id} for {agent_id}")

            return self._handles[instance_id]

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
