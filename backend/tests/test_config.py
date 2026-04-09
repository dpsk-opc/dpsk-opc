"""Tests for Configuration Management

This module contains unit tests for the configuration module.
"""

import os
from typing import Any

import pytest
from pydantic import ValidationError

from src.config import (
    BusConfig,
    Config,
    DatabaseConfig,
    LogConfig,
    MessageBusBackend,
    ServerConfig,
    load_config,
    reset_config,
)


class TestMessageBusBackend:
    """Test cases for MessageBusBackend enum."""

    def test_backend_values(self) -> None:
        """Test that all backend types have correct values."""
        assert MessageBusBackend.MEMORY.value == "memory"
        assert MessageBusBackend.REDIS.value == "redis"
        assert MessageBusBackend.KAFKA.value == "kafka"

    def test_backend_from_string(self) -> None:
        """Test creating backend from string."""
        assert MessageBusBackend("memory") == MessageBusBackend.MEMORY
        assert MessageBusBackend("redis") == MessageBusBackend.REDIS


class TestBusConfig:
    """Test cases for BusConfig model."""

    def test_default_values(self) -> None:
        """Test default configuration values."""
        config = BusConfig()
        assert config.backend == MessageBusBackend.MEMORY
        assert config.request_timeout_secs == 30
        assert config.group_task_timeout_secs == 60

    def test_custom_values(self) -> None:
        """Test custom configuration values."""
        config = BusConfig(
            backend=MessageBusBackend.REDIS,
            redis_url="redis://localhost:6379",
            redis_stream_max_len=5000,
        )
        assert config.backend == MessageBusBackend.REDIS
        assert config.redis_url == "redis://localhost:6379"
        assert config.redis_stream_max_len == 5000

    def test_invalid_backend(self) -> None:
        """Test that invalid backend type raises error."""
        with pytest.raises(ValidationError):
            BusConfig(backend="invalid")


class TestServerConfig:
    """Test cases for ServerConfig model."""

    def test_default_values(self) -> None:
        """Test default server configuration."""
        config = ServerConfig()
        assert config.host == "0.0.0.0"
        assert config.port == 8000

    def test_custom_values(self) -> None:
        """Test custom server configuration."""
        config = ServerConfig(host="127.0.0.1", port=9000)
        assert config.host == "127.0.0.1"
        assert config.port == 9000


class TestLogConfig:
    """Test cases for LogConfig model."""

    def test_default_values(self) -> None:
        """Test default log configuration."""
        config = LogConfig()
        assert config.level == "INFO"
        assert config.format == "json"

    def test_custom_values(self) -> None:
        """Test custom log configuration."""
        config = LogConfig(level="DEBUG", format="text")
        assert config.level == "DEBUG"
        assert config.format == "text"


class TestDatabaseConfig:
    """Test cases for DatabaseConfig model."""

    def test_default_values(self) -> None:
        """Test default database configuration."""
        config = DatabaseConfig()
        assert config.url == "sqlite:///./dpsk_opc.db"
        assert config.echo is False


class TestConfig:
    """Test cases for Config model."""

    def test_default_config(self) -> None:
        """Test creating default configuration."""
        config = Config()
        assert config.server.host == "0.0.0.0"
        assert config.server.port == 8000
        assert config.bus.backend == MessageBusBackend.MEMORY

    def test_custom_config(self) -> None:
        """Test creating custom configuration."""
        config = Config(
            server=ServerConfig(host="127.0.0.1", port=9000),
            bus=BusConfig(backend=MessageBusBackend.REDIS),
            log=LogConfig(level="DEBUG"),
        )
        assert config.server.host == "127.0.0.1"
        assert config.bus.backend == MessageBusBackend.REDIS
        assert config.log.level == "DEBUG"

    def test_config_from_dict(self) -> None:
        """Test creating configuration from dictionary."""
        data = {
            "server": {"host": "0.0.0.0", "port": 8000},
            "bus": {"backend": "redis", "redis_url": "redis://localhost:6379"},
            "log": {"level": "DEBUG"},
        }
        config = Config.from_dict(data)
        assert config.server.port == 8000
        assert config.bus.backend == MessageBusBackend.REDIS
        assert config.log.level == "DEBUG"

    def test_config_to_dict(self) -> None:
        """Test converting configuration to dictionary."""
        config = Config()
        data = config.to_dict()
        assert isinstance(data, dict)
        assert "server" in data
        assert "bus" in data


class TestConfigLoading:
    """Test cases for configuration loading functions."""

    def test_load_from_env(self) -> None:
        """Test loading configuration from environment variables."""
        os.environ["SERVER_HOST"] = "127.0.0.1"
        os.environ["SERVER_PORT"] = "9000"
        os.environ["MESSAGE_BUS_BACKEND"] = "redis"
        os.environ["REDIS_URL"] = "redis://localhost:6379"
        os.environ["LOG_LEVEL"] = "DEBUG"
        
        try:
            config = load_config()
            assert config.server.host == "127.0.0.1"
            assert config.server.port == 9000
            assert config.bus.backend == MessageBusBackend.REDIS
            assert config.bus.redis_url == "redis://localhost:6379"
            assert config.log.level == "DEBUG"
        finally:
            # Clean up
            reset_config()
            for key in ["SERVER_HOST", "SERVER_PORT", "MESSAGE_BUS_BACKEND", "REDIS_URL", "LOG_LEVEL"]:
                if key in os.environ:
                    del os.environ[key]

    def test_load_from_yaml(self, tmp_path: Any) -> None:
        """Test loading configuration from YAML file."""
        import yaml
        
        config_data = {
            "server": {"host": "0.0.0.0", "port": 8000},
            "bus": {"backend": "memory"},
            "log": {"level": "INFO"},
        }
        config_file = tmp_path / "config.yaml"
        config_file.write_text(yaml.dump(config_data))
        
        config = load_config(config_file=str(config_file))
        assert config.server.port == 8000
        assert config.bus.backend == MessageBusBackend.MEMORY

    def test_reset_config(self) -> None:
        """Test resetting configuration to defaults."""
        # Set some environment variables
        os.environ["SERVER_PORT"] = "9999"
        
        config1 = load_config()
        assert config1.server.port == 9999
        
        # Reset
        reset_config()
        
        # Should reload with defaults (or previously set env vars)
        config2 = load_config()
        # After reset, if env var is still set, it should be used
        assert config2.server.port == 9999
        
        # Clean up
        del os.environ["SERVER_PORT"]
        reset_config()
