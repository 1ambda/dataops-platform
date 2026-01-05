package com.dataops.basecamp.infra.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * QueryDSL 설정 클래스
 * - JPAQueryFactory Bean 등록
 * - QueryDSL 관련 설정
 */
@Configuration
class QueryDslConfig {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    /**
     * JPAQueryFactory Bean 등록
     * QueryDSL을 사용한 타입-안전한 쿼리 작성을 위한 팩토리
     */
    @Bean
    fun jpaQueryFactory(): JPAQueryFactory = JPAQueryFactory(entityManager)
}
