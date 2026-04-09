"""Tests for Logging and Tracing Utilities

This module contains unit tests for logging and tracing functionality.
"""

import json
import logging

import pytest

from src.utils.logging import (
    LogContext,
    configure_logging,
    get_logger,
    reset_logging,
)


class TestConfigureLogging:
    """Test cases for logging configuration."""

    def setup_method(self) -> None:
        """Reset logging before each test."""
        reset_logging()

    def test_configure_json_format(self) -> None:
        """Test configuring logging with JSON format."""
        configure_logging(level="INFO", format="json")
        logger = get_logger("test")
        assert logger is not None

    def test_configure_text_format(self) -> None:
        """Test configuring logging with text format."""
        configure_logging(level="DEBUG", format="text")
        logger = get_logger("test")
        assert logger is not None

    def test_configure_from_config(self) -> None:
        """Test configuring logging from Config object."""
        from src.config import LogConfig
        
        config = LogConfig(level="WARNING", format="json")
        configure_logging(level=config.level, format=config.format)
        logger = get_logger("test")
        assert logger is not None

    def test_reset_logging(self) -> None:
        """Test resetting logging configuration."""
        configure_logging(level="DEBUG")
        reset_logging()
        # After reset, should be able to configure again
        configure_logging(level="INFO")


class TestGetLogger:
    """Test cases for logger retrieval."""

    def setup_method(self) -> None:
        """Reset logging before each test."""
        reset_logging()
        configure_logging(level="DEBUG", format="text")

    def test_get_logger_basic(self) -> None:
        """Test getting a basic logger."""
        logger = get_logger("test")
        assert logger is not None
        assert logger.name == "test"

    def test_get_logger_with_fields(self) -> None:
        """Test getting a logger with additional fields."""
        logger = get_logger("test", agent_id="agent-1", trace_id="trace-123")
        assert logger is not None

    def test_logger_hierarchy(self) -> None:
        """Test logger name hierarchy."""
        parent = get_logger("parent")
        child = get_logger("parent.child")
        assert parent.name == "parent"
        assert child.name == "parent.child"


class TestLogContext:
    """Test cases for log context management."""

    def setup_method(self) -> None:
        """Reset logging before each test."""
        reset_logging()
        configure_logging(level="DEBUG", format="json")

    def test_context_manager(self) -> None:
        """Test using LogContext as a context manager."""
        logger = get_logger("context.test")
        
        with LogContext(agent_id="agent-1", trace_id="trace-123"):
            logger.info("Test message")
        
        # Should complete without error

    def test_bind_fields(self) -> None:
        """Test binding additional fields to logger."""
        logger = get_logger("bind.test")
        
        with LogContext(component="test-component"):
            bound_logger = logger.bind(component="override")
            assert bound_logger is not None

    def test_nested_context(self) -> None:
        """Test nested context managers."""
        logger = get_logger("nested.test")
        
        with LogContext(level=1):
            with LogContext(level=2):
                with LogContext(level=3):
                    logger.info("Deeply nested log")


class TestStructuredLogging:
    """Test cases for structured logging output."""

    def setup_method(self) -> None:
        """Reset logging before each test."""
        reset_logging()
        configure_logging(level="DEBUG", format="json")

    def test_log_with_trace_id(self, caplog: pytest.LogCaptureFixture) -> None:
        """Test logging with trace_id field."""
        logger = get_logger("trace.test")
        trace_id = "test-trace-123"
        
        with caplog.at_level(logging.INFO):
            logger.info("Test message", trace_id=trace_id)
        
        # Check that log contains trace_id (it's in the message JSON)
        assert any(
            trace_id in record.getMessage()
            for record in caplog.records
        )

    def test_log_with_agent_context(self, caplog: pytest.LogCaptureFixture) -> None:
        """Test logging with agent context."""
        logger = get_logger("agent.test")
        
        with caplog.at_level(logging.INFO):
            with LogContext(agent_id="agent-1"):
                logger.info("Agent message")
        
        # Check that log contains agent_id (it's in the message JSON)
        assert any(
            "agent-1" in record.getMessage()
            for record in caplog.records
        )
