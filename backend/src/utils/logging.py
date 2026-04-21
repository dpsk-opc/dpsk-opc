"""Logging and Tracing Utilities for DPSK-OPC Backend

This module provides structured logging with OpenTelemetry tracing support.
"""

import json
import logging
import os
import sys
import uuid
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any, Iterator

import structlog

# Context variable for trace ID propagation
_trace_id_var: ContextVar[str | None] = ContextVar("trace_id", default=None)
_component_var: ContextVar[str | None] = ContextVar("component", default=None)

# Flag to track if logging has been configured
_logging_configured = False


def get_trace_id() -> str | None:
    """Get the current trace ID from context."""
    return _trace_id_var.get()


def set_trace_id(trace_id: str) -> None:
    """Set the trace ID for the current context."""
    _trace_id_var.set(trace_id)


def generate_trace_id() -> str:
    """Generate a new trace ID."""
    return str(uuid.uuid4())


def _json_serializer(obj: Any, **kw: Any) -> str:
    """Default JSON serializer for structlog."""
    # Don't override the default if provided by structlog
    if "default" not in kw:
        kw["default"] = str
    return json.dumps(obj, **kw)


def _console_renderer(
    logger: Any, method_name: str, event_dict: Any
) -> str:
    """Console renderer for structlog with logger name."""
    # Get logger name from event_dict
    logger_name = event_dict.pop("logger", "")
    log_level = event_dict.pop("level", method_name.upper())
    
    # Format timestamp
    timestamp = event_dict.pop("timestamp", "")
    if timestamp:
        # Remove date part, keep time
        if "T" in timestamp:
            timestamp = timestamp.split("T")[1][:8]
    
    # Build output
    parts = []
    
    # Timestamp
    if timestamp:
        parts.append(f"{timestamp}")
    
    # Level
    level_colors = {
        "DEBUG": "\033[36m",    # Cyan
        "INFO": "\033[32m",     # Green
        "WARNING": "\033[33m",  # Yellow
        "ERROR": "\033[31m",    # Red
        "CRITICAL": "\033[35m", # Magenta
    }
    color = level_colors.get(log_level, "")
    reset = "\033[0m"
    parts.append(f"{color}[{log_level:8}]{reset}")
    
    # Logger name (category)
    if logger_name:
        parts.append(f"[{logger_name}]")
    
    # Event/message
    event = event_dict.pop("event", "")
    parts.append(event)
    
    # Remaining fields as key=value
    if event_dict:
        extra_parts = []
        for key, value in event_dict.items():
            if key not in ("stack_info", "exc_info", "format_exc_info"):
                extra_parts.append(f"{key}={value}")
        if extra_parts:
            parts.append(" ".join(extra_parts))
    
    return " ".join(parts)


def _console_renderer_simple(
    logger: Any, method_name: str, event_dict: Any
) -> str:
    """Simple console renderer without colors for structlog."""
    # Get logger name from event_dict
    logger_name = event_dict.pop("logger", "")
    log_level = event_dict.pop("level", method_name.upper())
    
    # Format timestamp
    timestamp = event_dict.pop("timestamp", "")
    if timestamp:
        if "T" in timestamp:
            timestamp = timestamp.split("T")[1][:8]
    
    # Build output
    parts = []
    if timestamp:
        parts.append(f"{timestamp}")
    
    parts.append(f"[{log_level:8}]")
    
    if logger_name:
        parts.append(f"[{logger_name}]")
    
    event = event_dict.pop("event", "")
    parts.append(event)
    
    if event_dict:
        extra_parts = []
        for key, value in event_dict.items():
            if key not in ("stack_info", "exc_info", "format_exc_info"):
                extra_parts.append(f"{key}={value}")
        if extra_parts:
            parts.append(" ".join(extra_parts))
    
    return " ".join(parts)


