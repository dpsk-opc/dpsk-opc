"""DPSK-OPC Backend Main Entry Point

This module provides the main application entry point.
"""

import asyncio
from contextlib import asynccontextmanager
from typing import AsyncIterator

import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from src.bus.memory import InMemoryMessageBus
from src.bus.models import Target, TargetType
from src.config import get_config, load_config
from src.utils.logging import configure_logging, get_logger

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
