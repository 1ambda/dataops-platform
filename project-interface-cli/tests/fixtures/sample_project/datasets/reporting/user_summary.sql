-- user_summary.sql
-- User summary report

SELECT
    user_id,
    SUM(click_count) AS total_clicks,
    AVG(avg_duration_ms) AS avg_duration_ms
FROM iceberg.analytics.daily_clicks
WHERE dt BETWEEN DATE('{{ start_date }}') AND DATE('{{ end_date }}')
GROUP BY user_id
ORDER BY total_clicks DESC
LIMIT {{ limit_count }}
