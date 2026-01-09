package com.dataops.basecamp.aspect

import com.dataops.basecamp.annotation.RequireFlag
import com.dataops.basecamp.common.exception.FlagDisabledException
import com.dataops.basecamp.domain.service.FlagService
import com.dataops.basecamp.util.SecurityContext
import mu.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

/**
 * Feature Flag 검증 Aspect
 *
 * @RequireFlag 어노테이션이 적용된 메서드나 클래스에 대해
 * Feature Flag 활성화 여부를 검증합니다.
 */
@Aspect
@Component
class FlagAspect(
    private val flagService: FlagService,
    private val securityContext: SecurityContext,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * @RequireFlag 어노테이션이 적용된 메서드 검증
     */
    @Around("@annotation(requireFlag)")
    fun checkFlag(
        joinPoint: ProceedingJoinPoint,
        requireFlag: RequireFlag,
    ): Any? {
        val userId = securityContext.getCurrentUserIdOrThrow()
        val flagKey = requireFlag.key

        logger.debug { "Checking feature flag '$flagKey' for user $userId" }

        if (!flagService.isEnabled(flagKey, userId)) {
            logger.warn { "Feature flag '$flagKey' is disabled for user $userId" }
            throw FlagDisabledException(
                flagKey = flagKey,
                message = requireFlag.fallbackMessage,
            )
        }

        logger.debug { "Feature flag '$flagKey' is enabled for user $userId, proceeding with method execution" }
        return joinPoint.proceed()
    }

    /**
     * @RequireFlag 어노테이션이 클래스에 적용된 경우 모든 메서드 검증
     */
    @Around("@within(requireFlag)")
    fun checkFlagOnClass(
        joinPoint: ProceedingJoinPoint,
        requireFlag: RequireFlag,
    ): Any? = checkFlag(joinPoint, requireFlag)
}
