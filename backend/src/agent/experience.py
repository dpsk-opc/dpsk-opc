"""Experience pool for Agent module.

This module provides experience storage and lookup using SQLite.
"""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Optional

logger = logging.getLogger(__name__)


class ExperienceDB:
    """SQLite-based experience pool for Agent task caching.

    Stores historical task inputs and outputs for exact-match caching.
    """

    def __init__(self, db_path: Path | str) -> None:
        """Initialize the experience database.

        Args:
            db_path: Path to SQLite database file
        """
        self.db_path = Path(db_path)
        self._conn: Optional[sqlite3.Connection] = None
        self._lock = asyncio.Lock()

    def _get_connection(self) -> sqlite3.Connection:
        """Get database connection, creating if needed."""
        if self._conn is None:
            self._db_path.parent.mkdir(parents=True, exist_ok=True)
            self._conn = sqlite3.connect(str(self._db_path), check_same_thread=False)
            self._conn.execute("PRAGMA journal_mode=WAL")
            self._create_tables()
        return self._conn

    def _create_tables(self) -> None:
        """Create database tables if they don't exist."""
        conn = self._conn
        if conn is None:
            return

        conn.execute("""
            CREATE TABLE IF NOT EXISTS experiences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                agent_id TEXT NOT NULL,
                task_name TEXT NOT NULL,
                input_hash TEXT NOT NULL,
                input_json TEXT,
                output_json TEXT NOT NULL,
                created_at REAL NOT NULL,
                hit_count INTEGER DEFAULT 1
            )
        """)

        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_agent_task_hash
            ON experiences(agent_id, task_name, input_hash)
        """)

        conn.commit()

    @staticmethod
    def compute_hash(data: dict[str, Any]) -> str:
        """Compute SHA256 hash of data.

        Args:
            data: Dictionary to hash

        Returns:
            Hex-encoded SHA256 hash
        """
        # Serialize and hash
        serialized = json.dumps(data, sort_keys=True, ensure_ascii=False)
        return hashlib.sha256(serialized.encode("utf-8")).hexdigest()

    async def store(
        self,
        agent_id: str,
        task_name: str,
        input_data: dict[str, Any],
        output_data: dict[str, Any],
    ) -> bool:
        """Store an experience entry.

        Args:
            agent_id: Agent identifier
            task_name: Task/skill name
            input_data: Task input data
            output_data: Task output data

        Returns:
            True if stored successfully
        """
        async with self._lock:
            try:
                conn = self._get_connection()
                input_hash = self.compute_hash(input_data)
                input_json = json.dumps(input_data, ensure_ascii=False)
                output_json = json.dumps(output_data, ensure_ascii=False)

                # Try to update existing entry
                cursor = conn.execute(
                    """
                    UPDATE experiences
                    SET output_json = ?, hit_count = hit_count + 1
                    WHERE agent_id = ? AND task_name = ? AND input_hash = ?
                    """,
                    (output_json, agent_id, task_name, input_hash),
                )

                if cursor.rowcount == 0:
                    # Insert new entry
                    import time

                    conn.execute(
                        """
                        INSERT INTO experiences
                        (agent_id, task_name, input_hash, input_json, output_json, created_at, hit_count)
                        VALUES (?, ?, ?, ?, ?, ?, 1)
                        """,
                        (
                            agent_id,
                            task_name,
                            input_hash,
                            input_json,
                            output_json,
                            time.time(),
                        ),
                    )

                conn.commit()
                return True

            except Exception as e:
                logger.error(f"Failed to store experience: {e}")
                return False

    async def lookup(
        self,
        agent_id: str,
        task_name: str,
        input_data: dict[str, Any],
    ) -> Optional[dict[str, Any]]:
        """Lookup an experience entry.

        Performs exact-match lookup using SHA256 hash.

        Args:
            agent_id: Agent identifier
            task_name: Task/skill name
            input_data: Task input data

        Returns:
            Cached output if found, None otherwise
        """
        async with self._lock:
            try:
                conn = self._get_connection()
                input_hash = self.compute_hash(input_data)

                cursor = conn.execute(
                    """
                    SELECT output_json FROM experiences
                    WHERE agent_id = ? AND task_name = ? AND input_hash = ?
                    """,
                    (agent_id, task_name, input_hash),
                )

                row = cursor.fetchone()
                if row:
                    # Update hit count
                    conn.execute(
                        """
                        UPDATE experiences SET hit_count = hit_count + 1
                        WHERE agent_id = ? AND task_name = ? AND input_hash = ?
                        """,
                        (agent_id, task_name, input_hash),
                    )
                    conn.commit()

                    return json.loads(row[0])

                return None

            except Exception as e:
                logger.error(f"Failed to lookup experience: {e}")
                return None

    async def get_stats(self, agent_id: str) -> dict[str, Any]:
        """Get statistics for an agent's experience pool.

        Args:
            agent_id: Agent identifier

        Returns:
            Statistics dictionary
        """
        async with self._lock:
            try:
                conn = self._get_connection()

                # Total entries
                cursor = conn.execute(
                    "SELECT COUNT(*) FROM experiences WHERE agent_id = ?",
                    (agent_id,),
                )
                total_entries = cursor.fetchone()[0]

                # Total hits
                cursor = conn.execute(
                    "SELECT SUM(hit_count) FROM experiences WHERE agent_id = ?",
                    (agent_id,),
                )
                total_hits = cursor.fetchone()[0] or 0

                # Tasks breakdown
                cursor = conn.execute(
                    """
                    SELECT task_name, COUNT(*), SUM(hit_count)
                    FROM experiences WHERE agent_id = ?
                    GROUP BY task_name
                    """,
                    (agent_id,),
                )
                tasks = [
                    {"task_name": row[0], "entries": row[1], "hits": row[2]}
                    for row in cursor.fetchall()
                ]

                return {
                    "agent_id": agent_id,
                    "total_entries": total_entries,
                    "total_hits": total_hits,
                    "tasks": tasks,
                }

            except Exception as e:
                logger.error(f"Failed to get stats: {e}")
                return {"agent_id": agent_id, "error": str(e)}

    async def cleanup(self, agent_id: str, max_entries: int) -> int:
        """Clean up old entries when max is exceeded.

        Deletes the lowest hit_count entries with oldest created_at.

        Args:
            agent_id: Agent identifier
            max_entries: Maximum entries to keep

        Returns:
            Number of entries deleted
        """
        async with self._lock:
            try:
                conn = self._get_connection()

                # Count current entries
                cursor = conn.execute(
                    "SELECT COUNT(*) FROM experiences WHERE agent_id = ?",
                    (agent_id,),
                )
                current_count = cursor.fetchone()[0]

                if current_count <= max_entries:
                    return 0

                # Calculate how many to delete (10% of entries)
                delete_count = max(int(current_count * 0.1), 1)

                # Delete lowest hit_count + oldest entries
                cursor = conn.execute(
                    """
                    DELETE FROM experiences
                    WHERE id IN (
                        SELECT id FROM experiences
                        WHERE agent_id = ?
                        ORDER BY hit_count ASC, created_at ASC
                        LIMIT ?
                    )
                    """,
                    (agent_id, delete_count),
                )

                conn.commit()
                deleted = cursor.rowcount
                logger.info(f"Cleaned up {deleted} experience entries for {agent_id}")
                return deleted

            except Exception as e:
                logger.error(f"Failed to cleanup experiences: {e}")
                return 0

    async def clear(self, agent_id: str) -> int:
        """Clear all experiences for an agent.

        Args:
            agent_id: Agent identifier

        Returns:
            Number of entries deleted
        """
        async with self._lock:
            try:
                conn = self._get_connection()
                cursor = conn.execute(
                    "DELETE FROM experiences WHERE agent_id = ?",
                    (agent_id,),
                )
                conn.commit()
                deleted = cursor.rowcount
                logger.info(f"Cleared {deleted} experiences for {agent_id}")
                return deleted

            except Exception as e:
                logger.error(f"Failed to clear experiences: {e}")
                return 0

    def close(self) -> None:
        """Close the database connection."""
        if self._conn:
            self._conn.close()
            self._conn = None

    def __enter__(self) -> ExperienceDB:
        """Context manager entry."""
        return self

    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        """Context manager exit."""
        self.close()
