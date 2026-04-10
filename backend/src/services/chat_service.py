"""Chat service module.

This module provides the core chat service functionality including:
- Context management for conversation history
- Mock LLM implementation for MVP
- Chat service orchestration
"""

import asyncio
import logging
import re
import time
import random
from datetime import datetime
from typing import AsyncGenerator, Dict, Optional, List

from src.models.chat import (
    ChatMessage,
    ChatSession,
    AgentInfo,
    SSEResponse,
    SSEResponseType,
)

# Use standard logging with fallback
try:
    from src.utils.logging import get_logger
except ImportError:
    def get_logger(name: str):
        """Fallback logger using standard logging."""
        return logging.getLogger(name)

logger = get_logger(__name__)


class AgentConfig:
    """Agent configuration model."""
    
    def __init__(
        self,
        agent_id: str = "secretary",
        name: str = "秘书小秘",
        avatar: Optional[str] = None,
        available: bool = True,
    ):
        self.agent_id = agent_id
        self.name = name
        self.avatar = avatar
        self.available = available
    
    def to_agent_info(self) -> AgentInfo:
        """Convert to AgentInfo model."""
        return AgentInfo(
            agent_id=self.agent_id,
            name=self.name,
            avatar=self.avatar,
            available=self.available,
        )


class ContextManager:
    """Manages conversation context and session storage.
    
    MVP阶段使用内存存储，不支持持久化。
    上下文窗口限制为最近10轮对话（20条消息）。
    """
    
    def __init__(self, max_turns: int = 10):
        """Initialize context manager.
        
        Args:
            max_turns: Maximum conversation turns to retain (default 10)
        """
        self._sessions: Dict[str, ChatSession] = {}
        self._max_turns = max_turns
        self._lock = asyncio.Lock()
        logger.info(f"ContextManager initialized with max_turns={max_turns}")
    
    async def create_session(self) -> ChatSession:
        """Create a new chat session.
        
        Returns:
            Newly created ChatSession
        """
        async with self._lock:
            session = ChatSession()
            self._sessions[session.session_id] = session
            logger.info(f"Created new session: {session.session_id}")
            return session
    
    async def get_session(self, session_id: str) -> Optional[ChatSession]:
        """Get an existing session by ID.
        
        Args:
            session_id: Session ID to retrieve
        
        Returns:
            ChatSession if found, None otherwise
        """
        async with self._lock:
            return self._sessions.get(session_id)
    
    async def add_message(
        self,
        session_id: str,
        role: str,
        content: str,
    ) -> Optional[ChatSession]:
        """Add a message to a session.
        
        Args:
            session_id: Target session ID
            role: Message role (user/assistant)
            content: Message content
        
        Returns:
            Updated ChatSession if successful, None if session not found
        """
        async with self._lock:
            session = self._sessions.get(session_id)
            if session is None:
                logger.warning(f"Session not found: {session_id}")
                return None
            
            message = ChatMessage(role=role, content=content)
            session.add_message(message)
            
            # Trim context if exceeds limit
            if len(session.messages) > self._max_turns * 2:
                session.messages = session.get_context(self._max_turns)
                logger.debug(
                    f"Trimmed session {session_id} to {len(session.messages)} messages"
                )
            
            return session
    
    async def get_context(self, session_id: str) -> List[ChatMessage]:
        """Get conversation context for a session.
        
        Args:
            session_id: Session ID
        
        Returns:
            List of recent messages within context window
        """
        session = await self.get_session(session_id)
        if session is None:
            return []
        return session.get_context(self._max_turns)


class MockLLM:
    """Mock LLM implementation for MVP testing.
    
    根据关键词返回预设响应，模拟打字效果。
    """
    
    # 预设响应规则
    GREETING_PATTERNS = [
        r"(你好|hi|hello|嗨|hi\s)",
    ]
    
    NAME_PATTERNS = [
        r"(名字|叫什么|你是谁|who are you)",
    ]
    
    THANKS_PATTERNS = [
        r"(谢谢|感谢|thanks)",
    ]
    
    HELP_PATTERNS = [
        r"(帮助|帮|怎么|help|how to)",
    ]
    
    def __init__(self, min_delay_ms: int = 30, max_delay_ms: int = 50):
        """Initialize MockLLM.
        
        Args:
            min_delay_ms: Minimum delay between chunks
            max_delay_ms: Maximum delay between chunks
        """
        self.min_delay_ms = min_delay_ms
        self.max_delay_ms = max_delay_ms
    
    def _get_response(self, message: str) -> str:
        """Get mock response based on message content.
        
        Args:
            message: User's message
        
        Returns:
            Mock response text
        """
        message_lower = message.lower()
        
        # Check greeting patterns
        for pattern in self.GREETING_PATTERNS:
            if re.search(pattern, message, re.IGNORECASE):
                return "你好！很高兴认识你。我是你的智能助手秘书小秘，有什么可以帮你的吗？"
        
        # Check name patterns
        for pattern in self.NAME_PATTERNS:
            if re.search(pattern, message, re.IGNORECASE):
                return "我叫秘书小秘，是你的专属智能助手。我可以帮助你处理日常事务、回答问题、或者只是陪你聊聊天。"
        
        # Check thanks patterns
        for pattern in self.THANKS_PATTERNS:
            if re.search(pattern, message, re.IGNORECASE):
                return "不客气！很高兴能帮到你。如果你还有其他问题，随时问我哦。"
        
        # Check help patterns
        for pattern in self.HELP_PATTERNS:
            if re.search(pattern, message, re.IGNORECASE):
                return "我可以帮助你做很多事情：\n1. 回答各种问题\n2. 帮你整理信息\n3. 陪你聊天解闷\n4. 提供建议和参考\n\n请告诉我你需要什么帮助？"
        
        # Default response with echo
        return f"我收到你的消息了：'{message}'\n\n作为MVP版本，我目前只能进行简单的对话。期待后续版本能为你提供更多帮助！"
    
    async def stream_response(
        self,
        message: str,
    ) -> AsyncGenerator[str, None]:
        """Generate streaming response.
        
        Args:
            message: User's message
        
        Yields:
            Response chunks
        """
        response = self._get_response(message)
        logger.debug(f"Mock LLM response: {response}")
        
        # Simulate typing effect by yielding character by character
        for char in response:
            # Random delay to simulate natural typing
            delay = random.uniform(
                self.min_delay_ms / 1000,
                self.max_delay_ms / 1000
            )
            await asyncio.sleep(delay)
            yield char


