"""Echo skill for testing the Agent framework."""

from __future__ import annotations

from typing import Any


async def run(params: Any) -> dict[str, Any]:
    """Echo skill - returns the input parameters.

    This is a simple test skill that echoes back the input.
    Useful for testing the skill execution framework.

    Args:
        params: Input parameters

    Returns:
        Echoed parameters
    """
    if isinstance(params, dict):
        return {"echo": params, "status": "success"}
    return {"echo": {"value": params}, "status": "success"}
