package com.github.lambda.domain.external

import com.github.lambda.domain.model.quality.GenerateSqlRequest
import com.github.lambda.domain.model.quality.GenerateSqlResponse

/**
 * Quality Rule Engine 클라이언트 인터페이스 (Domain Port)
 *
 * 외부 Quality Rule Engine 시스템과의 통신을 위한 도메인 포트 인터페이스
 */
interface QualityRuleEngineClient {
    /**
     * Quality 테스트 SQL 생성
     *
     * @param request SQL 생성 요청
     * @return 생성된 SQL 응답
     */
    fun generateSql(request: GenerateSqlRequest): GenerateSqlResponse

    /**
     * Quality Rule Engine 서버 연결 상태 확인
     *
     * @return 연결 가능 여부
     */
    fun isAvailable(): Boolean = true
}
