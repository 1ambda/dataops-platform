package com.github.lambda.common.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 날짜/시간 관련 유틸리티
 */
object DateTimeUtils {
    private val DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val UTC_ZONE = ZoneId.of("UTC")
    private val KST_ZONE = ZoneId.of("Asia/Seoul")

    /**
     * 현재 UTC 시간을 반환
     */
    fun nowUtc(): LocalDateTime = LocalDateTime.now(UTC_ZONE)

    /**
     * 현재 KST 시간을 반환
     */
    fun nowKst(): LocalDateTime = LocalDateTime.now(KST_ZONE)

    /**
     * LocalDateTime을 기본 포맷으로 문자열로 변환
     */
    fun format(dateTime: LocalDateTime): String = dateTime.format(DEFAULT_FORMATTER)

    /**
     * Instant를 UTC LocalDateTime으로 변환
     */
    fun toLocalDateTime(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, UTC_ZONE)

    /**
     * LocalDateTime을 Instant로 변환 (UTC 기준)
     */
    fun toInstant(dateTime: LocalDateTime): Instant = dateTime.atZone(UTC_ZONE).toInstant()
}
