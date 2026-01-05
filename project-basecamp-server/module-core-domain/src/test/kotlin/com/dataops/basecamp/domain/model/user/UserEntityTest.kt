package com.dataops.basecamp.domain.model.user

import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.domain.entity.user.UserEntity
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime

/**
 * UserEntity 도메인 모델 단위 테스트
 *
 * 이 테스트는 UserEntity의 도메인 로직과 비즈니스 규칙을 검증합니다.
 * JPA 관련 기능은 별도의 repository 테스트에서 다룹니다.
 */
@DisplayName("UserEntity 도메인 모델 테스트")
class UserEntityTest {
    @Test
    @DisplayName("사용자 엔티티가 기본값으로 올바르게 생성되어야 한다")
    fun `should create user entity with default values`() {
        // Given & When
        val user = UserEntity()

        // Then
        assertThat(user.username).isEqualTo("")
        assertThat(user.email).isEqualTo("")
        assertThat(user.role).isEqualTo(UserRole.PUBLIC)
        assertThat(user.password).isNull()
        assertThat(user.enabled).isTrue()
        assertThat(user.lastActiveAt).isNull()
    }

    @Test
    @DisplayName("사용자 엔티티가 생성자 파라미터로 올바르게 생성되어야 한다")
    fun `should create user entity with constructor parameters`() {
        // Given
        val username = "testuser"
        val email = "test@example.com"
        val role = UserRole.ADMIN
        val password = "encodedPassword"
        val enabled = true
        val lastActiveAt = LocalDateTime.now()

        // When
        val user =
            UserEntity(
                username = username,
                email = email,
                role = role,
                password = password,
                enabled = enabled,
                lastActiveAt = lastActiveAt,
            )

        // Then
        assertThat(user.username).isEqualTo(username)
        assertThat(user.email).isEqualTo(email)
        assertThat(user.role).isEqualTo(role)
        assertThat(user.password).isEqualTo(password)
        assertThat(user.enabled).isEqualTo(enabled)
        assertThat(user.lastActiveAt).isEqualTo(lastActiveAt)
    }

    @ParameterizedTest(name = "이메일 {0}으로부터 사용자명 {1}이 올바르게 추출되어야 한다")
    @CsvSource(
        value = [
            "john.doe@example.com, john.doe",
            "admin@company.org, admin",
            "user123@test.io, user123",
            "first.last+tag@domain.com, first.last+tag",
        ],
    )
    @DisplayName("이메일로부터 사용자명 동기화가 올바르게 작동해야 한다")
    fun `should sync username from email correctly`(
        email: String,
        expectedUsername: String,
    ) {
        // Given
        val user = UserEntity()

        // When
        user.sync(email)

        // Then
        assertThat(user.username).isEqualTo(expectedUsername)
    }

    @Test
    @DisplayName("비즈니스 규칙: 활성화된 사용자는 유효한 이메일을 가져야 한다")
    fun `should validate business rule - enabled user must have valid email`() {
        // Given
        val user =
            UserEntity(
                username = "testuser",
                email = "test@example.com",
                enabled = true,
            )

        // When & Then
        assertThat(user.enabled).isTrue()
        assertThat(user.email).isNotEmpty()
        assertThat(user.email).contains("@")
    }

    @Test
    @DisplayName("사용자 역할 변경이 올바르게 작동해야 한다")
    fun `should change user role correctly`() {
        // Given
        val user = UserEntity(role = UserRole.PUBLIC)
        assertThat(user.role).isEqualTo(UserRole.PUBLIC)

        // When
        user.role = UserRole.ADMIN

        // Then
        assertThat(user.role).isEqualTo(UserRole.ADMIN)
    }

    @Test
    @DisplayName("마지막 활성 시간 업데이트가 올바르게 작동해야 한다")
    fun `should update last active time correctly`() {
        // Given
        val user = UserEntity()
        val now = LocalDateTime.now()

        // When
        user.lastActiveAt = now

        // Then
        assertThat(user.lastActiveAt).isEqualTo(now)
    }

    @Test
    @DisplayName("비밀번호 설정과 제거가 올바르게 작동해야 한다")
    fun `should set and remove password correctly`() {
        // Given
        val user = UserEntity()
        val password = "encodedPassword123"

        // When - 비밀번호 설정
        user.password = password

        // Then
        assertThat(user.password).isEqualTo(password)

        // When - 비밀번호 제거
        user.password = null

        // Then
        assertThat(user.password).isNull()
    }

    @Test
    @DisplayName("사용자 활성화/비활성화가 올바르게 작동해야 한다")
    fun `should enable and disable user correctly`() {
        // Given
        val user = UserEntity(enabled = true)

        // When - 비활성화
        user.enabled = false

        // Then
        assertThat(user.enabled).isFalse()

        // When - 활성화
        user.enabled = true

        // Then
        assertThat(user.enabled).isTrue()
    }
}
