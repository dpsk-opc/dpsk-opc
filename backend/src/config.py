"""Configuration Management for DPSK-OPC Backend

This module provides configuration management with support for:
- Environment variables
- YAML configuration files
- Multiple environments (development, production)
"""

import os
from enum import Enum
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings


class MessageBusBackend(str, Enum):
    """Supported message bus backends."""

    MEMORY = "memory"  # In-memory, for development
    REDIS = "redis"  # Redis Stream, for production
    KAFKA = "kafka"  # Kafka, for high throughput


class ServerConfig(BaseModel):
    """Server configuration."""

    host: str = Field(default="0.0.0.0", description="Server host")
    port: int = Field(default=8000, ge=1, le=65535, description="Server port")


class BusConfig(BaseModel):
    """Message bus configuration."""

    backend: MessageBusBackend = Field(
        default=MessageBusBackend.MEMORY,
        description="Message bus backend"
    )
    
    # Redis configuration
    redis_url: str = Field(
        default="redis://localhost:6379",
        description="Redis connection URL"
    )
    redis_stream_max_len: int = Field(
        default=10000,
        ge=100,
        description="Maximum length of Redis streams"
    )
    
    # Kafka configuration
    kafka_brokers: list[str] = Field(
        default=["localhost:9092"],
        description="Kafka broker addresses"
    )
    kafka_client_id: str = Field(
        default="dpskopc",
        description="Kafka client ID"
    )
    
    # Timeout configuration
    request_timeout_secs: int = Field(
        default=30,
        ge=1,
        description="Request timeout in seconds"
    )
    group_task_timeout_secs: int = Field(
        default=60,
        ge=1,
        description="Group task timeout in seconds"
    )


class LogConfig(BaseModel):
    """Logging configuration."""

    level: str = Field(
        default="INFO",
        description="Log level (DEBUG, INFO, WARNING, ERROR)"
    )
    format: str = Field(
        default="json",
        description="Log format (json, text)"
    )


class DatabaseConfig(BaseModel):
    """Database configuration."""

    url: str = Field(
        default="sqlite:///./dpsk_opc.db",
        description="Database connection URL"
    )
    echo: bool = Field(
        default=False,
        description="Echo SQL queries"
    )


class MetricsConfig(BaseModel):
    """Metrics and observability configuration."""

    enabled: bool = Field(default=True, description="Enable metrics collection")
    prometheus_port: int = Field(
        default=9090,
        ge=1,
        le=65535,
        description="Prometheus metrics port"
    )


class LLMConfig(BaseModel):
    """LLM (Large Language Model) configuration."""

    # Default LLM settings
    provider: str = Field(
        default="openai",
        description="LLM provider (openai, azure, local, etc.)"
    )
    model: str = Field(
        default="gpt-3.5-turbo",
        description="Default model name"
    )
    api_key: str = Field(
        default="",
        description="API key for LLM provider"
    )
    base_url: str = Field(
        default="",
        description="Base URL for API (useful for proxies or Azure)"
    )
    temperature: float = Field(
        default=0.7,
        ge=0,
        le=2,
        description="Default sampling temperature"
    )
    max_tokens: int = Field(
        default=2000,
        ge=1,
        description="Default max tokens for response"
    )
    timeout: float = Field(
        default=60.0,
        ge=1,
        description="Request timeout in seconds"
    )
    # Streaming settings
    stream_chunk_delay_ms: int = Field(
        default=20,
        ge=0,
        description="Delay between chunks in streaming mode (ms)"
    )


class AgentConfig(BaseModel):
    """Agent configuration."""

    # Agent definitions directory
    agents_root: str = Field(
        default="~/.dpskopc/agents",
        description="Directory containing agent definitions"
    )
    # Default agent for chat
    default_agent_id: str = Field(
        default="秘书",
        description="Default agent ID for chat requests"
    )
    # Execution settings
    max_instances_per_agent: int = Field(
        default=5,
        ge=1,
        description="Maximum concurrent instances per agent"
    )
    agent_task_timeout_secs: int = Field(
        default=300,
        ge=1,
        description="Default task timeout for agent execution"
    )
    # Skills directory
    skills_root: str = Field(
        default="~/.dpskopc/skills",
        description="Directory containing skill modules"
    )
    # Experience pool (cache)
    experience_pool_enabled: bool = Field(
        default=True,
        description="Enable experience pool for caching results"
    )
    experience_pool_max_entries: int = Field(
        default=1000,
        ge=0,
        description="Maximum entries in experience pool"
    )


