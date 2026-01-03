package com.github.lambda.infra.repository

import com.github.lambda.domain.model.transpile.SqlDialect
import com.github.lambda.domain.model.transpile.TranspileRuleEntity
import com.github.lambda.infra.config.JpaTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * TranspileRuleRepositoryJpaImpl Integration Tests
 *
 * Tests CRUD operations, SQL dialect queries, enabled status queries,
 * name-based queries, and counting operations.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaTestConfig::class)
@DisplayName("TranspileRuleRepositoryJpaImpl Integration Tests")
@Execution(ExecutionMode.SAME_THREAD)
class TranspileRuleRepositoryJpaImplTest {
    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var repository: TranspileRuleRepositoryJpaImpl

    private lateinit var rule1: TranspileRuleEntity
    private lateinit var rule2: TranspileRuleEntity
    private lateinit var rule3: TranspileRuleEntity
    private lateinit var disabledRule: TranspileRuleEntity

    @BeforeEach
    fun setUp() {
        // Clean up any existing data from parallel test runs
        testEntityManager.entityManager.createNativeQuery("DELETE FROM transpile_rules").executeUpdate()
        testEntityManager.flush()
        testEntityManager.clear()

        // Create test entities
        rule1 =
            TranspileRuleEntity(
                name = "bigquery_to_trino_datetime",
                fromDialect = SqlDialect.BIGQUERY,
                toDialect = SqlDialect.TRINO,
                pattern = "DATETIME\\((.+?)\\)",
                replacement = "CAST(\\$1 AS TIMESTAMP)",
                priority = 100,
                enabled = true,
                description = "Convert BigQuery DATETIME to Trino TIMESTAMP",
            )

        rule2 =
            TranspileRuleEntity(
                name = "mysql_to_postgresql_limit",
                fromDialect = SqlDialect.MYSQL,
                toDialect = SqlDialect.POSTGRESQL,
                pattern = "LIMIT (\\d+)",
                replacement = "LIMIT \\$1",
                priority = 200,
                enabled = true,
                description = "MySQL to PostgreSQL LIMIT conversion",
            )

        rule3 =
            TranspileRuleEntity(
                name = "bigquery_to_trino_extract",
                fromDialect = SqlDialect.BIGQUERY,
                toDialect = SqlDialect.TRINO,
                pattern = "EXTRACT\\((\\w+)\\s+FROM\\s+(\\w+)\\)",
                replacement = "DATE_PART('\\$1', \\$2)",
                priority = 150,
                enabled = true,
                description = "Convert BigQuery EXTRACT to Trino DATE_PART",
            )

        disabledRule =
            TranspileRuleEntity(
                name = "disabled_rule",
                fromDialect = SqlDialect.BIGQUERY,
                toDialect = SqlDialect.TRINO,
                pattern = "OLD_FUNCTION\\((.+?)\\)",
                replacement = "NEW_FUNCTION(\\$1)",
                priority = 300,
                enabled = false,
                description = "Disabled transformation rule",
            )

        // Persist entities
        rule1 = testEntityManager.persistAndFlush(rule1)
        rule2 = testEntityManager.persistAndFlush(rule2)
        rule3 = testEntityManager.persistAndFlush(rule3)
        disabledRule = testEntityManager.persistAndFlush(disabledRule)
        testEntityManager.clear()
    }

