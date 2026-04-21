"""Admin plugin for DPSK-OPC.

Provides agent administration and management capabilities.
"""

from typing import TYPE_CHECKING

from src.plugins.builtin.admin.api.agents import router as agents_router
from src.plugins.builtin.admin.services.agent_service import AgentService
from src.plugins.interface import Plugin

if TYPE_CHECKING:
    from fastapi import APIRouter


class AdminPlugin(Plugin):
    """Agent administration plugin.

    Provides:
    - REST API for agent CRUD operations
    - Team (directory) management
    - CLI commands (future)

    API Endpoints:
    - GET    /plugins/admin/api/agents/           - List agents
    - GET    /plugins/admin/api/agents/{id}       - Get agent
    - POST   /plugins/admin/api/agents/            - Create agent
    - PUT    /plugins/admin/api/agents/{id}       - Update agent
    - DELETE /plugins/admin/api/agents/{id}       - Delete agent
    - GET    /plugins/admin/api/agents/teams/     - List teams
    - POST   /plugins/admin/api/agents/teams/     - Create team
    - DELETE /plugins/admin/api/agents/teams/{name} - Delete team
    """

    @property
    def name(self) -> str:
        return "admin"

    @property
    def version(self) -> str:
        return "0.1.0"

    @property
    def description(self) -> str:
        return "Agent administration and management"

    @property
    def author(self) -> str:
        return "dpsk-team"

    def get_api_router(self) -> "APIRouter":
        """Return the API router for agent management."""
        return agents_router

    def get_cli_commands(self) -> dict:
        """Return CLI commands (future implementation)."""
        # TODO: Implement CLI commands
        return {}

    def on_enable(self) -> None:
        """Called when the plugin is enabled."""
        # Initialize service with configured agents root
        # This will be picked up by the API routes
        pass
