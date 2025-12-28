-- Supply Chain KPI Analysis
-- Comprehensive performance metrics across inventory, suppliers, and logistics

WITH inventory_performance AS (
  SELECT
    '{{ report_date }}' as report_date,
    il.warehouse_id,
    il.product_id,
    p.product_category,
    w.region,
    w.warehouse_type,
    -- Inventory metrics
    il.current_stock_level,
    il.average_stock_level,
    il.reorder_point,
    CASE
      WHEN il.current_stock_level = 0 THEN true
      ELSE false
    END as is_stockout,
    CASE
      WHEN il.current_stock_level > il.optimal_stock_level * 1.5
      THEN (il.current_stock_level - il.optimal_stock_level) * p.unit_cost
      ELSE 0
    END as excess_inventory_value,
    -- Calculated KPIs
    CASE
      WHEN il.average_stock_level > 0
      THEN (il.cost_of_goods_sold_30d / il.average_stock_level)
      ELSE 0
    END as inventory_turnover_ratio,
    CASE
      WHEN il.cost_of_goods_sold_30d > 0
      THEN (il.average_stock_level * 30.0) / il.cost_of_goods_sold_30d
      ELSE 999
    END as days_of_supply,
    il.carrying_cost_rate * il.current_stock_level * p.unit_cost as carrying_cost_per_unit
  FROM warehouse.staging.inventory_levels il
  INNER JOIN warehouse.reference.products p
    ON il.product_id = p.product_id
  INNER JOIN warehouse.reference.warehouses w
    ON il.warehouse_id = w.warehouse_id
  WHERE il.snapshot_date = '{{ report_date }}'
    {% if region_filter %}
    AND w.region IN ({{ region_filter | sql_list }})
    {% endif %}
    {% if product_categories %}
    AND p.product_category IN ({{ product_categories | sql_list }})
    {% endif %}
),

order_fulfillment AS (
  SELECT
    co.warehouse_id,
    co.product_id,
    COUNT(*) as total_orders,
    SUM(co.quantity_ordered) as total_quantity_ordered,
    SUM(co.quantity_fulfilled) as total_quantity_fulfilled,
    AVG(EXTRACT(EPOCH FROM (co.actual_ship_date - co.order_date)) / 86400.0) as avg_cycle_time_days,
    AVG(
      CASE
        WHEN co.quantity_fulfilled = co.quantity_ordered
         AND co.actual_ship_date <= co.promised_ship_date
         AND co.quality_issues = 0
        THEN 1.0
        ELSE 0.0
      END
    ) as perfect_order_rate,
    AVG(co.quantity_fulfilled::float / co.quantity_ordered) as fill_rate_percentage
  FROM warehouse.staging.customer_orders co
  WHERE co.order_date >= DATE_SUB('{{ report_date }}', INTERVAL {{ performance_window_days }} DAY)
    AND co.order_date <= '{{ report_date }}'
    AND co.order_value >= {{ min_order_value }}
  GROUP BY co.warehouse_id, co.product_id
),

supplier_performance AS (
  SELECT
    sd.supplier_id,
    sd.warehouse_id,
    sd.product_id,
    COUNT(*) as total_deliveries,
    AVG(
      CASE
        WHEN sd.actual_delivery_date <= sd.promised_delivery_date
        THEN 1.0
        ELSE 0.0
      END
    ) as on_time_delivery_rate,
    AVG(sd.quality_score) as quality_score,
    SUM(sd.delivery_value) as total_procurement_cost,
    AVG(sd.logistics_cost / sd.delivery_value) as logistics_cost_percentage
  FROM warehouse.staging.supplier_deliveries sd
  WHERE sd.delivery_date >= DATE_SUB('{{ report_date }}', INTERVAL {{ performance_window_days }} DAY)
    AND sd.delivery_date <= '{{ report_date }}'
  GROUP BY sd.supplier_id, sd.warehouse_id, sd.product_id
),

