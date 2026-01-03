package com.github.lambda.domain.model.transpile

import com.github.lambda.domain.model.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Transpile Rule Entity
 *
 * SQL transformation rule that defines how to convert SQL patterns
 * from one dialect to another (e.g., BigQuery to Trino).
 */
@Entity
@Table(
    name = "transpile_rules",
    indexes = [
        Index(name = "idx_transpile_rules_name", columnList = "name", unique = true),
        Index(name = "idx_transpile_rules_dialects", columnList = "from_dialect,to_dialect"),
        Index(name = "idx_transpile_rules_priority", columnList = "priority"),
        Index(name = "idx_transpile_rules_enabled", columnList = "enabled"),
    ],
)
class TranspileRuleEntity(
    @NotBlank(message = "Rule name is required")
    @Size(max = 255, message = "Rule name must not exceed 255 characters")
    @Column(name = "name", nullable = false, unique = true, length = 255)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "from_dialect", nullable = false, length = 50)
    var fromDialect: SqlDialect = SqlDialect.ANY,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_dialect", nullable = false, length = 50)
    var toDialect: SqlDialect = SqlDialect.ANY,
    @NotBlank(message = "Pattern is required")
    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    var pattern: String = "",
    @NotBlank(message = "Replacement is required")
    @Column(name = "replacement", nullable = false, columnDefinition = "TEXT")
    var replacement: String = "",
    @Column(name = "priority", nullable = false)
    var priority: Int = 100,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
) : BaseEntity() {
    /**
     * Check if this rule applies to the given dialect pair
     */
    fun appliesToDialects(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
    ): Boolean {
        val matchesFrom = this.fromDialect == SqlDialect.ANY || this.fromDialect == fromDialect
        val matchesTo = this.toDialect == SqlDialect.ANY || this.toDialect == toDialect
        return matchesFrom && matchesTo && enabled
    }

    /**
     * Apply this rule to the given SQL text
     */
    fun applyTo(sql: String): String =
        if (enabled) {
            sql.replace(Regex(pattern), replacement)
        } else {
            sql
        }

    /**
     * Check if this rule would match the given SQL
     */
    fun matchesSQL(sql: String): Boolean = enabled && Regex(pattern).containsMatchIn(sql)
}
