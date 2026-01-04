package com.github.lambda.domain.service

import com.github.lambda.common.exception.InvalidDownloadTokenException
import com.github.lambda.common.exception.ResultNotFoundException
import com.github.lambda.domain.model.adhoc.RunExecutionConfig
import com.github.lambda.domain.projection.run.StoredResultProjection
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 결과 저장 서비스
 *
 * Ad-Hoc 쿼리 실행 결과를 임시 저장하고 다운로드를 관리합니다.
 * MVP에서는 인메모리 저장소를 사용하며, 추후 S3/GCS로 확장 가능합니다.
 *
 * Testability: Uses injected Clock for time operations and configurable secret.
 */
@Service
class ResultStorageService(
    private val config: RunExecutionConfig,
    private val clock: Clock,
    @Value("\${run.download-token-secret:basecamp-run-api-secret-key-2026}")
    private val tokenSecret: String = "basecamp-run-api-secret-key-2026",
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // In-memory storage for MVP - queryId -> StoredResultProjection
    private val resultStorage = ConcurrentHashMap<String, StoredResultProjection>()

    /**
     * 결과 저장 및 다운로드 URL 생성
     *
     * @param queryId 쿼리 ID
     * @param rows 결과 행 데이터
     * @param downloadFormat 요청된 다운로드 형식 (null이면 URL 생성 안 함)
     * @return 다운로드 URL 맵 (format -> URL)
     */
    fun storeResults(
        queryId: String,
        rows: List<Map<String, Any?>>,
        downloadFormat: String?,
    ): Map<String, String> {
        if (downloadFormat == null || rows.isEmpty()) {
            return emptyMap()
        }

        val expiresAt = LocalDateTime.now(clock).plusHours(config.resultExpirationHours.toLong())
        val expiresAtInstant = clock.instant().plusSeconds(config.resultExpirationHours * 3600L)

        // Convert to CSV bytes
        val csvContent = convertToCsv(rows)

        // Store the result
        val storedResult =
            StoredResultProjection(
                queryId = queryId,
                csvContent = csvContent,
                rowCount = rows.size,
                expiresAt = expiresAtInstant,
            )
        resultStorage[queryId] = storedResult

        logger.info(
            "Stored result for queryId: {}, rows: {}, size: {} bytes, expiresAt: {}",
            queryId,
            rows.size,
            csvContent.size,
            expiresAt,
        )

        // Generate download URL with signed token
        val token = generateDownloadToken(queryId, "csv", expiresAtInstant)
        val downloadUrls =
            mapOf(
                "csv" to "/api/v1/run/results/$queryId/download?format=csv&token=$token",
            )

        return downloadUrls
    }

    /**
     * 다운로드용 결과 조회
     *
     * @param queryId 쿼리 ID
     * @param format 다운로드 형식 (현재 csv만 지원)
     * @param token 다운로드 토큰
     * @return CSV 바이트 배열
     * @throws ResultNotFoundException 결과가 없거나 만료된 경우
     * @throws InvalidDownloadTokenException 토큰이 유효하지 않은 경우
     */
    fun getResultForDownload(
        queryId: String,
        format: String,
        token: String,
    ): ByteArray {
        // Validate token
        if (!validateDownloadToken(queryId, format, token)) {
            logger.warn("Invalid download token for queryId: {}", queryId)
            throw InvalidDownloadTokenException(queryId)
        }

        // Get stored result
        val storedResult =
            resultStorage[queryId]
                ?: run {
                    logger.warn("Result not found for queryId: {}", queryId)
                    throw ResultNotFoundException(queryId)
                }

        // Check expiration
        if (storedResult.expiresAt.isBefore(clock.instant())) {
            resultStorage.remove(queryId)
            logger.warn("Result expired for queryId: {}", queryId)
            throw ResultNotFoundException(queryId)
        }

        logger.info("Serving download for queryId: {}, format: {}", queryId, format)

        return when (format.lowercase()) {
            "csv" -> storedResult.csvContent
            else -> throw IllegalArgumentException("Unsupported format: $format. Only 'csv' is supported.")
        }
    }

    /**
     * 결과 존재 여부 확인
     */
    fun hasResult(queryId: String): Boolean {
        val result = resultStorage[queryId] ?: return false
        if (result.expiresAt.isBefore(clock.instant())) {
            resultStorage.remove(queryId)
            return false
        }
        return true
    }

    /**
     * 만료된 결과 정리 (1시간마다 실행)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    fun cleanupExpiredResults() {
        val now = clock.instant()
        val expiredCount =
            resultStorage.entries
                .filter { it.value.expiresAt.isBefore(now) }
                .onEach { resultStorage.remove(it.key) }
                .count()

        if (expiredCount > 0) {
            logger.info("Cleaned up {} expired results", expiredCount)
        }
    }

    // === Private Helper Methods ===

    /**
     * 결과를 CSV 형식으로 변환
     */
    private fun convertToCsv(rows: List<Map<String, Any?>>): ByteArray {
        if (rows.isEmpty()) return ByteArray(0)

        val headers = rows.first().keys.toList()
        val sb = StringBuilder()

        // Header row
        sb.appendLine(headers.joinToString(",") { escapeCsvValue(it) })

        // Data rows
        rows.forEach { row ->
            val line =
                headers
                    .map { header ->
                        escapeCsvValue(row[header]?.toString() ?: "")
                    }.joinToString(",")
            sb.appendLine(line)
        }

        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * CSV 값 이스케이프
     */
    private fun escapeCsvValue(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    /**
     * 다운로드 토큰 생성 (HMAC-SHA256)
     */
    private fun generateDownloadToken(
        queryId: String,
        format: String,
        expiresAt: Instant,
    ): String {
        val payload = "$queryId:$format:${expiresAt.epochSecond}"
        val signature = hmacSha256(payload, tokenSecret)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            "$payload:$signature".toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * 다운로드 토큰 검증
     */
    private fun validateDownloadToken(
        queryId: String,
        format: String,
        token: String,
    ): Boolean {
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
            val parts = decoded.split(":")
            if (parts.size != 4) return false

            val (tokenQueryId, tokenFormat, expiresAtStr, signature) = parts

            // Check query ID and format match
            if (tokenQueryId != queryId || tokenFormat != format) return false

            // Check expiration
            val expiresAt = Instant.ofEpochSecond(expiresAtStr.toLong())
            if (expiresAt.isBefore(clock.instant())) return false

            // Verify signature
            val payload = "$tokenQueryId:$tokenFormat:$expiresAtStr"
            val expectedSignature = hmacSha256(payload, tokenSecret)
            signature == expectedSignature
        } catch (e: Exception) {
            logger.debug("Token validation failed: {}", e.message)
            false
        }
    }

    /**
     * HMAC-SHA256 계산
     */
    private fun hmacSha256(
        data: String,
        secret: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
