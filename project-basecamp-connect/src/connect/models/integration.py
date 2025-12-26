"""Integration log and service mapping models."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, Integer, String, Text

from src.connect.models.base import Base


class IntegrationLog(Base):
    """Log of integration events between services."""

    __tablename__ = "integration_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    source_service = Column(String(50), nullable=False, index=True)
    target_service = Column(String(50), nullable=False, index=True)
    event_type = Column(String(100), nullable=False, index=True)
    source_id = Column(String(255), nullable=True)
    target_id = Column(String(255), nullable=True)
    payload = Column(Text, nullable=True)
    status = Column(String(50), nullable=False, default="pending")
    error_message = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    def __repr__(self) -> str:
        return (
            f"<IntegrationLog(id={self.id}, "
            f"source={self.source_service}->{self.target_service}, "
            f"event={self.event_type}, status={self.status})>"
        )


class ServiceMapping(Base):
    """Mapping between service identifiers (e.g., Jira ticket <-> Slack thread)."""

    __tablename__ = "service_mappings"

    id = Column(Integer, primary_key=True, autoincrement=True)
    source_service = Column(String(50), nullable=False, index=True)
    source_id = Column(String(255), nullable=False, index=True)
    target_service = Column(String(50), nullable=False, index=True)
    target_id = Column(String(255), nullable=False, index=True)
    mapping_type = Column(String(100), nullable=False)
    extra_data = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    def __repr__(self) -> str:
        return (
            f"<ServiceMapping(id={self.id}, "
            f"{self.source_service}:{self.source_id} -> "
            f"{self.target_service}:{self.target_id})>"
        )
