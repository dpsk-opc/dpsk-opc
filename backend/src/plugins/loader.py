"""Plugin loader abstraction.

This module provides a flexible loader interface that supports different
plugin installation methods (folder, PyPI, etc.).

The Loader abstract base class defines the interface, while specific
implementations handle different installation methods.
"""

from abc import ABC, abstractmethod
from pathlib import Path
from typing import TYPE_CHECKING, Any, Iterator

if TYPE_CHECKING:
    from src.plugins.interface import Plugin


class PluginLoadError(Exception):
    """Raised when a plugin fails to load."""
    pass


class PluginLoader(ABC):
    """Abstract base class for plugin loaders.

    Plugin loaders handle the actual discovery and loading of plugins
    from different sources (folder, PyPI, etc.).

    Implementations:
        - FolderLoader: Load from local directories
        - PyPILoader: Load from PyPI packages (future)
    """

    @abstractmethod
    def discover(self) -> Iterator[tuple[str, Path]]:
        """Discover available plugins.

        Yields:
            Tuples of (plugin_name, plugin_path) for each found plugin.

        Raises:
            PluginLoadError: If discovery fails.
        """
        pass

    @abstractmethod
    def load(self, plugin_path: Path) -> "Plugin":
        """Load a plugin from the given path.

        Args:
            plugin_path: Path to the plugin directory or package.

        Returns:
            Instantiated plugin object.

        Raises:
            PluginLoadError: If loading fails (invalid structure, missing files, etc.).
        """
        pass

    @abstractmethod
    def validate(self, plugin_path: Path) -> bool:
        """Validate that a path contains a valid plugin.

        Args:
            plugin_path: Path to validate.

        Returns:
            True if valid plugin, False otherwise.
        """
        pass


class FolderLoader(PluginLoader):
    """Load plugins from local directories.

    Expected structure:
        plugin_dir/
        ├── plugin.toml          # Required: metadata
        └── src/                  # Optional: plugin source code
            └── my_plugin.py

    Or with direct Python module:
        plugin_dir/
        ├── plugin.toml
        └── __init__.py          # Plugin class defined here
    """

    def __init__(self, plugin_root: Path):
        """Initialize folder loader.

        Args:
            plugin_root: Root directory containing plugin folders.
        """
        self.plugin_root = plugin_root

    def discover(self) -> Iterator[tuple[str, Path]]:
        """Scan plugin root for valid plugin directories."""
        if not self.plugin_root.exists():
            return

        for item in self.plugin_root.iterdir():
            if not item.is_dir():
                continue

            # Skip hidden directories
            if item.name.startswith("."):
                continue

            # Validate plugin structure
            if self.validate(item):
                yield item.name, item

    def validate(self, plugin_path: Path) -> bool:
        """Check if directory contains a valid plugin.

        Requires plugin.toml to be present.
        """
        plugin_toml = plugin_path / "plugin.toml"
        return plugin_toml.exists()

    def load(self, plugin_path: Path) -> "Plugin":
        """Load plugin from directory.

        1. Parse plugin.toml for metadata
        2. Import the plugin class
        3. Instantiate and return
        """
        import importlib.util
        import toml

        # Load metadata
        metadata_path = plugin_path / "plugin.toml"
        if not metadata_path.exists():
            raise PluginLoadError(f"Missing plugin.toml in {plugin_path}")

        try:
            metadata = toml.load(metadata_path)
        except Exception as e:
            raise PluginLoadError(f"Invalid plugin.toml: {e}") from e

        plugin_info = metadata.get("plugin", {})
        entry_point = plugin_info.get("entry", "")

        if not entry_point:
            raise PluginLoadError("Missing 'entry' in plugin.toml [plugin] section")

        # Import plugin class
        try:
            plugin_class = self._import_entry_point(entry_point, plugin_path)
        except Exception as e:
            raise PluginLoadError(f"Failed to load plugin entry point '{entry_point}': {e}") from e

        # Instantiate plugin
        try:
            plugin = plugin_class()
        except Exception as e:
            raise PluginLoadError(f"Failed to instantiate plugin: {e}") from e

        return plugin

    def _import_entry_point(self, entry_point: str, plugin_path: Path) -> type:
        """Import plugin class from entry point path.

        Args:
            entry_point: Dot-separated import path (e.g., "src.plugins.builtin.admin:AdminPlugin")
            plugin_path: Path to plugin directory (for sys.path manipulation)

        Returns:
            Plugin class (not instance)
        """
        import sys
        from importlib import import_module

        if ":" not in entry_point:
            raise ValueError(f"Invalid entry point format: {entry_point}. Expected 'module:class'")

        module_path, class_name = entry_point.split(":", 1)

        # Add plugin path to sys.path temporarily
        plugin_src = plugin_path / "src"
        if plugin_src.exists():
            sys.path.insert(0, str(plugin_src))
        else:
            sys.path.insert(0, str(plugin_path))

        try:
            module = import_module(module_path)
            return getattr(module, class_name)
        finally:
            # Clean up sys.path
            if plugin_src.exists():
                sys.path.remove(str(plugin_src))
            else:
                sys.path.remove(str(plugin_path))


# Future: PyPI Loader for downloading plugins from PyPI
# class PyPILoader(PluginLoader):
#     """Load plugins from PyPI packages."""
#
#     def discover(self) -> Iterator[tuple[str, Path]]:
#         # Query PyPI for dpsk-opc-plugins
#         pass
#
#     def load(self, plugin_path: Path) -> Plugin:
#         # Install package and load
#         pass
