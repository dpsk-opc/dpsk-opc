"""LLM Client Base Module.

This module defines the abstract interface for LLM clients.
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, AsyncIterator, Optional

logger = logging.getLogger(__name__)


@dataclass
class LLMResponse:
    """Response from LLM API."""
    
    content: str = ""
    model: str = ""
    usage: dict[str, int] = field(default_factory=dict)
    finish_reason: Optional[str] = None
    error: Optional[str] = None
    
    @property
    def success(self) -> bool:
        """Check if response is successful."""
        return self.error is None and self.content


@dataclass
class LLMConfig:
    """Configuration for LLM client."""
    
    model: str = "gpt-3.5-turbo"
    temperature: float = 0.7
    max_tokens: Optional[int] = None
    top_p: Optional[float] = None
    timeout: float = 60.0
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    
    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "LLMConfig":
        """Create config from dictionary."""
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})


class LLMClient(ABC):
    """Abstract base class for LLM clients.
    
    All LLM implementations must inherit from this class and implement
    the abstract methods.
    """
    
    def __init__(self, config: Optional[LLMConfig] = None) -> None:
        """Initialize LLM client.
        
        Args:
            config: LLM configuration
        """
        self.config = config or LLMConfig()
        self._logger = logging.getLogger(self.__class__.__name__)
    
    @property
    def model(self) -> str:
        """Get the model name."""
        return self.config.model
    
    @abstractmethod
    async def complete(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        messages: Optional[list[dict[str, str]]] = None,
        **kwargs: Any,
    ) -> LLMResponse:
        """Generate a completion from the LLM.
        
        Args:
            prompt: The user prompt
            system_prompt: Optional system prompt
            messages: Optional conversation history
            **kwargs: Additional parameters
            
        Returns:
            LLMResponse object
        """
        raise NotImplementedError
    
    @abstractmethod
    async def stream_complete(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        messages: Optional[list[dict[str, str]]] = None,
        **kwargs: Any,
    ) -> AsyncIterator[str]:
        """Generate a streaming completion from the LLM.
        
        Args:
            prompt: The user prompt
            system_prompt: Optional system prompt
            messages: Optional conversation history
            **kwargs: Additional parameters
            
        Yields:
            Text chunks
        """
        raise NotImplementedError
    
    async def chat(
        self,
        messages: list[dict[str, str]],
        **kwargs: Any,
    ) -> LLMResponse:
        """Chat with the LLM using message format.
        
        Args:
            messages: List of message dicts with 'role' and 'content'
            **kwargs: Additional parameters
            
        Returns:
            LLMResponse object
        """
        return await self.complete(
            prompt="",
            messages=messages,
            **kwargs,
        )
    
    async def chat_stream(
        self,
        messages: list[dict[str, str]],
        **kwargs: Any,
    ) -> AsyncIterator[str]:
        """Stream chat with the LLM using message format.
        
        Args:
            messages: List of message dicts with 'role' and 'content'
            **kwargs: Additional parameters
            
        Yields:
            Text chunks
        """
        async for chunk in self.stream_complete(
            prompt="",
            messages=messages,
            **kwargs,
        ):
            yield chunk
