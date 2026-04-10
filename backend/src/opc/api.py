"""OPC-Client FastAPI Routes.

This module provides REST API endpoints for OPC-Client operations.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Dict, Optional

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field

from src.opc.config import OPCConfig
from src.opc.errors import OPCError
from src.opc.models.result import FinalResult
from src.opc.models.workflow import WorkflowTask

logger = logging.getLogger(__name__)

# Router for OPC endpoints
router = APIRouter(prefix="/api/v1/opc", tags=["opc"])

# Global OPC client instance (set during app initialization)
_opc_client: Any = None


def set_opc_client(client: Any) -> None:
    """Set the global OPC client instance.

    Args:
        client: OPCClient instance
    """
    global _opc_client
    _opc_client = client


def get_opc_client() -> Any:
    """Get the global OPC client instance.

    Returns:
        OPCClient instance

    Raises:
        HTTPException: If client not initialized
    """
    if _opc_client is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="OPC-Client not initialized",
        )
    return _opc_client


# Request/Response Models


class RunRequest(BaseModel):
    """Request model for run endpoint."""

    request: str = Field(..., description="User's natural language request")


class RunResponse(BaseModel):
    """Response model for run endpoint."""

    request: str = Field(..., description="Original user request")
    status: str = Field(..., description="Overall workflow status")
    trace_id: str = Field(..., description="Trace ID for request tracking")
    total_duration_ms: int = Field(..., description="Total execution duration")
    error: Optional[str] = Field(None, description="Error message if failed")
    workflow: Optional[Dict[str, Any]] = Field(None, description="Generated workflow")
    results: Dict[str, Dict[str, Any]] = Field(
        default_factory=dict, description="Task results"
    )


class HealthResponse(BaseModel):
    """Response model for health check."""

    status: str = Field(default="healthy", description="Service health status")
    version: str = Field(default="1.0.2", description="OPC-Client version")
    client_ready: bool = Field(..., description="Whether client is ready")


# API Endpoints


@router.post("/run", response_model=RunResponse)
async def run_workflow(request: RunRequest) -> RunResponse:
    """Execute a user request as a workflow.

    This endpoint receives a natural language request and orchestrates
    its execution through dynamically generated workflows.

    Args:
        request: RunRequest containing the user's request

    Returns:
        RunResponse with execution results
    """
    client = get_opc_client()

    logger.info(
        f"Received run request",
        extra={"request": request.request},
    )

    try:
        result: FinalResult = await client.run(request.request)

        return RunResponse(
            request=result.request,
            status=result.status.value,
            trace_id=result.trace_id,
            total_duration_ms=result.total_duration_ms,
            error=result.error,
            workflow=result.workflow.to_dict() if result.workflow else None,
            results={k: v.to_dict() for k, v in result.results.items()},
        )

    except OPCError as e:
        logger.error(f"OPC execution error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={
                "error": e.__class__.__name__,
                "message": e.message,
                "trace_id": getattr(e, "trace_id", ""),
            },
        )

    except Exception as e:
        logger.exception("Unexpected error during execution")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={
                "error": "InternalError",
                "message": str(e),
            },
        )


@router.post("/run-stream")
async def run_workflow_stream(request: RunRequest):
    """Execute a user request with streaming progress.

    This endpoint executes the workflow and streams progress events
    as Server-Sent Events (SSE).

    Args:
        request: RunRequest containing the user's request

    Returns:
        Server-Sent Events stream of progress events
    """
    from fastapi.responses import StreamingResponse

    client = get_opc_client()

    logger.info(
        f"Received streaming run request",
        extra={"request": request.request},
    )

    async def event_generator():
        """Generate SSE events from progress stream."""
        try:
            async for event in client.run_with_progress(request.request):
                # Format as SSE
                data = event.to_dict()
                yield f"data: {data}\n\n"

        except OPCError as e:
            error_event = {
                "type": "error",
                "error": e.__class__.__name__,
                "message": e.message,
                "trace_id": getattr(e, "trace_id", ""),
            }
            yield f"data: {error_event}\n\n"

        except Exception as e:
            error_event = {
                "type": "error",
                "error": "InternalError",
                "message": str(e),
            }
            yield f"data: {error_event}\n\n"

        # Send completion event
        yield "data: {\"type\": \"done\"}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.get("/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    """Health check endpoint.

    Returns:
        Health status of OPC-Client
    """
    client_ready = _opc_client is not None

    return HealthResponse(
        status="healthy" if client_ready else "degraded",
        version="1.0.2",
        client_ready=client_ready,
    )


@router.get("/workflows")
async def list_workflows():
    """List available static workflows.

    Returns:
        List of available workflow definitions
    """
    # This would list workflows from the static workflow directory
    return {
        "workflows": [],
        "message": "Static workflow listing not yet implemented",
    }


class WorkflowSubmitRequest(BaseModel):
    """Request model for submitting a custom workflow."""

    tasks: list[Dict[str, Any]] = Field(..., description="List of workflow tasks")
    final_aggregator: Optional[str] = Field(
        None, description="Optional aggregator agent"
    )


@router.post("/workflow/submit")
async def submit_workflow(request: WorkflowSubmitRequest) -> RunResponse:
    """Submit and execute a custom workflow directly.

    This endpoint accepts a workflow definition directly,
    bypassing LLM generation.

    Args:
        request: Workflow definition

    Returns:
        RunResponse with execution results
    """
    from src.opc.models.workflow import Workflow, WorkflowTask
    from src.opc.providers.static_provider import load_workflow_from_dict
    from src.opc.validation import validate_workflow

    client = get_opc_client()

    try:
        # Build workflow from request
        workflow_data = {
            "tasks": request.tasks,
            "final_aggregator": request.final_aggregator,
        }

        # Validate workflow
        workflow = load_workflow_from_dict(workflow_data)

        # Get available agents
        available_agents = await client._get_available_agents()
        agent_ids = [a.get("agent_id", "") for a in available_agents if a]

        # Validate against registry
        errors = validate_workflow(workflow, agent_ids)
        if errors:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"errors": errors},
            )

        # Execute directly
        import uuid

        trace_id = str(uuid.uuid4())
        import time

        start_time = time.time()

        execution_context = {
            "trace_id": trace_id,
            "request": "custom_workflow",
        }

        results = await client.execution_strategy.execute(
            workflow.tasks,
            client.agent_invoker,
            execution_context,
        )

        # Determine status
        from src.opc.models.workflow import TaskStatus

        statuses = [r.status for r in results.values()]
        if TaskStatus.FAILED in statuses:
            final_status = "failed"
        elif all(s == TaskStatus.COMPLETED for s in statuses):
            final_status = "completed"
        else:
            final_status = "partial"

        total_duration_ms = int((time.time() - start_time) * 1000)

        return RunResponse(
            request="custom_workflow",
            status=final_status,
            trace_id=trace_id,
            total_duration_ms=total_duration_ms,
            workflow=workflow.to_dict(),
            results={k: v.to_dict() for k, v in results.items()},
        )

    except HTTPException:
        raise

    except Exception as e:
        logger.exception("Error executing custom workflow")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "ExecutionError", "message": str(e)},
        )
