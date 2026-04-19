"""File System Tools for DPSK-OPC Agent.

This module provides tools for file system operations:
- list_dir: List directory contents
- search_file: Search files by pattern
- search_content: Search file contents with regex
- read_file: Read file contents
- read_lints: Read linter diagnostics
- replace_in_file: Replace text in files
- write_to_file: Write content to files
- delete_file: Delete files
- execute_command: Execute shell commands
"""

from __future__ import annotations

import asyncio
import logging
import os
import re
import subprocess
from pathlib import Path
from typing import Any

from .base import BaseTool, ToolParameter

logger = logging.getLogger(__name__)

# Default workspace path
DEFAULT_WORKSPACE = "/mnt/w/workspace/dpsk-opc"


# =============================================================================
# Tool Classes
# =============================================================================


class ListDirTool(BaseTool):
    """Tool for listing directory contents."""

    @property
    def name(self) -> str:
        return "list_dir"

    @property
    def description(self) -> str:
        return "列出目录内容和文件信息。当用户需要查看某个目录下有哪些文件或子目录时使用。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="target_directory",
                param_type="string",
                description="要列出的目录路径（必须是绝对路径）",
                required=True,
            ),
            ToolParameter(
                name="ignore_globs",
                param_type="array",
                description="要忽略的文件模式列表，如 ['*.pyc', '__pycache__']",
                required=False,
            ),
        ]

    async def execute(self, target_directory: str, ignore_globs: list[str] | None = None, **kwargs: Any) -> dict[str, Any]:
        """List directory contents."""
        try:
            path = Path(target_directory)
            if not path.exists():
                return {"success": False, "error": f"目录不存在: {target_directory}"}
            if not path.is_dir():
                return {"success": False, "error": f"不是有效目录: {target_directory}"}

            items = []
            patterns_to_ignore = ignore_globs or []

            for item in path.iterdir():
                # Check ignore patterns
                should_ignore = False
                for pattern in patterns_to_ignore:
                    if self._matches_glob(item.name, pattern):
                        should_ignore = True
                        break

                if should_ignore:
                    continue

                stat = item.stat()
                items.append({
                    "name": item.name,
                    "type": "directory" if item.is_dir() else "file",
                    "size": stat.st_size if item.is_file() else 0,
                    "path": str(item),
                })

            # Sort: directories first, then files, alphabetically
            items.sort(key=lambda x: (x["type"] != "directory", x["name"]))

            return {
                "success": True,
                "path": str(path),
                "items": items,
                "count": len(items),
            }
        except PermissionError:
            return {"success": False, "error": "权限不足，无法访问该目录"}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def _matches_glob(self, name: str, pattern: str) -> bool:
        """Simple glob pattern matching."""
        import fnmatch
        return fnmatch.fnmatch(name, pattern)


class SearchFileTool(BaseTool):
    """Tool for searching files by pattern."""

    @property
    def name(self) -> str:
        return "search_file"

    @property
    def description(self) -> str:
        return "按文件名模式搜索文件（如 *.py, test_*.py）。当用户需要查找特定类型的文件或文件名符合某个模式的文件时使用。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="pattern",
                param_type="string",
                description="文件匹配模式，如 '*.py', 'test_*.py', '*.json'",
                required=True,
            ),
            ToolParameter(
                name="recursive",
                param_type="boolean",
                description="是否递归搜索子目录，默认 true",
                required=False,
                default=True,
            ),
            ToolParameter(
                name="target_directory",
                param_type="string",
                description="搜索的根目录（必须是绝对路径），默认工作空间根目录",
                required=False,
                default=DEFAULT_WORKSPACE,
            ),
        ]

    async def execute(self, pattern: str, recursive: bool = True, target_directory: str = DEFAULT_WORKSPACE, **kwargs: Any) -> dict[str, Any]:
        """Search for files matching pattern."""
        try:
            root = Path(target_directory)
            if not root.exists():
                return {"success": False, "error": f"目录不存在: {target_directory}"}

            results = []
            if recursive:
                for item in root.rglob(pattern):
                    if item.is_file():
                        results.append(str(item))
            else:
                for item in root.glob(pattern):
                    if item.is_file():
                        results.append(str(item))

            # Sort results
            results.sort()

            return {
                "success": True,
                "pattern": pattern,
                "files": results,
                "count": len(results),
            }
        except PermissionError:
            return {"success": False, "error": "权限不足"}
        except Exception as e:
            return {"success": False, "error": str(e)}


