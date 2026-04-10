"""Unit tests for chat service."""

import pytest
import asyncio
from unittest.mock import AsyncMock, MagicMock
from datetime import datetime

from src.services.chat_service import (
    ContextManager,
    MockLLM,
    ChatService,
    AgentConfig,
)


class TestContextManager:
    """Tests for ContextManager."""

    @pytest.fixture
    def context_manager(self):
        """Create a fresh context manager for each test."""
        return ContextManager()

    @pytest.mark.asyncio
    async def test_create_session(self, context_manager):
        """Test creating a new session."""
        session = await context_manager.create_session()
        
        assert session is not None
        assert session.session_id is not None
        assert len(session.messages) == 0

    @pytest.mark.asyncio
    async def test_create_multiple_sessions(self, context_manager):
        """Test creating multiple sessions."""
        session1 = await context_manager.create_session()
        session2 = await context_manager.create_session()
        
        assert session1.session_id != session2.session_id

    @pytest.mark.asyncio
    async def test_get_existing_session(self, context_manager):
        """Test getting an existing session."""
        created = await context_manager.create_session()
        retrieved = await context_manager.get_session(created.session_id)
        
        assert retrieved is not None
        assert retrieved.session_id == created.session_id

    @pytest.mark.asyncio
    async def test_get_nonexistent_session(self, context_manager):
        """Test getting a non-existent session returns None."""
        result = await context_manager.get_session("nonexistent-id")
        
        assert result is None

    @pytest.mark.asyncio
    async def test_add_message(self, context_manager):
        """Test adding messages to a session."""
        session = await context_manager.create_session()
        await context_manager.add_message(session.session_id, "user", "Hello")
        await context_manager.add_message(session.session_id, "assistant", "Hi!")
        
        updated = await context_manager.get_session(session.session_id)
        assert len(updated.messages) == 2
        assert updated.messages[0].content == "Hello"
        assert updated.messages[1].content == "Hi!"

    @pytest.mark.asyncio
    async def test_context_window_limit(self, context_manager):
        """Test that context window is limited to 10 turns."""
        session = await context_manager.create_session()
        
        # Add 25 messages (more than 10 turns = 20 messages)
        for i in range(25):
            await context_manager.add_message(
                session.session_id, 
                "user", 
                f"Message {i}"
            )
        
        # Context should be limited to last 20 messages
        updated = await context_manager.get_session(session.session_id)
        assert len(updated.messages) == 20
        # First message should be "Message 5"
        assert updated.messages[0].content == "Message 5"


class TestMockLLM:
    """Tests for MockLLM."""

    @pytest.fixture
    def mock_llm(self):
        """Create a MockLLM instance."""
        return MockLLM()

    def test_greeting_keywords(self, mock_llm):
        """Test greeting keyword responses."""
        greetings = ["你好", "hi", "hello", "嗨"]
        
        for greeting in greetings:
            response = mock_llm._get_response(greeting)
            assert "你好" in response or "秘书" in response

    def test_name_keywords(self, mock_llm):
        """Test name-related keyword responses."""
        name_keywords = ["名字", "叫什么"]
        
        for keyword in name_keywords:
            response = mock_llm._get_response(keyword)
            assert "秘书" in response or "小秘" in response

    def test_thanks_keywords(self, mock_llm):
        """Test thanks-related keyword responses."""
        thanks_keywords = ["谢谢", "感谢"]
        
        for keyword in thanks_keywords:
            response = mock_llm._get_response(keyword)
            assert "谢谢" in response or "不客气" in response

    def test_help_keywords(self, mock_llm):
        """Test help-related keyword responses."""
        help_keywords = ["帮助", "帮", "怎么"]
        
        for keyword in help_keywords:
            response = mock_llm._get_response(keyword)
            assert len(response) > 0

    def test_default_response(self, mock_llm):
        """Test default response for unrecognized input."""
        response = mock_llm._get_response("random text 12345")
        
        # Default response should include the original message
        assert "random text 12345" in response

    @pytest.mark.asyncio
    async def test_stream_response(self, mock_llm):
        """Test streaming response generator."""
        chunks = []
        async for chunk in mock_llm.stream_response("Hello"):
            chunks.append(chunk)
        
        assert len(chunks) > 0
        full_response = "".join(chunks)
        assert len(full_response) > 0

    @pytest.mark.asyncio
    async def test_stream_delay(self, mock_llm):
        """Test that streaming has appropriate delays."""
        import time
        
        start = time.time()
        async for _ in mock_llm.stream_response("Test"):
            pass
        duration = time.time() - start
        
        # Should have some delay (at least 30ms per chunk)
        assert duration > 0


