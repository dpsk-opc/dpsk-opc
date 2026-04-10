"""OPC-Client Local Demo Script.

This script provides a standalone OPC-Client instance for local testing
without requiring the full backend infrastructure.

Usage:
    python -m tests.test_opc.opc_demo
"""

from __future__ import annotations

import asyncio
import logging
import sys
from pathlib import Path
from typing import Any

# Add backend to path
backend_path = Path(__file__).parent.parent.parent
sys.path.insert(0, str(backend_path))

from src.opc.config import OPCConfig, WorkflowContext
from src.opc.models.events import EventType, ProgressEvent
from src.opc.models.result import FinalResult, TaskResult, WorkflowStatus
from src.opc.models.workflow import TaskStatus, Workflow, WorkflowTask
from src.opc.providers.base import WorkflowProvider
from src.opc.invokers.base import AgentInvoker
from src.opc.strategies.base import ExecutionStrategy
from src.opc.strategies.parallel_strategy import ParallelDependencyStrategy
from src.opc.client import OPCClient
from src.opc.validation import validate_workflow

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


# === Mock Components for Local Testing ===

class MockAgentRegistry:
    """Mock agent registry for local testing."""

    _agents: list[dict[str, Any]] = [
        {"agent_id": "researcher", "name": "Research Agent", "skills": ["search", "analyze"]},
        {"agent_id": "coder", "name": "Code Agent", "skills": ["coding", "debug"]},
        {"agent_id": "reviewer", "name": "Review Agent", "skills": ["review", "test"]},
        {"agent_id": "tester", "name": "Test Agent", "skills": ["test", "qa"]},
    ]

    def list_all(self) -> list[dict[str, Any]]:
        """List all available agents."""
        return self._agents

    def count(self) -> int:
        """Get agent count."""
        return len(self._agents)

    def get(self, agent_id: str) -> dict[str, Any] | None:
        """Get agent by ID."""
        for agent in self._agents:
            if agent["agent_id"] == agent_id:
                return agent
        return None


class MockAgentManager:
    """Mock agent manager for local testing."""

    registry: MockAgentRegistry

    def __init__(self, registry: MockAgentRegistry):
        self.registry = registry

    def get_agent(self, agent_id: str) -> dict[str, Any] | None:
        """Get agent definition."""
        return self.registry.get(agent_id)

    async def execute_agent(
        self,
        agent_id: str,
        task_id: str,
        task_input: dict[str, Any],
        config: OPCConfig | None = None,
    ) -> TaskResult:
        """Mock agent execution - simulates agent work."""
        # Simulate some work
        await asyncio.sleep(0.5)

        # Return mock result
        return TaskResult(
            task_id=task_id,
            status=TaskStatus.COMPLETED,
            output={
                "message": f"Mock response from {agent_id}",
                "agent_id": agent_id,
                "input": task_input,
            },
            duration_ms=500,
        )


class MockAgentInvoker(AgentInvoker):
    """Mock agent invoker for local testing."""

    agent_manager: MockAgentManager

    def __init__(self, agent_manager: MockAgentManager):
        self.agent_manager = agent_manager

    async def invoke(
        self,
        agent_id: str,
        task: str,
        context: dict[str, Any] | None = None,
    ) -> TaskResult:
        """Invoke mock agent.
        
        Args:
            agent_id: The agent to invoke
            task: Task description string
            context: Optional execution context
        """
        logger.info(f"Invoking agent: {agent_id}, task: {task}")

        # Extract task_id from context if available, otherwise generate
        task_id = context.get("task_id", f"task_{agent_id}") if context else f"task_{agent_id}"

        result = await self.agent_manager.execute_agent(
            agent_id=agent_id,
            task_id=task_id,
            task_input={"task": task, "agent_id": agent_id},
            config=None,
        )
        return result

    async def validate_agent_access(
        self,
        agent_id: str,
        context: dict[str, Any] | None = None,
    ) -> bool:
        """Validate agent access."""
        return self.agent_manager.get_agent(agent_id) is not None


