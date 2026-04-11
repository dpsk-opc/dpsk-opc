"""LLM Registry Module.

This module provides a registry for managing LLM clients.
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from src.llm.base import LLMClient, LLMConfig

logger = logging.getLogger(__name__)

# Global registry instance
_llm_registry: Optional["LLMRegistry"] = None


class LLMRegistry:
    """Registry for managing LLM clients.
    
    The registry manages both:
    - Default LLM client (shared across agents without custom config)
    - Model-specific clients (for agents with custom LLM requirements)
    """
    
    def __init__(self) -> None:
        """Initialize LLM registry."""
        self._default_client: Optional[LLMClient] = None
        self._model_clients: dict[str, LLMClient] = {}
        self._client_factories: dict[str, type[LLMClient]] = {}
        
        # Register default factory
        self._register_default_factories()
    
    def _register_default_factories(self) -> None:
        """Register default client factories."""
        try:
            from src.llm.openai_client import OpenAIClient
            self.register_factory("openai", OpenAIClient)
            self.register_factory("gpt-4", OpenAIClient)
            self.register_factory("gpt-3.5-turbo", OpenAIClient)
            logger.debug("Registered default LLM factories")
        except ImportError as e:
            logger.warning(f"Could not register default factories: {e}")
    
    def register_factory(self, model_prefix: str, factory: type[LLMClient]) -> None:
        """Register a client factory for a model prefix.
        
        Args:
            model_prefix: Model name or prefix (e.g., "openai", "gpt-4")
            factory: LLM client factory class
        """
        self._client_factories[model_prefix] = factory
        logger.debug(f"Registered factory for model prefix: {model_prefix}")
    
    def set_default_client(self, client: LLMClient) -> None:
        """Set the default LLM client.
        
        Args:
            client: Default LLM client
        """
        self._default_client = client
        logger.info(f"Set default LLM client: {client.model}")
    
    def get_default_client(self) -> Optional[LLMClient]:
        """Get the default LLM client.
        
        Returns:
            Default LLM client or None
        """
        return self._default_client
    
    def get_client(
        self,
        model: Optional[str] = None,
        model_config: Optional[dict[str, Any]] = None,
    ) -> Optional[LLMClient]:
        """Get an LLM client for a specific model.
        
        If model is specified and differs from default, creates a new client.
        Otherwise returns the default client.
        
        Args:
            model: Model name (uses default if None)
            model_config: Additional model configuration
            
        Returns:
            LLM client or None
        """
        # If no specific model requested, return default
        if model is None:
            return self._default_client
        
        # Check if we already have a client for this model
        cache_key = f"{model}:{hash(frozenset((model_config or {}).items()))}"
        
        # If same model as default, return default client
        if self._default_client and model == self._default_client.model:
            return self._default_client
        
        # Check cache
        if cache_key in self._model_clients:
            return self._model_clients[cache_key]
        
        # Create new client for this model
        client = self._create_client(model, model_config)
        if client:
            self._model_clients[cache_key] = client
            logger.debug(f"Created new LLM client for model: {model}")
        
        return client
    
    def _create_client(
        self,
        model: str,
        model_config: Optional[dict[str, Any]] = None,
    ) -> Optional[LLMClient]:
        """Create an LLM client for a specific model.
        
        Args:
            model: Model name
            model_config: Model configuration
            
        Returns:
            LLM client or None
        """
        config = LLMConfig(model=model)
        
        # Apply model config
        if model_config:
            for key, value in model_config.items():
                if hasattr(config, key):
                    setattr(config, key, value)
        
        # Find factory
        factory = self._find_factory(model)
        
        if factory is None:
            logger.warning(f"No factory found for model: {model}")
            return None
        
        try:
            return factory(config=config)
        except Exception as e:
            logger.exception(f"Failed to create LLM client for {model}: {e}")
            return None
    
    def _find_factory(self, model: str) -> Optional[type[LLMClient]]:
        """Find a factory for a model.
        
        Args:
            model: Model name
            
        Returns:
            Client factory or None
        """
        # Direct match
        if model in self._client_factories:
            return self._client_factories[model]
        
        # Prefix match (e.g., "gpt-4" matches "gpt-" prefix)
        for prefix, factory in self._client_factories.items():
            if model.startswith(prefix):
                return factory
        
        # Return default if available
        return self._client_factories.get("openai")
    
    def initialize_defaults(
        self,
        model: str = "gpt-3.5-turbo",
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        **kwargs: Any,
    ) -> None:
        """Initialize default LLM client.
        
        Args:
            model: Default model name
            api_key: API key
            base_url: Base URL for API
            **kwargs: Additional configuration
        """
        config = LLMConfig(
            model=model,
            api_key=api_key,
            base_url=base_url,
            **kwargs,
        )
        
        factory = self._find_factory(model)
        if factory:
            try:
                client = factory(config=config)
                self.set_default_client(client)
                logger.info(f"Initialized default LLM: {model}")
            except Exception as e:
                logger.error(f"Failed to initialize default LLM: {e}")
        else:
            logger.warning(f"No factory available for model: {model}")


def get_llm_registry() -> LLMRegistry:
    """Get the global LLM registry instance.
    
    Returns:
        LLM registry
    """
    global _llm_registry
    if _llm_registry is None:
        _llm_registry = LLMRegistry()
    return _llm_registry


def set_llm_registry(registry: LLMRegistry) -> None:
    """Set the global LLM registry instance.
    
    Args:
        registry: LLM registry
    """
    global _llm_registry
    _llm_registry = registry