    @Nested
    @DisplayName("CRUD Operations")
    inner class CRUDOperations {
        @Test
        @DisplayName("should save and retrieve transpile rule")
        fun shouldSaveAndRetrieveTranspileRule() {
            // Given
            val newRule =
                TranspileRuleEntity(
                    name = "new_transformation_rule",
                    fromDialect = SqlDialect.POSTGRESQL,
                    toDialect = SqlDialect.MYSQL,
                    pattern = "CONCAT\\((.+?)\\)",
                    replacement = "CONCAT(\\$1)",
                    priority = 250,
                    enabled = true,
                    description = "PostgreSQL to MySQL CONCAT",
                )

            // When
            val savedRule = repository.save(newRule)
            testEntityManager.flush()
            testEntityManager.clear()

            val foundRule = repository.findById(savedRule.id!!)

            // Then
            assertThat(foundRule).isPresent()
            assertThat(foundRule.get().name).isEqualTo("new_transformation_rule")
            assertThat(foundRule.get().fromDialect).isEqualTo(SqlDialect.POSTGRESQL)
            assertThat(foundRule.get().toDialect).isEqualTo(SqlDialect.MYSQL)
            assertThat(foundRule.get().pattern).isEqualTo("CONCAT\\((.+?)\\)")
            assertThat(foundRule.get().replacement).isEqualTo("CONCAT(\\$1)")
            assertThat(foundRule.get().priority).isEqualTo(250)
            assertThat(foundRule.get().enabled).isTrue()
            assertThat(foundRule.get().description).isEqualTo("PostgreSQL to MySQL CONCAT")
        }

        @Test
        @DisplayName("should update existing transpile rule")
        fun shouldUpdateExistingTranspileRule() {
            // Given
            val existingRule = repository.findById(rule1.id!!)
            assertThat(existingRule).isPresent()

            // When
            val ruleToUpdate = existingRule.get()
            ruleToUpdate.pattern = "NEW_DATETIME\\((.+?)\\)"
            ruleToUpdate.replacement = "CAST(\\$1 AS TIMESTAMP WITH TIME ZONE)"
            ruleToUpdate.priority = 99
            ruleToUpdate.description = "Updated BigQuery to Trino conversion"

            val updatedRule = repository.save(ruleToUpdate)
            testEntityManager.flush()
            testEntityManager.clear()

            // Then
            val retrievedRule = repository.findById(rule1.id!!)
            assertThat(retrievedRule).isPresent()
            assertThat(retrievedRule.get().pattern).isEqualTo("NEW_DATETIME\\((.+?)\\)")
            assertThat(retrievedRule.get().replacement).isEqualTo("CAST(\\$1 AS TIMESTAMP WITH TIME ZONE)")
            assertThat(retrievedRule.get().priority).isEqualTo(99)
            assertThat(retrievedRule.get().description).isEqualTo("Updated BigQuery to Trino conversion")
        }

        @Test
        @DisplayName("should delete transpile rule by ID")
        fun shouldDeleteTranspileRuleById() {
            // Given
            val ruleId = rule1.id!!
            assertThat(repository.existsById(ruleId)).isTrue()

            // When
            repository.deleteById(ruleId)
            testEntityManager.flush()

            // Then
            assertThat(repository.existsById(ruleId)).isFalse()
            assertThat(repository.findById(ruleId)).isEmpty()
        }

        @Test
        @DisplayName("should check if rule exists by ID")
        fun shouldCheckIfRuleExistsById() {
            // When & Then
            assertThat(repository.existsById(rule1.id!!)).isTrue()
            assertThat(repository.existsById(999999L)).isFalse()
        }

        @Test
        @DisplayName("should find all transpile rules")
        fun shouldFindAllTranspileRules() {
            // When
            val allRules = repository.findAll()

            // Then
            assertThat(allRules).hasSize(4)
            assertThat(allRules.map { it.name }).containsExactlyInAnyOrder(
                "bigquery_to_trino_datetime",
                "mysql_to_postgresql_limit",
                "bigquery_to_trino_extract",
                "disabled_rule",
            )
        }
    }

    @Nested
    @DisplayName("Name-Based Queries")
    inner class NameBasedQueries {
        @Test
        @DisplayName("should find rule by name")
        fun shouldFindRuleByName() {
            // When
            val foundRule = repository.findByName("bigquery_to_trino_datetime")

            // Then
            assertThat(foundRule).isNotNull()
            assertThat(foundRule!!.name).isEqualTo("bigquery_to_trino_datetime")
            assertThat(foundRule.fromDialect).isEqualTo(SqlDialect.BIGQUERY)
            assertThat(foundRule.toDialect).isEqualTo(SqlDialect.TRINO)
        }

        @Test
        @DisplayName("should return null when rule not found by name")
        fun shouldReturnNullWhenRuleNotFoundByName() {
            // When
            val foundRule = repository.findByName("non_existent_rule")

            // Then
            assertThat(foundRule).isNull()
        }

        @Test
        @DisplayName("should check if rule exists by name")
        fun shouldCheckIfRuleExistsByName() {
            // When & Then
            assertThat(repository.existsByName("bigquery_to_trino_datetime")).isTrue()
            assertThat(repository.existsByName("non_existent_rule")).isFalse()
        }
    }

