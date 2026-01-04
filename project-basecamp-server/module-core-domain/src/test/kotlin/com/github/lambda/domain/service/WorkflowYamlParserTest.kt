package com.github.lambda.domain.service

import com.github.lambda.domain.model.workflow.WorkflowScheduleSpec
import com.github.lambda.domain.model.workflow.WorkflowSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * WorkflowYamlParser Unit Tests
 *
 * Tests for parsing and serializing workflow YAML specifications.
 */
@DisplayName("WorkflowYamlParser Unit Tests")
class WorkflowYamlParserTest {
    private lateinit var parser: WorkflowYamlParser

    @BeforeEach
    fun setUp() {
        parser = WorkflowYamlParser()
    }

    @Nested
    @DisplayName("parse")
    inner class Parse {
        @Test
        @DisplayName("should parse valid YAML content successfully")
        fun `should parse valid YAML content successfully`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset_name
                owner: user@example.com
                team: data-platform
                description: Daily user activity aggregation
                schedule:
                  cron: "0 0 * * *"
                  timezone: Asia/Seoul
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isTrue()
            val spec = result.getOrNull()
            assertThat(spec).isNotNull
            assertThat(spec?.name).isEqualTo("catalog.schema.dataset_name")
            assertThat(spec?.owner).isEqualTo("user@example.com")
            assertThat(spec?.team).isEqualTo("data-platform")
            assertThat(spec?.description).isEqualTo("Daily user activity aggregation")
            assertThat(spec?.schedule?.cron).isEqualTo("0 0 * * *")
            assertThat(spec?.schedule?.timezone).isEqualTo("Asia/Seoul")
        }

