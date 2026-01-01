package com.github.lambda.domain.repository

import com.github.lambda.domain.model.dataset.DatasetEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Dataset Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Dataset에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface DatasetRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 충돌하지 않는 메서드들)
    fun save(dataset: DatasetEntity): DatasetEntity

    // 도메인 특화 조회 메서드 - 이름 기반
    fun findByName(name: String): DatasetEntity?

    fun existsByName(name: String): Boolean

    fun deleteByName(name: String): Long

    // 소유자 기반 조회
    fun findByOwner(owner: String): List<DatasetEntity>

    fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<DatasetEntity>

    // 태그 기반 조회 (기본적인 단일 태그 포함 검사)
    fun findByTagsContaining(tag: String): List<DatasetEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<DatasetEntity>

    // 통계 및 집계
    fun countByOwner(owner: String): Long

    fun count(): Long

    // 이름 패턴 검색 (단순 LIKE 검색)
    fun findByNameContainingIgnoreCase(namePattern: String): List<DatasetEntity>

    fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<DatasetEntity>
}
