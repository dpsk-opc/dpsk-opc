"""Agent management API routes.

Provides REST API endpoints for agent CRUD operations.
Design: Simple API working directly with raw markdown content.
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel, Field

from src.plugins.builtin.admin.services.agent_service import (
    AgentService,
    AgentServiceError,
    ValidationError,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/agents", tags=["agents"])


# ==================== Request/Response Models ====================

class CreateAgentRequest(BaseModel):
    """Request model for creating an agent."""
    agent_id: str = Field(..., description="Unique agent identifier (can include team prefix)")
    markdown: str = Field(..., description="Raw markdown content (should include frontmatter)")

    class Config:
        json_schema_extra = {
            "example": {
                "agent_id": "engineering/frontend",
                "markdown": """---
name: 前端工程师
description: 专业的前端开发工程师
skills:
  - react
  - typescript
model_config:
  temperature: 0.7
---

# 前端工程师

你是一个专业的前端工程师，擅长 React、TypeScript 等技术栈。."""
            }
        }


class UpdateAgentRequest(BaseModel):
    """Request model for updating an agent."""
    markdown: str = Field(..., description="New raw markdown content")


class CreateTeamRequest(BaseModel):
    """Request model for creating a team."""
    team_name: str = Field(..., description="Team name (can be nested like 'engineering/backend')")


class AgentResponse(BaseModel):
    """Response model for a single agent."""
    agent_id: str
    markdown: str


class AgentListItem(BaseModel):
    """Response model for agent list item."""
    agent_id: str
    name: str
    team: str = ""


class TeamResponse(BaseModel):
    """Response model for a team."""
    name: str
    path: str
    agent_count: int = 0


class MessageResponse(BaseModel):
    """Generic message response."""
    agent_id: Optional[str] = None
    team: Optional[str] = None
    message: str


# ==================== Service Instance ====================

_service: Optional[AgentService] = None


def get_service() -> AgentService:
    """Get or create the agent service singleton."""
    global _service
    if _service is None:
        _service = AgentService()
    return _service


def set_service(service: AgentService) -> None:
    """Set the agent service instance (for testing)."""
    global _service
    _service = service


# ==================== API Routes ====================

@router.get("/", response_model=list[AgentListItem])
async def list_agents(
    team: Optional[str] = Query(default=None, description="Filter by team"),
) -> list[dict[str, Any]]:
    """List all agents.

    Returns a list of agents, optionally filtered by team.
    Only returns id, name, and team - not the full markdown.
    """
    service = get_service()
    return service.list_agents(team=team)


# ==================== Team Routes (must be before /{agent_id:path}) ====================

@router.get("/teams/", response_model=list[TeamResponse])
async def list_teams() -> list[dict[str, Any]]:
    """List all teams (directories containing agents)."""
    service = get_service()
    return service.list_teams()


@router.post("/teams/", response_model=MessageResponse, status_code=status.HTTP_201_CREATED)
async def create_team(request: CreateTeamRequest) -> dict[str, str]:
    """Create a new team directory.

    Teams are used to organize agents into logical groups.
    The team directory will be created under the agents root.
    """
    service = get_service()

    try:
        return service.create_team(request.team_name)
    except ValidationError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except AgentServiceError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))


@router.delete("/teams/{team_name:path}", response_model=MessageResponse)
async def delete_team(
    team_name: str,
    force: bool = Query(default=False, description="Force delete even if not empty"),
) -> dict[str, str]:
    """Delete a team directory.

    By default, only empty teams can be deleted.
    Use force=true to delete teams containing agents.
    The 'team_name:path' syntax allows '/' in the parameter.
    """
    service = get_service()

    try:
        return service.delete_team(team_name, force=force)
    except AgentServiceError as e:
        detail = str(e)
        if "not found" in detail.lower():
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=detail)
        if "not empty" in detail.lower():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=detail)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=detail)


# ==================== Agent CRUD Routes ====================

@router.get("/{agent_id:path}", response_model=AgentResponse)
async def get_agent(agent_id: str) -> dict[str, Any]:
    """Get a single agent by ID.

    Returns the raw markdown content of the agent definition.
    The 'agent_id:path' syntax allows '/' in the parameter.
    """
    service = get_service()
    
    logger.debug(f"Getting agent with id: {agent_id}")
    
    try:
        return service.get_agent(agent_id)
    except AgentServiceError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))


@router.post("/", response_model=MessageResponse, status_code=status.HTTP_201_CREATED)
async def create_agent(request: CreateAgentRequest) -> dict[str, str]:
    """Create a new agent.

    The agent_id can include a team prefix (e.g., "engineering/frontend").
    The markdown field should contain the full agent definition including
    frontmatter and body content.
    """
    service = get_service()

    try:
        return service.create_agent(
            agent_id=request.agent_id,
            markdown=request.markdown,
        )
    except ValidationError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except AgentServiceError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))


@router.put("/{agent_id:path}", response_model=MessageResponse)
async def update_agent(
    agent_id: str,
    request: UpdateAgentRequest,
) -> dict[str, str]:
    """Update an existing agent.

    Replaces the entire markdown content of the agent.
    The 'agent_id:path' syntax allows '/' in the parameter.
    """
    service = get_service()

    try:
        return service.update_agent(
            agent_id=agent_id,
            markdown=request.markdown,
        )
    except ValidationError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except AgentServiceError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))


@router.delete("/{agent_id:path}", response_model=MessageResponse)
async def delete_agent(agent_id: str) -> dict[str, str]:
    """Delete an agent.

    This will permanently delete the agent definition file.
    The 'agent_id:path' syntax allows '/' in the parameter.
    """
    service = get_service()

    try:
        return service.delete_agent(agent_id)
    except AgentServiceError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
