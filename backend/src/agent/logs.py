"""Logging utilities for Agent module.

This module provides structured logging for Agent operations.
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import asdict, dataclass, field
from typing import Any, Optional

logger = logging.getLogger(__name__)


@dataclass
class StepLog:
    """Step-level log entry for Agent operations."""

    instance_id: str
    agent_id: str
    task_name: str
    step: str  # e.g., "start", "skill_execute", "complete", "error"
    duration_ms: int = 0
    input_data: Optional[dict[str, Any]] = None
    output_data: Optional[dict[str, Any]] = None
    error: Optional[str] = None
    from_experience: bool = False
    timestamp: float = field(default_factory=time.time)

    def to_dict(self) -> dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "instance_id": self.instance_id,
            "agent_id": self.agent_id,
            "task_name": self.task_name,
            "step": self.step,
            "duration_ms": self.duration_ms,
            "input_data": self.input_data,
            "output_data": self.output_data,
            "error": self.error,
            "from_experience": self.from_experience,
            "timestamp": self.timestamp,
        }

    def to_json(self) -> str:
        """Serialize to JSON string."""
        return json.dumps(self.to_dict(), ensure_ascii=False)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> StepLog:
        """Deserialize from dictionary."""
        return cls(**data)


class StepLogger:
    """Logger for step-level agent operations.

    Logs are sent to the message bus for centralized storage.
    """

    def __init__(self, instance_id: str, agent_id: str) -> None:
        """Initialize step logger.

        Args:
            instance_id: Current instance ID
            agent_id: Current agent ID
        """
        self.instance_id = instance_id
        self.agent_id = agent_id

    def log_start(self, task_name: str, input_data: Optional[dict[str, Any]] = None) -> StepLog:
        """Log task start.

        Args:
            task_name: Task name
            input_data: Task input

        Returns:
            StepLog entry
        """
        log = StepLog(
            instance_id=self.instance_id,
            agent_id=self.agent_id,
            task_name=task_name,
            step="start",
            input_data=input_data,
        )
        self._emit(log)
        return log

    def log_skill_execute(
        self,
        task_name: str,
        skill_name: str,
        duration_ms: int,
        output_data: Optional[dict[str, Any]] = None,
    ) -> StepLog:
        """Log skill execution.

        Args:
            task_name: Task name
            skill_name: Skill being executed
            duration_ms: Execution duration
            output_data: Skill output

        Returns:
            StepLog entry
        """
        log = StepLog(
            instance_id=self.instance_id,
            agent_id=self.agent_id,
            task_name=task_name,
            step="skill_execute",
            duration_ms=duration_ms,
            output_data=output_data,
        )
        self._emit(log)
        return log

    def log_experience_hit(
        self,
        task_name: str,
        cached_output: dict[str, Any],
    ) -> StepLog:
        """Log experience pool hit.

        Args:
            task_name: Task name
            cached_output: Cached output from experience

        Returns:
            StepLog entry
        """
        log = StepLog(
            instance_id=self.instance_id,
            agent_id=self.agent_id,
            task_name=task_name,
            step="complete",
            output_data=cached_output,
            from_experience=True,
        )
        self._emit(log)
        return log

    def log_complete(
        self,
        task_name: str,
        duration_ms: int,
        output_data: Optional[dict[str, Any]] = None,
        from_experience: bool = False,
    ) -> StepLog:
        """Log task completion.

        Args:
            task_name: Task name
            duration_ms: Total duration
            output_data: Task output
            from_experience: Whether result came from experience

        Returns:
            StepLog entry
        """
        log = StepLog(
            instance_id=self.instance_id,
            agent_id=self.agent_id,
            task_name=task_name,
            step="complete",
            duration_ms=duration_ms,
            output_data=output_data,
            from_experience=from_experience,
        )
        self._emit(log)
        return log

    def log_error(
        self,
        task_name: str,
        error: str,
        duration_ms: int = 0,
    ) -> StepLog:
        """Log task error.

        Args:
            task_name: Task name
            error: Error message
            duration_ms: Duration before error

        Returns:
            StepLog entry
        """
        log = StepLog(
            instance_id=self.instance_id,
            agent_id=self.agent_id,
            task_name=task_name,
            step="error",
            duration_ms=duration_ms,
            error=error,
        )
        self._emit(log)
        return log

    def _emit(self, log: StepLog) -> None:
        """Emit log entry.

        In production, this would send to the message bus (system.logging).
        For now, just log locally.
        """
        # Log to local logger
        if log.step == "error":
            logger.error(f"Agent step error: {log.to_json()}")
        else:
            logger.info(f"Agent step: {log.to_json()}")

        # TODO: Send to message bus
        # await bus.publish_event("agent.steps", log.to_message())


async def send_step_log_to_bus(log: StepLog) -> None:
    """Send step log to the message bus.

    Args:
        log: StepLog entry
    """
    # This would send the log to system.logging via message bus
    # For now, just emit locally
    logger.debug(f"Step log: {log.to_json()}")