    @Nested
    @DisplayName("Enabled Status Queries")
    inner class EnabledStatusQueries {
        @Test
        @DisplayName("should find only enabled rules")
        fun shouldFindOnlyEnabledRules() {
            // When
            val enabledRules = repository.findByEnabledTrue()

            // Then
            assertThat(enabledRules).hasSize(3)
            assertThat(enabledRules.map { it.name }).containsExactlyInAnyOrder(
                "bigquery_to_trino_datetime",
                "mysql_to_postgresql_limit",
                "bigquery_to_trino_extract",
            )
            assertThat(enabledRules).allMatch { it.enabled }
        }

        @Test
        @DisplayName("should count enabled rules")
        fun shouldCountEnabledRules() {
            // When
            val count = repository.countByEnabledTrue()

            // Then
            assertThat(count).isEqualTo(3L)
        }

        @Test
        @DisplayName("should handle zero enabled rules")
        fun shouldHandleZeroEnabledRules() {
            // Given - disable all rules
            val allRules = repository.findAll()
            allRules.forEach { it.enabled = false }
            repository.saveAll(allRules)
            testEntityManager.flush()

            // When
            val enabledRules = repository.findByEnabledTrue()
            val count = repository.countByEnabledTrue()

            // Then
            assertThat(enabledRules).isEmpty()
            assertThat(count).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("Dialect-Based Queries")
    inner class DialectBasedQueries {
        @Test
        @DisplayName("should find rules by from and to dialect")
        fun shouldFindRulesByFromAndToDialect() {
            // When
            val bigqueryToTrinoRules =
                repository.findByFromDialectAndToDialect(
                    SqlDialect.BIGQUERY,
                    SqlDialect.TRINO,
                )

            // Then
            assertThat(bigqueryToTrinoRules).hasSize(2)
            assertThat(bigqueryToTrinoRules.map { it.name }).containsExactlyInAnyOrder(
                "bigquery_to_trino_datetime",
                "bigquery_to_trino_extract",
            )
            assertThat(bigqueryToTrinoRules).allMatch {
                it.fromDialect == SqlDialect.BIGQUERY && it.toDialect == SqlDialect.TRINO
            }
        }

        @Test
        @DisplayName("should find enabled rules by from and to dialect")
        fun shouldFindEnabledRulesByFromAndToDialect() {
            // When
            val enabledBigqueryToTrinoRules =
                repository.findByFromDialectAndToDialectAndEnabledTrue(
                    SqlDialect.BIGQUERY,
                    SqlDialect.TRINO,
                )

            // Then
            assertThat(enabledBigqueryToTrinoRules).hasSize(2)
            assertThat(enabledBigqueryToTrinoRules.map { it.name }).containsExactlyInAnyOrder(
                "bigquery_to_trino_datetime",
                "bigquery_to_trino_extract",
            )
            assertThat(enabledBigqueryToTrinoRules).allMatch {
                it.fromDialect == SqlDialect.BIGQUERY &&
                    it.toDialect == SqlDialect.TRINO &&
                    it.enabled
            }
        }

        @Test
        @DisplayName("should exclude disabled rules in enabled dialect query")
        fun shouldExcludeDisabledRulesInEnabledDialectQuery() {
            // Given - Make one BigQuery to Trino rule disabled
            val ruleToDisable = repository.findByName("bigquery_to_trino_datetime")
            ruleToDisable!!.enabled = false
            repository.save(ruleToDisable)
            testEntityManager.flush()

            // When
            val enabledRules =
                repository.findByFromDialectAndToDialectAndEnabledTrue(
                    SqlDialect.BIGQUERY,
                    SqlDialect.TRINO,
                )
            val allRules =
                repository.findByFromDialectAndToDialect(
                    SqlDialect.BIGQUERY,
                    SqlDialect.TRINO,
                )

            // Then
            assertThat(enabledRules).hasSize(1)
            assertThat(enabledRules.first().name).isEqualTo("bigquery_to_trino_extract")
            assertThat(allRules).hasSize(2) // Still includes disabled rule
        }

        @Test
        @DisplayName("should return empty list for non-existent dialect combinations")
        fun shouldReturnEmptyListForNonExistentDialectCombinations() {
            // When
            val oracleToSQLServerRules =
                repository.findByFromDialectAndToDialect(
                    SqlDialect.GENERIC, // Using GENERIC as a less common dialect
                    SqlDialect.MYSQL,
                )

            // Then
            assertThat(oracleToSQLServerRules).isEmpty()
        }
    }

    @Nested
    @DisplayName("Priority and Ordering")
    inner class PriorityAndOrdering {
        @Test
        @DisplayName("should preserve rule priority values")
        fun shouldPreserveRulePriorityValues() {
            // When
            val rules = repository.findAll()

            // Then
            val rulesByName = rules.associateBy { it.name }
            assertThat(rulesByName["bigquery_to_trino_datetime"]!!.priority).isEqualTo(100)
            assertThat(rulesByName["mysql_to_postgresql_limit"]!!.priority).isEqualTo(200)
            assertThat(rulesByName["bigquery_to_trino_extract"]!!.priority).isEqualTo(150)
            assertThat(rulesByName["disabled_rule"]!!.priority).isEqualTo(300)
        }
    }

    @Nested
    @DisplayName("Complex Scenarios")
    inner class ComplexScenarios {
        @Test
        @DisplayName("should handle multiple dialects with mixed enabled states")
        fun shouldHandleMultipleDialectsWithMixedEnabledStates() {
            // Given - Create additional rules with mixed states
            val additionalRule1 =
                TranspileRuleEntity(
                    name = "trino_to_bigquery_date",
                    fromDialect = SqlDialect.TRINO,
                    toDialect = SqlDialect.BIGQUERY,
                    pattern = "DATE_TRUNC\\((.+?)\\)",
                    replacement = "DATETIME_TRUNC(\\$1)",
                    priority = 120,
                    enabled = true,
                )

            val additionalRule2 =
                TranspileRuleEntity(
                    name = "disabled_trino_to_bigquery",
                    fromDialect = SqlDialect.TRINO,
                    toDialect = SqlDialect.BIGQUERY,
                    pattern = "OLD_PATTERN",
                    replacement = "NEW_PATTERN",
                    priority = 110,
                    enabled = false,
                )

            repository.saveAll(listOf(additionalRule1, additionalRule2))
            testEntityManager.flush()

            // When
            val allTrinoToBigQuery =
                repository.findByFromDialectAndToDialect(
                    SqlDialect.TRINO,
                    SqlDialect.BIGQUERY,
                )
            val enabledTrinoToBigQuery =
                repository.findByFromDialectAndToDialectAndEnabledTrue(
                    SqlDialect.TRINO,
                    SqlDialect.BIGQUERY,
                )

            // Then
            assertThat(allTrinoToBigQuery).hasSize(2)
            assertThat(enabledTrinoToBigQuery).hasSize(1)
            assertThat(enabledTrinoToBigQuery.first().name).isEqualTo("trino_to_bigquery_date")
        }

        @Test
        @DisplayName("should handle duplicate name constraint violation gracefully")
        fun shouldHandleDuplicateNameConstraintViolationGracefully() {
            // Given
            val duplicateNameRule =
                TranspileRuleEntity(
                    name = "bigquery_to_trino_datetime", // Same name as existing rule
                    fromDialect = SqlDialect.MYSQL,
                    toDialect = SqlDialect.POSTGRESQL,
                    pattern = "different pattern",
                    replacement = "different replacement",
                    priority = 999,
                    enabled = true,
                )

            // When & Then - Should throw constraint violation exception
            org.junit.jupiter.api.assertThrows<org.springframework.dao.DataIntegrityViolationException> {
                repository.save(duplicateNameRule)
                testEntityManager.flush()
            }
        }

        @Test
        @DisplayName("should handle SQL patterns with special regex characters")
        fun shouldHandleSQLPatternsWithSpecialRegexCharacters() {
            // Given
            val complexPatternRule =
                TranspileRuleEntity(
                    name = "complex_regex_pattern",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "EXTRACT\\(YEAR\\s+FROM\\s+DATETIME\\(TIMESTAMP\\(\\\"(.+?)\\\"\\)\\)\\)",
                    replacement = "YEAR(CAST('\\$1' AS TIMESTAMP))",
                    priority = 500,
                    enabled = true,
                    description = "Complex nested function transformation",
                )

            // When
            val saved = repository.save(complexPatternRule)
            testEntityManager.flush()

            val retrieved = repository.findByName("complex_regex_pattern")

            // Then
            assertThat(retrieved).isNotNull()
            assertThat(
                retrieved!!.pattern,
            ).isEqualTo("EXTRACT\\(YEAR\\s+FROM\\s+DATETIME\\(TIMESTAMP\\(\\\"(.+?)\\\"\\)\\)\\)")
            assertThat(retrieved.replacement).isEqualTo("YEAR(CAST('\\$1' AS TIMESTAMP))")
        }
    }
}
