package com.dataops.basecamp.domain.repository.flag

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.domain.entity.flag.FlagEntity

/**
 * Flag Repository DSL 인터페이스 (복합 쿼리용)
 *
 * Flag에 대한 복합 쿼리 작업을 정의합니다.
 */
interface FlagRepositoryDsl {
    /**
     * 상태별 Flag 조회
     */
    fun findByStatus(status: FlagStatus): List<FlagEntity>

    /**
     * 검색 조건으로 Flag 조회
     */
    fun findBySearch(search: String): List<FlagEntity>
}
