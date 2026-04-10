"""Unit tests for chat models."""

import pytest
from datetime import datetime
from src.models.chat import (
    ChatMessage,
    ChatSession,
    ChatRequest,
    AgentInfo,
    SSEResponse,
    SSEResponseType,
)


class TestChatMessage:
    """Tests for ChatMessage model."""

    def test_create_user_message(self):
        """Test creating a user message."""
        msg = ChatMessage(role="user", content="Hello")
        
        assert msg.role == "user"
        assert msg.content == "Hello"
        assert msg.timestamp is not None
        assert isinstance(msg.timestamp, datetime)

    def test_create_assistant_message(self):
        """Test creating an assistant message."""
        msg = ChatMessage(role="assistant", content="Hi there!")
        
        assert msg.role == "assistant"
        assert msg.content == "Hi there!"

    def test_message_serialization(self):
        """Test message serialization to dict."""
        msg = ChatMessage(role="user", content="Test")
        data = msg.model_dump()
        
        assert data["role"] == "user"
        assert data["content"] == "Test"
        assert "timestamp" in data

    def test_message_deserialization(self):
        """Test message deserialization from dict."""
        data = {
            "role": "user",
            "content": "Hello",
            "timestamp": datetime.now().isoformat()
        }
        msg = ChatMessage(**data)
        
        assert msg.role == "user"
        assert msg.content == "Hello"


class TestChatSession:
    """Tests for ChatSession model."""

    def test_create_session(self):
        """Test creating a new session."""
        session = ChatSession()
        
        assert session.session_id is not None
        assert len(session.session_id) == 36  # UUID format
        assert session.messages == []
        assert session.created_at is not None

    def test_create_session_with_id(self):
        """Test creating a session with specific ID."""
        custom_id = "test-session-123"
        session = ChatSession(session_id=custom_id)
        
        assert session.session_id == custom_id

    def test_add_message_to_session(self):
        """Test adding messages to a session."""
        session = ChatSession()
        msg1 = ChatMessage(role="user", content="Hello")
        msg2 = ChatMessage(role="assistant", content="Hi!")
        
        session.add_message(msg1)
        session.add_message(msg2)
        
        assert len(session.messages) == 2
        assert session.messages[0].content == "Hello"
        assert session.messages[1].content == "Hi!"

    def test_session_serialization(self):
        """Test session serialization."""
        session = ChatSession()
        session.add_message(ChatMessage(role="user", content="Test"))
        
        data = session.to_dict()
        
        assert "session_id" in data
        assert "messages" in data
        assert len(data["messages"]) == 1


class TestAgentInfo:
    """Tests for AgentInfo model."""

    def test_create_agent_info(self):
        """Test creating agent info."""
        agent = AgentInfo(
            agent_id="secretary",
            name="秘书小秘",
            avatar=None
        )
        
        assert agent.agent_id == "secretary"
        assert agent.name == "秘书小秘"
        assert agent.avatar is None
        assert agent.available is True

    def test_agent_info_serialization(self):
        """Test agent info serialization."""
        agent = AgentInfo()
        data = agent.model_dump()
        
        assert data["agent_id"] == "secretary"
        assert data["name"] == "秘书小秘"
        assert data["avatar"] is None
        assert data["available"] is True


class TestChatRequest:
    """Tests for ChatRequest model."""

    def test_create_request(self):
        """Test creating a chat request."""
        request = ChatRequest(message="Hello")
        
        assert request.message == "Hello"
        assert request.session_id is None

    def test_create_request_with_session(self):
        """Test creating a request with session ID."""
        request = ChatRequest(message="Hello", session_id="session-123")
        
        assert request.message == "Hello"
        assert request.session_id == "session-123"

    def test_empty_message_validation(self):
        """Test that empty message is not allowed."""
        with pytest.raises(ValueError):
            ChatRequest(message="")

    def test_whitespace_only_message(self):
        """Test that whitespace-only message is not allowed."""
        with pytest.raises(ValueError):
            ChatRequest(message="   ")


class TestSSEResponse:
    """Tests for SSE response models."""

    def test_create_agent_info_event(self):
        """Test creating agent_info SSE event."""
        event = SSEResponse.agent_info(
            agent_id="secretary",
            name="秘书小秘",
            avatar=None,
            session_id="session-123"
        )
        
        assert event.type == SSEResponseType.AGENT_INFO
        assert event.agent_id == "secretary"
        assert event.session_id == "session-123"

    def test_create_log_event(self):
        """Test creating log SSE event."""
        event = SSEResponse.log(level="INFO", content="Test message")
        
        assert event.type == SSEResponseType.LOG
        assert event.level == "INFO"
        assert event.content == "Test message"
        assert event.timestamp is not None

    def test_create_message_event(self):
        """Test creating message SSE event."""
        event = SSEResponse.message(content="Hello", is_final=False)
        
        assert event.type == SSEResponseType.MESSAGE
        assert event.content == "Hello"
        assert event.is_final is False

    def test_create_message_final_event(self):
        """Test creating final message event."""
        event = SSEResponse.message(content="", is_final=True)
        
        assert event.type == SSEResponseType.MESSAGE
        assert event.is_final is True

    def test_create_done_event(self):
        """Test creating done SSE event."""
        event = SSEResponse.done()
        
        assert event.type == SSEResponseType.DONE

    def test_to_sse_format(self):
        """Test SSE format conversion."""
        event = SSEResponse.message(content="Hello", is_final=False)
        sse_data = event.to_sse_data()
        
        assert "type" in sse_data
        assert sse_data["content"] == "Hello"
