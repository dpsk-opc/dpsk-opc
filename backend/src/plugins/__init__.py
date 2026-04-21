"""Plugin system for DPSK-OPC.

This module provides a plugin architecture that allows extending OPC functionality
through downloadable plugin packages.
"""

from src.plugins.interface import Plugin
from src.plugins.manager import PluginManager

__all__ = ["Plugin", "PluginManager"]

# For backwards compatibility with absolute imports
import src.plugins.interface as interface
import src.plugins.manager as manager

globals()["Plugin"] = Plugin
globals()["PluginManager"] = PluginManager
