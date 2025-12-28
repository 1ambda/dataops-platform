-- User Engagement Metric Query
-- Aggregates user session data for engagement analysis

SELECT
    u.user_id,
    COUNT(DISTINCT e.session_id) AS session_count,
    SUM(e.duration_ms) AS total_duration_ms,
    AVG(e.duration_ms) AS avg_session_duration_ms,
    u.country_code,
    MAX(DATE(e.event_timestamp)) AS last_activity_date
FROM {{ ref('iceberg.raw.user_events') }} e
JOIN {{ ref('iceberg.dim.users') }} u ON e.user_id = u.user_id
WHERE DATE(e.event_timestamp) BETWEEN '{{ start_date }}' AND '{{ end_date }}'
GROUP BY u.user_id, u.country_code
HAVING COUNT(DISTINCT e.session_id) >= {{ min_sessions }}
ORDER BY session_count DESC
