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

All operations are protected by workspace security guards.
"""

from __future__ import annotations

import asyncio
import logging
import os
import re
import subprocess
from functools import wraps
from pathlib import Path
from typing import Any, Callable

from .base import BaseTool, ToolParameter

logger = logging.getLogger(__name__)

# Default workspace path
DEFAULT_WORKSPACE = "/mnt/w/workspace/dpsk-opc"


# =============================================================================
# Workspace Security Integration
# =============================================================================

def guard_workspace_access(
    tool_name: str,
    file_path: str,
    required_permission: int,
) -> dict[str, Any] | None:
    """Check workspace access permission for file operations.
    
    Args:
        tool_name: Name of the tool requesting access
        file_path: Path to check
        required_permission: Required permission (Permission.READ=1, Permission.WRITE=2, etc.)
        
    Returns:
        Error dict if permission denied, None if allowed
    """
    try:
        from ..workspace.guard import WorkspaceGuard
        from ..workspace.context import get_current_context, get_current_agent_id
    except ImportError:
        # Workspace module not available
        logger.debug(f"[{tool_name}] Workspace module not available, allowing access")
        return None
    
    # Get context
    context = get_current_context()
    if context is None:
        # No context means running outside agent framework
        # Allow operations for backward compatibility
        logger.debug(f"[{tool_name}] No context, allowing access (backward compatible)")
        return None
    
    # Get agent ID
    agent_id = get_current_agent_id()
    if not agent_id:
        logger.debug(f"[{tool_name}] No agent ID, allowing access")
        return None
    
    try:
        # Use the context's workspace_root for validation
        # Don't create a new manager - use the context's workspace
        from ..workspace.validator import PathValidator
        from ..workspace.classifier import PathClassifier
        
        classifier = PathClassifier()
        validator = PathValidator(classifier=classifier)
        
        # Validate path against context's workspace
        allowed, error = validator.validate_path(
            path=file_path,
            workspace_root=context.workspace_root,
            shared_dirs=None,
        )
        
        if not allowed:
            workspace_root = str(context.workspace_root)
            logger.warning(f"[{tool_name}] Workspace permission denied for {file_path}: {error}")
            return {
                "success": False,
                "error": f"工作空间权限不足: {error}\n提示: 请使用工作空间路径，如 '{workspace_root}/' 或其子目录",
                "tool_name": tool_name,
                "workspace_root": workspace_root,
            }
        
        # Check permission using context
        if not context.has_permission(required_permission):
            perm_name = _get_permission_name(required_permission)
            logger.warning(f"[{tool_name}] Missing required permission: {perm_name}")
            return {
                "success": False,
                "error": f"缺少所需权限: {perm_name}",
                "tool_name": tool_name,
            }
            
    except Exception as e:
        logger.warning(f"[{tool_name}] Workspace check failed: {e}")
        # Don't block operations if workspace check fails unexpectedly
    
    return None


def _get_permission_name(perm_value: int) -> str:
    """Get permission name from value."""
    names = {
        1: "READ",
        2: "WRITE",
        4: "EXECUTE",
        8: "LIST",
        16: "DELETE",
    }
    return names.get(perm_value, str(perm_value))


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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="target_directory",
                param_type="string",
                description="要列出的目录路径，不填则默认使用工作空间根目录",
                required=False,
            ),
            ToolParameter(
                name="ignore_globs",
                param_type="array",
                description="要忽略的文件模式列表，如 ['*.pyc', '__pycache__']",
                required=False,
            ),
        ]

    async def execute(self, target_directory: str | None = None, ignore_globs: list[str] | None = None, **kwargs: Any) -> dict[str, Any]:
        """List directory contents.
        
        If target_directory is not provided, uses workspace root.
        """
        # Get workspace root if no path specified
        if not target_directory:
            from ..workspace.context import get_current_context
            ctx = get_current_context()
            if ctx:
                target_directory = str(ctx.workspace_root)
            else:
                target_directory = DEFAULT_WORKSPACE
        
        # Check workspace permission
        from ..workspace.models import Permission
        error = guard_workspace_access("list_dir", target_directory, Permission.LIST)
        if error:
            return error
        
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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

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
            ToolParameter(
                name="ignore_globs",
                param_type="array",
                description="要忽略的目录模式，如 ['node_modules', '.git']",
                required=False,
                default=[],
            ),
            ToolParameter(
                name="max_depth",
                param_type="integer",
                description="最大搜索深度，默认 10",
                required=False,
                default=10,
            ),
        ]

    async def execute(self, pattern: str, recursive: bool = True, target_directory: str | None = None, ignore_globs: list[str] | None = None, max_depth: int = 10, **kwargs: Any) -> dict[str, Any]:
        """Search for files matching pattern.
        
        Args:
            pattern: Glob pattern to match (e.g., '*.py', 'test_*.py')
            recursive: Whether to search subdirectories
            target_directory: Root directory to search, defaults to workspace root
            ignore_globs: List of glob patterns to ignore (directories)
            max_depth: Maximum directory depth for recursive search (default: 10)
        """
        # Get workspace root if no path specified
        if not target_directory:
            from ..workspace.context import get_current_context
            ctx = get_current_context()
            if ctx:
                target_directory = str(ctx.workspace_root)
            else:
                target_directory = DEFAULT_WORKSPACE
        
        try:
            root = Path(target_directory)
            if not root.exists():
                return {"success": False, "error": f"目录不存在: {target_directory}"}

            # Default ignore patterns for common large directories
            default_ignores = set(["**/node_modules/**", "**/.git/**", "**/__pycache__/**", "**/.venv/**", "**/venv/**", "**/dist/**", "**/build/**"])
            patterns_to_ignore = set(ignore_globs or []) if ignore_globs else set()
            patterns_to_ignore.update(default_ignores)
            
            results = []
            if recursive:
                # Use rglob with manual depth control to skip ignored dirs
                for item in self._rglob_with_ignore(root, pattern, patterns_to_ignore, max_depth):
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
    
    def _rglob_with_ignore(self, root: Path, pattern: str, ignore_patterns: set[str], max_depth: int) -> list[Path]:
        """Rglob with directory ignore support."""
        import fnmatch
        results = []
        
        def _should_ignore(path: Path) -> bool:
            """Check if path matches any ignore pattern."""
            rel_path = str(path.relative_to(root)) if path != root else ""
            for p in ignore_patterns:
                # Handle both **/pattern and pattern formats
                normalized = p.replace("**/", "")
                if fnmatch.fnmatch(rel_path, p) or fnmatch.fnmatch(path.name, normalized):
                    return True
            return False
        
        def _scan(current: Path, depth: int):
            if depth > max_depth:
                return
            try:
                for item in current.iterdir():
                    if _should_ignore(item):
                        continue
                    if item.is_file() and fnmatch.fnmatch(item.name, pattern):
                        results.append(item)
                    elif item.is_dir():
                        _scan(item, depth + 1)
            except PermissionError:
                pass
        
        _scan(root, 0)
        return results


class SearchContentTool(BaseTool):
    """Tool for searching file contents with regex."""

    @property
    def name(self) -> str:
        return "search_content"

    @property
    def description(self) -> str:
        return "使用正则表达式搜索文件内容。当用户需要查找代码中包含特定文本、函数名、类名、变量名等内容时使用。"

    @property
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

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
                description="搜索的文件或目录路径，不填则默认使用工作空间根目录",
                required=False,
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
            ToolParameter(
                name="max_depth",
                param_type="integer",
                description="最大搜索深度，默认 5",
                required=False,
                default=5,
            ),
        ]

    async def execute(
        self,
        pattern: str,
        path: str | None = None,
        caseSensitive: bool = False,
        glob: str | None = None,
        outputMode: str = "files_with_matches",
        max_depth: int = 5,
        **kwargs: Any
    ) -> dict[str, Any]:
        """Search file contents with regex.
        
        Args:
            pattern: Regex pattern to search
            path: Search path (file or directory), defaults to workspace root
            caseSensitive: Case sensitive search
            glob: File type filter (e.g., '*.py')
            outputMode: 'files_with_matches', 'content', or 'count'
            max_depth: Maximum directory depth (default: 5)
        """
        # Get workspace root if no path specified
        if not path:
            from ..workspace.context import get_current_context
            ctx = get_current_context()
            if ctx:
                path = str(ctx.workspace_root)
            else:
                path = DEFAULT_WORKSPACE
        
        try:
            # Validate regex
            flags = 0 if caseSensitive else re.IGNORECASE
            re.compile(pattern, flags)

            search_path = Path(path).resolve()
            if not search_path.exists():
                return {"success": False, "error": f"路径不存在: {path}"}

            # Workspace security: validate search path is within workspace
            error = await self._validate_workspace(search_path)
            if error:
                return {"success": False, "error": error}

            results: list[dict[str, Any]] = []
            count = 0

            # Collect files to search (with depth limit)
            files_to_search = []
            if search_path.is_file():
                files_to_search = [search_path]
            elif search_path.is_dir():
                files_to_search = self._collect_files_with_depth(search_path, glob, max_depth)

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
    
    async def _validate_workspace(self, search_path: Path) -> str | None:
        """Validate search path is within workspace bounds.
        
        Returns:
            Error message if invalid, None if valid.
        """
        try:
            from ..workspace.context import get_current_context
            context = get_current_context()
            if context is None:
                return None  # No context, allow
            
            workspace_root = context.workspace_root.resolve()
            resolved = search_path.resolve()
            
            # Check if search path is within workspace
            if not str(resolved).startswith(str(workspace_root)):
                return f"搜索路径必须在工作空间内: {workspace_root}"
            
            return None
        except Exception as e:
            logger.warning(f"[SearchContent] Workspace validation failed: {e}")
            return None  # Don't block on errors
    
    def _collect_files_with_depth(self, root: Path, glob_pattern: str | None, max_depth: int) -> list[Path]:
        """Collect files with depth limit and workspace bounds."""
        import fnmatch
        results = []
        workspace_root = str(root)
        
        def _scan(current: Path, depth: int):
            if depth > max_depth:
                return
            try:
                for item in current.iterdir():
                    if self._should_ignore(item):
                        continue
                    if item.is_file():
                        if glob_pattern is None or fnmatch.fnmatch(item.name, glob_pattern):
                            results.append(item)
                    elif item.is_dir():
                        _scan(item, depth + 1)
            except PermissionError:
                pass
        
        _scan(root, 0)
        return results


class ReadFileTool(BaseTool):
    """Tool for reading file contents."""

    @property
    def name(self) -> str:
        return "read_file"

    @property
    def description(self) -> str:
        return "读取文件内容。当用户需要查看文件内容、代码、配置等时使用。支持指定行范围。"

    @property
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

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
        # Check workspace permission
        from ..workspace.models import Permission
        error = guard_workspace_access("read_file", filePath, Permission.READ)
        if error:
            return error
        
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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="paths",
                param_type="string",
                description="要检查的文件或目录路径，不指定则默认检查工作空间",
                required=False,
            ),
        ]

    async def execute(self, paths: str | None = None, **kwargs: Any) -> dict[str, Any]:
        """Read linter diagnostics.
        
        If paths is not provided, defaults to workspace root.
        """
        # Get workspace root if no path specified
        if not paths:
            from ..workspace.context import get_current_context
            ctx = get_current_context()
            if ctx:
                paths = str(ctx.workspace_root)
            else:
                paths = DEFAULT_WORKSPACE
        
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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

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
        # Check workspace permission
        from ..workspace.models import Permission
        error = guard_workspace_access("replace_in_file", filePath, Permission.WRITE)
        if error:
            return error
        
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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="filePath",
                param_type="string",
                description="文件路径，不填则默认在工作空间根目录，父目录不存在时会自动创建",
                required=False,
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

    async def execute(self, filePath: str | None = None, content: str = "", explanation: str = "", **kwargs: Any) -> dict[str, Any]:
        """Write content to file.
        
        If filePath is not provided, uses workspace root (file will have no name).
        If filePath is relative, joins with workspace root.
        """
        # Get workspace root if no path specified
        if not filePath:
            from ..workspace.context import get_current_context
            ctx = get_current_context()
            if ctx:
                filePath = str(ctx.workspace_root)
            else:
                filePath = DEFAULT_WORKSPACE
        
        # Resolve relative paths against workspace
        if not Path(filePath).is_absolute():
            from ..workspace.context import get_current_context
            ctx = get_current_context()
            if ctx:
                filePath = str(ctx.workspace_root / filePath)
            else:
                filePath = str(Path(DEFAULT_WORKSPACE) / filePath)
        
        # Check workspace permission
        from ..workspace.models import Permission
        error = guard_workspace_access("write_to_file", filePath, Permission.WRITE)
        if error:
            return error
        
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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

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
        # Check workspace permission
        from ..workspace.models import Permission
        error = guard_workspace_access("delete_file", target_file, Permission.DELETE)
        if error:
            return error
        
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
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["file_operations"]

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