warehouse_utilization AS (
  SELECT
    wu.warehouse_id,
    AVG(wu.space_utilized / wu.total_capacity) as warehouse_utilization_rate
  FROM warehouse.staging.warehouse_utilization wu
  WHERE wu.utilization_date >= DATE_SUB('{{ report_date }}', INTERVAL {{ performance_window_days }} DAY)
    AND wu.utilization_date <= '{{ report_date }}'
  GROUP BY wu.warehouse_id
),

demand_forecasting AS (
  SELECT
    df.warehouse_id,
    df.product_id,
    AVG(
      CASE
        WHEN df.actual_demand > 0
        THEN 1.0 - ABS(df.forecasted_demand - df.actual_demand) / df.actual_demand
        ELSE 0.0
      END
    ) as forecast_accuracy_percentage
  FROM warehouse.staging.demand_forecasts df
  WHERE df.forecast_date >= DATE_SUB('{{ report_date }}', INTERVAL {{ performance_window_days }} DAY)
    AND df.forecast_date <= '{{ report_date }}'
  GROUP BY df.warehouse_id, df.product_id
)

SELECT
  ip.report_date,
  ip.warehouse_id,
  ip.product_id,
  ip.product_category,
  ip.region,
  ip.warehouse_type,
  -- Supplier information
  COALESCE(sp.supplier_id, 'UNKNOWN') as supplier_id,
  COALESCE(s.supplier_name, 'Unknown Supplier') as supplier_name,
  COALESCE(s.supplier_tier, 'UNCLASSIFIED') as supplier_tier,

  -- Inventory KPIs
  ip.inventory_turnover_ratio,
  ip.days_of_supply,
  ip.is_stockout,
  ip.excess_inventory_value,
  ip.carrying_cost_per_unit,

  -- Order Fulfillment KPIs
  COALESCE(of.fill_rate_percentage, 0.0) as fill_rate_percentage,
  COALESCE(of.perfect_order_rate, 0.0) as perfect_order_rate,
  COALESCE(of.avg_cycle_time_days, 0.0) as avg_cycle_time_days,

  -- Supplier KPIs
  COALESCE(sp.on_time_delivery_rate, 0.0) as on_time_delivery_rate,
  COALESCE(sp.quality_score, 0.0) as quality_score,
  COALESCE(sp.total_procurement_cost, 0.0) as total_procurement_cost,
  COALESCE(sp.logistics_cost_percentage, 0.0) as logistics_cost_percentage,

  -- Warehouse KPIs
  COALESCE(wu.warehouse_utilization_rate, 0.0) as warehouse_utilization_rate,

  -- Forecasting KPIs
  COALESCE(df.forecast_accuracy_percentage, 0.0) as forecast_accuracy_percentage,

  -- Risk Indicators
  CASE
    WHEN ip.days_of_supply < 7 OR ip.is_stockout THEN 'HIGH'
    WHEN ip.days_of_supply < 14 THEN 'MEDIUM'
    ELSE 'LOW'
  END as inventory_risk_level,

  CASE
    WHEN COALESCE(sp.on_time_delivery_rate, 0) < 0.85 OR COALESCE(sp.quality_score, 0) < 0.8 THEN 'HIGH'
    WHEN COALESCE(sp.on_time_delivery_rate, 0) < 0.95 OR COALESCE(sp.quality_score, 0) < 0.9 THEN 'MEDIUM'
    ELSE 'LOW'
  END as supplier_risk_level

FROM inventory_performance ip
LEFT JOIN order_fulfillment of
  ON ip.warehouse_id = of.warehouse_id
  AND ip.product_id = of.product_id
LEFT JOIN supplier_performance sp
  ON ip.warehouse_id = sp.warehouse_id
  AND ip.product_id = sp.product_id
LEFT JOIN warehouse_utilization wu
  ON ip.warehouse_id = wu.warehouse_id
LEFT JOIN demand_forecasting df
  ON ip.warehouse_id = df.warehouse_id
  AND ip.product_id = df.product_id
LEFT JOIN warehouse.reference.suppliers s
  ON sp.supplier_id = s.supplier_id

WHERE ip.current_stock_level IS NOT NULL  -- Ensure we have valid inventory data
ORDER BY
  ip.region,
  ip.warehouse_id,
  ip.product_category,
  ip.product_id
