"""Admin API package."""

from src.plugins.builtin.admin.api.agents import router as agents_router

__all__ = ["agents_router"]
