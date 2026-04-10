"""Agent API routes for DPSK-OPC.

This module provides FastAPI routes for Agent management.
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from src.agent.manager import AgentManager

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/agents", tags=["agents"])


class SpawnRequest(BaseModel):
    """Request to spawn an agent."""

    ttl_seconds: Optional[float] = None
    task_context: Optional[dict[str, Any]] = None


class InstanceResponse(BaseModel):
    """Response with instance information."""

    instance_id: str
    agent_id: str
    status: str


class AgentDefResponse(BaseModel):
    """Response with agent definition."""

    agent_id: str
    name: str
    team: Optional[str] = None
    workspace: Optional[str] = None
    skills: list[str]
    model: Optional[str] = None
    max_instances: int


def create_agent_router(manager: AgentManager) -> APIRouter:
    """Create agent router with manager reference.

    Args:
        manager: AgentManager instance

    Returns:
        Configured API router
    """

    @router.get("/defs")
    async def list_agent_defs() -> list[dict[str, Any]]:
        """List all Agent definitions.

        Returns:
            List of agent definitions
        """
        return manager.list_agent_defs()

    @router.get("/defs/{agent_id}")
    async def get_agent_def(agent_id: str) -> dict[str, Any]:
        """Get a specific Agent definition.

        Args:
            agent_id: Agent identifier

        Returns:
            Agent definition
        """
        agent_def = manager.get_agent_def(agent_id)
        if not agent_def:
            raise HTTPException(status_code=404, detail=f"Agent not found: {agent_id}")
        return agent_def

    @router.get("/instances")
    async def list_instances(agent_id: Optional[str] = Query(None)) -> list[dict[str, Any]]:
        """List all running Agent instances.

        Args:
            agent_id: Optional filter by agent_id

        Returns:
            List of instance information
        """
        return await manager.list_instances(agent_id)

    @router.post("/{agent_id}/spawn")
    async def spawn_agent(
        agent_id: str,
        request: Optional[SpawnRequest] = None,
    ) -> dict[str, Any]:
        """Spawn a new Agent instance.

        Args:
            agent_id: Agent identifier
            request: Optional spawn request

        Returns:
            Spawned instance information
        """
        try:
            ttl_seconds = request.ttl_seconds if request else None
            task_context = request.task_context if request else None

            handle = await manager.spawn_agent(agent_id, ttl_seconds, task_context)
            return {
                "instance_id": handle.instance_id,
                "agent_id": handle.agent_id,
                "status": handle.status.value,
            }
        except ValueError as e:
            raise HTTPException(status_code=404, detail=str(e))
        except RuntimeError as e:
            raise HTTPException(status_code=409, detail=str(e))

    @router.delete("/instances/{instance_id}")
    async def destroy_instance(instance_id: str, force: bool = Query(False)) -> dict[str, str]:
        """Destroy an Agent instance.

        Args:
            instance_id: Instance identifier
            force: Whether to force destroy

        Returns:
            Status message
        """
        try:
            await manager.destroy_instance(instance_id, force)
            return {"status": "destroyed", "instance_id": instance_id}
        except ValueError as e:
            raise HTTPException(status_code=404, detail=str(e))

    @router.get("/instances/{instance_id}/queue")
    async def get_queue_status(instance_id: str) -> dict[str, Any]:
        """Get queue status for an Agent.

        Args:
            instance_id: Instance identifier

        Returns:
            Queue status
        """
        # Get instance to find agent_id
        instances = await manager.list_instances()
        instance = next((i for i in instances if i["instance_id"] == instance_id), None)

        if not instance:
            raise HTTPException(status_code=404, detail=f"Instance not found: {instance_id}")

        try:
            return await manager.get_queue_status(instance["agent_id"])
        except ValueError as e:
            raise HTTPException(status_code=404, detail=str(e))

    @router.get("/{agent_id}/skills")
    async def get_agent_skills(agent_id: str) -> dict[str, Any]:
        """Get skills for an Agent.

        Args:
            agent_id: Agent identifier

        Returns:
            Skills list
        """
        skills = manager.get_skills(agent_id)
        if not skills and not manager.registry.has_agent(agent_id):
            raise HTTPException(status_code=404, detail=f"Agent not found: {agent_id}")
        return {"agent_id": agent_id, "skills": skills}

    return router