class ChatService:
    """Main chat service orchestrator.
    
    协调 ContextManager、MockLLM 和 Agent 配置，
    提供完整的聊天功能。
    """
    
    def __init__(
        self,
        agent_config: Optional[AgentConfig] = None,
        context_manager: Optional[ContextManager] = None,
        mock_llm: Optional[MockLLM] = None,
    ):
        """Initialize chat service.
        
        Args:
            agent_config: Agent configuration
            context_manager: Context manager instance
            mock_llm: Mock LLM instance
        """
        self.agent_config = agent_config or AgentConfig()
        self.context_manager = context_manager or ContextManager()
        self.mock_llm = mock_llm or MockLLM()
        logger.info(
            f"ChatService initialized with agent={self.agent_config.name}"
        )
    
    async def get_agent_info(self) -> AgentInfo:
        """Get current agent information.
        
        Returns:
            AgentInfo instance
        """
        return self.agent_config.to_agent_info()
    
    async def create_session(self) -> ChatSession:
        """Create a new chat session.
        
        Returns:
            Newly created ChatSession
        """
        return await self.context_manager.create_session()
    
    async def get_session(self, session_id: str) -> Optional[ChatSession]:
        """Get an existing session.
        
        Args:
            session_id: Session ID
        
        Returns:
            ChatSession if found, None otherwise
        """
        return await self.context_manager.get_session(session_id)
    
    async def process_message(
        self,
        message: str,
        session_id: Optional[str] = None,
    ) -> AsyncGenerator[SSEResponse, None]:
        """Process a user message and generate response stream.
        
        Args:
            message: User's message content
            session_id: Optional session ID for continuing conversation
        
        Yields:
            SSEResponse events
        """
        start_time = time.time()
        
        # Validate message
        if not message or not message.strip():
            raise ValueError("message cannot be empty")
        
        message = message.strip()
        
        # Get or create session
        if session_id:
            session = await self.context_manager.get_session(session_id)
            if session is None:
                logger.warning(f"Session not found: {session_id}, creating new")
                session = await self.context_manager.create_session()
        else:
            session = await self.context_manager.create_session()
        
        # Yield agent info event
        agent_info = await self.get_agent_info()
        yield SSEResponse.agent_info(
            agent_id=agent_info.agent_id,
            name=agent_info.name,
            avatar=agent_info.avatar,
            session_id=session.session_id,
        )
        
        # Log: received message
        preview = message[:20] + "..." if len(message) > 20 else message
        yield SSEResponse.log("INFO", f"收到用户消息: {preview}")
        
        # Add user message to context
        await self.context_manager.add_message(
            session.session_id,
            role="user",
            content=message,
        )
        
        # Get context for logging
        context = await self.context_manager.get_context(session.session_id)
        yield SSEResponse.log("INFO", f"已加载 {len(context)} 条上下文")
        
        # Log: calling model
        yield SSEResponse.log("INFO", "开始调用 LLM...")
        
        # Generate response stream
        full_response = ""
        first_chunk = True
        
        async for chunk in self.mock_llm.stream_response(message):
            full_response += chunk
            
            if first_chunk:
                yield SSEResponse.log("INFO", "模型响应首字节")
                first_chunk = False
            
            yield SSEResponse.message(content=chunk, is_final=False)
        
        # Log completion
        duration_ms = int((time.time() - start_time) * 1000)
        yield SSEResponse.log("INFO", f"回复生成完成，耗时 {duration_ms}ms")
        
        # Add assistant message to context
        await self.context_manager.add_message(
            session.session_id,
            role="assistant",
            content=full_response,
        )
        
        # Send final message event
        yield SSEResponse.message(content="", is_final=True)
        
        # Send done event
        yield SSEResponse.done()
        
        logger.info(
            f"Message processed: session={session.session_id}, "
            f"duration={duration_ms}ms"
        )
