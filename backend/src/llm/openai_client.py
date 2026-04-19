"""OpenAI-compatible LLM Client.

This module provides an LLM client implementation for OpenAI API
and OpenAI-compatible APIs (e.g., Azure OpenAI, local models).
"""

from __future__ import annotations

import logging
from typing import Any, AsyncIterator, Optional

try:
    import openai
    from openai import AsyncOpenAI
    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False

from src.llm.base import LLMClient, LLMConfig, LLMResponse

logger = logging.getLogger(__name__)


class OpenAIChatResponse:
    """Wrapper for OpenAI chat completion response.

    Provides a consistent interface for both regular and tool-call responses.
    """

    def __init__(self, raw_response: Any) -> None:
        """Initialize with raw OpenAI response.

        Args:
            raw_response: Raw response from OpenAI API
        """
        self.raw = raw_response
        self.model = raw_response.model
        self.usage = raw_response.usage
        self._choice = raw_response.choices[0] if raw_response.choices else None

    @property
    def message(self) -> dict[str, Any]:
        """Get the response message as dict.

        Returns:
            Message dict with role, content, and tool_calls (if any).
        """
        if self._choice is None:
            return {"role": "assistant", "content": ""}

        msg = self._choice.message

        result: dict[str, Any] = {
            "role": msg.role or "assistant",
            "content": msg.content or "",
        }

        # Handle tool calls
        if msg.tool_calls:
            result["tool_calls"] = []
            for tc in msg.tool_calls:
                tc_dict: dict[str, Any] = {
                    "id": tc.id,
                    "type": tc.type,
                    "function": {
                        "name": tc.function.name,
                        "arguments": tc.function.arguments,
                    },
                }
                result["tool_calls"].append(tc_dict)

        return result

    @property
    def finish_reason(self) -> str | None:
        """Get finish reason."""
        return self._choice.finish_reason if self._choice else None


