"""Example usage of the Agent module.

This script demonstrates how to use the Agent module.
"""

import asyncio
import tempfile
from pathlib import Path

from src.agent.defs import AgentDef
from src.agent.experience import ExperienceDB
from src.agent.manager import AgentManager
from src.agent.parser import parse_agent_def
from src.agent.registry import AgentRegistry
from src.agent.spawner import LocalAgentSpawner


async def main():
    """Run example usage."""
    print("=== DPSK-OPC Agent Module Example ===\n")

    # 1. Create a temporary agents directory
    with tempfile.TemporaryDirectory() as tmpdir:
        agents_dir = Path(tmpdir)

        # 2. Create sample agent definitions
        (agents_dir / "秘书.md").write_text("""---
name: 秘书 Agent
team: 总裁办
workspace: system
skills:
  - task_planning
  - agent_discovery
  - group_coordination
capabilities:
  - 解析用户自然语言需求
  - 拆解任务为子任务
  - 调用下游 Agent 执行
model: gpt-4
max_instances: 1
queue_size: 10
---
# 秘书 Agent
负责全局任务调度。
""", encoding="utf-8")

        (agents_dir / "前端研发.md").write_text("""---
name: 前端研发 Agent
team: engineering
workspace: dev
skills:
  - debug_js
  - code_review
  - ui_testing
capabilities:
  - 调试 JavaScript 代码
  - 代码审查
model: gpt-4
max_instances: 3
queue_size: 5
---
# 前端研发 Agent
负责前端开发任务。
""", encoding="utf-8")

        # 3. Initialize registry
        print("1. Initializing Registry...")
        registry = AgentRegistry()
        registry.load_from_dir(agents_dir)
        print(f"   Loaded {registry.count()} agents: {[a.agent_id for a in registry.list_all()]}")

        # 4. Initialize spawner
        print("\n2. Initializing Spawner...")
        spawner = LocalAgentSpawner()

        # 5. Initialize manager
        print("\n3. Initializing Manager...")
        manager = AgentManager(registry, spawner, agents_dir)
        await manager.initialize()
        print(f"   Manager initialized")

        # 6. List agent definitions
        print("\n4. Agent Definitions:")
        for def_dict in manager.list_agent_defs():
            print(f"   - {def_dict['agent_id']}: {def_dict['name']} (skills: {def_dict['skills']})")

        # 7. Spawn an instance
        print("\n5. Spawning Secretary Agent...")
        try:
            handle = await manager.spawn_agent("秘书")
            print(f"   Spawned: {handle.instance_id}")

            # List instances
            instances = await manager.list_instances()
            print(f"\n6. Active Instances: {len(instances)}")
            for inst in instances:
                print(f"   - {inst['instance_id']} ({inst['status']})")

            # Get queue status
            status = await manager.get_queue_status("秘书")
            print(f"\n7. Queue Status: {status}")

        except RuntimeError as e:
            print(f"   Spawn failed: {e}")

        # 8. Test experience pool
        print("\n8. Testing Experience Pool...")
        with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
            db_path = Path(f.name)

        exp_db = ExperienceDB(db_path)
        await exp_db.store("test-agent", "echo", {"input": "hello"}, {"output": "world"})
        cached = await exp_db.lookup("test-agent", "echo", {"input": "hello"})
        print(f"   Cached result: {cached}")
        exp_db.close()
        db_path.unlink()

        print("\n=== Example Complete ===")


if __name__ == "__main__":
    asyncio.run(main())
