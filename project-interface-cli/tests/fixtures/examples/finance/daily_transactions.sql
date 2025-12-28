-- Daily financial transactions aggregation with risk scoring
-- Process raw transactions into aggregated daily summaries

INSERT INTO warehouse.finance.daily_transactions
WITH transaction_base AS (
  SELECT
    t.transaction_id,
    t.account_id,
    t.transaction_type,
    t.amount,
    t.currency,
    DATE(t.created_at) as transaction_date,
    t.merchant_id,
    t.country_code,
    -- Convert to USD using daily rates
    t.amount * COALESCE(cr.rate_to_usd, 1.0) as amount_usd,
    -- ML-based risk scoring
    COALESCE(rs.risk_score, 0.0) as risk_score,
    -- Compliance status based on multiple factors
    CASE
      WHEN t.amount > 10000 THEN 'REQUIRES_REVIEW'
      WHEN COALESCE(rs.risk_score, 0.0) >= {{ risk_threshold }} THEN 'HIGH_RISK'
      WHEN t.country_code IN ('US', 'CA', 'GB', 'DE', 'FR') THEN 'COMPLIANT'
      ELSE 'STANDARD'
    END as compliance_status
  FROM warehouse.raw.transactions t
  LEFT JOIN warehouse.lookup.currency_rates cr
    ON t.currency = cr.currency_code
    AND DATE(t.created_at) = cr.rate_date
  LEFT JOIN warehouse.ml.risk_scores rs
    ON t.transaction_id = rs.transaction_id
  WHERE DATE(t.created_at) = '{{ processing_date }}'
),

daily_aggregates AS (
  SELECT
    '{{ processing_date }}' as transaction_date,
    account_id,
    transaction_type,
    country_code,
    compliance_status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    SUM(amount_usd) as total_amount_usd,
    AVG(amount) as avg_amount,
    MAX(amount) as max_amount,
    MIN(amount) as min_amount,
    AVG(risk_score) as avg_risk_score,
    MAX(risk_score) as max_risk_score,
    COUNT(DISTINCT merchant_id) as unique_merchants,
    -- Business logic flags
    CASE
      WHEN SUM(amount_usd) > 50000 THEN true
      ELSE false
    END as high_volume_flag,
    CASE
      WHEN MAX(risk_score) >= {{ risk_threshold }} THEN true
      ELSE false
    END as high_risk_flag,
    CURRENT_TIMESTAMP as processed_at,
    '{{ processing_date }}' as processing_batch_date
  FROM transaction_base
  GROUP BY
    account_id,
    transaction_type,
    country_code,
    compliance_status
)

SELECT *
FROM daily_aggregates
WHERE transaction_count > 0  -- Only include accounts with actual transactions
