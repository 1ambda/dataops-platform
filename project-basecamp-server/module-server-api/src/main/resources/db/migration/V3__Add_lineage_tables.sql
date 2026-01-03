-- Lineage API Foundation Tables
-- This migration creates tables for lineage graph storage and traversal

-- Lineage Nodes Table (stores information about each node in the lineage graph)
CREATE TABLE IF NOT EXISTS lineage_nodes (
    name VARCHAR(255) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    owner VARCHAR(100) DEFAULT NULL,
    team VARCHAR(100) DEFAULT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_lineage_node_type (type),
    INDEX idx_lineage_node_owner (owner),
    INDEX idx_lineage_node_team (team)
);

-- Lineage Node Tags Table (many-to-many relationship for tags)
CREATE TABLE IF NOT EXISTS lineage_node_tags (
    lineage_node_name VARCHAR(255) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (lineage_node_name, tag),

    FOREIGN KEY (lineage_node_name) REFERENCES lineage_nodes(name)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_lineage_node_tags_tag (tag)
);

-- Lineage Edges Table (stores relationships between nodes)
CREATE TABLE IF NOT EXISTS lineage_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    edge_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    description VARCHAR(1000) DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    deleted_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,

    UNIQUE KEY idx_lineage_edge_source_target (source, target),
    INDEX idx_lineage_edge_source (source),
    INDEX idx_lineage_edge_target (target),
    INDEX idx_lineage_edge_type (edge_type)
);

-- Insert sample test data for demonstration
INSERT INTO lineage_nodes (name, type, owner, team, description) VALUES
    ('catalog.schema.orders', 'TABLE', 'data-eng@company.com', 'data-engineering', 'Customer orders table'),
    ('catalog.schema.customers', 'TABLE', 'data-eng@company.com', 'data-engineering', 'Customer information table'),
    ('catalog.schema.order_metrics', 'DATASET', 'analytics@company.com', 'analytics', 'Daily order metrics dataset'),
    ('catalog.schema.customer_lifetime_value', 'METRIC', 'analytics@company.com', 'analytics', 'Customer lifetime value metric'),
    ('catalog.schema.order_summary_view', 'VIEW', 'data-eng@company.com', 'data-engineering', 'Aggregated order summary view');

INSERT INTO lineage_node_tags (lineage_node_name, tag) VALUES
    ('catalog.schema.orders', 'production'),
    ('catalog.schema.orders', 'e-commerce'),
    ('catalog.schema.customers', 'production'),
    ('catalog.schema.customers', 'pii'),
    ('catalog.schema.order_metrics', 'analytics'),
    ('catalog.schema.customer_lifetime_value', 'kpi'),
    ('catalog.schema.order_summary_view', 'reporting');

INSERT INTO lineage_edges (source, target, edge_type, description) VALUES
    ('catalog.schema.orders', 'catalog.schema.order_metrics', 'DIRECT', 'Order metrics derived from orders table'),
    ('catalog.schema.customers', 'catalog.schema.customer_lifetime_value', 'DIRECT', 'CLV calculated from customer data'),
    ('catalog.schema.orders', 'catalog.schema.order_summary_view', 'DIRECT', 'Summary view aggregates order data'),
    ('catalog.schema.customers', 'catalog.schema.order_summary_view', 'DIRECT', 'Summary view includes customer data'),
    ('catalog.schema.order_metrics', 'catalog.schema.customer_lifetime_value', 'INDIRECT', 'CLV uses order metrics for calculation');