class MockWorkflowProvider(WorkflowProvider):
    """Mock workflow provider that uses predefined workflows."""

    _workflows: dict[str, Workflow] = {}

    def __init__(self, workflows: dict[str, Workflow] | None = None):
        self._workflows = workflows or {}

    async def generate(
        self,
        user_request: str,
        context: WorkflowContext,
    ) -> Workflow:
        """Generate or return mock workflow."""
        # Try to find a matching workflow based on request
        request_lower = user_request.lower()

        for name, workflow in self._workflows.items():
            if name in request_lower:
                logger.info(f"Using predefined workflow: {name}")
                return workflow

        # Return default workflow
        if "default" in self._workflows:
            logger.info("Using default workflow")
            return self._workflows["default"]

        # Generate simple linear workflow
        return self._create_simple_workflow(context.available_agents)

    def _create_simple_workflow(self, available_agents: list[dict[str, Any]]) -> Workflow:
        """Create a simple linear workflow."""
        agent_ids = [a["agent_id"] for a in available_agents[:3]]

        tasks = []
        for i, agent_id in enumerate(agent_ids):
            task = WorkflowTask(
                id=f"task_{i + 1}",
                agent=agent_id,
                task=f"Execute {agent_id} agent",
                depends_on=[tasks[-1].id] if tasks else [],
            )
            tasks.append(task)

        return Workflow(tasks=tasks)


# === Predefined Test Workflows ===

def create_sequential_workflow() -> Workflow:
    """Create a simple sequential workflow: A -> B -> C"""
    return Workflow(
        tasks=[
            WorkflowTask(
                id="task_1",
                agent="researcher",
                task="Research the topic",
                depends_on=[],
            ),
            WorkflowTask(
                id="task_2",
                agent="coder",
                task="Generate code based on research",
                depends_on=["task_1"],
            ),
            WorkflowTask(
                id="task_3",
                agent="reviewer",
                task="Review the code",
                depends_on=["task_2"],
            ),
        ],
    )


def create_parallel_workflow() -> Workflow:
    """Create a parallel workflow: A, B, C run simultaneously"""
    return Workflow(
        tasks=[
            WorkflowTask(
                id="task_research",
                agent="researcher",
                task="Research the topic",
                depends_on=[],
            ),
            WorkflowTask(
                id="task_code",
                agent="coder",
                task="Generate code",
                depends_on=[],
            ),
            WorkflowTask(
                id="task_test",
                agent="tester",
                task="Write tests",
                depends_on=[],
            ),
        ],
    )


def create_complex_workflow() -> Workflow:
    """Create a complex workflow with dependencies: A -> B -> C, D -> E, B+D -> F"""
    return Workflow(
        tasks=[
            # Sequential: A -> B
            WorkflowTask(
                id="task_1",
                agent="researcher",
                task="Research phase",
                depends_on=[],
            ),
            WorkflowTask(
                id="task_2",
                agent="coder",
                task="Implementation",
                depends_on=["task_1"],
            ),
            # Parallel: D, E run together
            WorkflowTask(
                id="task_3",
                agent="tester",
                task="Testing",
                depends_on=[],
            ),
            WorkflowTask(
                id="task_4",
                agent="reviewer",
                task="Review",
                depends_on=[],
            ),
            # Final: F depends on B (task_2) and E (task_4)
            WorkflowTask(
                id="task_5",
                agent="coder",
                task="Final integration",
                depends_on=["task_2", "task_4"],
            ),
        ],
    )


# === OPC-Client Factory ===

def create_local_opc_client(
    workflow: Workflow | None = None,
    agent_registry: MockAgentRegistry | None = None,
) -> OPCClient:
    """Create an OPC-Client for local testing.

    Args:
        workflow: Optional predefined workflow to use
        agent_registry: Optional custom agent registry

    Returns:
        Configured OPCClient instance
    """
    # Create mock registry
    registry = agent_registry or MockAgentRegistry()

    # Create mock agent manager
    agent_manager = MockAgentManager(registry)

    # Create mock agent invoker
    agent_invoker = MockAgentInvoker(agent_manager)

    # Create workflow provider with predefined workflows
    workflows: dict[str, Workflow] = {
        "sequential": create_sequential_workflow(),
        "parallel": create_parallel_workflow(),
        "complex": create_complex_workflow(),
    }
    if workflow:
        workflows["default"] = workflow

    workflow_provider = MockWorkflowProvider(workflows)

    # Create config
    config = OPCConfig(
        max_execution_time_secs=60,
        default_task_timeout_secs=30,
        failure_strategy="stop_on_failure",
        max_parallel_tasks=5,
        enable_progress_streaming=True,
    )

    # Create execution strategy
    execution_strategy = ParallelDependencyStrategy(config)

    # Create OPC-Client
    client = OPCClient(
        workflow_provider=workflow_provider,
        agent_invoker=agent_invoker,
        execution_strategy=execution_strategy,
        registry=registry,
        security_module=None,
        logger_instance=logger,
        config=config,
    )

    return client