def configure_logging(level: str = "INFO", format: str = "json") -> None:
    """Configure structured logging.
    
    This configures BOTH structlog (for src.utils.logging.get_logger)
    AND standard logging (for logging.getLogger) to ensure all modules
    output logs consistently based on LOG_LEVEL configuration.
    
    Args:
        level: Log level (DEBUG, INFO, WARNING, ERROR)
        format: Log format ('json' or 'text')
    """
    global _logging_configured
    
    # Convert level string to logging level
    numeric_level = getattr(logging, level.upper(), logging.INFO)
    
    # Configure structlog
    if format == "json":
        # JSON output for production
        structlog.configure(
            processors=[
                structlog.contextvars.merge_contextvars,
                structlog.stdlib.filter_by_level,
                structlog.stdlib.add_logger_name,
                structlog.stdlib.add_log_level,
                structlog.processors.TimeStamper(fmt="iso"),
                structlog.processors.StackInfoRenderer(),
                structlog.processors.format_exc_info,
                structlog.processors.UnicodeDecoder(),
                structlog.processors.JSONRenderer(serializer=_json_serializer),
            ],
            wrapper_class=structlog.stdlib.BoundLogger,
            context_class=dict,
            logger_factory=structlog.stdlib.LoggerFactory(),
            cache_logger_on_first_use=True,
        )
    else:
        # Text output for development
        structlog.configure(
            processors=[
                structlog.contextvars.merge_contextvars,
                structlog.stdlib.filter_by_level,
                structlog.stdlib.add_logger_name,
                structlog.stdlib.add_log_level,
                structlog.processors.TimeStamper(fmt="%Y-%m-%d %H:%M:%S"),
                structlog.processors.UnicodeDecoder(),
                _console_renderer,
            ],
            wrapper_class=structlog.stdlib.BoundLogger,
            context_class=dict,
            logger_factory=structlog.stdlib.LoggerFactory(),
            cache_logger_on_first_use=True,
        )
    
    # Configure root logging for standard library loggers
    root_logger = logging.getLogger()
    root_logger.setLevel(numeric_level)
    
    # Remove existing handlers
    for handler in root_logger.handlers[:]:
        root_logger.removeHandler(handler)
    
    # Create handler with proper formatter based on format
    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(numeric_level)
    
    if format == "json":
        # JSON formatter for production
        handler.setFormatter(logging.Formatter(
            '{"time":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","message":"%(message)s"}'
        ))
    else:
        # Human-readable formatter for development
        handler.setFormatter(logging.Formatter(
            '%(asctime)s [%(levelname)-8s] [%(name)s] %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        ))
    
    root_logger.addHandler(handler)
    
    # IMPORTANT: Also set level on the main 'src' logger so all sub-loggers inherit it
    # This ensures all modules using logging.getLogger(__name__) respect LOG_LEVEL
    src_logger = logging.getLogger("src")
    src_logger.setLevel(numeric_level)
    
    # Propagate to all sub-loggers under 'src'
    # This is crucial for agent, bus, llm, etc. modules to output logs
    src_logger.propagate = True
    
    # Mark as configured
    _logging_configured = True


def reset_logging() -> None:
    """Reset logging configuration to defaults."""
    # Reset structlog
    structlog.reset_defaults()
    
    # Reset root logger
    root_logger = logging.getLogger()
    for handler in root_logger.handlers[:]:
        root_logger.removeHandler(handler)
    
    # Add basic handler
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter(
        "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    ))
    root_logger.addHandler(handler)


def get_logger(name: str, **kwargs: Any) -> structlog.stdlib.BoundLogger:
    """Get a structured logger instance.
    
    If logging hasn't been configured yet (configure_logging not called),
    this will auto-configure with default settings from environment variables
    or sensible defaults (INFO level, text format).
    
    Args:
        name: Logger name
        **kwargs: Additional fields to bind to all log messages
        
    Returns:
        Structured logger instance
    """
    global _logging_configured
    
    # Auto-configure if not already configured
    if not _logging_configured:
        # Read from environment variables or use defaults
        level = os.environ.get("LOG_LEVEL", "INFO")
        format_type = os.environ.get("LOG_FORMAT", "text")
        configure_logging(level=level, format=format_type)
    
    logger = structlog.get_logger(name)
    
    # Bind initial context if provided
    if kwargs:
        logger = logger.bind(**kwargs)
    
    return logger


class LogContext:
    """Context manager for adding temporary fields to log context.
    
    Usage:
        logger = get_logger("test")
        with LogContext(trace_id="123", agent_id="agent-1"):
            logger.info("This log will include trace_id and agent_id")
    """
    
    def __init__(self, **kwargs: Any) -> None:
        """Initialize with fields to bind."""
        self.fields = kwargs
        self._token = None
    
    def __enter__(self) -> "LogContext":
        """Bind fields to context."""
        # Bind to structlog context vars
        for key, value in self.fields.items():
            structlog.contextvars.clear_contextvars()
            structlog.contextvars.bind_contextvars(**self.fields)
        return self
    
    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        """Unbind fields from context."""
        structlog.contextvars.clear_contextvars()


@contextmanager
def log_context(trace_id: str | None = None, **kwargs: Any) -> Iterator[None]:
    """Context manager for log context with optional trace_id.
    
    Args:
        trace_id: Optional trace ID to set
        **kwargs: Additional context fields
        
    Yields:
        None
    """
    # Set trace ID if provided
    if trace_id:
        set_trace_id(trace_id)
    
    # Bind to structlog
    structlog.contextvars.bind_contextvars(**kwargs)
    
    try:
        yield
    finally:
        # Clean up
        structlog.contextvars.clear_contextvars()
        if trace_id:
            _trace_id_var.set(None)


class TracingLogger:
    """Logger wrapper that automatically adds trace context.
    
    This class provides a logger that automatically includes
    trace_id in all log messages when available.
    """
    
    def __init__(self, name: str) -> None:
        """Initialize with logger name."""
        self._logger = get_logger(name)
    
    def _add_trace_context(self, **kwargs: Any) -> dict[str, Any]:
        """Add trace context to kwargs."""
        trace_id = get_trace_id()
        if trace_id:
            kwargs["trace_id"] = trace_id
        return kwargs
    
    def debug(self, msg: str, **kwargs: Any) -> None:
        """Log debug message with trace context."""
        self._logger.debug(msg, **self._add_trace_context(**kwargs))
    
    def info(self, msg: str, **kwargs: Any) -> None:
        """Log info message with trace context."""
        self._logger.info(msg, **self._add_trace_context(**kwargs))
    
    def warning(self, msg: str, **kwargs: Any) -> None:
        """Log warning message with trace context."""
        self._logger.warning(msg, **self._add_trace_context(**kwargs))
    
    def error(self, msg: str, **kwargs: Any) -> None:
        """Log error message with trace context."""
        self._logger.error(msg, **self._add_trace_context(**kwargs))
    
    def exception(self, msg: str, **kwargs: Any) -> None:
        """Log exception with trace context."""
        self._logger.exception(msg, **self._add_trace_context(**kwargs))
