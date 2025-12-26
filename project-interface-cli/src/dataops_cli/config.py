"""Configuration management for the DataOps CLI."""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Final

from pydantic import BaseModel, Field


class APIConfig(BaseModel):
    """API configuration model."""

    base_url: str = Field(default="http://localhost:8080", description="Base URL of the DataOps server")
    timeout: int = Field(default=30, description="HTTP request timeout in seconds")
    retries: int = Field(default=3, description="Number of HTTP request retries")


class CLIConfig(BaseModel):
    """CLI configuration model."""

    log_level: str = Field(default="INFO", description="Logging level")
    config_file: str = Field(default="~/.dli/config.json", description="Configuration file path")
    debug: bool = Field(default=False, description="Enable debug mode")
    
    
class CliConfigManager:
    """Manages CLI configuration persistence."""
    
    def __init__(self, config_file: str = "~/.dli/config.json") -> None:
        """Initialize the configuration manager.
        
        Args:
            config_file: Path to the configuration file
        """
        self.config_path = Path(config_file).expanduser()
        self._ensure_config_dir()
    
    def _ensure_config_dir(self) -> None:
        """Ensure configuration directory exists."""
        self.config_path.parent.mkdir(parents=True, exist_ok=True)
    
    def load_config(self) -> dict[str, Any]:
        """Load configuration from file.
        
        Returns:
            Dictionary containing configuration data
        """
        if not self.config_path.exists():
            return {}
        
        try:
            with open(self.config_path, encoding="utf-8") as f:
                return json.load(f)
        except (OSError, json.JSONDecodeError):
            return {}
    
    def save_config(self, config: dict[str, Any]) -> None:
        """Save configuration to file.
        
        Args:
            config: Configuration dictionary to save
            
        Raises:
            OSError: If file cannot be written
        """
        self._ensure_config_dir()
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)
    
    def get_api_config(self) -> APIConfig:
        """Get API configuration from file or defaults.
        
        Returns:
            APIConfig instance
        """
        config_data = self.load_config()
        return APIConfig(**config_data)
    
    def update_api_config(self, **kwargs: Any) -> None:
        """Update API configuration.
        
        Args:
            **kwargs: Configuration parameters to update
        """
        current_config = self.load_config()
        current_config.update(kwargs)
        self.save_config(current_config)


# Global configuration instances
DEFAULT_CONFIG_FILE: Final[str] = "~/.dli/config.json"
_config_manager: CliConfigManager | None = None


def get_config_manager(config_file: str | None = None) -> CliConfigManager:
    """Get or create the configuration manager singleton.
    
    Args:
        config_file: Optional configuration file path
        
    Returns:
        CliConfigManager instance
    """
    global _config_manager
    if _config_manager is None:
        _config_manager = CliConfigManager(config_file or DEFAULT_CONFIG_FILE)
    return _config_manager


def get_cli_config() -> CLIConfig:
    """Get CLI configuration from environment variables.
    
    Returns:
        CLIConfig instance
    """
    return CLIConfig(
        log_level=os.getenv("DLI_LOG_LEVEL", "INFO").upper(),
        config_file=os.getenv("DLI_CONFIG_FILE", DEFAULT_CONFIG_FILE),
        debug=os.getenv("DLI_DEBUG", "false").lower() == "true",
    )