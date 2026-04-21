"""Plugin manager for DPSK-OPC.

Handles plugin discovery, loading, enabling, and lifecycle management.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import TYPE_CHECKING, Any, Iterator, Optional

from src.plugins.interface import Plugin
from src.plugins.loader import FolderLoader, PluginLoadError, PluginLoader

if TYPE_CHECKING:
    from fastapi import APIRouter

logger = logging.getLogger(__name__)


class PluginManager:
    """Manages the plugin lifecycle for DPSK-OPC.

    Responsibilities:
    - Discover plugins from configured directories
    - Load and validate plugins
    - Track enabled/disabled state
    - Aggregate API routers and CLI commands

    Usage:
        ```python
        manager = PluginManager(
            builtin_dir=Path("src/plugins/builtin"),
            installed_dir=Path("plugins/installed")
        )

        # Discover and load all plugins
        await manager.discover_and_load()

        # Get aggregated API router
        main_router.include_router(manager.get_api_router())

        # Get CLI commands
        cli_commands = manager.get_all_cli_commands()
        ```

    Attributes:
        builtin_dir: Directory containing built-in plugins.
        installed_dir: Directory for user-installed plugins.
    """

    def __init__(
        self,
        builtin_dir: Optional[Path] = None,
        installed_dir: Optional[Path] = None,
    ):
        """Initialize the plugin manager.

        Args:
            builtin_dir: Path to built-in plugins directory.
            installed_dir: Path to installed (user) plugins directory.
        """
        # Default paths relative to src/plugins
        plugins_base = Path(__file__).parent

        self.builtin_dir = builtin_dir or (plugins_base / "builtin")
        self.installed_dir = installed_dir or (plugins_base / "installed")

        self._plugins: dict[str, Plugin] = {}
        self._enabled: set[str] = set()
        self._loaders: list[PluginLoader] = []

        # Register default loader (folder-based)
        self._register_default_loaders()

    def _register_default_loaders(self) -> None:
        """Register default plugin loaders."""
        if self.builtin_dir.exists():
            self._loaders.append(FolderLoader(self.builtin_dir))
            logger.debug(f"Registered folder loader for builtin: {self.builtin_dir}")

        if self.installed_dir.exists():
            self._loaders.append(FolderLoader(self.installed_dir))
            logger.debug(f"Registered folder loader for installed: {self.installed_dir}")

    async def discover_and_load(self) -> dict[str, Plugin]:
        """Discover and load all available plugins.

        Returns:
            Dict mapping plugin names to loaded plugin instances.
        """
        logger.info("Starting plugin discovery...")

        for loader in self._loaders:
            try:
                for plugin_name, plugin_path in loader.discover():
                    await self._load_plugin(plugin_name, loader, plugin_path)
            except Exception as e:
                logger.error(f"Error during plugin discovery: {e}")

        logger.info(f"Plugin discovery complete. Loaded: {list(self._plugins.keys())}")
        return self._plugins

    async def _load_plugin(
        self,
        plugin_name: str,
        loader: PluginLoader,
        plugin_path: Path,
    ) -> None:
        """Load a single plugin.

        Args:
            plugin_name: Unique plugin identifier.
            loader: Loader instance to use.
            plugin_path: Path to plugin directory.
        """
        try:
            plugin = loader.load(plugin_path)
            self._plugins[plugin_name] = plugin

            # Call on_load hook
            plugin.on_load()
            logger.info(f"Loaded plugin: {plugin_name} v{plugin.version}")

            # Auto-enable by default
            self.enable_plugin(plugin_name)

        except PluginLoadError as e:
            logger.error(f"Failed to load plugin '{plugin_name}': {e}")
        except Exception as e:
            logger.error(f"Unexpected error loading plugin '{plugin_name}': {e}")

    def enable_plugin(self, plugin_name: str) -> bool:
        """Enable a loaded plugin.

        Args:
            plugin_name: Plugin to enable.

        Returns:
            True if enabled, False if plugin not found.
        """
        if plugin_name not in self._plugins:
            logger.warning(f"Cannot enable unknown plugin: {plugin_name}")
            return False

        if plugin_name in self._enabled:
            logger.debug(f"Plugin already enabled: {plugin_name}")
            return True

        plugin = self._plugins[plugin_name]

        # Check dependencies
        for dep in plugin.dependencies:
            dep_name = dep.split(">")[0].strip()  # Handle version specifiers
            if dep_name not in self._enabled:
                logger.warning(f"Plugin '{plugin_name}' depends on '{dep_name}' which is not enabled")
                return False

        # Call on_enable hook
        plugin.on_enable()
        self._enabled.add(plugin_name)

        logger.info(f"Enabled plugin: {plugin_name}")
        return True

    def disable_plugin(self, plugin_name: str) -> bool:
        """Disable an enabled plugin.

        Args:
            plugin_name: Plugin to disable.

        Returns:
            True if disabled, False if not found or has dependents.
        """
        if plugin_name not in self._plugins:
            return False

        # Check if other enabled plugins depend on this one
        for other_name, other_plugin in self._plugins.items():
            if other_name != plugin_name and other_name in self._enabled:
                if plugin_name in other_plugin.dependencies:
                    logger.warning(
                        f"Cannot disable '{plugin_name}': required by '{other_name}'"
                    )
                    return False

        plugin = self._plugins[plugin_name]
        plugin.on_disable()
        self._enabled.discard(plugin_name)

        logger.info(f"Disabled plugin: {plugin_name}")
        return True

    def get_plugin(self, plugin_name: str) -> Optional[Plugin]:
        """Get a plugin by name.

        Args:
            plugin_name: Plugin identifier.

        Returns:
            Plugin instance or None.
        """
        return self._plugins.get(plugin_name)

    def list_plugins(self, enabled_only: bool = False) -> list[dict[str, Any]]:
        """List all plugins with their status.

        Args:
            enabled_only: If True, only return enabled plugins.

        Returns:
            List of plugin info dicts.
        """
        plugins = []

        for name, plugin in self._plugins.items():
            if enabled_only and name not in self._enabled:
                continue

            plugins.append({
                "name": name,
                "version": plugin.version,
                "description": plugin.description,
                "author": plugin.author,
                "enabled": name in self._enabled,
            })

        return plugins

    def get_api_router(self) -> Optional["APIRouter"]:
        """Get an aggregated API router from all enabled plugins.

        The router is structured as:
        /plugins/{plugin_name}/api/...

        Returns:
            Combined API router, or None if no plugins have routers.
        """
        from fastapi import APIRouter

        # Import here to avoid circular imports
        main_router = APIRouter(prefix="/plugins", tags=["plugins"])

        for plugin_name in sorted(self._enabled):
            plugin = self._plugins[plugin_name]
            router = plugin.get_api_router()

            if router is not None:
                # Mount each plugin's router at /plugins/{name}/api/...
                plugin_router = APIRouter(
                    prefix=f"/{plugin_name}/api",
                    tags=[f"plugin:{plugin_name}"]
                )
                plugin_router.include_router(router)
                main_router.include_router(plugin_router)

        return main_router

    def get_all_cli_commands(self) -> dict[str, Any]:
        """Aggregate CLI commands from all enabled plugins.

        Returns:
            Dict mapping command paths to command handlers.
            Structure: {"plugin_name": {"command": handler}}
        """
        commands = {}

        for plugin_name in sorted(self._enabled):
            plugin = self._plugins[plugin_name]
            plugin_commands = plugin.get_cli_commands()

            if plugin_commands:
                commands[plugin_name] = plugin_commands

        return commands

    def get_all_middleware(self) -> list[Any]:
        """Get middleware from all enabled plugins.

        Returns:
            List of middleware instances.
        """
        middleware = []

        for plugin_name in self._enabled:
            plugin = self._plugins[plugin_name]
            middleware.extend(plugin.get_middleware())

        return middleware

    @property
    def enabled_plugins(self) -> set[str]:
        """Get set of enabled plugin names."""
        return self._enabled.copy()
