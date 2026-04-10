"""Unit tests for Chat API endpoints."""

import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi import FastAPI

from src.api.chat import router, set_chat_service
from src.models.chat import ChatSession, AgentInfo
from src.services.chat_service import ChatService


def create_test_app():
    """Create a test application with chat service."""
    app = FastAPI()
    app.include_router(router)
    # Initialize chat service
    service = ChatService()
    set_chat_service(service)
    return app


class TestChatAPI:
    """Tests for Chat API endpoints."""

    @pytest.fixture
    def app(self):
        """Create test application."""
        return create_test_app()

    @pytest.mark.asyncio
    async def test_get_agent_info(self, app):
        """Test GET /api/v1/chat/agent endpoint."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        result = await service.get_agent_info()
        
        assert result.agent_id == "secretary"
        assert result.name == "秘书小秘"
        assert result.available is True

    @pytest.mark.asyncio
    async def test_create_session(self, app):
        """Test POST /api/v1/chat/session endpoint."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        session = await service.create_session()
        
        assert session.session_id is not None
        assert len(session.session_id) == 36  # UUID format

    @pytest.mark.asyncio
    async def test_get_session_not_found(self, app):
        """Test GET /api/v1/chat/session/{id} for non-existent session."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        result = await service.get_session("nonexistent-id")
        
        assert result is None

    @pytest.mark.asyncio
    async def test_get_session_success(self, app):
        """Test GET /api/v1/chat/session/{id} for existing session."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        session = await service.create_session()
        
        result = await service.get_session(session.session_id)
        
        assert result is not None
        assert result.session_id == session.session_id

    @pytest.mark.asyncio
    async def test_chat_stream_requires_message(self, app):
        """Test that chat stream requires non-empty message."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        
        # Empty message should raise ValueError
        with pytest.raises(ValueError):
            async for _ in service.process_message("", None):
                pass

    @pytest.mark.asyncio
    async def test_chat_stream_whitespace_message(self, app):
        """Test that chat stream rejects whitespace-only message."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        
        with pytest.raises(ValueError):
            async for _ in service.process_message("   ", None):
                pass


class TestSSEResponseFormat:
    """Tests for SSE response format."""

    @pytest.fixture
    def app(self):
        """Create test application."""
        return create_test_app()

    @pytest.mark.asyncio
    async def test_sse_agent_info_event(self, app):
        """Test that SSE contains agent_info event."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("Hello", None):
            events.append(event)
        
        # Check first event is agent_info
        assert len(events) > 0
        assert events[0].type.value == "agent_info"
        assert events[0].agent_id == "secretary"
        assert events[0].session_id is not None

    @pytest.mark.asyncio
    async def test_sse_log_events(self, app):
        """Test that SSE contains log events."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("Test", None):
            events.append(event)
        
        # Check for log events
        log_events = [e for e in events if e.type.value == "log"]
        assert len(log_events) > 0
        assert all(e.level is not None for e in log_events)
        assert all(e.content is not None for e in log_events)

    @pytest.mark.asyncio
    async def test_sse_message_events(self, app):
        """Test that SSE contains message events."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("Hi", None):
            events.append(event)
        
        # Check for message events
        message_events = [e for e in events if e.type.value == "message"]
        assert len(message_events) > 0
        assert all(e.content is not None for e in message_events)
        assert all(e.is_final is not None for e in message_events)

    @pytest.mark.asyncio
    async def test_sse_done_event(self, app):
        """Test that SSE contains done event."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("Test", None):
            events.append(event)
        
        # Check last event is done
        assert events[-1].type.value == "done"

    @pytest.mark.asyncio
    async def test_sse_event_sequence(self, app):
        """Test that SSE events follow correct sequence."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("Hello", None):
            events.append(event)
        
        # Check sequence: agent_info -> [log] -> [message] -> done
        assert events[0].type.value == "agent_info"
        assert events[-1].type.value == "done"
        
        # Middle events should be log or message
        for event in events[1:-1]:
            assert event.type.value in ["log", "message"]


class TestMockLLMResponse:
    """Tests for Mock LLM response patterns."""

    @pytest.fixture
    def app(self):
        """Create test application."""
        return create_test_app()

    @pytest.mark.asyncio
    async def test_greeting_response(self, app):
        """Test greeting returns friendly response."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("你好", None):
            events.append(event)
        
        # Get full message content
        message_content = ""
        for event in events:
            if event.type.value == "message" and event.content:
                message_content += event.content
        
        assert "你好" in message_content or "秘书" in message_content

    @pytest.mark.asyncio
    async def test_name_query_response(self, app):
        """Test name query returns agent name."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("你叫什么名字", None):
            events.append(event)
        
        message_content = ""
        for event in events:
            if event.type.value == "message" and event.content:
                message_content += event.content
        
        assert "秘书" in message_content or "小秘" in message_content

    @pytest.mark.asyncio
    async def test_default_response_echo(self, app):
        """Test default response echoes input."""
        from src.api.chat import get_chat_service
        
        test_message = "random test message 123"
        service = get_chat_service()
        events = []
        
        async for event in service.process_message(test_message, None):
            events.append(event)
        
        message_content = ""
        for event in events:
            if event.type.value == "message" and event.content:
                message_content += event.content
        
        assert test_message in message_content

    @pytest.mark.asyncio
    async def test_thanks_response(self, app):
        """Test thanks returns polite response."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("谢谢", None):
            events.append(event)
        
        message_content = ""
        for event in events:
            if event.type.value == "message" and event.content:
                message_content += event.content
        
        assert "谢谢" in message_content or "不客气" in message_content

    @pytest.mark.asyncio
    async def test_help_response(self, app):
        """Test help query returns capability list."""
        from src.api.chat import get_chat_service
        
        service = get_chat_service()
        events = []
        
        async for event in service.process_message("怎么帮你", None):
            events.append(event)
        
        message_content = ""
        for event in events:
            if event.type.value == "message" and event.content:
                message_content += event.content
        
        assert len(message_content) > 0
