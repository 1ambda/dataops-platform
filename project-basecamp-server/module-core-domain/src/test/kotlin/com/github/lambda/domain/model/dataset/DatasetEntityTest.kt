package com.github.lambda.domain.model.dataset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * DatasetEntity Unit Tests
 *
 * Tests entity business logic and validation methods.
 */
@DisplayName("DatasetEntity Unit Tests")
class DatasetEntityTest {
    @Nested
    @DisplayName("Constructor and Basic Properties")
    inner class ConstructorAndBasicProperties {
        @Test
        @DisplayName("should create dataset entity with required fields")
        fun `should create dataset entity with required fields`() {
            // Given & When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                )

            // Then
            assertThat(dataset.name).isEqualTo("catalog.schema.dataset")
            assertThat(dataset.owner).isEqualTo("test@example.com")
            assertThat(dataset.sql).isEqualTo("SELECT * FROM table")
            assertThat(dataset.team).isNull()
            assertThat(dataset.description).isNull()
            assertThat(dataset.tags).isEmpty()
            assertThat(dataset.dependencies).isEmpty()
            assertThat(dataset.scheduleCron).isNull()
            assertThat(dataset.scheduleTimezone).isEqualTo("UTC") // Default value
            assertThat(dataset.id).isNotNull()
            assertThat(dataset.createdAt).isNotNull()
            assertThat(dataset.updatedAt).isNotNull()
        }

        @Test
        @DisplayName("should create dataset entity with all optional fields")
        fun `should create dataset entity with all optional fields`() {
            // Given & When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Test dataset",
                    sql = "SELECT * FROM users WHERE active = true",
                    tags = setOf("test", "user", "active"),
                    dependencies = setOf("users", "user_profiles"),
                    scheduleCron = "0 9 * * *",
                    scheduleTimezone = "America/New_York",
                )

            // Then
            assertThat(dataset.name).isEqualTo("catalog.schema.dataset")
            assertThat(dataset.owner).isEqualTo("test@example.com")
            assertThat(dataset.team).isEqualTo("data-team")
            assertThat(dataset.description).isEqualTo("Test dataset")
            assertThat(dataset.sql).isEqualTo("SELECT * FROM users WHERE active = true")
            assertThat(dataset.tags).containsExactlyInAnyOrder("test", "user", "active")
            assertThat(dataset.dependencies).containsExactlyInAnyOrder("users", "user_profiles")
            assertThat(dataset.scheduleCron).isEqualTo("0 9 * * *")
            assertThat(dataset.scheduleTimezone).isEqualTo("America/New_York")
        }

        @Test
        @DisplayName("should generate unique IDs for different instances")
        fun `should generate unique IDs for different instances`() {
            // Given & When
            val dataset1 =
                DatasetEntity(
                    name = "catalog.schema.dataset1",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table1",
                )
            val dataset2 =
                DatasetEntity(
                    name = "catalog.schema.dataset2",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table2",
                )

            // Then
            assertThat(dataset1.id).isNotEqualTo(dataset2.id)
            assertThat(dataset1.id).isNotNull()
            assertThat(dataset2.id).isNotNull()
        }

