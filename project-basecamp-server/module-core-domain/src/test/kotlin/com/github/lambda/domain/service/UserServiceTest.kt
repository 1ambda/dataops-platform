package com.github.lambda.domain.service

import com.github.lambda.common.enums.UserRole
import com.github.lambda.common.exception.BusinessRuleViolationException
import com.github.lambda.domain.entity.user.UserEntity
import com.github.lambda.domain.model.user.UserAggregate
import com.github.lambda.domain.repository.user.UserAuthorityRepositoryJpa
import com.github.lambda.domain.repository.user.UserRepositoryDsl
import com.github.lambda.domain.repository.user.UserRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * UserService 비즈니스 로직 단위 테스트
 *
 * MockK를 사용하여 의존성을 격리하고 순수한 비즈니스 로직을 테스트합니다.
 * Note: No Spring context needed - pure unit test with MockK
 */
@DisplayName("UserService 비즈니스 로직 테스트")
class UserServiceTest {
    private val userRepositoryJpa: UserRepositoryJpa = mockk()
    private val userRepositoryDsl: UserRepositoryDsl = mockk()
    private val userAuthorityRepository: UserAuthorityRepositoryJpa = mockk()
    private val userService = UserService(userRepositoryJpa, userRepositoryDsl, userAuthorityRepository)

    @Test
    @DisplayName("이메일로 사용자 조회 성공 시 사용자를 반환해야 한다")
    fun `should return user when finding by email succeeds`() {
        // Given
        val email = "test@example.com"
        val expectedUser =
            UserEntity(
                username = "test",
                email = email,
                role = UserRole.PUBLIC,
            )
        every { userRepositoryJpa.findByEmail(email) } returns expectedUser

        // When
        val result = userService.findByEmailOrThrow(email)

        // Then
        assertThat(result).isEqualTo(expectedUser)
        verify(exactly = 1) { userRepositoryJpa.findByEmail(email) }
    }

    @Test
    @DisplayName("이메일로 사용자 조회 실패 시 BusinessRuleViolationException을 던져야 한다")
    fun `should throw BusinessRuleViolationException when user not found by email`() {
        // Given
        val email = "nonexistent@example.com"
        every { userRepositoryJpa.findByEmail(email) } returns null

        // When & Then
        val exception =
            assertThrows<BusinessRuleViolationException> {
                userService.findByEmailOrThrow(email)
            }

        assertThat(exception.message).isEqualTo("Business rule violation: User not found")
        verify(exactly = 1) { userRepositoryJpa.findByEmail(email) }
    }

    @Test
    @DisplayName("OIDC 추가 정보 동기화가 올바르게 작동해야 한다")
    fun `should sync OIDC additional information correctly`() {
        // Given
        val email = "newuser@example.com"
        val userEntity = mockk<UserEntity>(relaxed = true)
        val userAggregate = mockk<UserAggregate>()
        val savedUserSlot = slot<UserEntity>()

        every { userAggregate.getUser() } returns userEntity
        every { userRepositoryJpa.save(capture(savedUserSlot)) } returns userEntity

        // When
        userService.syncOidcAdditional(userAggregate, email)

        // Then
        verify(exactly = 1) { userEntity.sync(email) }
        verify(exactly = 1) { userRepositoryJpa.save(userEntity) }
    }

    @Test
    @DisplayName("이메일로 사용자 집합체 조회 성공 시 UserAggregate를 반환해야 한다")
    fun `should return UserAggregate when finding aggregate by email succeeds`() {
        // Given
        val email = "test@example.com"
        val expectedAggregate = mockk<UserAggregate>()
        every { userRepositoryDsl.findAggregateByEmail(email) } returns expectedAggregate

        // When
        val result = userService.findAggregateByEmailOrThrow(email)

        // Then
        assertThat(result).isEqualTo(expectedAggregate)
        verify(exactly = 1) { userRepositoryDsl.findAggregateByEmail(email) }
    }

    @Test
    @DisplayName("이메일로 사용자 집합체 조회 실패 시 BusinessRuleViolationException을 던져야 한다")
    fun `should throw BusinessRuleViolationException when aggregate not found by email`() {
        // Given
        val email = "nonexistent@example.com"
        every { userRepositoryDsl.findAggregateByEmail(email) } returns null

        // When & Then
        val exception =
            assertThrows<BusinessRuleViolationException> {
                userService.findAggregateByEmailOrThrow(email)
            }

        assertThat(exception.message).isEqualTo("Business rule violation: User not found")
        verify(exactly = 1) { userRepositoryDsl.findAggregateByEmail(email) }
    }

    @Test
    @DisplayName("빈 이메일로 사용자 조회 시 적절한 동작을 해야 한다")
    fun `should handle empty email appropriately`() {
        // Given
        val emptyEmail = ""
        every { userRepositoryJpa.findByEmail(emptyEmail) } returns null

        // When & Then
        val exception =
            assertThrows<BusinessRuleViolationException> {
                userService.findByEmailOrThrow(emptyEmail)
            }

        assertThat(exception.message).isEqualTo("Business rule violation: User not found")
        verify(exactly = 1) { userRepositoryJpa.findByEmail(emptyEmail) }
    }

    @Test
    @DisplayName("null 이메일로 사용자 조회 시 NullPointerException이 발생해야 한다")
    fun `should handle null email gracefully`() {
        // Given
        val nullEmail: String? = null

        // When & Then - Kotlin null safety에 의해 컴파일 타임에 방지되지만,
        // 런타임에 null이 전달될 수 있는 상황을 테스트
        assertThrows<NullPointerException> {
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            userService.findByEmailOrThrow(nullEmail!!)
        }
    }
}
