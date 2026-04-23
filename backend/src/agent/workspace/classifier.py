"""PathClassifier for DPSK-OPC Agent.

This module provides path classification functionality:
- Classify paths into different trust levels
- Determine access policies based on classification
- Support custom classification rules
"""

from __future__ import annotations

import fnmatch
import logging
import os
import re
from pathlib import Path
from typing import Optional

from .models import PathCategory, PathClassification, Policy, DefaultPathRules

logger = logging.getLogger(__name__)


class PathClassifier:
    """Path classifier for determining access policies."""
    
    def __init__(
        self,
        rules: Optional[list[PathClassification]] = None,
        home_dir: Optional[str] = None,
    ):
        """Initialize the classifier.
        
        Args:
            rules: Classification rules (uses DefaultPathRules.RULES if not provided)
            home_dir: Home directory path for expanding ~
        """
        self.rules = rules or DefaultPathRules.RULES
        self._home_dir = home_dir or os.path.expanduser("~")
    
    def classify(self, path: str) -> tuple[PathCategory, Policy]:
        """Classify a path and return its category and default policy.
        
        Args:
            path: The path to classify
            
        Returns:
            Tuple of (category, policy)
        """
        # Expand ~ to home directory
        expanded_path = self._expand_path(path)
        
        # Try to match rules in order
        for rule in self.rules:
            if self.matches_pattern(expanded_path, rule.pattern):
                return rule.category, rule.default_policy
        
        # Default: treat as forbidden if no rule matches
        return PathCategory.FORBIDDEN, Policy.DENY
    
    def is_allowed(self, path: str) -> tuple[bool, Policy, PathCategory, str]:
        """Check if a path is allowed.
        
        Args:
            path: The path to check
            
        Returns:
            Tuple of (allowed, policy, category, reason)
        """
        category, policy = self.classify(path)
        
        if policy == Policy.ALLOW:
            return True, policy, category, f"Path is in {category.value} category"
        elif policy == Policy.ALLOW_WITH_CONFIRM:
            return False, policy, category, f"Path requires user confirmation"
        else:
            return False, policy, category, f"Path is in forbidden {category.value} category"
    
    def is_forbidden(self, path: str) -> bool:
        """Check if a path is forbidden.
        
        Args:
            path: The path to check
            
        Returns:
            True if path is forbidden
        """
        category, policy = self.classify(path)
        return policy == Policy.DENY
    
    def requires_confirmation(self, path: str) -> bool:
        """Check if a path requires user confirmation.
        
        Args:
            path: The path to check
            
        Returns:
            True if path requires confirmation
        """
        category, policy = self.classify(path)
        return policy == Policy.ALLOW_WITH_CONFIRM
    
    def matches_pattern(self, path: str, pattern: str) -> bool:
        """Check if a path matches a glob pattern.
        
        Args:
            path: The path to check
            pattern: The glob pattern
            
        Returns:
            True if the path matches the pattern
        """
        # Normalize the pattern for matching
        # Handle trailing * differently
        if pattern.endswith("/*"):
            # Pattern like /etc/* - check if path starts with /etc/
            prefix = pattern[:-2]
            return path == prefix or path.startswith(prefix + "/")
        elif pattern == "/":
            # Special case for root
            return path == "/" or path.startswith("/")
        elif "*" in pattern:
            # Use fnmatch for patterns with wildcards
            return fnmatch.fnmatch(path, pattern)
        else:
            # Exact match or prefix match
            return path == pattern or path.startswith(pattern + "/")
    
    def _expand_path(self, path: str) -> str:
        """Expand ~ in path to home directory.
        
        Args:
            path: The path to expand
            
        Returns:
            Expanded path
        """
        if path.startswith("~"):
            return path.replace("~", self._home_dir, 1)
        return path
    
    def get_risk_level(self, path: str) -> str:
        """Get risk level for a path.
        
        Args:
            path: The path to check
            
        Returns:
            Risk level: "low", "medium", "high", "critical"
        """
        category, _ = self.classify(path)
        
        risk_mapping = {
            PathCategory.WORKSPACE: "low",
            PathCategory.SHARED: "low",
            PathCategory.USER_HOME: "medium",
            PathCategory.PROJECT: "medium",
            PathCategory.SYSTEM: "high",
            PathCategory.SENSITIVE: "high",
            PathCategory.FORBIDDEN: "critical",
        }
        
        return risk_mapping.get(category, "high")
    
    def add_rule(self, rule: PathClassification) -> None:
        """Add a classification rule.
        
        Args:
            rule: The rule to add
        """
        self.rules.append(rule)
        logger.debug(f"[PathClassifier] Added rule: {rule.pattern} -> {rule.category}")
    
    def remove_rule(self, pattern: str) -> bool:
        """Remove a classification rule.
        
        Args:
            pattern: The pattern of the rule to remove
            
        Returns:
            True if rule was removed
        """
        for i, rule in enumerate(self.rules):
            if rule.pattern == pattern:
                del self.rules[i]
                logger.debug(f"[PathClassifier] Removed rule: {pattern}")
                return True
        return False


# Global classifier instance
_classifier: Optional[PathClassifier] = None


def get_path_classifier() -> PathClassifier:
    """Get the global path classifier instance.
    
    Returns:
        PathClassifier instance
    """
    global _classifier
    if _classifier is None:
        _classifier = PathClassifier()
    return _classifier


def classify_path(path: str) -> tuple[PathCategory, Policy]:
    """Classify a path using the global classifier.
    
    Args:
        path: The path to classify
        
    Returns:
        Tuple of (category, policy)
    """
    return get_path_classifier().classify(path)


def is_path_allowed(path: str) -> tuple[bool, Policy, PathCategory]:
    """Check if a path is allowed.
    
    Args:
        path: The path to check
        
    Returns:
        Tuple of (allowed, policy, category)
    """
    classifier = get_path_classifier()
    allowed, policy, category, _ = classifier.is_allowed(path)
    return allowed, policy, category
