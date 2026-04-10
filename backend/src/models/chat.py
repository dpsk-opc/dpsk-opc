"""Chat module data models.

This module defines the data models for the MVP Chat API.
"""

import uuid
from datetime import datetime
from enum import Enum
from typing import List, Optional, Any, Dict

from pydantic import BaseModel, Field, field_validator


class SSEResponseType(str, Enum):
    """SSE event types."""
    AGENT_INFO = "agent_info"
    LOG = "log"
    MESSAGE = "message"
    DONE = "done"


class ChatMessage(BaseModel):
    """Single chat message model.
    
    Attributes:
        role: Message sender role (user/assistant/system)
        content: Message content
        timestamp: Message creation timestamp
    """
    role: str = Field(..., description="Message sender role")
    content: str = Field(..., description="Message content")
    timestamp: datetime = Field(default_factory=datetime.now, description="Message timestamp")
    
    class Config:
        json_encoders = {
            datetime: lambda v: int(v.timestamp())
        }


class ChatSession(BaseModel):
    """Chat session model.
    
    Attributes:
        session_id: Unique session identifier
        messages: List of messages in the session
        created_at: Session creation timestamp
    """
    session_id: str = Field(default_factory=lambda: str(uuid.uuid4()), description="Session ID")
    messages: List[ChatMessage] = Field(default_factory=list, description="Session messages")
    created_at: datetime = Field(default_factory=datetime.now, description="Creation time")
    
    def add_message(self, message: ChatMessage) -> None:
        """Add a message to the session.
        
        Args:
            message: ChatMessage to add
        """
        self.messages.append(message)
    
    def get_context(self, max_turns: int = 10) -> List[ChatMessage]:
        """Get recent context within the window limit.
        
        Args:
            max_turns: Maximum number of conversation turns (default 10)
        
        Returns:
            List of recent messages within the window
        """
        # 10 turns = 20 messages (user + assistant)
        max_messages = max_turns * 2
        return self.messages[-max_messages:] if len(self.messages) > max_messages else self.messages
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert session to dictionary.
        
        Returns:
            Dictionary representation of the session
        """
        return {
            "session_id": self.session_id,
            "messages": [
                {
                    "role": msg.role,
                    "content": msg.content,
                    "timestamp": int(msg.timestamp.timestamp())
                }
                for msg in self.messages
            ],
            "created_at": int(self.created_at.timestamp())
        }


class ChatRequest(BaseModel):
    """Chat request model.
    
    Attributes:
        message: User's message content
        session_id: Optional session ID for continuing a conversation
    """
    message: str = Field(..., description="User message content", min_length=1)
    session_id: Optional[str] = Field(None, description="Session ID (optional)")
    
    @field_validator("message")
    @classmethod
    def validate_message_not_empty(cls, v: str) -> str:
        """Validate that message is not empty or whitespace only.
        
        Args:
            v: Message content
        
        Returns:
            Validated message
        
        Raises:
            ValueError: If message is empty or whitespace only
        """
        if not v or not v.strip():
            raise ValueError("message cannot be empty or whitespace only")
        return v.strip()


class AgentInfo(BaseModel):
    """Agent information model.
    
    Attributes:
        agent_id: Unique agent identifier
        name: Agent display name
        avatar: Agent avatar URL (optional)
        available: Agent availability status
    """
    agent_id: str = Field(default="secretary", description="Agent ID")
    name: str = Field(default="秘书小秘", description="Agent name")
    avatar: Optional[str] = Field(default=None, description="Avatar URL")
    available: bool = Field(default=True, description="Availability status")


class SSEResponse(BaseModel):
    """Server-Sent Events response model.
    
    Attributes:
        type: SSE event type
        agent_id: Agent ID (for agent_info events)
        name: Agent name (for agent_info events)
        avatar: Agent avatar (for agent_info events)
        session_id: Session ID (for agent_info events)
        level: Log level (for log events)
        content: Message content (for message/log events)
        is_final: Whether this is the final message chunk
        timestamp: Event timestamp
    """
    type: SSEResponseType = Field(..., description="SSE event type")
    
    # Agent info fields
    agent_id: Optional[str] = Field(None, description="Agent ID")
    name: Optional[str] = Field(None, description="Agent name")
    avatar: Optional[str] = Field(None, description="Agent avatar")
    session_id: Optional[str] = Field(None, description="Session ID")
    
    # Log fields
    level: Optional[str] = Field(None, description="Log level")
    
    # Message fields
    content: Optional[str] = Field(None, description="Message content")
    is_final: Optional[bool] = Field(None, description="Is final chunk")
    
    # Timestamp
    timestamp: Optional[int] = Field(None, description="Event timestamp")
    
    @classmethod
    def agent_info(
        cls,
        agent_id: str,
        name: str,
        avatar: Optional[str] = None,
        session_id: Optional[str] = None
    ) -> "SSEResponse":
        """Create an agent_info event.
        
        Args:
            agent_id: Agent ID
            name: Agent name
            avatar: Agent avatar URL
            session_id: Session ID
        
        Returns:
            SSEResponse instance
        """
        return cls(
            type=SSEResponseType.AGENT_INFO,
            agent_id=agent_id,
            name=name,
            avatar=avatar,
            session_id=session_id,
            timestamp=int(datetime.now().timestamp())
        )
    
    @classmethod
    def log(cls, level: str, content: str) -> "SSEResponse":
        """Create a log event.
        
        Args:
            level: Log level (INFO/WARN/DEBUG)
            content: Log message content
        
        Returns:
            SSEResponse instance
        """
        return cls(
            type=SSEResponseType.LOG,
            level=level,
            content=content,
            timestamp=int(datetime.now().timestamp())
        )
    
    @classmethod
    def message(cls, content: str, is_final: bool = False) -> "SSEResponse":
        """Create a message event.
        
        Args:
            content: Message content
            is_final: Whether this is the final message chunk
        
        Returns:
            SSEResponse instance
        """
        return cls(
            type=SSEResponseType.MESSAGE,
            content=content,
            is_final=is_final
        )
    
    @classmethod
    def done(cls) -> "SSEResponse":
        """Create a done event.
        
        Returns:
            SSEResponse instance
        """
        return cls(type=SSEResponseType.DONE)
    
    def to_sse_data(self) -> Dict[str, Any]:
        """Convert to SSE data format.
        
        Returns:
            Dictionary suitable for SSE data field
        """
        data = {"type": self.type.value}
        
        if self.agent_id is not None:
            data["agent_id"] = self.agent_id
        if self.name is not None:
            data["name"] = self.name
        if self.avatar is not None:
            data["avatar"] = self.avatar
        if self.session_id is not None:
            data["session_id"] = self.session_id
        if self.level is not None:
            data["level"] = self.level
        if self.content is not None:
            data["content"] = self.content
        if self.is_final is not None:
            data["is_final"] = self.is_final
        if self.timestamp is not None:
            data["timestamp"] = self.timestamp
        
        return data