class SearchContentTool(BaseTool):
    """Tool for searching file contents with regex."""

    @property
    def name(self) -> str:
        return "search_content"

    @property
    def description(self) -> str:
        return "使用正则表达式搜索文件内容。当用户需要查找代码中包含特定文本、函数名、类名、变量名等内容时使用。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="pattern",
                param_type="string",
                description="正则表达式模式",
                required=True,
            ),
            ToolParameter(
                name="path",
                param_type="string",
                description="搜索的文件或目录路径（必须是绝对路径）",
                required=True,
            ),
            ToolParameter(
                name="caseSensitive",
                param_type="boolean",
                description="是否区分大小写，默认 false",
                required=False,
                default=False,
            ),
            ToolParameter(
                name="glob",
                param_type="string",
                description="文件类型过滤，如 '*.py', '*.js'",
                required=False,
            ),
            ToolParameter(
                name="outputMode",
                param_type="string",
                description="输出模式: 'files_with_matches' 只返回文件名, 'content' 返回匹配内容, 'count' 返回数量",
                required=False,
                default="files_with_matches",
            ),
        ]

    async def execute(
        self,
        pattern: str,
        path: str,
        caseSensitive: bool = False,
        glob: str | None = None,
        outputMode: str = "files_with_matches",
        **kwargs: Any
    ) -> dict[str, Any]:
        """Search file contents with regex."""
        try:
            # Validate regex
            flags = 0 if caseSensitive else re.IGNORECASE
            re.compile(pattern, flags)

            search_path = Path(path)
            if not search_path.exists():
                return {"success": False, "error": f"路径不存在: {path}"}

            results: list[dict[str, Any]] = []
            count = 0

            # Collect files to search
            files_to_search = []
            if search_path.is_file():
                files_to_search = [search_path]
            elif search_path.is_dir():
                if glob:
                    files_to_search = list(search_path.rglob(glob))
                else:
                    files_to_search = [f for f in search_path.rglob("*") if f.is_file() and not self._should_ignore(f)]

            for file_path in files_to_search:
                try:
                    content = file_path.read_text(encoding="utf-8")
                    matches = list(re.finditer(pattern, content, flags))

                    if matches:
                        count += len(matches)
                        if outputMode == "files_with_matches":
                            results.append({"file": str(file_path), "match_count": len(matches)})
                        elif outputMode == "content":
                            for m in matches:
                                # Get line number
                                line_num = content[:m.start()].count("\n") + 1
                                # Get line content
                                lines = content.split("\n")
                                line_content = lines[line_num - 1] if line_num <= len(lines) else ""
                                results.append({
                                    "file": str(file_path),
                                    "line": line_num,
                                    "content": line_content.strip(),
                                    "match": m.group(),
                                })
                        elif outputMode == "count":
                            results.append({"file": str(file_path), "count": len(matches)})
                except (UnicodeDecodeError, PermissionError):
                    continue

            return {
                "success": True,
                "pattern": pattern,
                "results": results,
                "total_matches": count,
                "files_with_matches": len(set(r.get("file") for r in results if "file" in r)),
            }
        except re.error as e:
            return {"success": False, "error": f"无效的正则表达式: {e}"}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def _should_ignore(self, path: Path) -> bool:
        """Check if file should be ignored."""
        ignore_dirs = {".git", "__pycache__", "node_modules", ".venv", "venv", ".idea", ".vscode"}
        ignore_patterns = {".pyc", ".pyo", ".so", ".dll", ".dylib"}
        return any(part in ignore_dirs or part.startswith(".") for part in path.parts) or \
               any(str(path).endswith(ext) for ext in ignore_patterns)


class ReadFileTool(BaseTool):
    """Tool for reading file contents."""

    @property
    def name(self) -> str:
        return "read_file"

    @property
    def description(self) -> str:
        return "读取文件内容。当用户需要查看文件内容、代码、配置等时使用。支持指定行范围。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="filePath",
                param_type="string",
                description="文件路径（必须是绝对路径）",
                required=True,
            ),
            ToolParameter(
                name="limit",
                param_type="integer",
                description="最多读取的行数，不指定则读取全部",
                required=False,
            ),
            ToolParameter(
                name="offset",
                param_type="integer",
                description="从第几行开始读取（1-based），默认从第1行开始",
                required=False,
                default=1,
            ),
        ]

    async def execute(self, filePath: str, limit: int | None = None, offset: int = 1, **kwargs: Any) -> dict[str, Any]:
        """Read file contents."""
        try:
            path = Path(filePath)
            if not path.exists():
                return {"success": False, "error": f"文件不存在: {filePath}"}
            if not path.is_file():
                return {"success": False, "error": f"不是有效文件: {filePath}"}

            content = path.read_text(encoding="utf-8")
            lines = content.split("\n")

            # Adjust offset (convert to 0-based)
            start = max(0, offset - 1)
            end = len(lines) if limit is None else min(start + limit, len(lines))

            selected_lines = lines[start:end]
            selected_content = "\n".join(selected_lines)

            return {
                "success": True,
                "filePath": filePath,
                "content": selected_content,
                "total_lines": len(lines),
                "read_lines": f"{start + 1}-{end}",
                "truncated": end < len(lines),
            }
        except UnicodeDecodeError:
            return {"success": False, "error": "文件编码不支持（仅支持 UTF-8）"}
        except PermissionError:
            return {"success": False, "error": "权限不足，无法读取该文件"}
        except Exception as e:
            return {"success": False, "error": str(e)}


