"""DPSK-OPC Backend Main Entry Point

This module provides the main application entry point.
"""

import asyncio
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, AsyncIterator, Dict, List, Optional

import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from src.bus.memory import InMemoryMessageBus
from src.bus.models import Target, TargetType
from src.config import get_config, load_config
from src.opc import (
    OPCClient,
    OPCConfig,
    ParallelDependencyStrategy,
    StaticFileWorkflowProvider,
    LocalAgentInvoker,
)
from src.utils.logging import configure_logging, get_logger

# Import Chat API router
from src.api.chat import router as chat_router

# Initialize logger
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Application lifespan manager."""
    # Startup
    logger.info("Starting DPSK-OPC Backend")
    
    # Initialize tracing
    from src.utils.tracing import init_tracing
    init_tracing()
    
    # Load configuration
    config = get_config()
    logger.info(f"Configuration loaded: backend={config.bus.backend}")
    
    # Initialize message bus
    from src.bus.memory import InMemoryMessageBus
    app.state.bus = InMemoryMessageBus()
    logger.info("Message bus initialized")
    
    # Initialize Agent Registry
    from src.agent.registry import AgentRegistry
    from src.agent.spawner import LocalAgentSpawner
    from src.agent.manager import AgentManager
    
    agents_root = Path.home() / ".dpskopc" / "agents"
    registry = AgentRegistry()
    spawner = LocalAgentSpawner()
    agent_manager = AgentManager(registry, spawner, agents_root)
    
    await agent_manager.initialize()
    app.state.agent_manager = agent_manager
    app.state.agent_registry = registry
    logger.info(f"Agent registry initialized with {registry.count()} agents")
    
    # Initialize OPC-Client
    opc_config = OPCConfig(
        max_execution_time_secs=300,
        default_task_timeout_secs=60,
        failure_strategy="stop_on_failure",
        max_parallel_tasks=10,
        enable_progress_streaming=True,
    )
    
    # Create workflow provider (static file loader)
    workflow_dir = Path.home() / ".dpskopc" / "workflows"
    available_agents = [a.agent_id for a in registry.list_all()]
    workflow_provider = StaticFileWorkflowProvider(
        workflow_dir=str(workflow_dir) if workflow_dir.exists() else None,
        available_agents=available_agents,
    )
    
    # Create agent invoker (convert OPCConfig to dict for compatibility)
    agent_invoker = LocalAgentInvoker(
        agent_manager=agent_manager,
        security_module=None,  # Security module integration pending
        config=opc_config.to_dict() if hasattr(opc_config, "to_dict") else opc_config,
    )
    
    # Create execution strategy
    execution_strategy = ParallelDependencyStrategy(opc_config)
    
    # Create OPC-Client
    opc_client = OPCClient(
        workflow_provider=workflow_provider,
        agent_invoker=agent_invoker,
        execution_strategy=execution_strategy,
        registry=registry,
        security_module=None,
        logger_instance=logger,
        config=opc_config,
    )
    
    app.state.opc_client = opc_client
    logger.info("OPC-Client initialized")
    
    # Initialize MVP Chat Service
    from src.api.chat import set_chat_service
    from src.services.chat_service import ChatService
    
    chat_service = ChatService()
    set_chat_service(chat_service)
    
    # Register Chat API router
    app.include_router(chat_router)
    logger.info("MVP Chat API registered")
    
    yield
    
    # Shutdown
    logger.info("Shutting down DPSK-OPC Backend")


# Create FastAPI application
app = FastAPI(
    title="DPSK-OPC Backend",
    description="Message Bus System for AI Agent Communication",
    version="1.0.0",
    lifespan=lifespan,
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# === Health Check Endpoints ===

@app.get("/health")
async def health_check() -> dict:
    """Health check endpoint."""
    bus = app.state.bus
    bus_status = await bus.health_check()
    return {
        "status": "healthy",
        "service": "dpsk-opc-backend",
        "version": "1.0.0",
        "bus": bus_status,
    }


@app.get("/health/ready")
async def readiness_check() -> dict:
    """Readiness check endpoint."""
    bus = app.state.bus
    bus_status = await bus.health_check()
    
    if bus_status.get("status") == "healthy":
        return {"status": "ready"}
    return JSONResponse(
        status_code=503,
        content={"status": "not_ready"},
    )


@app.get("/health/live")
async def liveness_check() -> dict:
    """Liveness check endpoint."""
    return {"status": "alive"}


# === Metrics Endpoint ===

@app.get("/metrics")
async def metrics() -> dict:
    """Prometheus metrics endpoint (placeholder)."""
    # In production, this would return actual Prometheus metrics
    return {
        "bus_messages_total": 0,
        "bus_message_duration_seconds": 0.0,
        "bus_group_task_waiting_count": 0,
        "bus_subscriber_count": 0,
    }


# === Message Bus API ===

from src.bus.models import Message, MessageType, TaskRequest


@app.post("/api/v1/send")
async def send_message(request: Request) -> dict:
    """Send a message through the bus.
    
    Request body:
    {
        "msg_type": "TaskRequest",
        "source": "agent-id",
        "target": {"type": "agent", "value": "target-id"},
        "payload": {...}
    }
    """
    body = await request.json()
    bus = app.state.bus
    
    message = Message(
        msg_type=MessageType(body["msg_type"]),
        source=body["source"],
        target=Target(type=body["target"]["type"], value=body["target"]["value"]),
        payload=body.get("payload", {}),
    )
    
    target = message.target
    
    if message.msg_type == MessageType.TASK_REQUEST:
        task_request = TaskRequest(
            source=message.source,
            target=target,
            task_name=body.get("task_name", "unnamed"),
            task_data=body.get("task_data", {}),
        )
        response = await bus.request(
            target=target,
            message=task_request,
            timeout=get_config().bus.request_timeout_secs,
        )
        return {
            "status": "success",
            "response": {
                "success": response.success,
                "result": response.result,
                "error": response.error,
            }
        }
    else:
        await bus.notify(target, message)
        return {"status": "sent"}


@app.post("/api/v1/publish/{topic}")
async def publish_event(topic: str, request: Request) -> dict:
    """Publish an event to a topic."""
    body = await request.json()
    bus = app.state.bus
    
    message = Message(
        msg_type=MessageType.EVENT,
        source=body.get("source", "gateway"),
        target=Target(type=TargetType.TOPIC, value=topic),
        payload=body.get("payload", {}),
    )
    
    await bus.publish_event(topic, message)
    return {"status": "published", "topic": topic}


@app.get("/api/v1/topics/{topic}/subscribe")
async def topic_subscribe(topic: str) -> dict:
    """Subscribe to a topic (WebSocket upgrade recommended for production)."""
    return {
        "status": "subscribed",
        "topic": topic,
        "message": "For production, use WebSocket for real-time subscriptions"
    }


# === Error Handlers ===

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Global exception handler."""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "error": "Internal server error",
            "detail": str(exc),
        },
    )