class Config(BaseModel):
    """Main configuration model."""

    server: ServerConfig = Field(default_factory=ServerConfig)
    bus: BusConfig = Field(default_factory=BusConfig)
    log: LogConfig = Field(default_factory=LogConfig)
    database: DatabaseConfig = Field(default_factory=DatabaseConfig)
    metrics: MetricsConfig = Field(default_factory=MetricsConfig)
    llm: LLMConfig = Field(default_factory=LLMConfig)
    agent: AgentConfig = Field(default_factory=AgentConfig)

    def to_dict(self) -> dict[str, Any]:
        """Convert configuration to dictionary."""
        return self.model_dump()

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Config":
        """Create configuration from dictionary."""
        # Handle nested configuration
        config = cls()
        
        if "server" in data:
            config.server = ServerConfig(**data["server"])
        
        if "bus" in data:
            bus_data = data["bus"].copy()
            if "backend" in bus_data and isinstance(bus_data["backend"], str):
                bus_data["backend"] = MessageBusBackend(bus_data["backend"])
            config.bus = BusConfig(**bus_data)
        
        if "log" in data:
            config.log = LogConfig(**data["log"])
        
        if "database" in data:
            config.database = DatabaseConfig(**data["database"])
        
        if "metrics" in data:
            config.metrics = MetricsConfig(**data["metrics"])
        
        if "llm" in data:
            config.llm = LLMConfig(**data["llm"])
        
        if "agent" in data:
            config.agent = AgentConfig(**data["agent"])
        
        return config


# Global configuration instance
_config: Config | None = None


def get_config() -> Config:
    """Get the current configuration instance."""
    global _config
    if _config is None:
        _config = _load_config_from_env()
    return _config


def reset_config() -> None:
    """Reset configuration to force reload."""
    global _config
    _config = None


def load_config(config_file: str | None = None) -> Config:
    """Load configuration from file and/or environment variables.
    
    Args:
        config_file: Optional path to YAML configuration file
        
    Returns:
        Loaded configuration
    """
    global _config
    
    # Start with default config
    config = Config()
    
    # Load from YAML file if provided
    if config_file and Path(config_file).exists():
        with open(config_file, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
            if data:
                config = Config.from_dict(data)
    
    # Override with environment variables
    config = _apply_env_overrides(config)
    
    _config = config
    return config


def _load_config_from_env() -> Config:
    """Load configuration from environment variables."""
    config = Config()
    return _apply_env_overrides(config)


def _apply_env_overrides(config: Config) -> Config:
    """Apply environment variable overrides to configuration."""
    
    # Server overrides
    if host := os.environ.get("SERVER_HOST"):
        config.server.host = host
    if port := os.environ.get("SERVER_PORT"):
        config.server.port = int(port)
    
    # Bus overrides
    if backend := os.environ.get("MESSAGE_BUS_BACKEND"):
        config.bus.backend = MessageBusBackend(backend)
    if redis_url := os.environ.get("REDIS_URL"):
        config.bus.redis_url = redis_url
    if redis_max_len := os.environ.get("REDIS_STREAM_MAX_LEN"):
        config.bus.redis_stream_max_len = int(redis_max_len)
    if kafka_brokers := os.environ.get("KAFKA_BROKERS"):
        config.bus.kafka_brokers = kafka_brokers.split(",")
    if kafka_client_id := os.environ.get("KAFKA_CLIENT_ID"):
        config.bus.kafka_client_id = kafka_client_id
    if request_timeout := os.environ.get("REQUEST_TIMEOUT_SECS"):
        config.bus.request_timeout_secs = int(request_timeout)
    if group_timeout := os.environ.get("GROUP_TASK_TIMEOUT_SECS"):
        config.bus.group_task_timeout_secs = int(group_timeout)
    
    # Log overrides
    if log_level := os.environ.get("LOG_LEVEL"):
        config.log.level = log_level
    if log_format := os.environ.get("LOG_FORMAT"):
        config.log.format = log_format
    
    # Database overrides
    if db_url := os.environ.get("DATABASE_URL"):
        config.database.url = db_url
    
    # Metrics overrides
    if prom_port := os.environ.get("PROMETHEUS_PORT"):
        config.metrics.prometheus_port = int(prom_port)
    
    # LLM overrides
    if llm_provider := os.environ.get("LLM_PROVIDER"):
        config.llm.provider = llm_provider
    if llm_model := os.environ.get("LLM_MODEL"):
        config.llm.model = llm_model
    if llm_api_key := os.environ.get("LLM_API_KEY"):
        config.llm.api_key = llm_api_key
    if llm_base_url := os.environ.get("LLM_BASE_URL"):
        config.llm.base_url = llm_base_url
    if llm_temp := os.environ.get("LLM_TEMPERATURE"):
        config.llm.temperature = float(llm_temp)
    if llm_max_tokens := os.environ.get("LLM_MAX_TOKENS"):
        config.llm.max_tokens = int(llm_max_tokens)
    if llm_timeout := os.environ.get("LLM_TIMEOUT"):
        config.llm.timeout = float(llm_timeout)
    
    # Agent overrides
    if agents_root := os.environ.get("AGENTS_ROOT"):
        config.agent.agents_root = agents_root
    if default_agent := os.environ.get("DEFAULT_AGENT_ID"):
        config.agent.default_agent_id = default_agent
    if skills_root := os.environ.get("SKILLS_ROOT"):
        config.agent.skills_root = skills_root
    if agent_timeout := os.environ.get("AGENT_TASK_TIMEOUT_SECS"):
        config.agent.agent_task_timeout_secs = int(agent_timeout)
    if max_instances := os.environ.get("MAX_INSTANCES_PER_AGENT"):
        config.agent.max_instances_per_agent = int(max_instances)
    
    return config


@lru_cache()
def get_settings() -> dict[str, Any]:
    """Get configuration as a dictionary for caching purposes."""
    return get_config().to_dict()
