package com.dataops.basecamp.common.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 테스트 유틸리티 클래스
 *
 * 모든 테스트에서 공통으로 사용할 수 있는 유틸리티 메서드를 제공합니다.
 */
object TestUtils {
    /**
     * 테스트용 ObjectMapper
     */
    val objectMapper: ObjectMapper =
        ObjectMapper().apply {
            registerKotlinModule()
            findAndRegisterModules()
        }

    /**
     * 객체를 JSON 문자열로 변환
     */
    fun toJson(obj: Any): String = objectMapper.writeValueAsString(obj)

    /**
     * JSON 문자열을 객체로 변환
     */
    inline fun <reified T> fromJson(json: String): T = objectMapper.readValue(json, T::class.java)

    /**
     * 현재 시간을 ISO 8601 형식 문자열로 반환
     */
    fun nowAsIsoString(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * 테스트용 고정 시간 문자열
     */
    const val FIXED_TIME_STRING = "2024-01-15T10:30:00"

    /**
     * 테스트용 고정 시간
     */
    val FIXED_TIME: LocalDateTime = LocalDateTime.parse(FIXED_TIME_STRING)

    /**
     * 랜덤 문자열 생성
     */
    fun randomString(length: Int = 10): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * 테스트용 이메일 생성
     */
    fun randomEmail(): String = "${randomString(8)}@test.com"

    /**
     * SQL 쿼리 문자열 정규화 (공백과 줄바꿈 제거)
     */
    fun normalizeSql(sql: String): String = sql.replace(Regex("\\s+"), " ").trim()

    /**
     * 두 LocalDateTime이 초 단위까지 같은지 비교
     */
    fun isEqualToSecond(
        time1: LocalDateTime?,
        time2: LocalDateTime?,
    ): Boolean {
        if (time1 == null && time2 == null) return true
        if (time1 == null || time2 == null) return false

        return time1.withNano(0) == time2.withNano(0)
    }
}