# === OPC-Client Task API ===

class TaskSubmitRequest(BaseModel):
    """Request model for submitting a task."""
    request: str = Field(..., description="User's natural language request")
    workflow_id: Optional[str] = Field(None, description="Optional workflow ID to use")
    options: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Execution options")


class TaskStatusResponse(BaseModel):
    """Response model for task status."""
    task_id: str
    status: str
    progress: int = 0
    message: str = ""


class TaskSubmitResponse(BaseModel):
    """Response model for task submission."""
    task_id: str
    status: str
    message: str


@app.post("/api/v1/opc/tasks", response_model=TaskSubmitResponse)
async def submit_task(request: TaskSubmitRequest) -> TaskSubmitResponse:
    """Submit a new task for execution.
    
    This endpoint accepts a natural language request and returns
    a task ID for tracking the execution.
    """
    import uuid
    
    opc_client = app.state.opc_client
    task_id = str(uuid.uuid4())
    
    # Store task in memory (in production, use Redis or database)
    if not hasattr(app.state, "tasks"):
        app.state.tasks = {}
    
    app.state.tasks[task_id] = {
        "request": request.request,
        "workflow_id": request.workflow_id,
        "status": "pending",
        "created_at": asyncio.get_event_loop().time(),
    }
    
    logger.info(f"Task submitted: {task_id}", extra={"request": request.request})
    
    return TaskSubmitResponse(
        task_id=task_id,
        status="pending",
        message="Task submitted successfully",
    )


@app.get("/api/v1/opc/tasks/{task_id}/status", response_model=TaskStatusResponse)
async def get_task_status(task_id: str) -> TaskStatusResponse:
    """Get the status of a submitted task."""
    tasks = getattr(app.state, "tasks", {})
    
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    
    task = tasks[task_id]
    
    return TaskStatusResponse(
        task_id=task_id,
        status=task.get("status", "unknown"),
        progress=task.get("progress", 0),
        message=task.get("message", ""),
    )


@app.get("/api/v1/opc/tasks", response_model=List[Dict[str, Any]])
async def list_tasks(
    status: Optional[str] = None,
    limit: int = 20,
) -> List[Dict[str, Any]]:
    """List all tasks with optional filtering."""
    tasks = getattr(app.state, "tasks", {})
    
    result = []
    for task_id, task in tasks.items():
        if status is None or task.get("status") == status:
            result.append({
                "task_id": task_id,
                **task,
            })
    
    return result[:limit]


