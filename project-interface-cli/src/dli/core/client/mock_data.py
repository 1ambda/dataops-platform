"""Mock data factory for Basecamp Server client.

This module contains mock data generation for testing purposes.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

from dli.core.client.enums import RunStatus, WorkflowSource


class MockDataFactory:
    """Factory class for generating mock data for testing."""

    @staticmethod
    def create_all_mock_data() -> dict[str, list[dict[str, Any]]]:
        """Create all mock data for testing.

        Returns:
            Dictionary containing all mock data categories
        """
        now = datetime.now()
        return {
            "catalog_tables": MockDataFactory.create_catalog_tables(now),
            "queries": MockDataFactory.create_queries(now),
            "metrics": MockDataFactory.create_metrics(),
            "datasets": MockDataFactory.create_datasets(),
            "workflows": MockDataFactory.create_workflows(now),
            "workflow_runs": MockDataFactory.create_workflow_runs(now),
        }

    @staticmethod
    def create_metrics() -> list[dict[str, Any]]:
        """Create mock metric data."""
        return [
            {
                "name": "iceberg.reporting.user_summary",
                "type": "Metric",
                "owner": "analyst@example.com",
                "team": "@analytics",
                "description": "User summary metrics",
                "tags": ["reporting", "daily"],
            },
            {
                "name": "iceberg.analytics.revenue_daily",
                "type": "Metric",
                "owner": "data@example.com",
                "team": "@data-eng",
                "description": "Daily revenue metrics",
                "tags": ["revenue", "kpi"],
            },
        ]

    @staticmethod
    def create_datasets() -> list[dict[str, Any]]:
        """Create mock dataset data."""
        return [
            {
                "name": "iceberg.analytics.daily_clicks",
                "type": "Dataset",
                "owner": "engineer@example.com",
                "team": "@data-eng",
                "description": "Daily click aggregations",
                "tags": ["feed", "daily"],
            },
            {
                "name": "iceberg.warehouse.user_events",
                "type": "Dataset",
                "owner": "warehouse@example.com",
                "team": "@warehouse",
                "description": "User events fact table",
                "tags": ["events", "fact"],
            },
        ]

    @staticmethod
    def create_workflows(now: datetime) -> list[dict[str, Any]]:
        """Create mock workflow data."""
        return [
            {
                "dataset_name": "iceberg.analytics.daily_clicks",
                "source": WorkflowSource.CODE.value,
                "schedule": "0 6 * * *",
                "enabled": True,
                "paused": False,
                "owner": "engineer@example.com",
                "team": "@data-eng",
                "last_run_at": (now - timedelta(hours=6)).isoformat(),
                "last_run_status": RunStatus.COMPLETED.value,
                "next_run_at": (now + timedelta(hours=18)).isoformat(),
            },
            {
                "dataset_name": "iceberg.warehouse.user_events",
                "source": WorkflowSource.CODE.value,
                "schedule": "0 */4 * * *",
                "enabled": True,
                "paused": False,
                "owner": "warehouse@example.com",
                "team": "@warehouse",
                "last_run_at": (now - timedelta(hours=2)).isoformat(),
                "last_run_status": RunStatus.COMPLETED.value,
                "next_run_at": (now + timedelta(hours=2)).isoformat(),
            },
            {
                "dataset_name": "iceberg.reporting.weekly_summary",
                "source": WorkflowSource.MANUAL.value,
                "schedule": None,
                "enabled": True,
                "paused": True,
                "owner": "analyst@example.com",
                "team": "@analytics",
                "last_run_at": (now - timedelta(days=7)).isoformat(),
                "last_run_status": RunStatus.FAILED.value,
                "next_run_at": None,
            },
        ]

    @staticmethod
    def create_workflow_runs(now: datetime) -> list[dict[str, Any]]:
        """Create mock workflow run data."""
        return [
            {
                "run_id": "iceberg.analytics.daily_clicks_20240115_060000",
                "dataset_name": "iceberg.analytics.daily_clicks",
                "source": WorkflowSource.CODE.value,
                "status": RunStatus.COMPLETED.value,
                "started_at": (now - timedelta(hours=6)).isoformat(),
                "ended_at": (now - timedelta(hours=6) + timedelta(minutes=15)).isoformat(),
                "duration_seconds": 900,
                "triggered_by": "scheduler",
            },
            {
                "run_id": "iceberg.warehouse.user_events_20240115_040000",
                "dataset_name": "iceberg.warehouse.user_events",
                "source": WorkflowSource.CODE.value,
                "status": RunStatus.COMPLETED.value,
                "started_at": (now - timedelta(hours=4)).isoformat(),
                "ended_at": (now - timedelta(hours=4) + timedelta(minutes=30)).isoformat(),
                "duration_seconds": 1800,
                "triggered_by": "scheduler",
            },
            {
                "run_id": "iceberg.reporting.weekly_summary_20240108_100000",
                "dataset_name": "iceberg.reporting.weekly_summary",
                "source": WorkflowSource.MANUAL.value,
                "status": RunStatus.FAILED.value,
                "started_at": (now - timedelta(days=7)).isoformat(),
                "ended_at": (now - timedelta(days=7) + timedelta(minutes=5)).isoformat(),
                "duration_seconds": 300,
                "triggered_by": "user@example.com",
                "error_message": "Query timeout: exceeded 30 minute limit",
            },
            {
                "run_id": "iceberg.analytics.daily_clicks_20240115_120000",
                "dataset_name": "iceberg.analytics.daily_clicks",
                "source": WorkflowSource.MANUAL.value,
                "status": RunStatus.RUNNING.value,
                "started_at": (now - timedelta(minutes=10)).isoformat(),
                "ended_at": None,
                "duration_seconds": None,
                "triggered_by": "user@example.com",
            },
        ]

    @staticmethod
    def create_catalog_tables(now: datetime) -> list[dict[str, Any]]:
        """Create mock catalog table data."""
        return [
            {
                "name": "my-project.analytics.users",
                "engine": "bigquery",
                "owner": "data-team@example.com",
                "team": "@data-eng",
                "description": "User dimension table with profile information",
                "tags": ["tier::critical", "domain::analytics", "pii"],
                "row_count": 1500000,
                "last_updated": (now - timedelta(hours=2)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/my-project.analytics.users",
                "columns": [
                    {"name": "user_id", "data_type": "STRING", "description": "Unique user identifier", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1500000},
                    {"name": "email", "data_type": "STRING", "description": "User email address", "is_pii": True, "fill_rate": 0.98, "distinct_count": 1470000},
                    {"name": "name", "data_type": "STRING", "description": "Full name", "is_pii": True, "fill_rate": 0.95, "distinct_count": 1200000},
                    {"name": "created_at", "data_type": "TIMESTAMP", "description": "Account creation time", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1000000},
                    {"name": "country", "data_type": "STRING", "description": "User country code", "is_pii": False, "fill_rate": 0.92, "distinct_count": 195},
                ],
                "ownership": {
                    "owner": "data-team@example.com",
                    "team": "@data-eng",
                    "stewards": ["alice@example.com", "bob@example.com"],
                    "consumers": ["@analytics", "@marketing", "@product"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(hours=2)).isoformat(),
                    "avg_update_lag_hours": 1.5,
                    "update_frequency": "hourly",
                    "is_stale": False,
                    "stale_threshold_hours": 6,
                },
                "quality": {
                    "score": 92,
                    "total_tests": 15,
                    "passed_tests": 14,
                    "failed_tests": 1,
                    "warnings": 0,
                    "recent_tests": [
                        {"test_name": "user_id_not_null", "test_type": "not_null", "status": "pass", "failed_rows": 0},
                        {"test_name": "email_unique", "test_type": "unique", "status": "pass", "failed_rows": 0},
                        {"test_name": "country_in_set", "test_type": "accepted_values", "status": "fail", "failed_rows": 42},
                    ],
                },
                "sample_queries": [
                    {"title": "Active users by country", "sql": "SELECT country, COUNT(*) FROM `my-project.analytics.users` WHERE last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1", "author": "analyst@example.com", "run_count": 156, "last_run": (now - timedelta(hours=1)).isoformat()},
                    {"title": "User growth trend", "sql": "SELECT DATE(created_at) as date, COUNT(*) as new_users FROM `my-project.analytics.users` GROUP BY 1 ORDER BY 1", "author": "growth@example.com", "run_count": 89, "last_run": (now - timedelta(hours=3)).isoformat()},
                ],
            },
            {
                "name": "my-project.analytics.orders",
                "engine": "bigquery",
                "owner": "commerce@example.com",
                "team": "@commerce",
                "description": "Order transaction fact table",
                "tags": ["tier::critical", "domain::commerce", "fact"],
                "row_count": 25000000,
                "last_updated": (now - timedelta(hours=1)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/my-project.analytics.orders",
                "columns": [
                    {"name": "order_id", "data_type": "STRING", "description": "Unique order identifier", "is_pii": False, "fill_rate": 1.0, "distinct_count": 25000000},
                    {"name": "user_id", "data_type": "STRING", "description": "Customer user ID", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1200000},
                    {"name": "total_amount", "data_type": "FLOAT64", "description": "Order total in USD", "is_pii": False, "fill_rate": 1.0, "distinct_count": 50000},
                    {"name": "status", "data_type": "STRING", "description": "Order status", "is_pii": False, "fill_rate": 1.0, "distinct_count": 5},
                    {"name": "created_at", "data_type": "TIMESTAMP", "description": "Order creation time", "is_pii": False, "fill_rate": 1.0, "distinct_count": 20000000},
                ],
                "ownership": {
                    "owner": "commerce@example.com",
                    "team": "@commerce",
                    "stewards": ["commerce-data@example.com"],
                    "consumers": ["@finance", "@analytics", "@fulfillment"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(hours=1)).isoformat(),
                    "avg_update_lag_hours": 0.5,
                    "update_frequency": "hourly",
                    "is_stale": False,
                    "stale_threshold_hours": 4,
                },
                "quality": {
                    "score": 98,
                    "total_tests": 20,
                    "passed_tests": 20,
                    "failed_tests": 0,
                    "warnings": 0,
                    "recent_tests": [
                        {"test_name": "order_id_unique", "test_type": "unique", "status": "pass", "failed_rows": 0},
                        {"test_name": "total_amount_positive", "test_type": "expression", "status": "pass", "failed_rows": 0},
                    ],
                },
                "sample_queries": [],
            },
            {
                "name": "my-project.warehouse.events",
                "engine": "bigquery",
                "owner": "platform@example.com",
                "team": "@data-platform",
                "description": "Raw event stream from mobile and web",
                "tags": ["tier::standard", "domain::platform", "raw"],
                "row_count": 500000000,
                "last_updated": (now - timedelta(minutes=15)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/my-project.warehouse.events",
                "columns": [
                    {"name": "event_id", "data_type": "STRING", "description": "Unique event identifier", "is_pii": False, "fill_rate": 1.0, "distinct_count": 500000000},
                    {"name": "event_type", "data_type": "STRING", "description": "Event type name", "is_pii": False, "fill_rate": 1.0, "distinct_count": 150},
                    {"name": "user_id", "data_type": "STRING", "description": "User identifier", "is_pii": False, "fill_rate": 0.85, "distinct_count": 2000000},
                    {"name": "properties", "data_type": "JSON", "description": "Event properties", "is_pii": True, "fill_rate": 0.95, "distinct_count": None},
                    {"name": "timestamp", "data_type": "TIMESTAMP", "description": "Event timestamp", "is_pii": False, "fill_rate": 1.0, "distinct_count": 400000000},
                ],
                "ownership": {
                    "owner": "platform@example.com",
                    "team": "@data-platform",
                    "stewards": [],
                    "consumers": ["@analytics", "@data-science", "@product"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(minutes=15)).isoformat(),
                    "avg_update_lag_hours": 0.1,
                    "update_frequency": "streaming",
                    "is_stale": False,
                    "stale_threshold_hours": 1,
                },
                "quality": {
                    "score": 85,
                    "total_tests": 10,
                    "passed_tests": 8,
                    "failed_tests": 1,
                    "warnings": 1,
                    "recent_tests": [
                        {"test_name": "event_id_not_null", "test_type": "not_null", "status": "pass", "failed_rows": 0},
                        {"test_name": "timestamp_recent", "test_type": "expression", "status": "warn", "failed_rows": 100},
                    ],
                },
                "sample_queries": [],
            },
            {
                "name": "other-project.reporting.sales_summary",
                "engine": "trino",
                "owner": "sales@example.com",
                "team": "@sales",
                "description": "Daily sales summary by region",
                "tags": ["tier::standard", "domain::sales", "aggregated"],
                "row_count": 50000,
                "last_updated": (now - timedelta(hours=6)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/other-project.reporting.sales_summary",
                "columns": [
                    {"name": "date", "data_type": "DATE", "description": "Report date", "is_pii": False, "fill_rate": 1.0, "distinct_count": 365},
                    {"name": "region", "data_type": "VARCHAR", "description": "Sales region", "is_pii": False, "fill_rate": 1.0, "distinct_count": 12},
                    {"name": "total_sales", "data_type": "DECIMAL", "description": "Total sales amount", "is_pii": False, "fill_rate": 1.0, "distinct_count": 45000},
                    {"name": "order_count", "data_type": "INTEGER", "description": "Number of orders", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1000},
                ],
                "ownership": {
                    "owner": "sales@example.com",
                    "team": "@sales",
                    "stewards": ["sales-ops@example.com"],
                    "consumers": ["@executive", "@finance"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(hours=6)).isoformat(),
                    "avg_update_lag_hours": 6.0,
                    "update_frequency": "daily",
                    "is_stale": False,
                    "stale_threshold_hours": 24,
                },
                "quality": {
                    "score": 100,
                    "total_tests": 5,
                    "passed_tests": 5,
                    "failed_tests": 0,
                    "warnings": 0,
                    "recent_tests": [],
                },
                "sample_queries": [],
            },
        ]

    @staticmethod
    def create_queries(now: datetime) -> list[dict[str, Any]]:
        """Create mock query execution data."""
        return [
            # Personal query - current user, successful
            {
                "query_id": "bq_job_abc123",
                "engine": "bigquery",
                "state": "success",
                "account": "current_user@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=2)).isoformat(),
                "finished_at": (now - timedelta(hours=2) + timedelta(seconds=12.5)).isoformat(),
                "duration_seconds": 12.5,
                "tables_used_count": 3,
                "tags": ["team::analytics", "pipeline::daily"],
                "query_preview": "SELECT user_id, COUNT(*) as event_count FROM analytics.raw_events...",
            },
            # Personal query - current user, failed
            {
                "query_id": "bq_job_def456",
                "engine": "bigquery",
                "state": "failed",
                "account": "current_user@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=3)).isoformat(),
                "finished_at": (now - timedelta(hours=3) + timedelta(seconds=0.8)).isoformat(),
                "duration_seconds": 0.8,
                "tables_used_count": 1,
                "error_message": "Quota exceeded: Your project exceeded quota for free query bytes scanned.",
                "tags": [],
                "query_preview": "SELECT * FROM analytics.large_table WHERE date = '2026-01-01'...",
            },
            # System account query - airflow, running
            {
                "query_id": "airflow_job_001",
                "engine": "trino",
                "state": "running",
                "account": "airflow-prod",
                "account_type": "system",
                "started_at": (now - timedelta(minutes=5)).isoformat(),
                "finished_at": None,
                "duration_seconds": None,
                "tables_used_count": 5,
                "tags": ["pipeline::daily_aggregation", "team::data-eng"],
                "query_preview": "INSERT INTO warehouse.daily_metrics SELECT date, SUM(amount)...",
            },
            # System account query - airflow, completed
            {
                "query_id": "airflow_job_002",
                "engine": "bigquery",
                "state": "success",
                "account": "airflow-prod",
                "account_type": "system",
                "started_at": (now - timedelta(hours=1)).isoformat(),
                "finished_at": (now - timedelta(hours=1) + timedelta(minutes=2)).isoformat(),
                "duration_seconds": 120.5,
                "tables_used_count": 8,
                "tags": ["pipeline::hourly_sync", "team::data-eng"],
                "query_preview": "MERGE INTO warehouse.users USING staging.users_delta...",
            },
            # System account query - dbt-runner, pending
            {
                "query_id": "dbt_run_xyz789",
                "engine": "trino",
                "state": "pending",
                "account": "dbt-runner",
                "account_type": "system",
                "started_at": (now - timedelta(seconds=30)).isoformat(),
                "finished_at": None,
                "duration_seconds": None,
                "tables_used_count": 0,
                "tags": ["dbt::model", "team::analytics"],
                "query_preview": "CREATE TABLE analytics.user_summary AS SELECT...",
            },
            # Other user query - alice
            {
                "query_id": "trino_alice_001",
                "engine": "trino",
                "state": "success",
                "account": "alice@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=4)).isoformat(),
                "finished_at": (now - timedelta(hours=4) + timedelta(seconds=45.2)).isoformat(),
                "duration_seconds": 45.2,
                "tables_used_count": 2,
                "tags": ["experiment::ab_test_v2"],
                "query_preview": "SELECT * FROM users WHERE experiment_group = 'treatment'...",
            },
            # Other user query - bob, cancelled
            {
                "query_id": "bq_bob_cancelled",
                "engine": "bigquery",
                "state": "cancelled",
                "account": "bob@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=5)).isoformat(),
                "finished_at": (now - timedelta(hours=5) + timedelta(minutes=3)).isoformat(),
                "duration_seconds": 180.0,
                "tables_used_count": 1,
                "tags": [],
                "query_preview": "SELECT * FROM warehouse.events WHERE date BETWEEN...",
            },
        ]
