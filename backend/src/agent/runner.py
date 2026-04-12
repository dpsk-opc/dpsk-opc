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

# Global LLM client (injected at runtime)
_llm_client = None

# Configuration from environment
AGENT_ID = os.environ.get("AGENT_ID", "unknown")
MESSAGE_BUS_ADDRESS = os.environ.get("MESSAGE_BUS_ADDRESS", "memory")
TEMP_TOKEN = os.environ.get("TEMP_TOKEN", "")


def set_llm_client(client: Any) -> None:
    """Set the global LLM client.
    
    Args:
        client: LLM client instance
    """
    global _llm_client
    _llm_client = client
    logger.info(f"LLM client set: {client.__class__.__name__}")


def get_llm_client() -> Optional[Any]:
    """Get the global LLM client.
    
    Returns:
        LLM client or None
    """
    return _llm_client


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
    agent_info: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    """Handle a task request.

    This is the core execution function for an agent. It:
    1. Checks experience pool for cached results
    2. Executes skill handler if available
    3. Falls back to LLM call using agent's system prompt

    Args:
        task_name: Name of the task to execute
        task_data: Input data for the task
        experience_db: Optional experience database for caching
        agent_info: Agent information including system prompt

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

        # 2. Execute skill handler if available
        handler = SKILL_HANDLERS.get(task_name)
        if handler:
            skill_result = await handler(task_data)
            result["result"] = skill_result
            logger.info(f"Executed skill handler: {task_name}")
        else:
            # 3. No skill found, use LLM with agent's system prompt
            llm_result = await _execute_with_llm(
                task_name=task_name,
                task_data=task_data,
                agent_info=agent_info,
            )
            result["result"] = llm_result
            result["llm_generated"] = True

        # 4. Store experience asynchronously
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
    system_prompt: Optional[str] = None,
    messages: Optional[list[dict[str, str]]] = None,
    **kwargs: Any,
) -> dict[str, Any]:
    """Call LLM using the global LLM client.

    Args:
        prompt: The prompt to send
        model: Model to use (optional, uses current client if matches)
        system_prompt: Optional system prompt
        messages: Optional conversation history
        **kwargs: Additional model parameters

    Returns:
        LLM response dictionary with 'content', 'error', etc.
    """
    client = get_llm_client()
    
    if client is None:
        logger.error("[LLM] LLM client not available")
        return {
            "error": "LLM client not initialized. "
                     "Set LLM client with runner.set_llm_client()",
            "content": "",
        }
    
    try:
        # Check if we need to switch models
        if model and model != client.model:
            # Try to get a client for the specific model
            from src.llm.registry import get_llm_registry
            registry = get_llm_registry()
            model_client = registry.get_client(model=model)
            if model_client:
                client = model_client
                logger.info(f"[LLM] Switched to model: {client.model}")
            else:
                logger.warning(f"[LLM] Model {model} not available, using default: {client.model}")
        
        # Call LLM
        response = await client.complete(
            prompt=prompt,
            system_prompt=system_prompt,
            messages=messages,
            **kwargs,
        )
        
        if response.success:
            return {
                "content": response.content,
                "model": response.model,
                "usage": response.usage,
                "finish_reason": response.finish_reason,
                "error": None,
            }
        else:
            return {
                "content": "",
                "error": response.error,
            }
            
    except Exception as e:
        logger.exception(f"[LLM] LLM call exception: {e}")
        return {
            "content": "",
            "error": str(e),
        }


async def _execute_with_llm(
    task_name: str,
    task_data: dict[str, Any],
    agent_info: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    """Execute task using LLM with agent's system prompt.

    This is the fallback when no skill handler is available.
    It uses the agent's system prompt (description) to guide the LLM.

    Args:
        task_name: Name of the task
        task_data: Task input data
        agent_info: Agent information dict with 'description' as system prompt

    Returns:
        Task execution result
    """
    # Get agent's system prompt
    system_prompt = ""
    if agent_info and agent_info.get("description"):
        system_prompt = agent_info["description"]
    
    # Build user prompt from task data
    if isinstance(task_data, dict):
        user_prompt = task_data.get("instruction", "") or task_data.get("input", "") or str(task_data)
    else:
        user_prompt = str(task_data)
    
    if not user_prompt:
        # If no user input, use task name as prompt
        user_prompt = task_name
    
    # Call LLM
    result = await call_llm(
        prompt=user_prompt,
        system_prompt=system_prompt,
    )
    
    if result.get("error"):
        logger.error(f"[LLM] LLM execution failed: {result['error']}")
        return {
            "success": False,
            "error": result["error"],
            "content": "",
        }
    
    logger.info(
        f"[LLM] LLM execution success: task={task_name}, "
        f"content_preview={result.get('content', '')[:100]}"
    )
    
    return {
        "success": True,
        "content": result.get("content", ""),
        "model": result.get("model", ""),
        "usage": result.get("usage", {}),
        "agent_id": agent_info.get("agent_id", "unknown") if agent_info else "unknown",
        "task_name": task_name,
    }


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
