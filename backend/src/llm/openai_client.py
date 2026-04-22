"""OpenAI-compatible LLM Client.

This module provides an LLM client implementation for OpenAI API
and OpenAI-compatible APIs (e.g., Azure OpenAI, local models).
"""

from __future__ import annotations

import json
import logging
import re
import uuid
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
            message = wrapped.message

            # Post-process: Some models (like DeepSeek) output function_calls in content
            # instead of using the standard tool_calls field. Try to parse and extract.
            if not message.get("tool_calls") and message.get("content"):
                parsed_tc = self._parse_inline_function_calls(message.get("content", ""))
                if parsed_tc:
                    logger.info(f"[OPENAI] Parsed {len(parsed_tc)} function calls from content")
                    message["tool_calls"] = parsed_tc
                    # Remove the function_calls block from content
                    message["content"] = self._remove_inline_function_calls(message.get("content", ""))

            return {
                "message": message,
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

    def _parse_inline_function_calls(self, content: str) -> list[dict[str, Any]]:
        """Parse inline function calls from content.

        Some models output function calls in a special format within the content
        instead of using the standard tool_calls field. This method parses those.

        Args:
            content: The message content string

        Returns:
            List of parsed tool calls, or empty list if none found.
        """
        tool_calls = []

        # Pattern for DSML format: <｜DSML｜invoke name="xxx">
        # with arguments between <｜DSML｜invoke_arg name="xxx">...</｜DSML｜invoke_arg>
        invoke_pattern = r'<｜DSML｜invoke name="([^"]+)"[^>]*>(.*?)(?=<｜DSML｜invoke|$)'
        # Use [\s\S]*? instead of [^<]* to match content across multiple lines
        arg_pattern = r'<｜DSML｜invoke_arg name="([^"]+)"[^>]*>([\s\S]*?)</｜DSML｜invoke_arg>'

        matches = re.finditer(invoke_pattern, content, re.DOTALL)
        for idx, match in enumerate(matches):
            tool_name = match.group(1).strip()
            invoke_content = match.group(2)
            logger.info(f"[OPENAI] Matched invoke '{tool_name}', content length: {len(invoke_content)}")
            logger.info(f"[OPENAI] invoke_content preview: {invoke_content[:200]}...")

            # Parse arguments from the invoke block
            args: dict[str, Any] = {}
            arg_matches = re.finditer(arg_pattern, invoke_content, re.DOTALL)
            for arg_match in arg_matches:
                arg_name = arg_match.group(1).strip()
                arg_value = arg_match.group(2).strip()
                logger.info(f"[OPENAI] Matched arg: {arg_name} = '{arg_value[:50]}...' (len={len(arg_value)})")
                try:
                    # Try to parse as JSON
                    args[arg_name] = json.loads(arg_value)
                except (json.JSONDecodeError, ValueError):
                    # Use as string if not valid JSON
                    args[arg_name] = arg_value

            logger.info(f"[OPENAI] Final args for {tool_name}: {args}")
            tool_call = {
                "id": f"call_{uuid.uuid4().hex[:8]}",
                "type": "function",
                "function": {
                    "name": tool_name,
                    "arguments": json.dumps(args, ensure_ascii=False),
                },
            }
            tool_calls.append(tool_call)

        if tool_calls:
            logger.info(f"[OPENAI] Parsed {len(tool_calls)} inline function calls: {[tc['function']['name'] for tc in tool_calls]}")
        else:
            # Check if content contains function_calls markers
            has_markers = '<｜DSML｜invoke' in content
            logger.warning(f"[OPENAI] No function calls parsed! has_DSML_markers={has_markers}")
            if has_markers:
                # Find the relevant section
                idx = content.find('<｜DSML｜invoke')
                logger.warning(f"[OPENAI] Content around first marker: ...{content[max(0,idx-20):idx+150]}...")

        return tool_calls

    def _remove_inline_function_calls(self, content: str) -> str:
        """Remove inline function calls from content.

        After extracting function calls, remove the blocks from the content
        so they don't appear in the final response.

        Args:
            content: The message content string

        Returns:
            Content with function call blocks removed.
        """
        # Remove DSML function call blocks while keeping surrounding text
        # Pattern to match the entire function_calls block
        pattern = r'\s*<｜DSML｜function_calls[^>]*>.*?<｜DSML｜function_calls>\s*'

        # Also handle individual invoke blocks
        pattern2 = r'\s*<｜DSML｜invoke[^>]*>.*?</｜DSML｜invoke>\s*'

        cleaned = re.sub(pattern, '\n', content, flags=re.DOTALL)
        cleaned = re.sub(pattern2, '\n', cleaned, flags=re.DOTALL)

        # Clean up multiple newlines
        cleaned = re.sub(r'\n{3,}', '\n\n', cleaned)

        return cleaned.strip()
    
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
