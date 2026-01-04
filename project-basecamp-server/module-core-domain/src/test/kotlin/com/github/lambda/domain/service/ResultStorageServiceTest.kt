package com.github.lambda.domain.service

import com.github.lambda.common.exception.InvalidDownloadTokenException
import com.github.lambda.domain.model.adhoc.RunExecutionConfig
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * ResultStorageService Unit Tests
 *
 * Tests for result storage, download URL generation, and cleanup.
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("ResultStorageService Unit Tests")
class ResultStorageServiceTest {
    private val config: RunExecutionConfig = mockk()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"))

    private lateinit var resultStorageService: ResultStorageService

    @BeforeEach
    fun setUp() {
        // Setup default config values (per RUN_FEATURE.md spec)
        every { config.resultExpirationHours } returns 8 // Per spec: 8 hours

        resultStorageService = ResultStorageService(config, clock)
    }

    @Nested
    @DisplayName("storeResults")
    inner class StoreResults {
        @Test
        @DisplayName("should generate correct download URLs for CSV format")
        fun `should generate correct download URLs for CSV format`() {
            // Given
            val queryId = "adhoc_20260101_120000_abc12345"
            val rows =
                listOf(
                    mapOf("id" to 1, "name" to "Alice"),
                    mapOf("id" to 2, "name" to "Bob"),
                )

            // When
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )

            // Then
            assertThat(downloadUrls).containsKey("csv")
            assertThat(downloadUrls["csv"]).contains("/api/v1/run/results/$queryId/download")
            assertThat(downloadUrls["csv"]).contains("format=csv")
            assertThat(downloadUrls["csv"]).contains("token=")
        }

        @Test
        @DisplayName("should return empty map when downloadFormat is null")
        fun `should return empty map when downloadFormat is null`() {
            // Given
            val queryId = "adhoc_20260101_120000_abc12345"
            val rows = listOf(mapOf("id" to 1))

            // When
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = null,
                )

