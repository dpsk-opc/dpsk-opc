"""Integration test for fitness coach agent with workspace security.

This test verifies that the fitness coach agent can create workout plans
in its workspace, with proper workspace isolation and permission enforcement.
"""

import asyncio
import tempfile
import shutil
from pathlib import Path

import pytest

from src.agent.workspace.models import Permission
from src.agent.workspace.manager import WorkspaceManager
from src.agent.workspace.context import (
    set_current_context,
    get_current_context,
    clear_current_context,
    AgentContextManager,
)
from src.agent.tools.file_tools import (
    WriteToFileTool,
    ReadFileTool,
    ListDirTool,
)


class TestFitnessCoachWorkspace:
    """Test fitness coach agent workspace operations."""
    
    @pytest.fixture(autouse=True)
    def setup(self):
        """Set up test fixtures."""
        self.temp_dir = tempfile.mkdtemp()
        self.workspace_root = Path(self.temp_dir) / "ws"
        self.exchange_path = Path(self.temp_dir) / "exchange"
        self.shared_path = Path(self.temp_dir) / "shared"
        
        # Clear context before and after each test
        clear_current_context()
        yield
        clear_current_context()
        shutil.rmtree(self.temp_dir, ignore_errors=True)
    
    def test_fitness_coach_workspace_operations(self):
        """Test that fitness coach can create workout plan in workspace."""
        
        # 1. Create workspace manager and agent workspace
        manager = WorkspaceManager(
            workspace_root=self.workspace_root,
            exchange_path=self.exchange_path,
            shared_path=self.shared_path,
        )
        
        # 2. Create workspace for fitness coach agent
        workspace = manager.create_workspace(
            agent_id="健身教练",
            permissions=Permission.READ | Permission.WRITE | Permission.LIST,
        )
        
        print(f"\n✅ Created workspace: {workspace.workspace_id}")
        print(f"   Root path: {workspace.root_path}")
        print(f"   Permissions: {workspace.permissions}")
        
        # 3. Create context using context manager
        with AgentContextManager(
            agent_id="健身教练",
            workspace_id=workspace.workspace_id,
            workspace_root=workspace.root_path,
            permissions=workspace.permissions,
        ) as ctx:
            current = get_current_context()
            print(f"✅ Agent context set: {current.agent_id}")
            print(f"   Workspace: {current.workspace_id}")
            print(f"   Permissions: {current.permissions}")
            
            # 4. Create workout plan file using WriteToFileTool
            write_tool = WriteToFileTool()
            
            workout_plan = """# 🏋️ 健身计划

## 📅 减脂训练计划（每周4天）

### 周一：全身力量 + 有氧
- 深蹲 4×10 (60kg)
- 卧推 4×10 (50kg)
- 划船 4×10 (45kg)
- 慢跑 30分钟（燃脂心率）

### 周三：HIIT高强度间歇
- 波比跳 30秒×8组
- 高抬腿 30秒×8组
- 开合跳 30秒×8组
- 休息间隔15秒

### 周五：下肢力量 + 核心
- 硬拉 4×8 (70kg)
- 保加利亚剪蹲 3×10
- 平板支撑 3×30秒
- 卷腹 3×15

### 周六：有氧耐力日
- 游泳/动感单车 45分钟
- 保持燃脂心率区间

## 💡 注意事项
1. 每次训练前热身5-10分钟
2. 训练后拉伸放松
3. 记录每日体重和训练感受
"""
            
            workout_file = workspace.root_path / "workout_plan.md"
            result = asyncio.run(write_tool.execute(
                filePath=str(workout_file),
                content=workout_plan,
                explanation="创建健身计划文档",
            ))
            
            print(f"\n✅ Write file result: {result}")
            assert result.get("success") is True, f"Failed to write file: {result.get('error')}"
            assert result.get("action") == "created"
            
            # 5. Read back the file using ReadFileTool
            read_tool = ReadFileTool()
            read_result = asyncio.run(read_tool.execute(
                filePath=str(workout_file),
            ))
            
            print(f"✅ Read file result:")
            assert read_result.get("success") is True, f"Failed to read file: {read_result.get('error')}"
            content = read_result.get("content", "")
            print(f"   Content length: {len(content)} chars")
            assert "健身计划" in content
            
            # 6. List directory contents
            list_tool = ListDirTool()
            list_result = asyncio.run(list_tool.execute(
                target_directory=str(workspace.root_path),
            ))
            
            print(f"\n✅ Directory listing:")
            assert list_result.get("success") is True
            items = list_result.get("items", [])
            item_names = [item["name"] for item in items]
            print(f"   Files: {item_names}")
            assert "workout_plan.md" in item_names
        
        # Context should be cleared after exit
        assert get_current_context() is None
        print(f"\n✅ Context cleared after exit")
    
    def test_workspace_isolation(self):
        """Test that workspace isolation prevents writing outside workspace."""
        
        manager = WorkspaceManager(
            workspace_root=self.workspace_root,
            exchange_path=self.exchange_path,
            shared_path=self.shared_path,
        )
        
        workspace = manager.create_workspace(
            agent_id="健身教练",
            permissions=Permission.READ | Permission.WRITE | Permission.LIST,
        )
        
        with AgentContextManager(
            agent_id="健身教练",
            workspace_id=workspace.workspace_id,
            workspace_root=workspace.root_path,
            permissions=workspace.permissions,
        ):
            write_tool = WriteToFileTool()
            
            # Try to write outside workspace
            bad_result = asyncio.run(write_tool.execute(
                filePath="/etc/test.txt",
                content="should be blocked",
                explanation="尝试写入系统文件",
            ))
            
            print(f"\n🔒 Blocked write to /etc/test.txt: {bad_result.get('error')}")
            assert bad_result.get("success") is False
            assert "权限" in bad_result.get("error", "")
    
    def test_permission_enforcement(self):
        """Test that insufficient permissions are properly enforced."""
        
        manager = WorkspaceManager(
            workspace_root=self.workspace_root,
            exchange_path=self.exchange_path,
            shared_path=self.shared_path,
        )
        
        # Create workspace with only READ permission
        workspace = manager.create_workspace(
            agent_id="readonly-agent",
            permissions=Permission.READ,  # No WRITE permission
        )
        
        with AgentContextManager(
            agent_id="readonly-agent",
            workspace_id=workspace.workspace_id,
            workspace_root=workspace.root_path,
            permissions=workspace.permissions,
        ):
            write_tool = WriteToFileTool()
            result = asyncio.run(write_tool.execute(
                filePath=str(workspace.root_path / "test.txt"),
                content="should fail",
            ))
            
            print(f"\n🔒 Write blocked with READ-only permission: {result.get('error')}")
            assert result.get("success") is False
            assert "权限" in result.get("error", "")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
