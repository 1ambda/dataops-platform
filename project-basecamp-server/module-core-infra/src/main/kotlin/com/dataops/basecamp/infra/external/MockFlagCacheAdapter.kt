package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.external.flag.FlagCachePort
import com.dataops.basecamp.domain.projection.flag.FlagTargetWithKeyProjection
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock Feature Flag 캐시 어댑터
 *
 * 개발 및 테스트 환경에서 사용하는 인메모리 캐시 구현체입니다.
 * ConcurrentHashMap을 사용하여 스레드 안전성을 보장합니다.
 */
@Component
class MockFlagCacheAdapter : FlagCachePort {
    private val flagCache = ConcurrentHashMap<String, FlagEntity>()
    private val targetCache = ConcurrentHashMap<String, List<FlagTargetWithKeyProjection>>()

    override fun getFlag(flagKey: String): FlagEntity? = flagCache[flagKey]

    override fun setFlag(
        flagKey: String,
        flag: FlagEntity,
    ) {
        flagCache[flagKey] = flag
    }

    override fun evictFlag(flagKey: String) {
        flagCache.remove(flagKey)
    }

    override fun getTargets(
        subjectType: SubjectType,
        subjectId: Long,
    ): List<FlagTargetWithKeyProjection>? = targetCache[buildTargetKey(subjectType, subjectId)]

    override fun setTargets(
        subjectType: SubjectType,
        subjectId: Long,
        targets: List<FlagTargetWithKeyProjection>,
    ) {
        targetCache[buildTargetKey(subjectType, subjectId)] = targets
    }

    override fun evictTargets(
        subjectType: SubjectType,
        subjectId: Long,
    ) {
        targetCache.remove(buildTargetKey(subjectType, subjectId))
    }

    override fun evictAll() {
        flagCache.clear()
        targetCache.clear()
    }

    private fun buildTargetKey(
        subjectType: SubjectType,
        subjectId: Long,
    ): String = "target:$subjectType:$subjectId"
}