        @Test
        @DisplayName("should parse minimal valid YAML with only required fields")
        fun `should parse minimal valid YAML with only required fields`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: user@example.com
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isTrue()
            val spec = result.getOrNull()
            assertThat(spec).isNotNull
            assertThat(spec?.name).isEqualTo("catalog.schema.dataset")
            assertThat(spec?.owner).isEqualTo("user@example.com")
            assertThat(spec?.team).isNull()
            assertThat(spec?.description).isNull()
            assertThat(spec?.schedule).isNull()
        }

        @Test
        @DisplayName("should fail for empty YAML content")
        fun `should fail for empty YAML content`() {
            // Given
            val yamlContent = ""

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
            assertThat(result.errorsOrEmpty()).contains("YAML content is empty")
        }

        @Test
        @DisplayName("should fail for blank YAML content")
        fun `should fail for blank YAML content`() {
            // Given
            val yamlContent = "   \n  \t  "

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
            assertThat(result.errorsOrEmpty()).contains("YAML content is empty")
        }

        @Test
        @DisplayName("should fail for invalid YAML syntax")
        fun `should fail for invalid YAML syntax`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: [invalid
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
            assertThat(result.errorsOrEmpty().first()).contains("Failed to parse YAML")
        }

        @Test
        @DisplayName("should fail when name is missing")
        fun `should fail when name is missing`() {
            // Given
            val yamlContent =
                """
                owner: user@example.com
                team: data-platform
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
        }

        @Test
        @DisplayName("should fail when name format is invalid")
        fun `should fail when name format is invalid`() {
            // Given
            val yamlContent =
                """
                name: invalid_name_without_dots
                owner: user@example.com
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
            assertThat(result.errorsOrEmpty().any { it.contains("catalog.schema.name") }).isTrue()
        }

        @Test
        @DisplayName("should fail when owner is missing")
        fun `should fail when owner is missing`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                team: data-platform
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
        }

        @Test
        @DisplayName("should fail when owner is not a valid email")
        fun `should fail when owner is not a valid email`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: not_an_email
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
            assertThat(result.errorsOrEmpty().any { it.contains("email") }).isTrue()
        }

        @Test
        @DisplayName("should parse YAML with metadata")
        fun `should parse YAML with metadata`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: user@example.com
                metadata:
                  tags:
                    - production
                    - user-data
                  priority: high
                """.trimIndent()

            // When
            val result = parser.parse(yamlContent)

            // Then
            assertThat(result.isSuccess()).isTrue()
            val spec = result.getOrNull()
            assertThat(spec?.metadata).isNotNull
            assertThat(spec?.metadata?.get("priority")).isEqualTo("high")
        }
    }

    @Nested
    @DisplayName("parseWithoutValidation")
    inner class ParseWithoutValidation {
        @Test
        @DisplayName("should parse YAML without validation errors")
        fun `should parse YAML without validation errors`() {
            // Given - invalid name format but should parse
            val yamlContent =
                """
                name: invalid_name
                owner: not_an_email
                """.trimIndent()

            // When
            val result = parser.parseWithoutValidation(yamlContent)

            // Then
            assertThat(result.isSuccess()).isTrue()
            val spec = result.getOrNull()
            assertThat(spec?.name).isEqualTo("invalid_name")
            assertThat(spec?.owner).isEqualTo("not_an_email")
        }

        @Test
        @DisplayName("should still fail for invalid YAML syntax")
        fun `should still fail for invalid YAML syntax`() {
            // Given
            val yamlContent =
                """
                name: [invalid
                """.trimIndent()

            // When
            val result = parser.parseWithoutValidation(yamlContent)

            // Then
            assertThat(result.isSuccess()).isFalse()
            assertThat(result.errorsOrEmpty().first()).contains("Failed to parse YAML")
        }
    }

    @Nested
    @DisplayName("serialize")
    inner class Serialize {
        @Test
        @DisplayName("should serialize WorkflowSpec to YAML")
        fun `should serialize WorkflowSpec to YAML`() {
            // Given
            val spec =
                WorkflowSpec(
                    name = "catalog.schema.dataset",
                    owner = "user@example.com",
                    team = "data-platform",
                    description = "Test workflow",
                    schedule =
                        WorkflowScheduleSpec(
                            cron = "0 8 * * *",
                            timezone = "UTC",
                        ),
                )

            // When
            val yaml = parser.serialize(spec)

            // Then
            assertThat(yaml).contains("name:")
            assertThat(yaml).contains("catalog.schema.dataset")
            assertThat(yaml).contains("owner:")
            assertThat(yaml).contains("user@example.com")
            assertThat(yaml).contains("team:")
            assertThat(yaml).contains("data-platform")
            assertThat(yaml).contains("schedule:")
            assertThat(yaml).contains("cron:")
        }

        @Test
        @DisplayName("should serialize and parse back to same spec")
        fun `should serialize and parse back to same spec`() {
            // Given
            val originalSpec =
                WorkflowSpec(
                    name = "catalog.schema.dataset",
                    owner = "user@example.com",
                    team = "data-platform",
                    description = "Test workflow",
                    schedule =
                        WorkflowScheduleSpec(
                            cron = "0 8 * * *",
                            timezone = "UTC",
                        ),
                )

            // When
            val yaml = parser.serialize(originalSpec)
            val parseResult = parser.parse(yaml)

            // Then
            assertThat(parseResult.isSuccess()).isTrue()
            val parsedSpec = parseResult.getOrNull()
            assertThat(parsedSpec?.name).isEqualTo(originalSpec.name)
            assertThat(parsedSpec?.owner).isEqualTo(originalSpec.owner)
            assertThat(parsedSpec?.team).isEqualTo(originalSpec.team)
            assertThat(parsedSpec?.description).isEqualTo(originalSpec.description)
            assertThat(parsedSpec?.schedule?.cron).isEqualTo(originalSpec.schedule?.cron)
            assertThat(parsedSpec?.schedule?.timezone).isEqualTo(originalSpec.schedule?.timezone)
        }
    }

    @Nested
    @DisplayName("isValidYaml")
    inner class IsValidYaml {
        @Test
        @DisplayName("should return true for valid YAML")
        fun `should return true for valid YAML`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: user@example.com
                """.trimIndent()

            // When
            val result = parser.isValidYaml(yamlContent)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false for invalid YAML syntax")
        fun `should return false for invalid YAML syntax`() {
            // Given
            val yamlContent =
                """
                name: [invalid
                """.trimIndent()

            // When
            val result = parser.isValidYaml(yamlContent)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for empty content")
        fun `should return false for empty content`() {
            // Given
            val yamlContent = ""

            // When
            val result = parser.isValidYaml(yamlContent)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for blank content")
        fun `should return false for blank content`() {
            // Given
            val yamlContent = "   \n  \t  "

            // When
            val result = parser.isValidYaml(yamlContent)

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("extractName")
    inner class ExtractName {
        @Test
        @DisplayName("should extract name from valid YAML")
        fun `should extract name from valid YAML`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: user@example.com
                """.trimIndent()

            // When
            val name = parser.extractName(yamlContent)

            // Then
            assertThat(name).isEqualTo("catalog.schema.dataset")
        }

        @Test
        @DisplayName("should return null for invalid YAML")
        fun `should return null for invalid YAML`() {
            // Given
            val yamlContent = "invalid: [yaml"

            // When
            val name = parser.extractName(yamlContent)

            // Then
            assertThat(name).isNull()
        }

        @Test
        @DisplayName("should return null for blank name")
        fun `should return null for blank name`() {
            // Given
            val yamlContent =
                """
                name: ""
                owner: user@example.com
                """.trimIndent()

            // When
            val name = parser.extractName(yamlContent)

            // Then
            assertThat(name).isNull()
        }
    }

    @Nested
    @DisplayName("extractOwner")
    inner class ExtractOwner {
        @Test
        @DisplayName("should extract owner from valid YAML")
        fun `should extract owner from valid YAML`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: user@example.com
                """.trimIndent()

            // When
            val owner = parser.extractOwner(yamlContent)

            // Then
            assertThat(owner).isEqualTo("user@example.com")
        }

        @Test
        @DisplayName("should return null for invalid YAML")
        fun `should return null for invalid YAML`() {
            // Given
            val yamlContent = "invalid: [yaml"

            // When
            val owner = parser.extractOwner(yamlContent)

            // Then
            assertThat(owner).isNull()
        }

        @Test
        @DisplayName("should return null for blank owner")
        fun `should return null for blank owner`() {
            // Given
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: ""
                """.trimIndent()

            // When
            val owner = parser.extractOwner(yamlContent)

            // Then
            assertThat(owner).isNull()
        }
    }
}
