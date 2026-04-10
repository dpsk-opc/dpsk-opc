"""LLM Workflow Provider.

This module provides a workflow provider that uses LLMs to generate workflows.
"""

from __future__ import annotations

import json
import logging
from typing import Any, Optional

from src.opc.config import OPCConfig, WorkflowContext
from src.opc.errors import LLMParseError, WorkflowGenerationError
from src.opc.models.workflow import Workflow
from src.opc.providers.base import WorkflowProvider
from src.opc.validation import validate_workflow

logger = logging.getLogger(__name__)

# Default prompt template for workflow generation
DEFAULT_PROMPT_TEMPLATE = """You are a workflow orchestration assistant. Given a user request and available agents, generate a workflow plan.

## Available Agents:
{agent_summary}

## User Request:
{user_request}

## Instructions:
1. Analyze the user request to understand the goal
2. Identify which agents are needed to accomplish this goal
3. Break down the work into tasks with proper dependencies
4. Output a valid JSON workflow

## Output Format:
{{
    "tasks": [
        {{
            "id": "unique_task_id",
            "agent": "agent_id",
            "task": "task description",
            "depends_on": ["task_id_1", "task_id_2"],  // empty if no dependencies
            "context_hint": "optional hint for context passing"
        }}
    ],
    "final_aggregator": "optional_agent_id"  // agent to aggregate final results
}}

## Rules:
- Task IDs must be unique within the workflow
- Tasks must only depend on tasks that appear earlier conceptually
- Use the exact agent IDs from the available agents list
- No cyclic dependencies allowed
- If the task is simple, use a single task
"""


class LLMWorkflowProvider(WorkflowProvider):
    """Workflow provider that generates workflows using LLMs.

    This provider takes a user request and available agents, then uses
    an LLM to generate an appropriate workflow.
    """

    def __init__(
        self,
        llm_client: Any,
        config: Optional[OPCConfig] = None,
        prompt_template: Optional[str] = None,
    ) -> None:
        """Initialize the LLM workflow provider.

        Args:
            llm_client: LLM client (must implement completion method)
            config: OPC configuration
            prompt_template: Custom prompt template
        """
        self.llm_client = llm_client
        self.config = config or OPCConfig()
        self.prompt_template = prompt_template or DEFAULT_PROMPT_TEMPLATE

    async def generate(
        self,
        user_request: str,
        context: "WorkflowContext",
    ) -> Workflow:
        """Generate workflow using LLM.

        Args:
            user_request: The user's natural language request
            context: Workflow context with available agents

        Returns:
            Generated workflow

        Raises:
            WorkflowGenerationError: If generation fails
            LLMParseError: If LLM output cannot be parsed
        """
        retry_times = self.config.llm_retry_times
        last_error: Optional[str] = None

        for attempt in range(retry_times + 1):
            try:
                # Build prompt
                prompt = self._build_prompt(user_request, context)

                # Call LLM
                response = await self._call_llm(prompt)

                # Parse and validate workflow
                workflow = self._parse_and_validate(response, context)

                logger.info(
                    f"Successfully generated workflow with {len(workflow.tasks)} tasks",
                    extra={"trace_id": context.trace_id},
                )

                return workflow

            except (LLMParseError, WorkflowGenerationError) as e:
                last_error = str(e)
                logger.warning(
                    f"Workflow generation attempt {attempt + 1} failed: {e}",
                    extra={"trace_id": context.trace_id},
                )
                if attempt < retry_times:
                    continue

        # All retries exhausted
        raise WorkflowGenerationError(
            f"Failed to generate workflow after {retry_times + 1} attempts: {last_error}",
            trace_id=context.trace_id,
        )

    def _build_prompt(self, user_request: str, context: "WorkflowContext") -> str:
        """Build prompt for LLM.

        Args:
            user_request: User request
            context: Workflow context

        Returns:
            Formatted prompt
        """
        agent_summary = context.get_agent_summary()

        prompt = self.prompt_template.format(
            agent_summary=agent_summary,
            user_request=user_request,
        )

        return prompt

    async def _call_llm(self, prompt: str) -> str:
        """Call LLM to generate workflow.

        Args:
            prompt: Formatted prompt

        Returns:
            LLM response text
        """
        # This is a placeholder - actual implementation would call the LLM
        # The LLM client should implement something like:
        # response = await llm_client.complete(
        #     prompt=prompt,
        #     temperature=self.config.llm_temperature,
        #     max_tokens=self.config.llm_max_tokens,
        # )
        # return response

        raise NotImplementedError(
            "LLM client integration not implemented. "
            "Please provide an LLM client that implements the completion interface."
        )

    def _parse_and_validate(
        self,
        llm_response: str,
        context: "WorkflowContext",
    ) -> Workflow:
        """Parse and validate LLM response.

        Args:
            llm_response: Raw LLM response
            context: Workflow context

        Returns:
            Validated workflow

        Raises:
            LLMParseError: If parsing fails
            WorkflowGenerationError: If validation fails
        """
        try:
            # Extract JSON from response (handle markdown code blocks)
            json_str = self._extract_json(llm_response)

            # Parse JSON
            data = json.loads(json_str)

            # Create workflow
            workflow = Workflow.from_dict(data)

            # Validate
            errors = validate_workflow(
                workflow,
                available_agents=context.available_agents,
                trace_id=context.trace_id,
            )

            if errors:
                raise WorkflowGenerationError(
                    f"Generated workflow failed validation: {'; '.join(errors)}",
                    trace_id=context.trace_id,
                )

            return workflow

        except json.JSONDecodeError as e:
            raise LLMParseError(f"Invalid JSON: {e}", trace_id=context.trace_id)
        except Exception as e:
            if isinstance(e, (LLMParseError, WorkflowGenerationError)):
                raise
            raise LLMParseError(str(e), trace_id=context.trace_id)

    def _extract_json(self, text: str) -> str:
        """Extract JSON from LLM response text.

        Handles common patterns like markdown code blocks.

        Args:
            text: Raw LLM response

        Returns:
            Extracted JSON string
        """
        text = text.strip()

        # Remove markdown code blocks
        if text.startswith("```"):
            lines = text.split("\n")
            # Remove first line (```json or ```)
            if lines[0].startswith("```"):
                lines = lines[1:]
            # Remove last line (```)
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            text = "\n".join(lines)

        return text.strip()