class OpenAIClient(LLMClient):
    """LLM client for OpenAI API.
    
    Supports:
    - OpenAI API (api_key required)
    - Azure OpenAI (via base_url)
    - OpenAI-compatible APIs (via base_url)
    """
    
    def __init__(
        self,
        config: Optional[LLMConfig] = None,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        **kwargs: Any,
    ) -> None:
        """Initialize OpenAI client.
        
        Args:
            config: LLM configuration
            api_key: OpenAI API key (overrides config)
            base_url: Custom base URL for compatible APIs
            **kwargs: Additional configuration
        """
        super().__init__(config)
        
        if not OPENAI_AVAILABLE:
            raise ImportError(
                "openai package not installed. "
                "Install with: pip install openai"
            )
        
        # Override config with provided values
        if api_key:
            self.config.api_key = api_key
        if base_url:
            self.config.base_url = base_url
        
        # Create async client
        client_kwargs: dict[str, Any] = {
            "api_key": self.config.api_key or "not-provided",
        }
        if self.config.base_url:
            client_kwargs["base_url"] = self.config.base_url
        if self.config.timeout:
            client_kwargs["timeout"] = self.config.timeout
        
        self._client = AsyncOpenAI(**client_kwargs)
        self._logger.debug(f"OpenAI client initialized with model: {self.config.model}")
    
    async def complete(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        messages: Optional[list[dict[str, str]]] = None,
        **kwargs: Any,
    ) -> LLMResponse:
        """Generate a completion from OpenAI API.

        Args:
            prompt: The user prompt
            system_prompt: Optional system prompt
            messages: Optional conversation history (takes precedence)
            **kwargs: Additional parameters

        Returns:
            LLMResponse object
        """
        try:
            # Build messages
            chat_messages: list[dict[str, Any]] = []

            if messages:
                chat_messages = messages
            else:
                if system_prompt:
                    chat_messages.append({"role": "system", "content": system_prompt})
                chat_messages.append({"role": "user", "content": prompt})

            # Build request parameters
            request_params: dict[str, Any] = {
                "model": kwargs.pop("model", self.config.model),
                "messages": chat_messages,
            }

            # Apply config overrides
            if self.config.temperature is not None:
                request_params["temperature"] = kwargs.pop("temperature", self.config.temperature)
            if self.config.max_tokens is not None:
                request_params["max_tokens"] = kwargs.pop("max_tokens", self.config.max_tokens)
            if self.config.top_p is not None:
                request_params["top_p"] = kwargs.pop("top_p", self.config.top_p)

            # Apply any additional kwargs
            request_params.update(kwargs)

            # Make request
            response = await self._client.chat.completions.create(**request_params)

            # Parse response
            wrapped = OpenAIChatResponse(response)
            msg_dict = wrapped.message

            return LLMResponse(
                content=msg_dict.get("content", ""),
                model=response.model,
                usage={
                    "prompt_tokens": response.usage.prompt_tokens if response.usage else 0,
                    "completion_tokens": response.usage.completion_tokens if response.usage else 0,
                    "total_tokens": response.usage.total_tokens if response.usage else 0,
                },
                finish_reason=wrapped.finish_reason,
                tool_calls=msg_dict.get("tool_calls"),
                raw_response=response,
            )

        except Exception as e:
            self._logger.exception(f"OpenAI API error: {e}")
            return LLMResponse(
                error=str(e),
            )

    async def chat(
        self,
        messages: list[dict[str, Any]],
        tools: Optional[list[dict[str, Any]]] = None,
        **kwargs: Any,
    ) -> dict[str, Any]:
        """Chat completion with optional tool support.

        This method is designed for the new Tool Calling architecture,
        returning a dict with message and optional tool_calls.

        Args:
            messages: List of chat messages
            tools: Optional list of tools in OpenAI format
            **kwargs: Additional parameters

        Returns:
            Dict with 'message' containing role, content, and tool_calls
        """
        try:
            # Build request parameters
            request_params: dict[str, Any] = {
                "model": kwargs.pop("model", self.config.model),
                "messages": messages,
            }

            # Add tools if provided
            if tools:
                request_params["tools"] = tools
                # Auto-add tool_choice if tools are present
                request_params["tool_choice"] = kwargs.pop("tool_choice", "auto")

            # Apply config overrides
            if self.config.temperature is not None:
                request_params["temperature"] = kwargs.pop("temperature", self.config.temperature)
            if self.config.max_tokens is not None:
                request_params["max_tokens"] = kwargs.pop("max_tokens", self.config.max_tokens)
            if self.config.top_p is not None:
                request_params["top_p"] = kwargs.pop("top_p", self.config.top_p)

            # Apply any additional kwargs
            request_params.update(kwargs)

            # Make request
            # Log request details for debugging
            msgs = request_params.get('messages', [])
            tools_param = request_params.get('tools', [])
            logger.info(f"[OPENAI] Request: model={request_params.get('model')}, messages={len(msgs)}, tools={len(tools_param)}")

            # Log FULL message sequence with all details
            for i, msg in enumerate(msgs):
                role = msg.get('role', '?')
                has_tc = 'tool_calls' in msg
                has_tcid = 'tool_call_id' in msg
                content = msg.get('content', '')
                content_preview = str(content)[:80] if content else ''

                # Get tool_call info
                tc_info = []
                if 'tool_calls' in msg:
                    for tc in msg.get('tool_calls', []):
                        tc_info.append(f"id={tc.get('id')}, name={tc.get('function', {}).get('name')}")

                tc_id_val = msg.get('tool_call_id', 'N/A')

                logger.info(f"[OPENAI]   msg[{i}]: role={role}")
                logger.info(f"[OPENAI]     content: {content_preview}...")
                if tc_info:
                    logger.info(f"[OPENAI]     tool_calls: {tc_info}")
                if has_tcid:
                    logger.info(f"[OPENAI]     tool_call_id: {tc_id_val}")

            response = await self._client.chat.completions.create(**request_params)

            # Wrap response
            wrapped = OpenAIChatResponse(response)

            return {
                "message": wrapped.message,
                "model": response.model,
                "usage": {
                    "prompt_tokens": response.usage.prompt_tokens if response.usage else 0,
                    "completion_tokens": response.usage.completion_tokens if response.usage else 0,
                    "total_tokens": response.usage.total_tokens if response.usage else 0,
                },
                "finish_reason": wrapped.finish_reason,
            }

        except Exception as e:
            self._logger.exception(f"OpenAI API error: {e}")
            return {
                "error": str(e),
                "message": {"role": "assistant", "content": f"Error: {str(e)}"},
            }
    
    async def stream_complete(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        messages: Optional[list[dict[str, str]]] = None,
        **kwargs: Any,
    ) -> AsyncIterator[str]:
        """Generate a streaming completion from OpenAI API.
        
        Args:
            prompt: The user prompt
            system_prompt: Optional system prompt
            messages: Optional conversation history
            **kwargs: Additional parameters
            
        Yields:
            Text chunks
        """
        try:
            # Build messages
            chat_messages: list[dict[str, str]] = []
            
            if messages:
                chat_messages = messages
            else:
                if system_prompt:
                    chat_messages.append({"role": "system", "content": system_prompt})
                chat_messages.append({"role": "user", "content": prompt})
            
            # Build request parameters
            request_params: dict[str, Any] = {
                "model": kwargs.pop("model", self.config.model),
                "messages": chat_messages,
                "stream": True,
            }
            
            # Apply config overrides
            if self.config.temperature is not None:
                request_params["temperature"] = kwargs.pop("temperature", self.config.temperature)
            if self.config.max_tokens is not None:
                request_params["max_tokens"] = kwargs.pop("max_tokens", self.config.max_tokens)
            
            request_params.update(kwargs)
            
            # Stream response
            response = await self._client.chat.completions.create(**request_params)
            
            async for chunk in response:
                if chunk.choices and chunk.choices[0].delta.content:
                    yield chunk.choices[0].delta.content
                    
        except Exception as e:
            self._logger.exception(f"OpenAI streaming error: {e}")
            yield f"[Error: {e}]"
