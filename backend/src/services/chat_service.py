"""Chat service module.

This module provides the core chat service functionality including:
- Context management for conversation history
- Integration with OPC-Client for task orchestration
- Agent execution via LLM
"""

import asyncio
import logging
import time
from datetime import datetime
from typing import AsyncGenerator, Dict, Optional, List, Any

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


class ContextManager:
    """Manages conversation context and session storage.
    
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
        """Create a new chat session."""
        async with self._lock:
            session = ChatSession()
            self._sessions[session.session_id] = session
            logger.info(f"Created new session: {session.session_id}")
            return session
    
    async def get_session(self, session_id: str) -> Optional[ChatSession]:
        """Get an existing session by ID."""
        async with self._lock:
            return self._sessions.get(session_id)
    
    async def add_message(
        self,
        session_id: str,
        role: str,
        content: str,
    ) -> Optional[ChatSession]:
        """Add a message to a session."""
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
        """Get conversation context for a session."""
        session = await self.get_session(session_id)
        if session is None:
            return []
        return session.get_context(self._max_turns)
    
    def get_context_for_llm(self, session_id: str, system_prompt: str = "") -> List[Dict[str, str]]:
        """Get conversation context formatted for LLM.
        
        Args:
            session_id: Session ID
            system_prompt: Optional system prompt
            
        Returns:
            List of messages in OpenAI format
        """
        messages = []
        
        # Add system prompt if provided
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        
        # Add conversation history
        context = self._sessions.get(session_id)
        if context:
            for msg in context.messages:
                messages.append({
                    "role": msg.role,
                    "content": msg.content,
                })
        
        return messages


class ChatService:
    """Main chat service orchestrator.
    
    协调 ContextManager、OPC-Client 和 AgentManager，
    提供完整的聊天功能。
    
    ChatService 是用户交互的入口，将用户消息路由到 OPC-Client
    进行任务编排和执行。
    """
    
    def __init__(
        self,
        opc_client: Any = None,
        agent_manager: Any = None,
        context_manager: Optional[ContextManager] = None,
        default_agent_id: str = "秘书",
    ):
        """Initialize chat service.
        
        Args:
            opc_client: OPC-Client instance for task orchestration
            agent_manager: AgentManager instance for agent information
            context_manager: Context manager instance
            default_agent_id: Default agent ID to use
        """
        self.opc_client = opc_client
        self.agent_manager = agent_manager
        self.context_manager = context_manager or ContextManager()
        self.default_agent_id = default_agent_id
        self._fallback_mode = opc_client is None
        
        if self._fallback_mode:
            logger.warning(
                "ChatService running in fallback mode without OPC-Client. "
                "Using direct agent execution."
            )
        else:
            logger.info(
                f"ChatService initialized with OPC-Client, default_agent={default_agent_id}"
            )
    
    async def get_agent_info(self) -> AgentInfo:
        """Get current agent information.
        
        Returns:
            AgentInfo instance
        """
        if self.agent_manager:
            # Get from actual agent manager
            agents = self.agent_manager.list_agent_defs()
            if agents:
                agent = agents[0]  # Use first agent
                return AgentInfo(
                    agent_id=agent.get("agent_id", "unknown"),
                    name=agent.get("name", "Unknown"),
                    available=True,
                )
        
        # Default fallback
        return AgentInfo(
            agent_id=self.default_agent_id,
            name="秘书小秘",
            available=True,
        )
    
    async def create_session(self) -> ChatSession:
        """Create a new chat session."""
        return await self.context_manager.create_session()
    
    async def get_session(self, session_id: str) -> Optional[ChatSession]:
        """Get an existing session."""
        return await self.context_manager.get_session(session_id)
    
    async def process_message(
        self,
        message: str,
        session_id: Optional[str] = None,
    ) -> AsyncGenerator[SSEResponse, None]:
        """Process a user message and generate response stream.
        
        This method integrates with OPC-Client for task orchestration.
        
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
        
        try:
            if self.opc_client and not self._fallback_mode:
                # Use OPC-Client for task orchestration
                yield SSEResponse.log("INFO", "通过 OPC-Client 执行请求...")
                full_response = await self._execute_via_opc(
                    message, 
                    session.session_id,
                )
            else:
                # Fallback: use agent manager directly
                yield SSEResponse.log("INFO", "通过 Agent 直接执行请求...")
                full_response = await self._execute_via_agent(
                    message,
                    session.session_id,
                )
            
            # Log completion
            duration_ms = int((time.time() - start_time) * 1000)
            yield SSEResponse.log("INFO", f"回复生成完成，耗时 {duration_ms}ms")
            
            # Stream the response character by character for better UX
            first_chunk = True
            for char in full_response:
                if first_chunk:
                    yield SSEResponse.log("INFO", "模型响应首字节")
                    first_chunk = False
                yield SSEResponse.message(content=char, is_final=False)
                await asyncio.sleep(0.01)  # Small delay for streaming effect
            
            # Add assistant message to context
            await self.context_manager.add_message(
                session.session_id,
                role="assistant",
                content=full_response,
            )
            
        except Exception as e:
            logger.exception(f"Error processing message: {e}")
            yield SSEResponse.log("ERROR", f"处理失败: {str(e)}")
            full_response = f"抱歉，处理您的请求时出现了错误：{str(e)}"
            
            # Stream error response
            for char in full_response:
                yield SSEResponse.message(content=char, is_final=False)
        
        # Send final message event
        yield SSEResponse.message(content="", is_final=True)
        
        # Send done event
        yield SSEResponse.done()
        
        logger.info(
            f"Message processed: session={session.session_id}, "
            f"duration={int((time.time() - start_time) * 1000)}ms"
        )
    
    async def _execute_via_opc(
        self,
        message: str,
        session_id: str,
        log_callback=None,
    ) -> str:
        """Execute request via OPC-Client.
        
        Args:
            message: User's message
            session_id: Session ID for context
            log_callback: Optional callback for log messages
            
        Returns:
            Response content
        """
        try:
            # Build request with context
            request = message
            
            # Use run_with_progress for streaming
            response_chunks = []
            async for event in self.opc_client.run_with_progress(request):
                # Handle ProgressEvent as dataclass
                event_type = event.type if hasattr(event, 'type') else ""
                event_data = event.data if hasattr(event, 'data') and event.data else {}
                event_task_id = event.task_id if hasattr(event, 'task_id') else ""
                
                if event_type == "workflow_start":
                    logger.info("开始工作流执行...")
                elif event_type == "workflow_complete":
                    logger.info("工作流执行完成")
                    # Extract content from data
                    if event_data:
                        content = event_data.get("content") or event_data.get("output", "")
                        if content:
                            response_chunks.append(str(content))
                elif event_type == "workflow_failed":
                    logger.warning(f"工作流执行失败: {event_data.get('error', 'Unknown')}")
                elif event_type == "task_start":
                    logger.debug(f"开始执行任务: {event_task_id}")
                elif event_type == "task_complete":
                    logger.debug(f"任务完成: {event_task_id}")
                    # Extract output from task result
                    output = event_data.get("output", {})
                    if isinstance(output, dict):
                        content = output.get("content", "")
                        if content:
                            response_chunks.append(content)
                    elif isinstance(output, str):
                        response_chunks.append(output)
                elif event_type == "error":
                    logger.error(f"执行错误: {event_data.get('message', 'Unknown')}")
            
            if response_chunks:
                return "\n".join(response_chunks)
            else:
                return "任务执行完成，但没有返回内容。"
                
        except Exception as e:
            logger.exception(f"OPC-Client execution failed: {e}")
            raise
    
    async def _execute_via_agent(
        self,
        message: str,
        session_id: str,
    ) -> str:
        """Execute request directly via Agent (fallback mode).
        
        Args:
            message: User's message
            session_id: Session ID for context
            
        Returns:
            Response content
        """
        from src.agent.runner import call_llm
        
        # Get agent info
        agent_info = await self.get_agent_info()
        
        # Build context for LLM
        context_messages = self.context_manager.get_context_for_llm(
            session_id,
            system_prompt=f"你是一个智能助手，名叫{agent_info.name}。"
        )
        
        # Add current message
        context_messages.append({"role": "user", "content": message})
        
        # Call LLM
        try:
            result = await call_llm(
                prompt="",
                messages=context_messages,
            )
            
            if result.get("error"):
                raise RuntimeError(result["error"])
            
            return result.get("content", "没有收到回复")
            
        except Exception as e:
            logger.exception(f"Agent execution failed: {e}")
            return f"抱歉，执行失败：{str(e)}"
