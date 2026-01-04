package com.github.lambda.infra.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.lambda.common.constant.CommonConstants
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * 캐시 설정 클래스
 *
 * Redis 기반 캐싱 전략:
 * - Dataset: 중간 TTL (30분) - 메타데이터 변경이 가끔
 * - User: 긴 TTL (1시간) - 사용자 정보는 자주 변경되지 않음
 * - Workflow: 짧은 TTL (5분) - 실행 상태 변경이 빈번
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfiguration {
    /**
     * ObjectMapper 빈 설정 (Jackson 자동 구성이 없는 경우를 위해)
     */
    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    /**
     * Redis Template 설정
     */
    @Bean
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisTemplate<String, Any> =
        RedisTemplate<String, Any>().apply {
            this.connectionFactory = connectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
            afterPropertiesSet()
        }

    /**
     * 캐시 동기화를 위한 Redis Cache Manager
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisCacheManager {
        val defaultConfig =
            RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(CommonConstants.Cache.DEFAULT_TTL_MINUTES))
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()),
                ).serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJackson2JsonRedisSerializer(objectMapper),
                    ),
                ).disableCachingNullValues()

        // 개별 캐시 설정
        val cacheConfigurations =
            mapOf(
                "pipeline" to defaultConfig.entryTtl(Duration.ofMinutes(60)), // 1시간
                "job" to defaultConfig.entryTtl(Duration.ofMinutes(5)), // 5분
                "dataset" to defaultConfig.entryTtl(Duration.ofMinutes(30)), // 30분
                "pipeline-statistics" to defaultConfig.entryTtl(Duration.ofMinutes(15)), // 15분
                "job-statistics" to defaultConfig.entryTtl(Duration.ofMinutes(10)), // 10분
                "pipeline-readonly" to defaultConfig.entryTtl(Duration.ofHours(2)), // 2시간
            )

        return RedisCacheManager
            .builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware() // 트랜잭션 인식 캐시
            .build()
    }
}

/**
 * 캐시 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "app.cache")
data class CacheProperties(
    val defaultTtl: Long = CommonConstants.Cache.DEFAULT_TTL_MINUTES,
    val shortTtl: Long = CommonConstants.Cache.SHORT_TTL_MINUTES,
    val longTtl: Long = CommonConstants.Cache.LONG_TTL_MINUTES,
    val maxSize: Long = 10000,
    val enableStatistics: Boolean = true,
)
