"""Tracing Utilities for DPSK-OPC Backend

This module provides distributed tracing support using OpenTelemetry.
"""

import uuid
from contextvars import ContextVar
from typing import Any, Callable, Generator
from functools import wraps

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, ConsoleSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.trace import Span, Status, StatusCode

# Context variable for trace propagation
_current_trace_id: ContextVar[str | None] = ContextVar("current_trace_id", default=None)
_current_span_id: ContextVar[str | None] = ContextVar("current_span_id", default=None)


def init_tracing(service_name: str = "dpsk-opc-backend") -> None:
    """Initialize OpenTelemetry tracing.
    
    Args:
        service_name: Name of the service for tracing
    """
    # Create resource with service info
    resource = Resource.create({
        "service.name": service_name,
        "service.version": "1.0.0",
    })
    
    # Create tracer provider
    provider = TracerProvider(resource=resource)
    
    # Add console exporter for development
    # In production, replace with OTLP exporter
    processor = BatchSpanProcessor(ConsoleSpanExporter())
    provider.add_span_processor(processor)
    
    # Set as global tracer provider
    trace.set_tracer_provider(provider)


def get_tracer(name: str = "dpsk-opc") -> trace.Tracer:
    """Get a tracer instance.
    
    Args:
        name: Name for the tracer
        
    Returns:
        OpenTelemetry Tracer instance
    """
    return trace.get_tracer(name)


def get_current_trace_id() -> str | None:
    """Get the current trace ID from context."""
    return _current_trace_id.get()


def get_current_span_id() -> str | None:
    """Get the current span ID from context."""
    return _current_span_id.get()


def create_trace_context(trace_id: str | None = None) -> dict[str, str]:
    """Create a trace context dictionary for propagation.
    
    Args:
        trace_id: Optional trace ID to use
        
    Returns:
        Dictionary containing trace_id and span_id
    """
    if trace_id is None:
        trace_id = str(uuid.uuid4())
    
    span_id = str(uuid.uuid4())[:16]  # Span IDs are typically 16 hex chars
    
    return {
        "trace_id": trace_id,
        "span_id": span_id,
    }


def extract_trace_context(headers: dict[str, str]) -> dict[str, str] | None:
    """Extract trace context from HTTP headers.
    
    Standard W3C trace context headers are supported:
    - traceparent: <version>-<trace-id>-<span-id>-<trace-flags>
    
    Args:
        headers: HTTP headers dictionary
        
    Returns:
        Trace context dictionary or None if not found
    """
    traceparent = headers.get("traceparent")
    if not traceparent:
        return None
    
    try:
        # Parse W3C traceparent header
        # Format: version-trace_id-span_id-trace_flags
        parts = traceparent.split("-")
        if len(parts) >= 4:
            return {
                "trace_id": parts[1],
                "span_id": parts[2],
                "trace_flags": parts[3],
            }
    except (ValueError, IndexError):
        pass
    
    return None


def inject_trace_context() -> dict[str, str]:
    """Inject trace context into a dictionary for propagation.
    
    Returns:
        Dictionary with W3C traceparent header
    """
    trace_id = get_current_trace_id()
    span_id = get_current_span_id()
    
    if trace_id and span_id:
        return {
            "traceparent": f"00-{trace_id}-{span_id}-01",
        }
    elif trace_id:
        # Generate new span ID if we have trace ID but no span
        span_id = str(uuid.uuid4())[:16]
        return {
            "traceparent": f"00-{trace_id}-{span_id}-01",
        }
    
    return {}


class traced:
    """Decorator for tracing function execution.
    
    Usage:
        @traced
        async def my_function(arg1, arg2):
            pass
        
        @traced(name="custom_name", attributes={"key": "value"})
        def sync_function():
            pass
    """
    
    def __init__(
        self,
        name: str | None = None,
        attributes: dict[str, Any] | None = None,
    ) -> None:
        """Initialize decorator.
        
        Args:
            name: Custom span name (defaults to function name)
            attributes: Additional span attributes
        """
        self.name = name
        self.attributes = attributes or {}
        self._tracer = get_tracer()
    
    def __call__(self, func: Callable) -> Callable:
        """Apply decorator to function."""
        span_name = self.name or func.__name__
        
        @wraps(func)
        async def async_wrapper(*args: Any, **kwargs: Any) -> Any:
            with self._tracer.start_as_current_span(
                span_name,
                attributes=self.attributes,
            ) as span:
                try:
                    result = await func(*args, **kwargs)
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise
        
        @wraps(func)
        def sync_wrapper(*args: Any, **kwargs: Any) -> Any:
            with self._tracer.start_as_current_span(
                span_name,
                attributes=self.attributes,
            ) as span:
                try:
                    result = func(*args, **kwargs)
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise
        
        import asyncio
        if asyncio.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper


class SpanContext:
    """Context manager for creating custom spans.
    
    Usage:
        with SpanContext("my-operation", {"key": "value"}) as span:
            # Do work
            span.set_attribute("result", "success")
    """
    
    def __init__(
        self,
        name: str,
        attributes: dict[str, Any] | None = None,
    ) -> None:
        """Initialize span context.
        
        Args:
            name: Span name
            attributes: Initial span attributes
        """
        self.name = name
        self.attributes = attributes or {}
        self._tracer = get_tracer()
        self._span: Span | None = None
    
    def __enter__(self) -> "SpanContext":
        """Start the span."""
        self._span = self._tracer.start_span(self.name, attributes=self.attributes)
        return self
    
    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        """End the span."""
        if self._span:
            if exc_type:
                self._span.set_status(Status(StatusCode.ERROR, str(exc_val)))
                self._span.record_exception(exc_val)
            else:
                self._span.set_status(Status(StatusCode.OK))
            self._span.end()
    
    def set_attribute(self, key: str, value: Any) -> None:
        """Set a span attribute."""
        if self._span:
            self._span.set_attribute(key, value)
    
    def add_event(self, name: str, attributes: dict[str, Any] | None = None) -> None:
        """Add an event to the span."""
        if self._span:
            self._span.add_event(name, attributes=attributes)
    
    def set_status(self, status: Status) -> None:
        """Set span status."""
        if self._span:
            self._span.set_status(status)
