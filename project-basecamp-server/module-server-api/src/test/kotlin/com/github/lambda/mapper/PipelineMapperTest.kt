package com.github.lambda.mapper

import com.github.lambda.domain.model.pipeline.PipelineExecution
import com.github.lambda.domain.model.pipeline.PipelineStatistics
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.dto.CreatePipelineRequest
import com.github.lambda.dto.PipelineDto
import com.github.lambda.dto.UpdatePipelineRequest
import com.github.lambda.dto.UpdatePipelineStatusRequest
import com.github.lambda.security.FieldAccessControl
import com.github.lambda.security.MaskingLevel
import com.github.lambda.security.SecurityLevel
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

/**
 * PipelineMapper 단위 테스트
 *
 * DTO와 Domain 객체 간의 변환 로직과 보안 제어를 검증합니다.
 */
@DisplayName("PipelineMapper 테스트")
class PipelineMapperTest {
    private val fieldAccessControl = mockk<FieldAccessControl>()
    private lateinit var pipelineMapper: PipelineMapper

    private lateinit var testPipelineDto: PipelineDto
    private lateinit var testCreateRequest: CreatePipelineRequest
    private lateinit var testUpdateRequest: UpdatePipelineRequest

    @BeforeEach
    fun setUp() {
        pipelineMapper = PipelineMapper(fieldAccessControl)

        testPipelineDto =
            PipelineDto(
                id = 1L,
                name = "test-pipeline",
                description = "Test description",
                status = PipelineStatus.ACTIVE,
                owner = "test-owner",
                scheduleExpression = "0 0 * * *",
                isActive = true,
                jobCount = 5,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testCreateRequest =
            CreatePipelineRequest(
                name = "new-pipeline",
                description = "New description",
                owner = "new-owner",
                scheduleExpression = "0 0 12 * * ?",
                isActive = true,
            )

        testUpdateRequest =
            UpdatePipelineRequest(
                name = "updated-pipeline",
                description = "Updated description",
                scheduleExpression = "0 0 6 * * ?",
            )
    }

    @Test
    @DisplayName("CreatePipelineRequest를 CreatePipelineCommand로 변환할 수 있다")
    fun `should convert CreatePipelineRequest to CreatePipelineCommand`() {
        // When
        val command = pipelineMapper.toCommand(testCreateRequest)

        // Then
        assert(command.name == testCreateRequest.name)
        assert(command.description == testCreateRequest.description)
        assert(command.owner == testCreateRequest.owner)
        assert(command.scheduleExpression == testCreateRequest.scheduleExpression)
        assert(command.isActive == testCreateRequest.isActive)
    }

    @Test
    @DisplayName("UpdatePipelineRequest를 UpdatePipelineCommand로 변환할 수 있다")
    fun `should convert UpdatePipelineRequest to UpdatePipelineCommand`() {
        // When
        val command = pipelineMapper.toCommand(1L, testUpdateRequest)

        // Then
        assert(command.id == 1L)
        assert(command.name == testUpdateRequest.name)
        assert(command.description == testUpdateRequest.description)
        assert(command.scheduleExpression == testUpdateRequest.scheduleExpression)
    }

    @Test
    @DisplayName("상태 변경 요청을 UpdatePipelineStatusCommand로 변환할 수 있다")
    fun `should convert status request to UpdatePipelineStatusCommand`() {
        // Given
        val request = UpdatePipelineStatusRequest(PipelineStatus.PAUSED)

        // When
        val command = pipelineMapper.toCommand(1L, request)

        // Then
        assert(command.id == 1L)
        assert(command.status == PipelineStatus.PAUSED)
    }

    @Test
    @DisplayName("파이프라인 실행 명령을 생성할 수 있다")
    fun `should create ExecutePipelineCommand`() {
        // Given
        val parameters = mapOf("param1" to "value1", "param2" to "value2")

        // When
        val command = pipelineMapper.toExecuteCommand(1L, parameters)

        // Then
        assert(command.id == 1L)
        assert(command.parameters == parameters)
    }

    @Test
    @DisplayName("쿼리 파라미터를 GetPipelinesQuery로 변환할 수 있다")
    fun `should convert query parameters to GetPipelinesQuery`() {
        // Given
        val pageable = PageRequest.of(0, 20)

        // When
        val query = pipelineMapper.toQuery("test-owner", PipelineStatus.ACTIVE, true, pageable)

        // Then
        assert(query.owner == "test-owner")
        assert(query.status == PipelineStatus.ACTIVE)
        assert(query.isActive == true)
        assert(query.pageable == pageable)
        assert(!query.includeDeleted)
    }

    @Test
    @DisplayName("PipelineDto를 PipelineResponse로 변환할 수 있다")
    fun `should convert PipelineDto to PipelineResponse`() {
        // When
        val response = pipelineMapper.toResponse(testPipelineDto)

        // Then
        assert(response.id == testPipelineDto.id)
        assert(response.name == testPipelineDto.name)
        assert(response.description == testPipelineDto.description)
        assert(response.status == testPipelineDto.status)
        assert(response.owner == testPipelineDto.owner)
        assert(response.scheduleExpression == testPipelineDto.scheduleExpression)
        assert(response.isActive == testPipelineDto.isActive)
        assert(response.jobCount == testPipelineDto.jobCount)
        assert(response.createdAt == testPipelineDto.createdAt)
        assert(response.updatedAt == testPipelineDto.updatedAt)
    }

    @Test
    @DisplayName("ADMIN 보안 레벨에서는 모든 정보가 노출된다")
    fun `should expose all information for ADMIN security level`() {
        // Given
        every { fieldAccessControl.getCurrentUserSecurityLevel() } returns SecurityLevel.ADMIN
        every { fieldAccessControl.canAccessField("scheduleExpression") } returns true
        every { fieldAccessControl.getMaskingLevel("description") } returns MaskingLevel.NONE
        every { fieldAccessControl.getMaskingLevel("owner") } returns MaskingLevel.NONE
        every { fieldAccessControl.maskData("Test description", MaskingLevel.NONE) } returns "Test description"
        every { fieldAccessControl.maskData("test-owner", MaskingLevel.NONE) } returns "test-owner"

        // When
        val response = pipelineMapper.toSecureResponse(testPipelineDto)

        // Then
        assert(response.owner == "test-owner")
        assert(response.description == "Test description")
        assert(response.scheduleExpression == "0 0 * * *")
    }

    @Test
    @DisplayName("INTERNAL 보안 레벨에서는 일부 정보가 마스킹된다")
    fun `should mask some information for INTERNAL security level`() {
        // Given
        every { fieldAccessControl.getCurrentUserSecurityLevel() } returns SecurityLevel.INTERNAL
        every { fieldAccessControl.canAccessField("scheduleExpression") } returns true
        every { fieldAccessControl.canAccessField("owner", "test-owner") } returns true
        every { fieldAccessControl.getMaskingLevel("description") } returns MaskingLevel.NONE
        every { fieldAccessControl.getMaskingLevel("owner") } returns MaskingLevel.PARTIAL
        every { fieldAccessControl.maskData("Test description", MaskingLevel.NONE) } returns "Test description"
        every { fieldAccessControl.maskData("test-owner", MaskingLevel.PARTIAL) } returns "tes*****"

        // When
        val response = pipelineMapper.toSecureResponse(testPipelineDto, SecurityLevel.INTERNAL)

        // Then
        assert(response.owner == "tes*****")
        assert(response.description == "Test description")
        assert(response.scheduleExpression == "0 0 * * *")
    }

    @Test
    @DisplayName("PUBLIC 보안 레벨에서는 민감한 정보가 제거된다")
    fun `should remove sensitive information for PUBLIC security level`() {
        // Given
        every { fieldAccessControl.canAccessField("scheduleExpression") } returns false
        every { fieldAccessControl.canAccessField("owner", "test-owner") } returns false
        every { fieldAccessControl.getMaskingLevel("description") } returns MaskingLevel.PARTIAL
        every { fieldAccessControl.maskData("Test description", MaskingLevel.PARTIAL) } returns "Tes***********"

        // When
        val response = pipelineMapper.toSecureResponse(testPipelineDto, SecurityLevel.PUBLIC)

        // Then
        assert(response.owner == null)
        assert(response.description == "Tes***********")
        assert(response.scheduleExpression == null)
    }

    @Test
    @DisplayName("PipelineExecution을 PipelineExecutionResponse로 변환할 수 있다")
    fun `should convert PipelineExecution to PipelineExecutionResponse`() {
        // Given
        val execution =
            PipelineExecution(
                executionId = "exec-12345",
                pipelineId = 1L,
                pipelineName = "test-pipeline",
                status = "STARTED",
                startedAt = LocalDateTime.now(),
                message = "Pipeline started",
            )

        // When
        val response = pipelineMapper.toResponse(execution)

        // Then
        assert(response.executionId == execution.executionId)
        assert(response.pipelineId == execution.pipelineId)
        assert(response.pipelineName == execution.pipelineName)
        assert(response.status == execution.status)
        assert(response.startedAt == execution.startedAt)
        assert(response.message == execution.message)
    }

    @Test
    @DisplayName("삭제 명령을 생성할 수 있다")
    fun `should create delete command`() {
        // When
        val command = pipelineMapper.toDeleteCommand(1L, "admin-user", "Test deletion")

        // Then
        assert(command.id == 1L)
        assert(command.deletedBy == "admin-user")
        assert(command.reason == "Test deletion")
    }

    @Test
    @DisplayName("실행 중지 명령을 생성할 수 있다")
    fun `should create stop command`() {
        // When
        val command = pipelineMapper.toStopCommand(1L, "exec-12345", "User requested")

        // Then
        assert(command.pipelineId == 1L)
        assert(command.executionId == "exec-12345")
        assert(command.reason == "User requested")
    }

    @Test
    @DisplayName("통계 쿼리를 생성할 수 있다")
    fun `should create statistics query`() {
        // When
        val query = pipelineMapper.toStatisticsQuery("test-owner")

        // Then
        assert(query.owner == "test-owner")
        assert(query.includeJobCounts)
        assert(query.includeStatusCounts)
    }

    @Test
    @DisplayName("통계 도메인 객체를 응답 Map으로 변환할 수 있다")
    fun `should convert statistics to response map`() {
        // Given
        val statistics =
            PipelineStatistics(
                totalPipelines = 100L,
                activePipelines = 80L,
                runningPipelines = 10L,
                pausedPipelines = 5L,
                failedPipelines = 5L,
                totalJobs = 500L,
                averageJobsPerPipeline = 5.0,
                statusBreakdown = mapOf(PipelineStatus.ACTIVE to 80L, PipelineStatus.PAUSED to 20L),
                ownerBreakdown = emptyList(),
            )

        // When
        val response = pipelineMapper.toStatisticsResponse(statistics, SecurityLevel.INTERNAL)

        // Then
        assert(response["totalPipelines"] == 100L)
        assert(response["activePipelines"] == 80L)
        assert(response["averageJobsPerPipeline"] == 5.0)
        assert(!response.containsKey("ownerBreakdown")) // Not ADMIN level
    }

    @Test
    @DisplayName("ADMIN 레벨에서는 통계에 소유자 정보가 포함된다")
    fun `should include owner breakdown in statistics for ADMIN level`() {
        // Given
        val statistics =
            PipelineStatistics(
                totalPipelines = 100L,
                activePipelines = 80L,
                runningPipelines = 10L,
                pausedPipelines = 5L,
                failedPipelines = 5L,
                totalJobs = 500L,
                averageJobsPerPipeline = 5.0,
                statusBreakdown = mapOf(PipelineStatus.ACTIVE to 80L),
                ownerBreakdown = emptyList(),
            )

        // When
        val response = pipelineMapper.toStatisticsResponse(statistics, SecurityLevel.ADMIN)

        // Then
        assert(response.containsKey("ownerBreakdown")) // ADMIN level includes owner info
    }
}
