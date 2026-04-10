"""Execution Strategies."""

from src.opc.strategies.base import ExecutionStrategy
from src.opc.strategies.parallel_strategy import ParallelDependencyStrategy

__all__ = [
    "ExecutionStrategy",
    "ParallelDependencyStrategy",
]
