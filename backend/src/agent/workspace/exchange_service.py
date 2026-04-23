"""ExchangeService for DPSK-OPC Agent.

This module provides cross-agent file exchange functionality:
- Push files to exchange area
- Pull files from exchange area
- Access control and authorization
- Automatic expiration and cleanup
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import shutil
import time
from pathlib import Path
from typing import Any, Optional

from .models import ExchangeMeta, ExchangeStatus
from .audit import AuditLogger, get_audit_logger

logger = logging.getLogger(__name__)


class ExchangeService:
    """Service for cross-agent file exchange."""
    
    def __init__(
        self,
        exchange_path: Optional[Path] = None,
        default_ttl_seconds: int = 86400,  # 24 hours
        max_file_size_mb: int = 100,
        audit_logger: Optional[AuditLogger] = None,
    ):
        """Initialize the exchange service.
        
        Args:
            exchange_path: Path for exchange directory
            default_ttl_seconds: Default time-to-live for files
            max_file_size_mb: Maximum file size in MB
            audit_logger: Audit logger instance
        """
        self.exchange_path = Path(exchange_path) if exchange_path else Path("/storage/exchange")
        self.default_ttl = default_ttl_seconds
        self.max_file_size = max_file_size_mb * 1024 * 1024
        self.audit_logger = audit_logger or get_audit_logger()
        
        # In-memory metadata store (in production, use database)
        self._exchange_meta: dict[str, ExchangeMeta] = {}
        
        # Ensure exchange directory exists
        self._ensure_directories()
        
        # Background cleanup task
        self._cleanup_task: Optional[asyncio.Task] = None
    
    def _ensure_directories(self) -> None:
        """Ensure exchange directories exist."""
        try:
            self.exchange_path.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            logger.error(f"[ExchangeService] Failed to create exchange directory: {e}")
    
    async def start_cleanup_task(self) -> None:
        """Start the background cleanup task."""
        if self._cleanup_task is None or self._cleanup_task.done():
            self._cleanup_task = asyncio.create_task(self._cleanup_loop())
            logger.info("[ExchangeService] Started cleanup task")
    
    async def stop_cleanup_task(self) -> None:
        """Stop the background cleanup task."""
        if self._cleanup_task:
            self._cleanup_task.cancel()
            try:
                await self._cleanup_task
            except asyncio.CancelledError:
                pass
            logger.info("[ExchangeService] Stopped cleanup task")
    
    async def _cleanup_loop(self) -> None:
        """Background cleanup loop."""
        while True:
            try:
                await asyncio.sleep(3600)  # Check every hour
                cleaned = await self.cleanup_expired()
                if cleaned > 0:
                    logger.info(f"[ExchangeService] Cleaned up {cleaned} expired exchanges")
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"[ExchangeService] Cleanup error: {e}")
    
    async def push_file(
        self,
        from_agent_id: str,
        filename: str,
        content: bytes,
        authorized_agents: list[str],
        expires_in_seconds: Optional[int] = None,
    ) -> dict[str, str]:
        """Push a file to the exchange area.
        
        Args:
            from_agent_id: Sender agent ID
            filename: Original filename
            content: File content
            authorized_agents: List of agent IDs authorized to read
            expires_in_seconds: Time to live (defaults to 24 hours)
            
        Returns:
            Dict with exchange_id and expires_at
        """
        # Validate
        if not authorized_agents:
            raise ValueError("At least one authorized agent is required")
        
        if from_agent_id not in authorized_agents:
            authorized_agents = [from_agent_id] + authorized_agents
        
        if len(content) > self.max_file_size:
            raise ValueError(f"File too large: {len(content)} bytes (max {self.max_file_size})")
        
        ttl = expires_in_seconds or self.default_ttl
        
        # Create metadata
        meta = ExchangeMeta.create(
            from_agent_id=from_agent_id,
            authorized_agents=authorized_agents,
            original_filename=filename,
            ttl_seconds=ttl,
        )
        
        # Store metadata
        self._exchange_meta[meta.exchange_id] = meta
        
        # Write file
        file_path = self.exchange_path / f"{meta.exchange_id}.file"
        meta_path = self.exchange_path / f"{meta.exchange_id}.meta"
        
        try:
            with open(file_path, "wb") as f:
                f.write(content)
            
            with open(meta_path, "w") as f:
                json.dump({
                    "exchange_id": meta.exchange_id,
                    "from_agent_id": meta.from_agent_id,
                    "authorized_agents": meta.authorized_agents,
                    "original_filename": meta.original_filename,
                    "created_at": meta.created_at,
                    "expires_at": meta.expires_at,
                    "status": meta.status.value,
                }, f)
        except Exception as e:
            if file_path.exists():
                file_path.unlink()
            if meta_path.exists():
                meta_path.unlink()
            del self._exchange_meta[meta.exchange_id]
            raise e
        
        self.audit_logger.log_exchange_operation(
            exchange_id=meta.exchange_id,
            from_agent_id=from_agent_id,
            to_agent_id=",".join(authorized_agents),
            operation="push",
            filename=filename,
            allowed=True,
            file_size=len(content),
        )
        
        logger.info(f"[ExchangeService] Pushed file {filename} from {from_agent_id}")
        
        return {"exchange_id": meta.exchange_id, "expires_at": str(meta.expires_at)}
    
    async def pull_file(
        self,
        to_agent_id: str,
        exchange_id: str,
    ) -> Optional[dict[str, Any]]:
        """Pull a file from the exchange area."""
        meta = self._exchange_meta.get(exchange_id)
        
        if not meta:
            meta_path = self.exchange_path / f"{exchange_id}.meta"
            if not meta_path.exists():
                raise ValueError(f"Exchange not found: {exchange_id}")
            with open(meta_path, "r") as f:
                data = json.load(f)
                meta = ExchangeMeta(
                    exchange_id=data["exchange_id"],
                    from_agent_id=data["from_agent_id"],
                    authorized_agents=data["authorized_agents"],
                    original_filename=data["original_filename"],
                    created_at=data["created_at"],
                    expires_at=data.get("expires_at"),
                    status=ExchangeStatus(data["status"]),
                )
                self._exchange_meta[exchange_id] = meta
        
        if not meta.can_be_read_by(to_agent_id):
            raise ValueError(f"Access denied to exchange: {exchange_id}")
        
        if not meta.is_active():
            raise ValueError(f"Exchange expired: {exchange_id}")
        
        file_path = self.exchange_path / f"{exchange_id}.file"
        if not file_path.exists():
            raise ValueError(f"Exchange file not found: {exchange_id}")
        
        with open(file_path, "rb") as f:
            content = f.read()
        
        meta.consume()
        
        self.audit_logger.log_exchange_operation(
            exchange_id=exchange_id,
            from_agent_id=meta.from_agent_id,
            to_agent_id=to_agent_id,
            operation="pull",
            filename=meta.original_filename,
            allowed=True,
            file_size=len(content),
        )
        
        return {"filename": meta.original_filename, "content": content, "exchange_id": exchange_id}
    
    async def cleanup_expired(self) -> int:
        """Clean up expired exchanges."""
        now = time.time()
        cleaned = 0
        expired_ids = [eid for eid, meta in self._exchange_meta.items() if meta.expires_at and now > meta.expires_at]
        
        for exchange_id in expired_ids:
            meta = self._exchange_meta[exchange_id]
            meta.expire()
            file_path = self.exchange_path / f"{exchange_id}.file"
            meta_path = self.exchange_path / f"{exchange_id}.meta"
            try:
                if file_path.exists(): file_path.unlink()
                if meta_path.exists(): meta_path.unlink()
            except Exception: pass
            del self._exchange_meta[exchange_id]
            cleaned += 1
        
        return cleaned


_exchange_service: Optional[ExchangeService] = None

def get_exchange_service() -> ExchangeService:
    """Get the global exchange service instance."""
    global _exchange_service
    if _exchange_service is None:
        import os
        exchange_path = os.environ.get("DPSK_EXCHANGE_PATH", "/storage/exchange")
        _exchange_service = ExchangeService(exchange_path=Path(exchange_path))
    return _exchange_service