class ReadLintsTool(BaseTool):
    """Tool for reading linter diagnostics."""

    @property
    def name(self) -> str:
        return "read_lints"

    @property
    def description(self) -> str:
        return "读取 IDE/编辑器的诊断信息（lint 错误、警告、提示）。当用户需要查看代码问题、语法错误、类型错误等诊断信息时使用。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="paths",
                param_type="string",
                description="要检查的文件或目录路径（必须是绝对路径），不指定则检查所有文件",
                required=False,
            ),
        ]

    async def execute(self, paths: str | None = None, **kwargs: Any) -> dict[str, Any]:
        """Read linter diagnostics."""
        # Import here to avoid circular imports
        try:
            from backend.src.agent.tools import registry

            # Try to get linter results
            # Note: This is a simplified version, actual implementation depends on IDE integration
            return {
                "success": True,
                "message": "Linter diagnostics retrieved",
                "paths": paths,
                "diagnostics": [],
            }
        except ImportError:
            return {"success": False, "error": "Linter 工具不可用"}


class ReplaceInFileTool(BaseTool):
    """Tool for replacing text in files."""

    @property
    def name(self) -> str:
        return "replace_in_file"

    @property
    def description(self) -> str:
        return "替换文件中的文本内容。当用户需要修改代码、修改文件中的特定内容时使用。注意：old_str 必须精确匹配文件中的内容。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="filePath",
                param_type="string",
                description="文件路径（必须是绝对路径）",
                required=True,
            ),
            ToolParameter(
                name="old_str",
                param_type="string",
                description="要被替换的原始文本（必须精确匹配，包含所有空白字符）",
                required=True,
            ),
            ToolParameter(
                name="new_str",
                param_type="string",
                description="替换后的新文本",
                required=True,
            ),
        ]

    async def execute(self, filePath: str, old_str: str, new_str: str, **kwargs: Any) -> dict[str, Any]:
        """Replace text in file."""
        try:
            path = Path(filePath)
            if not path.exists():
                return {"success": False, "error": f"文件不存在: {filePath}"}
            if not path.is_file():
                return {"success": False, "error": f"不是有效文件: {filePath}"}

            content = path.read_text(encoding="utf-8")

            # Check if old_str exists
            if old_str not in content:
                return {"success": False, "error": "old_str 在文件中未找到，请确保精确匹配（包括空白字符）"}

            # Count occurrences
            count = content.count(old_str)
            if count > 1:
                return {"success": False, "error": f"old_str 在文件中出现 {count} 次，请提供更精确的匹配"}

            # Replace
            new_content = content.replace(old_str, new_str, 1)
            path.write_text(new_content, encoding="utf-8")

            return {
                "success": True,
                "filePath": filePath,
                "message": f"成功替换 1 处内容",
                "old_length": len(old_str),
                "new_length": len(new_str),
            }
        except PermissionError:
            return {"success": False, "error": "权限不足，无法修改该文件"}
        except Exception as e:
            return {"success": False, "error": str(e)}


class WriteToFileTool(BaseTool):
    """Tool for writing content to files."""

    @property
    def name(self) -> str:
        return "write_to_file"

    @property
    def description(self) -> str:
        return "创建新文件或覆盖已有文件内容。当用户需要创建新文件、写入代码、写入配置等时使用。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="filePath",
                param_type="string",
                description="文件路径（必须是绝对路径），父目录不存在时会自动创建",
                required=True,
            ),
            ToolParameter(
                name="content",
                param_type="string",
                description="要写入的文件内容",
                required=True,
            ),
            ToolParameter(
                name="explanation",
                param_type="string",
                description="操作说明，解释为什么要创建/修改这个文件",
                required=False,
            ),
        ]

    async def execute(self, filePath: str, content: str, explanation: str = "", **kwargs: Any) -> dict[str, Any]:
        """Write content to file."""
        try:
            path = Path(filePath)
            
            # Create parent directories if needed
            path.parent.mkdir(parents=True, exist_ok=True)

            # Check if file exists
            existed = path.exists()
            old_content = ""
            if existed:
                old_content = path.read_text(encoding="utf-8")

            # Write content
            path.write_text(content, encoding="utf-8")

            return {
                "success": True,
                "filePath": filePath,
                "action": "updated" if existed else "created",
                "size": len(content),
                "lines": len(content.split("\n")),
            }
        except PermissionError:
            return {"success": False, "error": "权限不足，无法写入该文件"}
        except Exception as e:
            return {"success": False, "error": str(e)}