@app.post("/api/v1/opc/tasks/{task_id}/run")
async def run_task(task_id: str) -> Dict[str, Any]:
    """Execute a submitted task.
    
    This endpoint triggers the actual execution of a task
    and returns the result.
    """
    tasks = getattr(app.state, "tasks", {})
    
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    
    task = tasks[task_id]
    opc_client = app.state.opc_client
    
    # Update status
    task["status"] = "running"
    task["message"] = "Executing workflow..."
    
    try:
        result = await opc_client.run(task["request"])
        
        # Update task with result
        task["status"] = result.status.value
        task["result"] = result.to_dict()
        task["completed_at"] = asyncio.get_event_loop().time()
        
        if result.is_success():
            task["message"] = "Task completed successfully"
            task["progress"] = 100
        else:
            task["message"] = result.error or "Task failed"
        
        return {
            "task_id": task_id,
            "status": result.status.value,
            "trace_id": result.trace_id,
            "results": {k: v.to_dict() for k, v in result.results.items()},
            "error": result.error,
            "duration_ms": result.total_duration_ms,
        }
        
    except Exception as e:
        task["status"] = "failed"
        task["message"] = str(e)
        logger.exception(f"Task execution failed: {task_id}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/v1/opc/tasks/{task_id}/run-stream")
async def run_task_stream(task_id: str):
    """Execute a task with streaming progress.
    
    Returns Server-Sent Events for real-time progress updates.
    """
    from fastapi.responses import StreamingResponse
    
    tasks = getattr(app.state, "tasks", {})
    
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    
    task = tasks[task_id]
    opc_client = app.state.opc_client
    
    task["status"] = "running"
    
    async def event_generator():
        """Generate SSE events from progress stream."""
        try:
            async for event in opc_client.run_with_progress(task["request"]):
                data = event.to_dict()
                yield f"data: {data}\n\n"
                
                # Update task status
                if event.type == "workflow_complete":
                    task["status"] = "completed"
                    task["progress"] = 100
                    task["message"] = "Task completed"
                elif event.type == "workflow_failed":
                    task["status"] = "failed"
                    task["message"] = event.data.get("error", "Workflow failed")
                elif event.type == "task_start":
                    task["progress"] = 50  # Mid-progress
                    
        except Exception as e:
            error_event = {
                "type": "error",
                "error": "ExecutionError",
                "message": str(e),
            }
            yield f"data: {error_event}\n\n"
            task["status"] = "failed"
            task["message"] = str(e)
        
        yield "data: {\"type\": \"done\"}\n\n"
    
    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        },
    )


# === OPC Workflow Management ===

@app.get("/api/v1/opc/workflows")
async def list_workflows() -> Dict[str, Any]:
    """List available static workflows."""
    workflow_dir = Path.home() / ".dpskopc" / "workflows"
    
    if not workflow_dir.exists():
        return {"workflows": [], "count": 0}
    
    workflows = []
    for file_path in workflow_dir.glob("*.json"):
        workflows.append({
            "name": file_path.stem,
            "type": "json",
            "path": str(file_path),
        })
    for file_path in workflow_dir.glob("*.yaml"):
        workflows.append({
            "name": file_path.stem,
            "type": "yaml",
            "path": str(file_path),
        })
    
    return {"workflows": workflows, "count": len(workflows)}


@app.get("/api/v1/opc/agents")
async def list_agents() -> Dict[str, Any]:
    """List all available agents from registry."""
    registry = app.state.agent_registry
    
    agents = []
    for agent_def in registry.list_all():
        agents.append({
            "agent_id": agent_def.agent_id,
            "name": agent_def.name,
            "skills": agent_def.skills,
            "capabilities": agent_def.capabilities,
            "description": agent_def.description[:100] + "..." if len(agent_def.description) > 100 else agent_def.description,
        })
    
    return {"agents": agents, "count": len(agents)}


@app.get("/api/v1/opc/health")
async def opc_health_check() -> Dict[str, Any]:
    """OPC-Client specific health check."""
    opc_client = getattr(app.state, "opc_client", None)
    registry = getattr(app.state, "agent_registry", None)
    
    return {
        "status": "healthy" if opc_client else "degraded",
        "opc_client_ready": opc_client is not None,
        "agent_count": registry.count() if registry else 0,
        "version": "1.0.2",
    }


def main() -> None:
    """Run the application."""
    # Load configuration
    config = load_config()
    
    # Configure logging
    configure_logging(level=config.log.level, format=config.log.format)
    
    # Run with uvicorn
    uvicorn.run(
        "src.main:app",
        host=config.server.host,
        port=config.server.port,
        reload=False,
        log_level="info",
    )


if __name__ == "__main__":
    main()