        @Test
        @DisplayName("should set creation and update timestamps")
        fun `should set creation and update timestamps`() {
            // Given
            val beforeCreation = LocalDateTime.now().minusSeconds(1)

            // When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                )

            val afterCreation = LocalDateTime.now().plusSeconds(1)

            // Then
            assertThat(dataset.createdAt).isBetween(beforeCreation, afterCreation)
            assertThat(dataset.updatedAt).isBetween(beforeCreation, afterCreation)
        }
    }

    @Nested
    @DisplayName("Business Logic Methods")
    inner class BusinessLogicMethods {
        @Test
        @DisplayName("hasSchedule should return true when scheduleCron is set")
        fun `hasSchedule should return true when scheduleCron is set`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    scheduleCron = "0 9 * * *",
                )

            // When & Then
            assertThat(dataset.hasSchedule()).isTrue()
        }

        @Test
        @DisplayName("hasSchedule should return false when scheduleCron is null")
        fun `hasSchedule should return false when scheduleCron is null`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    scheduleCron = null,
                )

            // When & Then
            assertThat(dataset.hasSchedule()).isFalse()
        }

        @Test
        @DisplayName("hasSchedule should return false when scheduleCron is empty")
        fun `hasSchedule should return false when scheduleCron is empty`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    scheduleCron = "",
                )

            // When & Then
            assertThat(dataset.hasSchedule()).isFalse()
        }

        @Test
        @DisplayName("hasSchedule should return false when scheduleCron is blank")
        fun `hasSchedule should return false when scheduleCron is blank`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    scheduleCron = "   ",
                )

            // When & Then
            assertThat(dataset.hasSchedule()).isFalse()
        }

        @Test
        @DisplayName("hasDependencies should return true when dependencies exist")
        fun `hasDependencies should return true when dependencies exist`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM users JOIN orders ON users.id = orders.user_id",
                    dependencies = setOf("users", "orders"),
                )

            // When & Then
            assertThat(dataset.hasDependencies()).isTrue()
        }

        @Test
        @DisplayName("hasDependencies should return false when no dependencies")
        fun `hasDependencies should return false when no dependencies`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT COUNT(*) FROM users",
                    dependencies = emptySet(),
                )

            // When & Then
            assertThat(dataset.hasDependencies()).isFalse()
        }

        @Test
        @DisplayName("hasTags should return true when tags exist")
        fun `hasTags should return true when tags exist`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    tags = setOf("test", "user"),
                )

            // When & Then
            assertThat(dataset.hasTags()).isTrue()
        }

        @Test
        @DisplayName("hasTags should return false when no tags")
        fun `hasTags should return false when no tags`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    tags = emptySet(),
                )

            // When & Then
            assertThat(dataset.hasTags()).isFalse()
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    inner class EqualsAndHashCode {
        @Test
        @DisplayName("should be equal when names are the same")
        fun `should be equal when names are the same`() {
            // Given
            val dataset1 =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test1@example.com",
                    sql = "SELECT * FROM table1",
                )
            val dataset2 =
                DatasetEntity(
                    name = "catalog.schema.dataset", // Same name
                    owner = "test2@example.com", // Different owner
                    sql = "SELECT * FROM table2", // Different SQL
                )

            // When & Then
            assertThat(dataset1).isEqualTo(dataset2)
            assertThat(dataset1.hashCode()).isEqualTo(dataset2.hashCode())
        }

        @Test
        @DisplayName("should not be equal when names are different")
        fun `should not be equal when names are different`() {
            // Given
            val dataset1 =
                DatasetEntity(
                    name = "catalog.schema.dataset1",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                )
            val dataset2 =
                DatasetEntity(
                    name = "catalog.schema.dataset2", // Different name
                    owner = "test@example.com", // Same owner
                    sql = "SELECT * FROM table", // Same SQL
                )

            // When & Then
            assertThat(dataset1).isNotEqualTo(dataset2)
            assertThat(dataset1.hashCode()).isNotEqualTo(dataset2.hashCode())
        }

        @Test
        @DisplayName("should be equal to itself")
        fun `should be equal to itself`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                )

            // When & Then
            assertThat(dataset).isEqualTo(dataset)
            assertThat(dataset.hashCode()).isEqualTo(dataset.hashCode())
        }

        @Test
        @DisplayName("should not be equal to null")
        fun `should not be equal to null`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                )

            // When & Then
            assertThat(dataset).isNotEqualTo(null)
        }

        @Test
        @DisplayName("should not be equal to different type")
        fun `should not be equal to different type`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                )

            // When & Then
            assertThat(dataset).isNotEqualTo("not a dataset")
            assertThat(dataset).isNotEqualTo(42)
        }
    }

    @Nested
    @DisplayName("Edge Cases and Constraints")
    inner class EdgeCasesAndConstraints {
        @Test
        @DisplayName("should handle long SQL strings")
        fun `should handle long SQL strings`() {
            // Given
            val longSql = "SELECT " + "column, ".repeat(1000) + "id FROM very_long_table_name"

            // When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = longSql,
                )

            // Then
            assertThat(dataset.sql).isEqualTo(longSql)
        }

        @Test
        @DisplayName("should handle many tags")
        fun `should handle many tags`() {
            // Given
            val manyTags = (1..10).map { "tag$it" }.toSet()

            // When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    tags = manyTags,
                )

            // Then
            assertThat(dataset.tags).hasSize(10)
            assertThat(dataset.tags).containsAll(manyTags)
        }

        @Test
        @DisplayName("should handle many dependencies")
        fun `should handle many dependencies`() {
            // Given
            val manyDependencies = (1..5).map { "table$it" }.toSet()

            // When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table1 JOIN table2 JOIN table3 JOIN table4 JOIN table5",
                    dependencies = manyDependencies,
                )

            // Then
            assertThat(dataset.dependencies).hasSize(5)
            assertThat(dataset.dependencies).containsAll(manyDependencies)
        }

        @Test
        @DisplayName("should handle different timezone formats")
        fun `should handle different timezone formats`() {
            // Given
            val timezones = listOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo", "GMT+05:30")

            timezones.forEach { timezone ->
                // When
                val dataset =
                    DatasetEntity(
                        name = "catalog.schema.dataset",
                        owner = "test@example.com",
                        sql = "SELECT * FROM table",
                        scheduleTimezone = timezone,
                    )

                // Then
                assertThat(dataset.scheduleTimezone).isEqualTo(timezone)
            }
        }

        @Test
        @DisplayName("should handle complex SQL with comments and formatting")
        fun `should handle complex SQL with comments and formatting`() {
            // Given
            val complexSql =
                """
                -- Daily user activity report
                SELECT
                    u.id,
                    u.email,
                    COUNT(a.id) as activity_count,
                    MAX(a.created_at) as last_activity
                FROM users u
                LEFT JOIN activities a ON u.id = a.user_id
                WHERE u.created_at >= '{{start_date}}'
                    AND u.status = 'active'
                GROUP BY u.id, u.email
                HAVING COUNT(a.id) > 0
                ORDER BY last_activity DESC
                """.trimIndent()

            // When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.daily_user_activity",
                    owner = "analyst@example.com",
                    description = "Daily report of user activity with metrics",
                    sql = complexSql,
                    tags = setOf("daily", "users", "activity", "report"),
                    dependencies = setOf("users", "activities"),
                )

            // Then
            assertThat(dataset.sql).isEqualTo(complexSql)
            assertThat(dataset.sql).contains("-- Daily user activity report")
            assertThat(dataset.sql).contains("WHERE u.created_at >= '{{start_date}}'")
        }

        @Test
        @DisplayName("should handle various cron expressions")
        fun `should handle various cron expressions`() {
            // Given
            val cronExpressions =
                listOf(
                    "0 9 * * *", // Daily at 9 AM
                    "0 */6 * * *", // Every 6 hours
                    "0 0 1 * *", // First day of month
                    "0 9 * * 1", // Every Monday at 9 AM
                    "*/30 * * * *", // Every 30 minutes
                    "0 9-17 * * 1-5", // Business hours on weekdays
                )

            cronExpressions.forEach { cron ->
                // When
                val dataset =
                    DatasetEntity(
                        name = "catalog.schema.dataset_$cron".replace(" ", "_").replace("*", "star"),
                        owner = "test@example.com",
                        sql = "SELECT * FROM table",
                        scheduleCron = cron,
                    )

                // Then
                assertThat(dataset.scheduleCron).isEqualTo(cron)
                assertThat(dataset.hasSchedule()).isTrue()
            }
        }

        @Test
        @DisplayName("should handle special characters in descriptions")
        fun `should handle special characters in descriptions`() {
            // Given
            val specialDescription = "Dataset with special chars: @#\$%^&*()_+{}[]|\\:;\"'<>?,./"

            // When
            val dataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT * FROM table",
                    description = specialDescription,
                )

            // Then
            assertThat(dataset.description).isEqualTo(specialDescription)
        }
    }
}
