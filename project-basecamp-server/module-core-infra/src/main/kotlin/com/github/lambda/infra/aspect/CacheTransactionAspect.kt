package com.github.lambda.infra.aspect

import com.github.lambda.common.constant.CommonConstants
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationAdapter
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 캐시-트랜잭션 동기화 AOP
 *
 * 트랜잭션 롤백 시 관련 캐시를 자동으로 무효화합니다.
 */
@Aspect
@Component
class CacheTransactionAspect(
    private val cacheManager: CacheManager,
) {
    private val logger = LoggerFactory.getLogger(CacheTransactionAspect::class.java)

    /**
     * @CacheEvict 어노테이션이 있는 메소드 실행 시 트랜잭션 동기화
     */
    @Around("@annotation(org.springframework.cache.annotation.CacheEvict)")
    fun aroundCacheEvict(joinPoint: ProceedingJoinPoint): Any? =
        try {
            val result = joinPoint.proceed()

            // 트랜잭션이 활성화된 경우 롤백 시 캐시 무효화 등록
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronizationAdapter() {
                        override fun afterCompletion(status: Int) {
                            if (status == STATUS_ROLLED_BACK) {
                                // 롤백 시 모든 관련 캐시 무효화
                                evictAllRelatedCaches()
                                logger.warn("Transaction rolled back, evicting all related caches")
                            }
                        }
                    },
                )
            }

            result
        } catch (ex: Exception) {
            // 예외 발생 시에도 캐시 무효화
            evictAllRelatedCaches()
            logger.error("Exception occurred, evicting all related caches", ex)
            throw ex
        }

    /**
     * @CachePut 어노테이션이 있는 메소드의 트랜잭션 동기화
     */
    @Around("@annotation(org.springframework.cache.annotation.CachePut)")
    fun aroundCachePut(joinPoint: ProceedingJoinPoint): Any? =
        try {
            val result = joinPoint.proceed()

            // 트랜잭션 커밋 후에만 캐시 업데이트가 유효하도록 보장
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronizationAdapter() {
                        override fun afterCompletion(status: Int) {
                            if (status == STATUS_ROLLED_BACK) {
                                // 롤백 시 잘못된 캐시 데이터 방지
                                evictAllRelatedCaches()
                                logger.debug("Transaction rolled back, evicted caches to prevent stale data")
                            }
                        }
                    },
                )
            }

            result
        } catch (ex: Exception) {
            evictAllRelatedCaches()
            throw ex
        }

    /**
     * 관련 모든 캐시 무효화
     */
    private fun evictAllRelatedCaches() {
        try {
            // 관련 캐시들을 순서대로 무효화
            val cacheNames =
                listOf(
                    CommonConstants.Cache.DATASET_CACHE,
                    CommonConstants.Cache.USER_CACHE,
                    CommonConstants.Cache.WORKFLOW_CACHE,
                    CommonConstants.Cache.WORKFLOW_STATS_CACHE,
                )

            cacheNames.forEach { cacheName ->
                cacheManager.getCache(cacheName)?.clear()
                logger.debug("Evicted cache: {}", cacheName)
            }
        } catch (ex: Exception) {
            logger.error("Failed to evict caches", ex)
        }
    }

    /**
     * 특정 캐시만 무효화
     */
    private fun evictSpecificCache(
        cacheName: String,
        key: Any?,
    ) {
        try {
            val cache = cacheManager.getCache(cacheName)
            if (key != null) {
                cache?.evict(key)
                logger.debug("Evicted cache key: {} from cache: {}", key, cacheName)
            } else {
                cache?.clear()
                logger.debug("Cleared entire cache: {}", cacheName)
            }
        } catch (ex: Exception) {
            logger.error("Failed to evict cache: {} with key: {}", cacheName, key, ex)
        }
    }
}
