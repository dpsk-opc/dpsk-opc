"""Chat API module.

This module provides the REST API endpoints for the MVP Chat functionality.
"""

import json
from typing import AsyncGenerator, Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse, JSONResponse

from src.models.chat import (
    ChatRequest,
    ChatSession,
    AgentInfo,
    SSEResponse,
)
from src.services.chat_service import ChatService

# Create router
router = APIRouter(
    prefix="/api/v1/chat",
    tags=["chat"],
)

# Global chat service instance (initialized by create_app)
_chat_service: Optional[ChatService] = None


def get_chat_service() -> ChatService:
    """Get or create chat service instance.
    
    Returns:
        ChatService instance
    
    Raises:
        HTTPException: If chat service is not initialized
    """
    if _chat_service is None:
        raise HTTPException(
            status_code=500,
            detail="Chat service not initialized"
        )
    return _chat_service


def set_chat_service(service: ChatService) -> None:
    """Set global chat service instance.
    
    Args:
        service: ChatService instance to use
    """
    global _chat_service
    _chat_service = service


def create_app() -> None:
    """Initialize chat service and return app for testing."""
    global _chat_service
    if _chat_service is None:
        _chat_service = ChatService()


# === Agent Info Endpoint ===

@router.get("/agent", response_model=AgentInfo)
async def get_agent_info(
    service: ChatService = Depends(get_chat_service),
) -> AgentInfo:
    """Get current agent information.
    
    Returns:
        AgentInfo containing agent_id, name, avatar, and availability
    """
    return await service.get_agent_info()


# === Session Management Endpoints ===

@router.post("/session")
async def create_session(
    service: ChatService = Depends(get_chat_service),
) -> dict:
    """Create a new chat session.
    
    Returns:
        Dictionary containing the new session_id
    """
    session = await service.create_session()
    return {"session_id": session.session_id}


@router.get("/session/{session_id}")
async def get_session(
    session_id: str,
    service: ChatService = Depends(get_chat_service),
) -> dict:
    """Get session details and message history.
    
    Args:
        session_id: Session ID to retrieve
    
    Returns:
        Session data including messages
    
    Raises:
        HTTPException: If session not found
    """
    session = await service.get_session(session_id)
    if session is None:
        raise HTTPException(
            status_code=404,
            detail=f"Session not found: {session_id}"
        )
    return session.to_dict()


# === Chat Stream Endpoint ===

@router.post("/stream")
async def chat_stream(
    request: ChatRequest,
    service: ChatService = Depends(get_chat_service),
) -> StreamingResponse:
    """Send a chat message and receive streaming response.
    
    This endpoint uses Server-Sent Events (SSE) to stream responses.
    
    Args:
        request: ChatRequest containing message and optional session_id
    
    Returns:
        StreamingResponse with text/event-stream content type
    
    Raises:
        HTTPException: 400 if message is empty
        HTTPException: 404 if session not found
        HTTPException: 500 for server errors
    """
    async def event_generator() -> AsyncGenerator[str, None]:
        """Generate SSE events from chat service."""
        try:
            async for event in service.process_message(
                message=request.message,
                session_id=request.session_id,
            ):
                data = event.to_sse_data()
                yield f"data: {json.dumps(data)}\n\n"
        
        except ValueError as e:
            # Validation errors
            error_data = {
                "type": "error",
                "error": "ValidationError",
                "message": str(e),
            }
            yield f"data: {json.dumps(error_data)}\n\n"
        
        except Exception as e:
            # Unexpected errors
            error_data = {
                "type": "error",
                "error": "InternalError",
                "message": str(e),
            }
            yield f"data: {json.dumps(error_data)}\n\n"
            raise
    
    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # Disable nginx buffering
            "Content-Encoding": "identity",  # Disable gzip compression for SSE streaming
        },
    )
