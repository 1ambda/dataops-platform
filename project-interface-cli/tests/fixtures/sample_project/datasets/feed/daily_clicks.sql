-- daily_clicks.sql
-- Daily clicks aggregation per user

{% set target = 'iceberg.analytics.daily_clicks' %}
{% set source = 'iceberg.raw.user_events' %}

INSERT INTO {{ target }}
SELECT
    dt,
    user_id,
    COUNT(DISTINCT item_id) AS click_count,
    AVG(click_duration_ms) AS avg_duration_ms
FROM {{ source }}
WHERE dt BETWEEN DATE_ADD('day', -{{ lookback_days }}, DATE('{{ execution_date }}'))
      AND DATE('{{ execution_date }}')
  AND event_type = 'click'
GROUP BY dt, user_id
