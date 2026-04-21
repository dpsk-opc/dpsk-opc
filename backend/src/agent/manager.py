"""Agent Manager for DPSK-OPC.

This module provides the AgentManager class for managing Agents.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Optional

from src.agent.defs import AgentDef, AgentHandle, AgentInstance
from src.agent.registry import AgentRegistry
from src.agent.spawner import AgentSpawner

logger = logging.getLogger(__name__)


class AgentManager:
    """Manager for Agent lifecycle and operations.

    Wraps the registry and spawner to provide a unified interface.
    """

    def __init__(
        self,
        registry: AgentRegistry,
        spawner: AgentSpawner,
        agents_root: Optional[Path] = None,
        bus: Any = None,
        llm_registry: Any = None,
        default_llm_client: Any = None,
    ) -> None:
        """Initialize the Agent Manager.

        Args:
            registry: Agent registry instance
            spawner: Agent spawner instance
            agents_root: Root directory for agent definitions
            bus: Message bus instance for agent communication
            llm_registry: LLM registry for model management
            default_llm_client: Default LLM client for agents without custom model
        """
        self.registry = registry
        self.spawner = spawner
        self.agents_root = agents_root or Path.home() / ".dpskopc" / "agents"
        self._initialized = False
        self._bus = bus
        self._llm_registry = llm_registry
        self._default_llm_client = default_llm_client
        
        # Set bus on spawner if available
        if bus is not None and hasattr(self.spawner, 'set_bus'):
            self.spawner.set_bus(bus)
        
        # Set LLM registry on spawner if available
        if llm_registry is not None and hasattr(self.spawner, 'set_llm_registry'):
            self.spawner.set_llm_registry(llm_registry)
        
        # Set default LLM client on spawner if available
        if default_llm_client is not None and hasattr(self.spawner, 'set_default_llm_client'):
            self.spawner.set_default_llm_client(default_llm_client)
    
    def set_bus(self, bus: Any) -> None:
        """Set the message bus instance.

        Args:
            bus: Message bus instance
        """
        self._bus = bus
        if hasattr(self.spawner, 'set_bus'):
            self.spawner.set_bus(bus)
    
    def set_llm_registry(self, registry: Any) -> None:
        """Set the LLM registry.
        
        Args:
            registry: LLM registry instance
        """
        self._llm_registry = registry
        if hasattr(self.spawner, 'set_llm_registry'):
            self.spawner.set_llm_registry(registry)
    
    def set_default_llm_client(self, client: Any) -> None:
        """Set the default LLM client.
        
        Args:
            client: Default LLM client
        """
        self._default_llm_client = client
        if hasattr(self.spawner, 'set_default_llm_client'):
            self.spawner.set_default_llm_client(client)

    async def initialize(self) -> None:
        """Initialize the manager and load agents."""
        if self._initialized:
            return

        logger.info(f"Initializing AgentManager with root: {self.agents_root}")

        # Load agents from directory
        self.registry.load_from_dir(self.agents_root)

        # NOTE: Do NOT auto-spawn agents at startup.
        # Each request will spawn the agent instance as needed.
        # If you need persistent agents, set max_instances > 1 in agent definition
        # and implement a different lifecycle management strategy.

        self._initialized = True
        logger.info(f"AgentManager initialized with {self.registry.count()} definitions")

    async def spawn_agent(
        self,
        agent_id: str,
        ttl_seconds: Optional[float] = None,
        task_context: Optional[dict[str, Any]] = None,
    ) -> AgentHandle:
        """Spawn an Agent instance.

        Args:
            agent_id: Agent identifier
            ttl_seconds: Optional time-to-live
            task_context: Optional task context

        Returns:
            AgentHandle for the spawned instance

        Raises:
            ValueError: If agent not found
            RuntimeError: If spawning fails
        """
        agent_def = self.registry.get(agent_id)
        if not agent_def:
            raise ValueError(f"Unknown agent_id: {agent_id}")

        logger.info(f"Spawning Agent: {agent_id}")
        return await self.spawner.spawn(
            agent_def,
            task_context=task_context,
            ttl_seconds=ttl_seconds,
        )

    async def destroy_instance(self, instance_id: str, force: bool = False) -> None:
        """Destroy an Agent instance.

        Args:
            instance_id: Instance identifier
            force: Whether to force destroy

        Raises:
            ValueError: If instance not found
        """
        instance = await self.spawner.get_instance(instance_id)
        if not instance:
            raise ValueError(f"Instance not found: {instance_id}")

        logger.info(f"Destroying instance {instance_id}, force={force}")
        if force:
            await self.spawner.destroy(instance_id)
        else:
            await self.spawner.stop(instance_id)

    async def list_instances(self, agent_id: Optional[str] = None) -> list[dict[str, Any]]:
        """List Agent instances.

        Args:
            agent_id: Optional filter by agent_id

        Returns:
            List of instance dictionaries
        """
        instances = await self.spawner.list_instances(agent_id)
        return [inst.to_dict() for inst in instances]

    def get_agent_def(self, agent_id: str) -> Optional[dict[str, Any]]:
        """Get an Agent definition.

        Args:
            agent_id: Agent identifier

        Returns:
            Agent definition dictionary or None
        """
        agent_def = self.registry.get(agent_id)
        if agent_def:
            return agent_def.to_dict()
        return None

    def list_agent_defs(self) -> list[dict[str, Any]]:
        """List all Agent definitions.

        Returns:
            List of agent definition dictionaries
        """
        return [agent.to_dict() for agent in self.registry.list_all()]

    def get_skills(self, agent_id: str) -> list[str]:
        """Get skills for an Agent.

        Args:
            agent_id: Agent identifier

        Returns:
            List of skill names
        """
        return self.registry.get_skills(agent_id)

    async def get_queue_status(self, agent_id: str) -> dict[str, Any]:
        """Get queue status for an Agent.

        Args:
            agent_id: Agent identifier

        Returns:
            Queue status dictionary
        """
        agent_def = self.registry.get(agent_id)
        if not agent_def:
            raise ValueError(f"Unknown agent_id: {agent_id}")

        queue_size = await self.spawner.get_queue_size(agent_id)
        instances = await self.spawner.list_instances(agent_id)

        return {
            "agent_id": agent_id,
            "max_instances": agent_def.max_instances,
            "current_instances": len(instances),
            "queue_size": agent_def.queue_size,
            "pending_tasks": queue_size,
        }
