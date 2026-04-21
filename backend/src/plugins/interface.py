"""Plugin interface definition.

All plugins must implement the Plugin abstract base class.
"""

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Any, Optional

if TYPE_CHECKING:
    from fastapi import APIRouter


class Plugin(ABC):
    """Abstract base class for all DPSK-OPC plugins.

    Plugins can provide:
    - API routers (FastAPI)
    - CLI commands
    - Services
    - Event handlers

    Example:
        ```python
        class MyPlugin(Plugin):
            @property
            def name(self) -> str:
                return "my-plugin"

            @property
            def version(self) -> str:
                return "1.0.0"

            def on_load(self) -> None:
                # Initialize plugin resources
                pass

            def on_enable(self) -> None:
                # Plugin is now active
                pass

            def on_disable(self) -> None:
                # Cleanup when disabled
                pass

            def get_api_router(self) -> Optional["APIRouter"]:
                from fastapi import APIRouter
                router = APIRouter()
                @router.get("/hello")
                def hello():
                    return {"message": "Hello from my-plugin"}
                return router
        ```
    """

    @property
    @abstractmethod
    def name(self) -> str:
        """Unique identifier for the plugin.

        This name is used for:
        - Plugin discovery and identification
        - CLI command namespace (e.g., `dpsk <plugin-name> <command>`)
        - Configuration keys (e.g., `plugins.<name>.enabled`)

        Should be lowercase with hyphens only (e.g., "admin", "slack-integration").
        """
        pass

    @property
    @abstractmethod
    def version(self) -> str:
        """Plugin version string.

        Following semantic versioning (semver.org).
        Used for compatibility checks and updates.
        """
        pass

    @property
    def description(self) -> str:
        """Human-readable description of the plugin.

        Shown in plugin listings and help commands.
        """
        return ""

    @property
    def author(self) -> str:
        """Plugin author name or organization."""
        return ""

    @property
    def dependencies(self) -> list[str]:
        """List of plugin names this plugin depends on.

        These plugins will be enabled before this plugin.
        Format: ["plugin-name"] or ["other-plugin>=1.0.0"]
        """
        return []

    def on_load(self) -> None:
        """Called when the plugin is first loaded.

        Use this for:
        - Loading configuration
        - Initializing resources
        - Registering event handlers
        - Setting up database connections

        Note: Plugin may not be fully enabled yet.
        """
        pass

    def on_enable(self) -> None:
        """Called when the plugin is enabled.

        Use this for:
        - Starting background tasks
        - Registering with external services
        - Final initialization

        This is called after on_load.
        """
        pass

    def on_disable(self) -> None:
        """Called when the plugin is disabled.

        Use this for:
        - Stopping background tasks
        - Closing connections
        - Saving state
        - Cleanup

        The plugin can be re-enabled later.
        """
        pass

    def on_unload(self) -> None:
        """Called when the plugin is completely unloaded.

        This happens when OPC is shutting down or
        the plugin is being removed.
        """
        pass

    def get_api_router(self) -> Optional["APIRouter"]:
        """Return a FastAPI router for the plugin's API endpoints.

        The router will be mounted at:
        /plugins/{plugin.name}/api/...

        Returns:
            FastAPI router instance, or None if plugin has no API.

        Example:
            ```python
            from fastapi import APIRouter, Depends

            router = APIRouter(prefix="/agents", tags=["agents"])

            @router.get("/")
            def list_agents():
                return []

            return router
            ```
        """
        return None

    def get_cli_commands(self) -> dict[str, Any]:
        """Return CLI commands provided by this plugin.

        Returns:
            Dict mapping command names to command functions/groups.

        Example:
            ```python
            return {
                "list": list_agents_cmd,
                "create": create_agent_cmd,
                "group": {"sub1": cmd1, "sub2": cmd2}
            }
            ```
        """
        return {}

    def get_middleware(self) -> list[Any]:
        """Return FastAPI middleware for this plugin.

        Returns:
            List of middleware instances.
        """
        return []

    def get_lifespan_handler(self) -> Any:
        """Return an async context manager for lifespan events.

        Useful for startup/shutdown logic that needs access
        to the app state.

        Returns:
            Async context manager, or None.
        """
        return None
