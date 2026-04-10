"""Tests for Experience Pool (experience.py)."""

import tempfile
import time
from pathlib import Path

import pytest

from src.agent.experience import ExperienceDB


@pytest.fixture
def temp_db():
    """Create a temporary database for testing."""
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        db_path = Path(f.name)
    db = ExperienceDB(db_path)
    yield db
    db.close()
    # Cleanup
    try:
        db_path.unlink()
    except Exception:
        pass


class TestExperienceDB:
    """Tests for ExperienceDB class."""

    def test_create_db(self, temp_db):
        """Test database creation."""
        assert temp_db.db_path.exists()

    def test_store_and_lookup(self, temp_db):
        """Test storing and looking up an experience."""
        input_data = {"code": "print('hello')", "language": "python"}
        output_data = {"result": "hello"}

        # Store
        result = temp_db.store("agent1", "run_code", input_data, output_data)
        assert result is True

        # Lookup
        cached = temp_db.lookup("agent1", "run_code", input_data)
        assert cached is not None
        assert cached == output_data

    def test_lookup_miss(self, temp_db):
        """Test lookup miss."""
        cached = temp_db.lookup("agent1", "run_code", {"different": "data"})
        assert cached is None

    def test_lookup_different_agent(self, temp_db):
        """Test lookup for different agent."""
        input_data = {"key": "value"}
        output_data = {"result": "data"}

        temp_db.store("agent1", "task", input_data, output_data)
        cached = temp_db.lookup("agent2", "task", input_data)
        assert cached is None

    def test_hit_count_increments(self, temp_db):
        """Test that hit count increments on lookup."""
        input_data = {"x": 1}
        output_data = {"y": 2}

        temp_db.store("agent1", "task", input_data, output_data)

        # First lookup
        temp_db.lookup("agent1", "task", input_data)
        # Second lookup
        temp_db.lookup("agent1", "task", input_data)

        # Check stats
        stats = temp_db.get_stats("agent1")
        assert stats["total_hits"] == 2

    def test_get_stats(self, temp_db):
        """Test getting statistics."""
        temp_db.store("agent1", "task1", {"a": 1}, {"r": 1})
        temp_db.store("agent1", "task1", {"b": 2}, {"r": 2})
        temp_db.store("agent1", "task2", {"c": 3}, {"r": 3})

        stats = temp_db.get_stats("agent1")
        assert stats["agent_id"] == "agent1"
        assert stats["total_entries"] == 3
        assert len(stats["tasks"]) == 2

    def test_cleanup(self, temp_db):
        """Test cleanup of old entries."""
        # Store more than max
        for i in range(20):
            temp_db.store("agent1", "task", {"i": i}, {"result": i})
            time.sleep(0.01)  # Ensure different timestamps

        stats_before = temp_db.get_stats("agent1")
        assert stats_before["total_entries"] == 20

        # Cleanup keeping max 10
        deleted = temp_db.cleanup("agent1", 10)

        stats_after = temp_db.get_stats("agent1")
        assert stats_after["total_entries"] == 10
        assert deleted == 10

    def test_clear(self, temp_db):
        """Test clearing all entries for an agent."""
        temp_db.store("agent1", "task1", {}, {"r": 1})
        temp_db.store("agent1", "task2", {}, {"r": 2})
        temp_db.store("agent2", "task", {}, {"r": 3})

        deleted = temp_db.clear("agent1")

        assert deleted == 2
        stats = temp_db.get_stats("agent1")
        assert stats["total_entries"] == 0
        # agent2 should be unaffected
        stats2 = temp_db.get_stats("agent2")
        assert stats2["total_entries"] == 1

    def test_compute_hash(self):
        """Test hash computation is deterministic."""
        data1 = {"a": 1, "b": 2}
        data2 = {"b": 2, "a": 1}  # Same content, different order

        hash1 = ExperienceDB.compute_hash(data1)
        hash2 = ExperienceDB.compute_hash(data2)

        assert hash1 == hash2

    def test_compute_hash_different(self):
        """Test different data produces different hash."""
        data1 = {"a": 1}
        data2 = {"a": 2}

        hash1 = ExperienceDB.compute_hash(data1)
        hash2 = ExperienceDB.compute_hash(data2)

        assert hash1 != hash2

    def test_context_manager(self):
        """Test using database as context manager."""
        with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
            db_path = Path(f.name)

        with ExperienceDB(db_path) as db:
            db.store("agent1", "task", {"x": 1}, {"y": 2})
            cached = db.lookup("agent1", "task", {"x": 1})
            assert cached == {"y": 2}

        # File should still exist but connection closed
        assert db_path.exists()
        try:
            db_path.unlink()
        except Exception:
            pass

    def test_update_existing(self, temp_db):
        """Test updating existing experience."""
        input_data = {"code": "x = 1"}
        output1 = {"result": "1"}
        output2 = {"result": "updated"}

        # Store first
        temp_db.store("agent1", "task", input_data, output1)

        # Update with new output
        temp_db.store("agent1", "task", input_data, output2)

        # Should have only one entry
        stats = temp_db.get_stats("agent1")
        assert stats["total_entries"] == 1

        # Should return updated output
        cached = temp_db.lookup("agent1", "task", input_data)
        assert cached == output2