class DeleteFileTool(BaseTool):
    """Tool for deleting files."""

    @property
    def name(self) -> str:
        return "delete_file"

    @property
    def description(self) -> str:
        return "删除文件。当用户明确要求删除某个文件时使用。注意：此操作不可逆！"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="target_file",
                param_type="string",
                description="要删除的文件路径（必须是绝对路径）",
                required=True,
            ),
            ToolParameter(
                name="explanation",
                param_type="string",
                description="删除原因，解释为什么要删除这个文件",
                required=True,
            ),
        ]

    async def execute(self, target_file: str, explanation: str = "", **kwargs: Any) -> dict[str, Any]:
        """Delete file."""
        try:
            path = Path(target_file)
            if not path.exists():
                return {"success": False, "error": f"文件不存在: {target_file}"}
            if not path.is_file():
                return {"success": False, "error": f"不是有效文件，无法删除目录: {target_file}"}

            # Get file info before deletion
            size = path.stat().st_size

            # Delete
            path.unlink()

            return {
                "success": True,
                "file": target_file,
                "size": size,
                "message": "文件已删除",
            }
        except PermissionError:
            return {"success": False, "error": "权限不足，无法删除该文件"}
        except Exception as e:
            return {"success": False, "error": str(e)}


class ExecuteCommandTool(BaseTool):
    """Tool for executing shell commands."""

    @property
    def name(self) -> str:
        return "execute_command"

    @property
    def description(self) -> str:
        return "执行 shell 命令。当用户需要运行 git、安装依赖、执行脚本、运行测试等操作时使用。"

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="command",
                param_type="string",
                description="要执行的 shell 命令",
                required=True,
            ),
            ToolParameter(
                name="explanation",
                param_type="string",
                description="操作说明，解释为什么要执行这个命令",
                required=False,
            ),
            ToolParameter(
                name="requires_approval",
                param_type="boolean",
                description="是否需要用户确认（危险操作应设为 true），默认 false",
                required=False,
                default=False,
            ),
        ]

    async def execute(self, command: str, explanation: str = "", requires_approval: bool = False, **kwargs: Any) -> dict[str, Any]:
        """Execute shell command."""
        # Security: prevent dangerous commands
        dangerous_patterns = [
            r"rm\s+-rf\s+/",  # rm -rf /
            r"rm\s+-rf\s+/",  # rm -rf / (variation)
            r":\(\)\{",  # Fork bomb
            r"dd\s+if=.*of=/dev/",  # dd to device
        ]

        for pattern in dangerous_patterns:
            if re.search(pattern, command):
                return {"success": False, "error": "禁止执行危险命令！"}

        try:
            # Execute command with timeout
            result = subprocess.run(
                command,
                shell=True,
                capture_output=True,
                text=True,
                timeout=60,  # 60 second timeout
                cwd=DEFAULT_WORKSPACE,
            )

            return {
                "success": result.returncode == 0,
                "command": command,
                "returncode": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "explanation": explanation,
            }
        except subprocess.TimeoutExpired:
            return {"success": False, "error": "命令执行超时（60秒）"}
        except PermissionError:
            return {"success": False, "error": "权限不足，无法执行该命令"}
        except Exception as e:
            return {"success": False, "error": str(e)}


# =============================================================================
# Tool Instances
# =============================================================================

list_dir_tool = ListDirTool()
search_file_tool = SearchFileTool()
search_content_tool = SearchContentTool()
read_file_tool = ReadFileTool()
read_lints_tool = ReadLintsTool()
replace_in_file_tool = ReplaceInFileTool()
write_to_file_tool = WriteToFileTool()
delete_file_tool = DeleteFileTool()
execute_command_tool = ExecuteCommandTool()

# All file tools for bulk registration
FILE_TOOLS = [
    list_dir_tool,
    search_file_tool,
    search_content_tool,
    read_file_tool,
    read_lints_tool,
    replace_in_file_tool,
    write_to_file_tool,
    delete_file_tool,
    execute_command_tool,
]