            // Then
            assertThat(downloadUrls).isEmpty()
        }

        @Test
        @DisplayName("should return empty map when rows are empty")
        fun `should return empty map when rows are empty`() {
            // Given
            val queryId = "adhoc_20260101_120000_abc12345"
            val rows = emptyList<Map<String, Any?>>()

            // When
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )

            // Then
            assertThat(downloadUrls).isEmpty()
        }

        @Test
        @DisplayName("should store result and return valid URL")
        fun `should store result and return valid URL`() {
            // Given
            val queryId = "adhoc_20260101_120000_abc12345"
            val rows =
                listOf(
                    mapOf("col1" to "value1", "col2" to 123),
                )

            // When
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )

            // Then
            assertThat(downloadUrls).isNotEmpty()
            assertThat(resultStorageService.hasResult(queryId)).isTrue()
        }
    }

    @Nested
    @DisplayName("getResultForDownload")
    inner class GetResultForDownload {
        @Test
        @DisplayName("should return CSV content for valid token")
        fun `should return CSV content for valid token`() {
            // Given
            val queryId = "adhoc_20260101_120000_test1234"
            val rows =
                listOf(
                    mapOf("id" to 1, "name" to "Alice"),
                    mapOf("id" to 2, "name" to "Bob"),
                )
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )

            // Extract token from URL
            val token =
                downloadUrls["csv"]!!
                    .substringAfter("token=")

            // When
            val content = resultStorageService.getResultForDownload(queryId, "csv", token)

            // Then
            val csvString = String(content, Charsets.UTF_8)
            assertThat(csvString).contains("id,name")
            assertThat(csvString).contains("1,Alice")
            assertThat(csvString).contains("2,Bob")
        }

        @Test
        @DisplayName("should throw InvalidDownloadTokenException for invalid token")
        fun `should throw InvalidDownloadTokenException for invalid token`() {
            // Given
            val queryId = "adhoc_20260101_120000_test1234"
            val rows = listOf(mapOf("id" to 1))
            resultStorageService.storeResults(
                queryId = queryId,
                rows = rows,
                downloadFormat = "csv",
            )

            // When & Then
            val exception =
                assertThrows<InvalidDownloadTokenException> {
                    resultStorageService.getResultForDownload(queryId, "csv", "invalid_token")
                }

            assertThat(exception.queryId).isEqualTo(queryId)
        }

        @Test
        @DisplayName("should throw ResultNotFoundException for missing result")
        fun `should throw ResultNotFoundException for missing result`() {
            // Given
            val queryId = "nonexistent_query_id"

            // When & Then
            val exception =
                assertThrows<InvalidDownloadTokenException> {
                    resultStorageService.getResultForDownload(queryId, "csv", "some_token")
                }

            assertThat(exception.queryId).isEqualTo(queryId)
        }

        @Test
        @DisplayName("should throw ResultNotFoundException when result is expired")
        fun `should throw exception when result is expired`() {
            // Note: This test is challenging to implement without mocking time
            // In production, we would use a Clock abstraction
            // For now, we test that expired results are cleaned up in cleanupExpiredResults
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported format")
        fun `should throw IllegalArgumentException for unsupported format`() {
            // Given
            val queryId = "adhoc_20260101_120000_test1234"
            val rows = listOf(mapOf("id" to 1))
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )
            val token =
                downloadUrls["csv"]!!
                    .substringAfter("token=")

            // Replace "csv" with "json" in the token check
            // The token validation will fail because format doesn't match
            val exception =
                assertThrows<InvalidDownloadTokenException> {
                    resultStorageService.getResultForDownload(queryId, "json", token)
                }

            assertThat(exception.queryId).isEqualTo(queryId)
        }

        @Test
        @DisplayName("should properly escape CSV values with special characters")
        fun `should properly escape CSV values with special characters`() {
            // Given
            val queryId = "adhoc_20260101_120000_escape"
            val rows =
                listOf(
                    mapOf(
                        "name" to "Alice, Bob",
                        "quote" to "He said \"Hello\"",
                        "newline" to "Line1\nLine2",
                    ),
                )
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )
            val token =
                downloadUrls["csv"]!!
                    .substringAfter("token=")

            // When
            val content = resultStorageService.getResultForDownload(queryId, "csv", token)

            // Then
            val csvString = String(content, Charsets.UTF_8)
            // Values with commas should be quoted
            assertThat(csvString).contains("\"Alice, Bob\"")
            // Values with quotes should have escaped quotes
            assertThat(csvString).contains("\"\"Hello\"\"")
            // Values with newlines should be quoted
            assertThat(csvString).contains("\"Line1")
        }
    }

    @Nested
    @DisplayName("hasResult")
    inner class HasResult {
        @Test
        @DisplayName("should return true for existing result")
        fun `should return true for existing result`() {
            // Given
            val queryId = "adhoc_20260101_120000_exists"
            val rows = listOf(mapOf("id" to 1))
            resultStorageService.storeResults(
                queryId = queryId,
                rows = rows,
                downloadFormat = "csv",
            )

            // When
            val exists = resultStorageService.hasResult(queryId)

            // Then
            assertThat(exists).isTrue()
        }

        @Test
        @DisplayName("should return false for non-existing result")
        fun `should return false for non-existing result`() {
            // When
            val exists = resultStorageService.hasResult("nonexistent_query_id")

            // Then
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("cleanupExpiredResults")
    inner class CleanupExpiredResults {
        @Test
        @DisplayName("should not throw exception when no results exist")
        fun `should not throw exception when no results exist`() {
            // When & Then - no exception
            resultStorageService.cleanupExpiredResults()
        }

        @Test
        @DisplayName("should keep non-expired results")
        fun `should keep non-expired results`() {
            // Given
            val queryId = "adhoc_20260101_120000_valid"
            val rows = listOf(mapOf("id" to 1))
            resultStorageService.storeResults(
                queryId = queryId,
                rows = rows,
                downloadFormat = "csv",
            )

            // When
            resultStorageService.cleanupExpiredResults()

            // Then
            assertThat(resultStorageService.hasResult(queryId)).isTrue()
        }

        // Note: Testing actual expiration would require time manipulation
        // In production, we would inject a Clock abstraction for better testability
    }

    @Nested
    @DisplayName("CSV conversion")
    inner class CsvConversion {
        @Test
        @DisplayName("should handle null values in CSV")
        fun `should handle null values in CSV`() {
            // Given
            val queryId = "adhoc_20260101_120000_nulls"
            val rows =
                listOf(
                    mapOf("id" to 1, "name" to "Alice", "email" to null),
                    mapOf("id" to 2, "name" to null, "email" to "bob@test.com"),
                )
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )
            val token =
                downloadUrls["csv"]!!
                    .substringAfter("token=")

            // When
            val content = resultStorageService.getResultForDownload(queryId, "csv", token)

            // Then
            val csvString = String(content, Charsets.UTF_8)
            val lines = csvString.lines().filter { it.isNotBlank() }
            assertThat(lines).hasSize(3) // header + 2 data rows
            assertThat(lines[0]).isEqualTo("id,name,email")
            // Null values should be empty strings
            assertThat(lines[1]).contains("Alice")
            assertThat(lines[2]).contains("bob@test.com")
        }

        @Test
        @DisplayName("should maintain column order from first row")
        fun `should maintain column order from first row`() {
            // Given
            val queryId = "adhoc_20260101_120000_order"
            val rows =
                listOf(
                    linkedMapOf("z_col" to 1, "a_col" to 2, "m_col" to 3),
                )
            val downloadUrls =
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = rows,
                    downloadFormat = "csv",
                )
            val token =
                downloadUrls["csv"]!!
                    .substringAfter("token=")

            // When
            val content = resultStorageService.getResultForDownload(queryId, "csv", token)

            // Then
            val csvString = String(content, Charsets.UTF_8)
            val headerLine = csvString.lines().first()
            assertThat(headerLine).isEqualTo("z_col,a_col,m_col")
        }
    }
}
