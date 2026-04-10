"""Static File Workflow Provider.

This module provides a workflow provider that loads workflows from files.
"""

from __future__ import annotations

from src.opc.models.workflow import WorkflowTask
import json
import re
from pathlib import Path
from typing import Any, Optional

import yaml

from src.opc.config import WorkflowContext
from src.opc.errors import WorkflowValidationError
from src.opc.models.workflow import Workflow, WorkflowTask
from src.opc.providers.base import WorkflowProvider
from src.opc.validation import validate_workflow


class StaticFileWorkflowProvider(WorkflowProvider):
    """Workflow provider that loads workflows from static files.

    Supports JSON and YAML workflow definitions.
    """

    def __init__(
        self,
        workflow_dir: Optional[str] = None,
        available_agents: Optional[list[str]] = None,
    ) -> None:
        """Initialize the static workflow provider.

        Args:
            workflow_dir: Directory containing workflow files
            available_agents: List of available agent IDs for validation
        """
        self.workflow_dir = Path(workflow_dir) if workflow_dir else Path("workflows")
        self.available_agents = available_agents or []

    async def generate(
        self,
        user_request: str,
        context: "WorkflowContext",
    ) -> Workflow:
        """Generate workflow by matching request to a static file.

        For static provider, this method looks for a workflow file
        that matches the request pattern.

        Args:
            user_request: The user's request (used as workflow identifier)
            context: Workflow context

        Returns:
            Loaded workflow

        Raises:
            WorkflowValidationError: If workflow loading or validation fails
        """
        # Try to find a workflow file matching the request
        workflow_path = self._find_workflow(user_request)

        if workflow_path and workflow_path.exists():
            return await self.load_workflow(workflow_path)

        # Return a simple default workflow with the request as task
        return self._create_simple_workflow(user_request)

    def _find_workflow(self, request: str) -> Path | None:
        """Find a workflow file matching the request.

        Args:
            request: User request or workflow identifier

        Returns:
            Path to matching workflow file, or None
        """
        if not self.workflow_dir.exists():
            return None

        # Try exact match
        base_name = self._sanitize_filename(request)
        for ext in [".json", ".yaml", ".yml"]:
            path = self.workflow_dir / f"{base_name}{ext}"
            if path.exists():
                return path

        # Try pattern match
        for file_path in self.workflow_dir.iterdir():
            if base_name.lower() in file_path.stem.lower():
                return file_path

        return None

    def _sanitize_filename(self, name: str) -> str:
        """Sanitize a string for use as filename."""
        # Replace special chars with underscores
        sanitized = re.sub(r"[^\w\s-]", "_", name)
        # Replace whitespace with underscores
        sanitized = re.sub(r"\s+", "_", sanitized)
        return sanitized[:100]  # Limit length

    async def load_workflow(self, file_path: Path) -> Workflow:
        """Load workflow from a file.

        Args:
            file_path: Path to workflow file

        Returns:
            Loaded workflow

        Raises:
            WorkflowValidationError: If file cannot be loaded or validated
        """
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                if file_path.suffix in [".yaml", ".yml"]:
                    data = yaml.safe_load(f)
                elif file_path.suffix == ".json":
                    data = json.load(f)
                else:
                    raise WorkflowValidationError(
                        f"Unsupported workflow file format: {file_path.suffix}"
                    )

            workflow = Workflow.from_dict(data)

            # Validate the workflow
            errors = validate_workflow(workflow, self.available_agents)
            if errors:
                raise WorkflowValidationError(
                    f"Workflow validation failed: {'; '.join(errors)}"
                )

            return workflow

        except yaml.YAMLError as e:
            raise WorkflowValidationError(f"Failed to parse YAML workflow: {e}")
        except json.JSONDecodeError as e:
            raise WorkflowValidationError(f"Failed to parse JSON workflow: {e}")
        except Exception as e:
            raise WorkflowValidationError(f"Failed to load workflow: {e}")

    def _create_simple_workflow(self, request: str) -> Workflow:
        """Create a simple single-task workflow.

        Args:
            request: Task description

        Returns:
            Simple workflow with one task
        """
        task: WorkflowTask = WorkflowTask(
            id="task_1",
            agent="秘书",  # Default to secretary agent
            task=request,
            depends_on=[],
        )
        return Workflow(tasks=[task])


def load_workflow_from_dict(data: dict[str, Any]) -> Workflow:
    """Load workflow from a dictionary.

    Args:
        data: Workflow data dictionary

    Returns:
        Workflow object

    Raises:
        WorkflowValidationError: If validation fails
    """
    workflow = Workflow.from_dict(data)

    errors = validate_workflow(workflow)
    if errors:
        raise WorkflowValidationError(
            f"Workflow validation failed: {'; '.join(errors)}"
        )

    return workflow
