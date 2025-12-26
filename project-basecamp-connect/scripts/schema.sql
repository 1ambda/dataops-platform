-- MySQL DDL for Connect Service
-- Compatible with MySQL 8.0+
--
-- This schema supports the Jira-Slack integration features:
-- - Jira ticket monitoring and storage
-- - Slack message and thread storage
-- - Bidirectional linking between Jira tickets and Slack threads
--
-- Usage:
--   mysql -u <user> -p connect < schema.sql

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================================
-- Integration Logs Table
-- Stores all integration events between services
-- ============================================================================
CREATE TABLE IF NOT EXISTS integration_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_service VARCHAR(50) NOT NULL,
    target_service VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source_id VARCHAR(255) NULL,
    target_id VARCHAR(255) NULL,
    payload TEXT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_source_service (source_service),
    INDEX idx_target_service (target_service),
    INDEX idx_event_type (event_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Service Mappings Table
-- Generic mapping between service identifiers
-- ============================================================================
CREATE TABLE IF NOT EXISTS service_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_service VARCHAR(50) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    target_service VARCHAR(50) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    mapping_type VARCHAR(100) NOT NULL,
    extra_data TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_source_service (source_service),
    INDEX idx_source_id (source_id),
    INDEX idx_target_service (target_service),
    INDEX idx_target_id (target_id),
    UNIQUE KEY uq_service_mapping (source_service, source_id, target_service, mapping_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Jira Tickets Table
-- Stores monitored Jira tickets
-- ============================================================================
CREATE TABLE IF NOT EXISTS jira_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jira_id VARCHAR(50) NOT NULL,
    jira_key VARCHAR(50) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    description TEXT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'open',
    priority VARCHAR(20) NULL DEFAULT 'medium',
    issue_type VARCHAR(50) NOT NULL,
    assignee_id VARCHAR(100) NULL,
    assignee_name VARCHAR(255) NULL,
    reporter_id VARCHAR(100) NULL,
    reporter_name VARCHAR(255) NULL,
    labels TEXT NULL COMMENT 'Comma-separated list of labels',
    created_at_jira DATETIME NULL,
    updated_at_jira DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_jira_id (jira_id),
    UNIQUE KEY uq_jira_key (jira_key),
    INDEX idx_project_key (project_key),
    INDEX idx_status (status),
    INDEX idx_issue_type (issue_type),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_created_at_jira (created_at_jira)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Slack Threads Table
-- Stores Slack thread metadata
-- ============================================================================
CREATE TABLE IF NOT EXISTS slack_threads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id VARCHAR(20) NOT NULL,
    channel_name VARCHAR(100) NULL,
    thread_ts VARCHAR(50) NOT NULL,
    parent_message_ts VARCHAR(50) NOT NULL,
    permalink VARCHAR(500) NULL,
    created_by_bot TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_channel_id (channel_id),
    INDEX idx_thread_ts (thread_ts),
    UNIQUE KEY uq_channel_thread (channel_id, thread_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Slack Messages Table
-- Stores individual Slack messages
-- ============================================================================
CREATE TABLE IF NOT EXISTS slack_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id VARCHAR(20) NOT NULL,
    message_ts VARCHAR(50) NOT NULL,
    thread_id BIGINT NULL,
    thread_ts VARCHAR(50) NULL COMMENT 'Thread timestamp for replies',
    user_id VARCHAR(20) NULL,
    user_name VARCHAR(255) NULL,
    text TEXT NULL,
    message_type VARCHAR(50) NOT NULL DEFAULT 'message',
    is_bot_message TINYINT(1) NOT NULL DEFAULT 0,
    reactions TEXT NULL COMMENT 'JSON string of reactions',
    attachments TEXT NULL COMMENT 'JSON string of attachments',
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_channel_id (channel_id),
    INDEX idx_message_ts (message_ts),
    INDEX idx_thread_id (thread_id),
    INDEX idx_thread_ts (thread_ts),
    INDEX idx_user_id (user_id),
    INDEX idx_sent_at (sent_at),
    UNIQUE KEY uq_channel_message (channel_id, message_ts)
    -- Note: FK constraint removed by design - application layer handles referential integrity
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Jira-Slack Links Table
-- Links between Jira tickets and Slack threads
-- ============================================================================
CREATE TABLE IF NOT EXISTS jira_slack_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jira_ticket_id BIGINT NOT NULL,
    slack_thread_id BIGINT NOT NULL,
    link_type VARCHAR(50) NOT NULL DEFAULT 'ticket_thread',
    sync_enabled TINYINT(1) NOT NULL DEFAULT 1,
    last_sync_at DATETIME NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'active',
    link_metadata TEXT NULL COMMENT 'JSON string for extra data',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_jira_ticket_id (jira_ticket_id),
    INDEX idx_slack_thread_id (slack_thread_id),
    INDEX idx_link_type (link_type),
    INDEX idx_sync_status (sync_status),
    UNIQUE KEY uq_jira_slack_link (jira_ticket_id, slack_thread_id, link_type)
    -- Note: FK constraints removed by design - application layer handles referential integrity
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Slack Reply Syncs Table
-- Tracks Slack thread replies synced to Jira as comments
-- Direction: Slack -> Jira (Jira is Single Source of Truth)
-- ============================================================================
CREATE TABLE IF NOT EXISTS slack_reply_syncs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jira_ticket_id BIGINT NOT NULL,
    slack_thread_id BIGINT NOT NULL,
    slack_message_ts VARCHAR(50) NOT NULL COMMENT 'Slack message timestamp of the reply',
    slack_user_id VARCHAR(50) NULL,
    slack_user_name VARCHAR(255) NULL,
    body TEXT NULL,
    jira_comment_id VARCHAR(50) NULL COMMENT 'Jira comment ID after syncing',
    sync_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    synced_at DATETIME NULL,
    sent_at_slack DATETIME NULL COMMENT 'When the reply was sent in Slack',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_jira_ticket_id (jira_ticket_id),
    INDEX idx_slack_thread_id (slack_thread_id),
    INDEX idx_slack_message_ts (slack_message_ts),
    INDEX idx_jira_comment_id (jira_comment_id),
    INDEX idx_sync_status (sync_status),
    UNIQUE KEY uq_slack_reply_sync (jira_ticket_id, slack_message_ts)
    -- Note: FK constraints removed by design - application layer handles referential integrity
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Ticket Closure Notifications Table
-- Tracks closure notifications sent to Slack when Jira tickets are closed
-- ============================================================================
CREATE TABLE IF NOT EXISTS ticket_closure_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jira_ticket_id BIGINT NOT NULL,
    slack_thread_id BIGINT NOT NULL,
    jira_status VARCHAR(50) NOT NULL COMMENT 'The terminal status that triggered notification',
    notification_message_ts VARCHAR(50) NULL COMMENT 'Slack message timestamp of the notification',
    reaction_added TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether emoji reaction was added',
    reaction_emoji VARCHAR(50) NULL COMMENT 'Emoji name that was added',
    notified_at DATETIME NULL COMMENT 'When the notification was sent',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_jira_ticket_id (jira_ticket_id),
    INDEX idx_slack_thread_id (slack_thread_id),
    INDEX idx_jira_status (jira_status),
    UNIQUE KEY uq_ticket_closure (jira_ticket_id, slack_thread_id)
    -- Note: FK constraints removed by design - application layer handles referential integrity
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Example: Valid status values for reference
-- ============================================================================
-- Jira ticket statuses: 'open', 'in_progress', 'resolved', 'closed', 'done'
-- Jira terminal statuses (configurable): 'Done', 'Closed'
-- Jira priorities: 'lowest', 'low', 'medium', 'high', 'highest'
-- Link types: 'ticket_thread', 'ticket_notification', 'comment_sync'
-- Sync statuses: 'active', 'paused', 'error'
-- Reply sync statuses: 'pending', 'synced', 'failed', 'skipped'
-- Integration log statuses: 'pending', 'processing', 'success', 'failed'
