"""Agent Runner for DPSK-OPC.

This module provides the runtime framework for executing Agent tasks.
It runs inside the sandbox and connects to the message bus.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import sys
import time
from pathlib import Path
from typing import Any, Callable, Optional

logger = logging.getLogger(__name__)

# Global skill handlers registry
SKILL_HANDLERS: dict[str, Callable[[dict[str, Any]], Any]] = {}

# Configuration from environment
AGENT_ID = os.environ.get("AGENT_ID", "unknown")
MESSAGE_BUS_ADDRESS = os.environ.get("MESSAGE_BUS_ADDRESS", "memory")
TEMP_TOKEN = os.environ.get("TEMP_TOKEN", "")


def register_skill(task_name: str, handler: Callable[[dict[str, Any]], Any]) -> None:
    """Register a skill handler.

    Args:
        task_name: Unique identifier for the skill
        handler: Async function that takes task_data and returns result
    """
    SKILL_HANDLERS[task_name] = handler
    logger.info(f"Registered skill: {task_name}")


async def load_skills_from_dir(dir_path: Path) -> int:
    """Dynamically load all skill modules from a directory.

    Args:
        dir_path: Directory containing skill modules

    Returns:
        Number of skills loaded
    """
    import importlib.util

    if not dir_path.exists():
        logger.warning(f"Skills directory does not exist: {dir_path}")
        return 0

    count = 0
    for file_path in dir_path.glob("*.py"):
        if file_path.name.startswith("_"):
            continue

        try:
            # Load module dynamically
            spec = importlib.util.spec_from_file_location(file_path.stem, file_path)
            if spec and spec.loader:
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)

                # Look for run function
                if hasattr(module, "run"):
                    # Get task name from filename
                    task_name = file_path.stem
                    register_skill(task_name, module.run)
                    count += 1
                    logger.debug(f"Loaded skill module: {task_name}")
        except Exception as e:
            logger.error(f"Failed to load skill {file_path}: {e}")

    logger.info(f"Loaded {count} skills from {dir_path}")
    return count


async def handle_task_request(
    task_name: str,
    task_data: dict[str, Any],
    experience_db: Optional[Any] = None,
) -> dict[str, Any]:
    """Handle a task request.

    Args:
        task_name: Name of the skill to execute
        task_data: Input data for the skill
        experience_db: Optional experience database for caching

    Returns:
        Task response dictionary
    """
    start_time = time.time()
    result = {"success": True, "result": {}, "error": None}

    try:
        # 1. Try experience pool lookup (exact match)
        if experience_db:
            cached = await experience_db.lookup(AGENT_ID, task_name, task_data)
            if cached:
                logger.info(f"Experience hit for {task_name}")
                result["result"] = cached
                result["from_experience"] = True
                return result

        # 2. Execute skill handler
        handler = SKILL_HANDLERS.get(task_name)
        if not handler:
            result["success"] = False
            result["error"] = f"Unknown skill: {task_name}"
            return result

        # Execute handler
        skill_result = await handler(task_data)
        result["result"] = skill_result

        # 3. Store experience asynchronously
        if experience_db:
            asyncio.create_task(
                experience_db.store(AGENT_ID, task_name, task_data, skill_result)
            )

    except Exception as e:
        logger.exception(f"Error executing skill {task_name}")
        result["success"] = False
        result["error"] = str(e)

    finally:
        result["duration_ms"] = int((time.time() - start_time) * 1000)

    return result


async def call_security_operation(operation: str, params: dict[str, Any]) -> dict[str, Any]:
    """Call a security operation via message bus.

    Args:
        operation: Operation name (e.g., "read_file", "decrypt_secret")
        params: Operation parameters

    Returns:
        Operation result

    Raises:
        RuntimeError: If bus is not available
    """
    # This would normally send a message via bus to system.security
    # For now, return a placeholder
    logger.warning(f"Security operation requested: {operation} (bus integration pending)")
    return {"error": "Bus integration not implemented"}


async def call_llm(
    prompt: str,
    model: Optional[str] = None,
    **kwargs: Any,
) -> dict[str, Any]:
    """Call LLM via message bus.

    Args:
        prompt: The prompt to send
        model: Model to use (optional, uses Agent default)
        **kwargs: Additional model parameters

    Returns:
        LLM response

    Raises:
        RuntimeError: If bus is not available
    """
    # This would normally send a message via bus to system.llm
    logger.warning("LLM call requested (bus integration pending)")
    return {"error": "Bus integration not implemented"}


class TaskRequest:
    """Task request message (matches bus.models)."""

    def __init__(
        self,
        source: str,
        target: Any,
        task_name: str,
        task_data: dict[str, Any],
        correlation_id: Optional[str] = None,
        trace_id: Optional[str] = None,
    ) -> None:
        self.source = source
        self.target = target
        self.task_name = task_name
        self.task_data = task_data
        self.correlation_id = correlation_id
        self.trace_id = trace_id or ""
        self.id = ""
        self.timestamp = 0
        self.ttl = 60
        self.priority = 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "target": self.target.to_dict() if hasattr(self.target, "to_dict") else str(self.target),
            "task_name": self.task_name,
            "task_data": self.task_data,
            "correlation_id": self.correlation_id,
            "trace_id": self.trace_id,
        }


class TaskResponse:
    """Task response message (matches bus.models)."""

    def __init__(
        self,
        source: str,
        target: Any,
        success: bool,
        result: dict[str, Any],
        error: Optional[str] = None,
        correlation_id: Optional[str] = None,
        trace_id: Optional[str] = None,
    ) -> None:
        self.source = source
        self.target = target
        self.success = success
        self.result = result
        self.error = error
        self.correlation_id = correlation_id
        self.trace_id = trace_id or ""
        self.id = ""
        self.timestamp = 0
        self.ttl = 60
        self.priority = 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "target": self.target.to_dict() if hasattr(self.target, "to_dict") else str(self.target),
            "success": self.success,
            "result": self.result,
            "error": self.error,
            "correlation_id": self.correlation_id,
            "trace_id": self.trace_id,
        }


async def send_ready_event() -> None:
    """Send agent ready event to the bus."""
    # This would send an Event message to notify the backend
    logger.info(f"Agent {AGENT_ID} is ready")


async def send_heartbeat() -> None:
    """Send heartbeat to the bus."""
    # This would send a periodic heartbeat
    pass


async def run(skills_dir: Optional[Path] = None) -> None:
    """Main runner loop.

    This runs inside the sandbox and:
    1. Loads skills from the skills directory
    2. Registers with the message bus
    3. Processes incoming task requests
    4. Sends responses back

    Args:
        skills_dir: Path to skills directory
    """
    logger.info(f"Starting Agent Runner for {AGENT_ID}")
    logger.info(f"Bus address: {MESSAGE_BUS_ADDRESS}")

    # Load skills
    if skills_dir is None:
        skills_dir = Path("/app/skills")
    await load_skills_from_dir(skills_dir)

    # Send ready event
    await send_ready_event()

    # Main loop would listen for messages here
    # For now, just log that we're running
    logger.info(f"Agent Runner ready, registered skills: {list(SKILL_HANDLERS.keys())}")

    # Keep running until shutdown
    while True:
        await asyncio.sleep(60)


def main() -> None:
    """Entry point for agent_runner."""
    # Configure logging
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    )

    # Get skills directory
    skills_dir = os.environ.get("SKILLS_DIR", "/app/skills")

    # Run the agent
    try:
        asyncio.run(run(Path(skills_dir)))
    except KeyboardInterrupt:
        logger.info(f"Agent {AGENT_ID} shutting down")


if __name__ == "__main__":
    main()