# === Demo Functions ===

async def demo_sequential() -> FinalResult:
    """Demo: Sequential workflow execution."""
    print("\n" + "=" * 60)
    print("Demo 1: Sequential Workflow (A -> B -> C)")
    print("=" * 60)

    client = create_local_opc_client()

    result = await client.run("Run the sequential workflow")

    print_result(result)
    return result


async def demo_parallel() -> FinalResult:
    """Demo: Parallel workflow execution."""
    print("\n" + "=" * 60)
    print("Demo 2: Parallel Workflow (A || B || C)")
    print("=" * 60)

    client = create_local_opc_client()

    result = await client.run("Run the parallel workflow")

    print_result(result)
    return result


async def demo_with_progress() -> None:
    """Demo: Workflow execution with progress streaming."""
    print("\n" + "=" * 60)
    print("Demo 3: Workflow with Progress Streaming")
    print("=" * 60)

    client = create_local_opc_client(create_complex_workflow())

    print("Progress events:")
    async for event in client.run_with_progress("complex workflow"):
        event_type = event.type.value if hasattr(event.type, 'value') else str(event.type)
        event_status = event.status or ""
        
        # Handle different data types
        if isinstance(event.data, dict):
            event_data = event.data
            message = event_data.get('message', event_data.get('error', ''))
        else:
            event_data = {}
            message = str(event.data) if event.data else ''
        
        print(f"  [{event_type}] {event_status}: {message}")

        if hasattr(event.type, 'value') and event.type == EventType.TASK_COMPLETE:
            print(f"       -> Task '{event_data.get('task_id')}' completed")


async def demo_validation() -> None:
    """Demo: Workflow validation."""
    print("\n" + "=" * 60)
    print("Demo 4: Workflow Validation")
    print("=" * 60)

    registry = MockAgentRegistry()
    agent_ids = [a["agent_id"] for a in registry.list_all()]

    # Test 1: Valid workflow
    print("\nTest 1: Valid workflow")
    workflow = create_sequential_workflow()
    errors = validate_workflow(workflow, agent_ids, "test-001")
    print(f"  Errors: {errors if errors else 'None (valid)'}")

    # Test 2: Cyclic dependency
    print("\nTest 2: Cyclic dependency detection")
    cyclic_workflow = Workflow(
        tasks=[
            WorkflowTask(id="a", agent="researcher", task="Task A", depends_on=["c"]),
            WorkflowTask(id="b", agent="coder", task="Task B", depends_on=["a"]),
            WorkflowTask(id="c", agent="reviewer", task="Task C", depends_on=["b"]),
        ],
    )
    errors = validate_workflow(cyclic_workflow, agent_ids, "test-002")
    print(f"  Errors: {errors if errors else 'None'}")

    # Test 3: Non-existent agent
    print("\nTest 3: Non-existent agent detection")
    bad_agent_workflow = Workflow(
        tasks=[
            WorkflowTask(id="x", agent="nonexistent_agent", task="Bad Task", depends_on=[]),
        ],
    )
    errors = validate_workflow(bad_agent_workflow, agent_ids, "test-003")
    print(f"  Errors: {errors if errors else 'None'}")


def print_result(result: FinalResult) -> None:
    """Print formatted result."""
    print(f"\nStatus: {result.status.value}")
    print(f"Trace ID: {result.trace_id}")
    print(f"Duration: {result.total_duration_ms}ms")

    if result.error:
        print(f"Error: {result.error}")

    print(f"\nTask Results ({len(result.results)} tasks):")
    for task_id, task_result in result.results.items():
        print(f"  [{task_result.status.value}] {task_id}")
        if task_result.output:
            output = task_result.output.get("message", "")
            print(f"       -> {output}")


# === Main Entry Point ===

async def main() -> None:
    """Run all demos."""
    print("\n" + "=" * 60)
    print("OPC-Client Local Demo")
    print("=" * 60)

    # Run demos
    await demo_validation()
    await demo_sequential()
    await demo_parallel()
    await demo_with_progress()

    print("\n" + "=" * 60)
    print("All demos completed!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
