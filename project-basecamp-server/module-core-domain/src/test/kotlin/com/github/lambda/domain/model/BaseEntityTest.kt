package com.github.lambda.domain.model

import com.github.lambda.domain.entity.BaseEntity
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime

/**
 * BaseEntity 단위 테스트
 *
 * 도메인 모델의 기본 기능들을 테스트합니다.
 */
class BaseEntityTest {
    // Test implementation of BaseEntity
    private class TestEntity : BaseEntity() {
        var name: String = ""
    }

    @Test
    fun `isDeleted should return false when deletedAt is null`() {
        // Given
        val entity = TestEntity()

        // When & Then
        assertThat(entity.isDeleted).isFalse()
        assertThat(entity.deletedAt).isNull()
    }

    @Test
    fun `isDeleted should return true when deletedAt is set`() {
        // Given
        val entity = TestEntity()

        // When
        entity.deletedAt = LocalDateTime.now()

        // Then
        assertThat(entity.isDeleted).isTrue()
        assertThat(entity.deletedAt).isNotNull()
    }

    @Test
    fun `equals should work correctly with same id`() {
        // Given
        val entity1 = TestEntity()
        val entity2 = TestEntity()

        // When both entities have null id
        assertThat(entity1).isNotEqualTo(entity2)

        // When both entities have same id (using reflection to set id)
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity1, 1L)
        idField.set(entity2, 1L)

        // Then
        assertThat(entity1).isEqualTo(entity2)
    }

    @Test
    fun `equals should work correctly with different id`() {
        // Given
        val entity1 = TestEntity()
        val entity2 = TestEntity()

        // When entities have different ids
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity1, 1L)
        idField.set(entity2, 2L)

        // Then
        assertThat(entity1).isNotEqualTo(entity2)
    }

    @Test
    fun `equals should return true for same instance`() {
        // Given
        val entity = TestEntity()

        // When & Then
        assertThat(entity).isEqualTo(entity)
    }

    @Test
    fun `equals should return false for different class`() {
        // Given
        val entity = TestEntity()
        val otherObject = "not an entity"

        // When & Then
        assertThat(entity).isNotEqualTo(otherObject)
    }

    @Test
    fun `hashCode should work correctly`() {
        // Given
        val entity1 = TestEntity()
        val entity2 = TestEntity()

        // When both have null id
        assertThat(entity1.hashCode()).isEqualTo(0)
        assertThat(entity2.hashCode()).isEqualTo(0)

        // When entity has id
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity1, 42L)

        // Then
        assertThat(entity1.hashCode()).isEqualTo(42)
    }

    @Test
    fun `audit fields should be properly initialized`() {
        // Given
        val entity = TestEntity()

        // When & Then
        assertDoesNotThrow {
            assertThat(entity.createdBy).isNull()
            assertThat(entity.updatedBy).isNull()
            assertThat(entity.deletedBy).isNull()
            assertThat(entity.createdAt).isNull()
            assertThat(entity.updatedAt).isNull()
            assertThat(entity.deletedAt).isNull()
        }
    }
}
