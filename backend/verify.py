#!/usr/bin/env python3
"""Quick verification script for DPSK-OPC Backend.

This script verifies that the core modules can be imported and basic
functionality works correctly.
"""

import asyncio
import sys
import os

# Add src to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))


async def verify_message_models() -> bool:
    """Verify message models work correctly."""
    print("✓ Verifying message models...")
    
    from src.bus.models import (
        Message,
        MessageType,
        Target,
        TargetType,
        TaskRequest,
        TaskResponse,
        Event,
    )
    
    # Test Message creation
    msg = Message(
        msg_type=MessageType.TASK_REQUEST,
        source="agent-1",
        target=Target(type=TargetType.AGENT, value="agent-2"),
        payload={"task": "test"},
    )
    assert msg.id is not None
    assert msg.trace_id is not None
    
    # Test TaskRequest
    request = TaskRequest(
        source="caller",
        target=Target(type=TargetType.AGENT, value="callee"),
        task_name="test-task",
        task_data={"param": "value"},
    )
    assert request.task_name == "test-task"
    
    # Test serialization
    json_str = request.to_json()
    restored = Message.from_json(json_str)
    assert restored.id == request.id
    
    print("  ✓ Message models OK")
    return True


async def verify_config() -> bool:
    """Verify configuration works correctly."""
    print("✓ Verifying configuration...")
    
    from src.config import Config, BusConfig, MessageBusBackend
    
    # Test default config
    config = Config()
    assert config.bus.backend == MessageBusBackend.MEMORY
    
    # Test custom config
    custom = Config(
        bus=BusConfig(backend=MessageBusBackend.REDIS)
    )
    assert custom.bus.backend == MessageBusBackend.REDIS
    
    print("  ✓ Configuration OK")
    return True


async def verify_logging() -> bool:
    """Verify logging works correctly."""
    print("✓ Verifying logging...")
    
    from src.utils.logging import configure_logging, get_logger
    
    configure_logging(level="DEBUG", format="text")
    logger = get_logger("test")
    
    logger.info("Test log message")
    
    print("  ✓ Logging OK")
    return True


async def verify_message_bus() -> bool:
    """Verify message bus works correctly."""
    print("✓ Verifying message bus...")
    
    from src.bus.memory import InMemoryMessageBus
    from src.bus.models import Target, TargetType, TaskRequest, TaskResponse
    
    bus = InMemoryMessageBus()
    
    # Test health check
    status = await bus.health_check()
    assert status["status"] == "healthy"
    
    # Test subscription and notify
    received = []
    
    async def handler(msg) -> None:
        received.append(msg)
    
    target = Target(type=TargetType.AGENT, value="test-agent")
    await bus.subscribe(target, handler)
    
    from src.bus.models import Message, MessageType
    msg = Message(
        msg_type=MessageType.EVENT,
        source="test",
        target=target,
        payload={"data": "test"},
    )
    await bus.notify(target, msg)
    
    # Give async operations time to complete
    await asyncio.sleep(0.01)
    
    assert len(received) == 1
    assert received[0].payload["data"] == "test"
    
    print("  ✓ Message bus OK")
    return True


async def main() -> int:
    """Run all verification tests."""
    print("=" * 50)
    print("DPSK-OPC Backend Verification")
    print("=" * 50)
    
    checks = [
        verify_message_models,
        verify_config,
        verify_logging,
        verify_message_bus,
    ]
    
    passed = 0
    failed = 0
    
    for check in checks:
        try:
            if await check():
                passed += 1
        except Exception as e:
            print(f"  ✗ {check.__name__} failed: {e}")
            import traceback
            traceback.print_exc()
            failed += 1
    
    print("=" * 50)
    print(f"Results: {passed} passed, {failed} failed")
    print("=" * 50)
    
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    sys.exit(exit_code)