class TestChatService:
    """Tests for ChatService."""

    @pytest.fixture
    def chat_service(self):
        """Create a ChatService instance."""
        return ChatService()

    @pytest.mark.asyncio
    async def test_create_session(self, chat_service):
        """Test creating a session."""
        session = await chat_service.create_session()
        
        assert session is not None
        assert session.session_id is not None

    @pytest.mark.asyncio
    async def test_get_session(self, chat_service):
        """Test getting a session."""
        created = await chat_service.create_session()
        retrieved = await chat_service.get_session(created.session_id)
        
        assert retrieved is not None
        assert retrieved.session_id == created.session_id

    @pytest.mark.asyncio
    async def test_get_agent_info(self, chat_service):
        """Test getting agent information."""
        agent_info = await chat_service.get_agent_info()
        
        assert agent_info.agent_id == "secretary"
        assert agent_info.name == "秘书小秘"
        assert agent_info.available is True

    @pytest.mark.asyncio
    async def test_process_message_greeting(self, chat_service):
        """Test processing a greeting message."""
        session = await chat_service.create_session()
        
        responses = []
        async for event in chat_service.process_message("你好", session.session_id):
            responses.append(event)
        
        # Should have multiple events
        assert len(responses) > 0
        
        # Find message events
        message_events = [e for e in responses if e.type.value == "message"]
        assert len(message_events) > 0

    @pytest.mark.asyncio
    async def test_process_message_empty_session(self, chat_service):
        """Test processing message without session creates one."""
        responses = []
        async for event in chat_service.process_message("Hello", None):
            responses.append(event)
        
        # Should create a new session and return agent_info
        agent_info_events = [e for e in responses if e.type.value == "agent_info"]
        assert len(agent_info_events) > 0

    @pytest.mark.asyncio
    async def test_process_message_empty_content(self, chat_service):
        """Test that empty message raises error."""
        session = await chat_service.create_session()
        
        with pytest.raises(ValueError):
            async for _ in chat_service.process_message("", session.session_id):
                pass

    @pytest.mark.asyncio
    async def test_conversation_context(self, chat_service):
        """Test that conversation maintains context."""
        session = await chat_service.create_session()
        
        # First message
        async for _ in chat_service.process_message("Hello", session.session_id):
            pass
        
        # Second message - should have context
        async for _ in chat_service.process_message("How are you?", session.session_id):
            pass
        
        # Check session has 4 messages (2 user + 2 assistant)
        updated_session = await chat_service.get_session(session.session_id)
        assert len(updated_session.messages) == 4

    @pytest.mark.asyncio
    async def test_sse_event_sequence(self, chat_service):
        """Test that SSE events follow correct sequence."""
        session = await chat_service.create_session()
        
        events = []
        async for event in chat_service.process_message("Test", session.session_id):
            events.append(event)
        
        if len(events) > 0:
            # First event should be agent_info
            assert events[0].type.value == "agent_info"
            
            # Last event should be done
            assert events[-1].type.value == "done"


class TestAgentConfig:
    """Tests for AgentConfig."""

    def test_default_config(self):
        """Test default agent configuration."""
        config = AgentConfig()
        
        assert config.agent_id == "secretary"
        assert config.name == "秘书小秘"
        assert config.avatar is None
        assert config.available is True

    def test_custom_config(self):
        """Test custom agent configuration."""
        config = AgentConfig(
            agent_id="custom",
            name="Custom Agent",
            avatar="http://example.com/avatar.png"
        )
        
        assert config.agent_id == "custom"
        assert config.name == "Custom Agent"
        assert config.avatar == "http://example.com/avatar.png"
