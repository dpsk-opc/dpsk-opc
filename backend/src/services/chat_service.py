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
    
    支持 agent 事件订阅，用于可观测性：
    - agent:task_received - 任务被接收
    - agent:task_started - 任务开始处理
    - agent:react:think_start - ReAct 思考开始
    - agent:react:act_start - ReAct 工具调用开始
    - agent:tool:call_start - 工具调用开始
    - agent:tool:call_end - 工具调用结束
    - agent:task_completed - 任务完成
    - agent:task_failed - 任务失败
    """
    
    def __init__(
        self,
        opc_client: Any = None,
        agent_manager: Any = None,
        context_manager: Optional[ContextManager] = None,
        default_agent_id: str = "秘书",
        message_bus: Any = None,
    ):
        """Initialize chat service.
        
        Args:
            opc_client: OPC-Client instance for task orchestration
            agent_manager: AgentManager instance for agent information
            context_manager: Context manager instance
            default_agent_id: Default agent ID to use
            message_bus: Message bus instance for event subscriptions
        """
        self.opc_client = opc_client
        self.agent_manager = agent_manager
        self.context_manager = context_manager or ContextManager()
        self.default_agent_id = default_agent_id
        self._fallback_mode = opc_client is None
        self._message_bus = message_bus
        self._event_queue: Optional[asyncio.Queue[Any]] = None
        self._event_task: Optional[asyncio.Task[Any]] = None
        
        if self._fallback_mode:
            logger.warning(
                "ChatService running in fallback mode without OPC-Client. "
                "Using direct agent execution."
            )
        else:
            logger.info(
                f"ChatService initialized with OPC-Client, default_agent={default_agent_id}"
            )
    
    def set_message_bus(self, bus: Any) -> None:
        """Set the message bus for event subscriptions.
        
        Args:
            bus: Message bus instance
        """
        self._message_bus = bus
        logger.info("ChatService: message bus configured for event subscriptions")
    
    async def _subscribe_to_agent_events(self, correlation_id: str) -> None:
        """Subscribe to agent events for observability.
        
        Args:
            correlation_id: Correlation ID to filter events
        """
        if self._message_bus is None:
            logger.debug("No message bus configured, skipping event subscription")
            return
        
        try:
            from src.bus.models import Target, TargetType, Event as BusEvent
            
            self._event_queue = asyncio.Queue()
            
            async def event_handler(message: BusEvent) -> None:
                """Handle incoming agent events."""
                # Filter by correlation_id if provided
                if correlation_id and message.correlation_id != correlation_id:
                    return
                
                # Extract event data
                event_type = getattr(message, 'event_type', None) or message.payload.get('event_type', '')
                event_data = getattr(message, 'event_data', None) or message.payload.get('event_data', {})
                
                # Put event in queue for processing
                await self._event_queue.put({
                    'event_type': event_type,
                    'event_data': event_data,
                    'correlation_id': message.correlation_id,
                })
                logger.debug(f"Agent event received: {event_type}")
            
            # Subscribe to agent events topic
            target = Target(type=TargetType.TOPIC, value="agent.events")
            await self._message_bus.subscribe(target, event_handler)
            logger.info(f"Subscribed to agent.events topic for correlation_id={correlation_id}")
            
        except Exception as e:
            logger.warning(f"Failed to subscribe to agent events: {e}")
    
    async def _unsubscribe_from_agent_events(self) -> None:
        """Unsubscribe from agent events."""
        if self._message_bus is None or self._event_queue is None:
            return
        
        try:
            from src.bus.models import Target, TargetType
            
            # Cancel event task
            if self._event_task and not self._event_task.done():
                self._event_task.cancel()
                try:
                    await self._event_task
                except asyncio.CancelledError:
                    pass
            
            # Note: Full unsubscription would require keeping the handler reference
            # For now, we just stop processing
            self._event_queue = None
            logger.info("Unsubscribed from agent events")
            
        except Exception as e:
            logger.warning(f"Failed to unsubscribe from agent events: {e}")
    
    async def _event_listener(self) -> None:
        """Listen for agent events and forward to SSE."""
        if self._event_queue is None:
            return
        
        try:
            while True:
                try:
                    event = await asyncio.wait_for(self._event_queue.get(), timeout=0.1)
                    
                    # Create SSE response for the event
                    event_type = event.get('event_type', '')
                    event_data = event.get('event_data', {})
                    
                    yield SSEResponse.agent_event(
                        event_type=event_type,
                        message=event_data.get('message', ''),
                        agent_id=event_data.get('agent_id'),
                        react_step=event_data.get('react_step'),
                        tool_name=event_data.get('tool_name'),
                        event_data=event_data,
                    )
                    
                except asyncio.TimeoutError:
                    # Check if queue is closed
                    if self._event_queue is None or self._event_queue.empty() and False:
                        break
                    continue
                    
        except asyncio.CancelledError:
            logger.debug("Event listener cancelled")
        except Exception as e:
            logger.error(f"Event listener error: {e}")
    
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
        
        This method integrates with OPC-Client for task orchestration
        and supports agent event subscriptions for observability.
        
        Args:
            message: User's message content
            session_id: Optional session ID for continuing conversation
        
        Yields:
            SSEResponse events (including agent events for observability)
        """
        import uuid
        
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
        
        # Generate correlation ID for event tracking
        correlation_id = str(uuid.uuid4())
        
        # Subscribe to agent events
        await self._subscribe_to_agent_events(correlation_id)
        
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
        
        # Emit agent event: task received
        yield SSEResponse.agent_event(
            event_type="session:message_received",
            message=f"收到消息: {preview}",
            agent_id=agent_info.agent_id,
            event_data={"correlation_id": correlation_id},
        )
        
        # Add user message to context
        await self.context_manager.add_message(
            session.session_id,
            role="user",
            content=message,
        )
        
        # Get context for logging
        context = await self.context_manager.get_context(session.session_id)
        yield SSEResponse.log("INFO", f"已加载 {len(context)} 条上下文")
        
        # Create a task to listen for events while executing
        event_task = None
        full_response = ""
        
        try:
            # Start event listener as a background task
            if self._message_bus is not None:
                event_task = asyncio.create_task(self._listen_for_events())
            
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
        
        finally:
            # Cleanup: cancel event listener
            if event_task and not event_task.done():
                event_task.cancel()
                try:
                    await event_task
                except asyncio.CancelledError:
                    pass
            await self._unsubscribe_from_agent_events()
        
        # Send final message event
        yield SSEResponse.message(content="", is_final=True)
        
        # Send done event
        yield SSEResponse.done()
        
        logger.info(
            f"Message processed: session={session.session_id}, "
            f"duration={int((time.time() - start_time) * 1000)}ms"
        )
    
    async def _listen_for_events(self) -> None:
        """Background task to listen for agent events and yield SSE responses."""
        try:
            async for event in self._event_listener():
                # This will be yielded back to the caller
                # Since we're in a background task, we just log it
                logger.debug(f"Background event: {event.to_sse_data()}")
        except asyncio.CancelledError:
            logger.debug("Event listener cancelled")
    
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
