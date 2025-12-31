package com.github.lambda.controller

import tools.jackson.databind.json.JsonMapper
import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.command.pipeline.CreatePipelineCommand
import com.github.lambda.domain.command.pipeline.DeletePipelineCommand
import com.github.lambda.domain.command.pipeline.ExecutePipelineCommand
import com.github.lambda.domain.command.pipeline.UpdatePipelineCommand
import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineExecution
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.domain.query.pipeline.GetPipelineQuery
import com.github.lambda.domain.query.pipeline.GetPipelinesQuery
import com.github.lambda.domain.service.PipelineService
import com.github.lambda.dto.CreatePipelineRequest
import com.github.lambda.dto.PipelineDto
import com.github.lambda.dto.PipelineExecutionResponse
import com.github.lambda.dto.PipelineResponse
import com.github.lambda.dto.UpdatePipelineRequest
import com.github.lambda.mapper.PipelineMapper
import com.github.lambda.security.FieldAccessControl
import com.github.lambda.security.SecurityLevel
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * PipelineController REST API 테스트
 *
 * Spring Boot 4.x 패턴:
 * - @SpringBootTest + @AutoConfigureMockMvc: 통합 테스트 (multi-module 프로젝트 호환)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 호환)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class PipelineControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @MockkBean(relaxed = true)
    private lateinit var pipelineService: PipelineService

    @MockkBean(relaxed = true)
    private lateinit var pipelineMapper: PipelineMapper

    @MockkBean(relaxed = true)
    private lateinit var fieldAccessControl: FieldAccessControl

    private lateinit var testPipelineDto: PipelineDto
    private lateinit var testPipelineEntity: PipelineEntity
    private lateinit var testPipelineResponse: PipelineResponse

    @BeforeEach
    fun setUp() {
        testPipelineDto =
            PipelineDto(
                id = 1L,
                name = "test-pipeline",
                description = "Test pipeline description",
                status = PipelineStatus.ACTIVE,
                owner = "test-owner",
                scheduleExpression = "0 0 * * *",
                isActive = true,
                jobCount = 0,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testPipelineEntity =
            PipelineEntity(
                name = "test-pipeline",
                description = "Test pipeline description",
                status = PipelineStatus.ACTIVE,
                owner = "test-owner",
                scheduleExpression = "0 0 * * *",
                isActive = true,
            )

        testPipelineResponse =
            PipelineResponse(
                id = 1L,
                name = "test-pipeline",
                description = "Test pipeline description",
                status = PipelineStatus.ACTIVE,
                owner = "test-owner",
                scheduleExpression = "0 0 * * *",
                isActive = true,
                jobCount = 0,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
    }

    @Nested
    @DisplayName("GET /api/v1/pipelines")
    inner class GetPipelines {
        @Test
        @DisplayName("파이프라인 목록을 조회할 수 있다")
        fun `should return pipeline list with proper mapping`() {
            // Given
            val querySlot = slot<GetPipelinesQuery>()
            val pipelinePage = PageImpl(listOf(testPipelineEntity))

            every { pipelineMapper.toQuery(any(), any(), any(), any()) } returns
                GetPipelinesQuery(pageable = PageRequest.of(0, 20))
            every { pipelineService.getPipelines(capture(querySlot)) } returns pipelinePage
            every { pipelineMapper.toResponse(testPipelineEntity) } returns testPipelineResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/pipelines"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("test-pipeline"))

            verify { pipelineMapper.toQuery(null, null, null, any()) }
            verify { pipelineService.getPipelines(any()) }
            verify { pipelineMapper.toResponse(testPipelineEntity) }
        }

        @Test
        @DisplayName("필터 조건을 적용하여 파이프라인을 조회할 수 있다")
        fun `should return filtered pipelines with query mapping`() {
            // Given
            val querySlot = slot<GetPipelinesQuery>()
            val pipelinePage = PageImpl(listOf(testPipelineEntity))

            every {
                pipelineMapper.toQuery("test-owner", PipelineStatus.ACTIVE, true, any())
            } returns
                GetPipelinesQuery(
                    owner = "test-owner",
                    status = PipelineStatus.ACTIVE,
                    isActive = true,
                    pageable = PageRequest.of(0, 10),
                )
            every { pipelineService.getPipelines(capture(querySlot)) } returns pipelinePage
            every { pipelineMapper.toResponse(testPipelineEntity) } returns testPipelineResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/pipelines")
                        .param("owner", "test-owner")
                        .param("status", "ACTIVE")
                        .param("isActive", "true")
                        .param("page", "0")
                        .param("size", "10"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))

            verify { pipelineMapper.toQuery("test-owner", PipelineStatus.ACTIVE, true, any()) }
            verify { pipelineService.getPipelines(any()) }

            // Verify query mapping
            val capturedQuery = querySlot.captured
            assert(capturedQuery.owner == "test-owner")
            assert(capturedQuery.status == PipelineStatus.ACTIVE)
            assert(capturedQuery.isActive == true)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/pipelines/{id}")
    inner class GetPipeline {
        @Test
        @DisplayName("ID로 파이프라인을 조회할 수 있다")
        fun `should return pipeline by id with proper mapping`() {
            // Given
            val querySlot = slot<GetPipelineQuery>()
            every { pipelineMapper.toQuery(1L, false) } returns GetPipelineQuery(id = 1L)
            every { pipelineService.getPipeline(capture(querySlot)) } returns testPipelineEntity
            every { pipelineMapper.toResponse(testPipelineEntity) } returns testPipelineResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/pipelines/1"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("test-pipeline"))

            verify { pipelineMapper.toQuery(1L, false) }
            verify { pipelineService.getPipeline(any()) }
            verify { pipelineMapper.toResponse(testPipelineEntity) }

            // Verify query structure
            val capturedQuery = querySlot.captured
            assert(capturedQuery.id == 1L)
            assert(!capturedQuery.includeJobs)
        }

        @Test
        @DisplayName("존재하지 않는 파이프라인 조회시 404를 반환한다")
        fun `should return 404 when pipeline not found`() {
            // Given
            every { pipelineMapper.toQuery(999L, false) } returns GetPipelineQuery(id = 999L)
            every { pipelineService.getPipeline(any()) } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/pipelines/999"))
                .andExpect(status().isNotFound())

            verify { pipelineService.getPipeline(any()) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/pipelines")
    inner class CreatePipeline {
        @Test
        @DisplayName("유효한 데이터로 파이프라인을 생성할 수 있다")
        fun `should create pipeline with command mapping`() {
            // Given
            val request =
                CreatePipelineRequest(
                    name = "new-pipeline",
                    description = "New pipeline description",
                    owner = "new-owner",
                    scheduleExpression = "0 0 12 * * ?",
                    isActive = true,
                )

            val commandSlot = slot<CreatePipelineCommand>()
            every { pipelineMapper.toCommand(request) } returns
                CreatePipelineCommand(
                    name = request.name,
                    description = request.description,
                    owner = request.owner,
                    scheduleExpression = request.scheduleExpression,
                    isActive = request.isActive,
                )
            every { pipelineService.createPipeline(capture(commandSlot)) } returns testPipelineEntity
            every { pipelineMapper.toResponse(testPipelineEntity) } returns testPipelineResponse

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/pipelines")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())

            verify { pipelineMapper.toCommand(request) }
            verify { pipelineService.createPipeline(any()) }
            verify { pipelineMapper.toResponse(testPipelineEntity) }

            // Verify command structure
            val capturedCommand = commandSlot.captured
            assert(capturedCommand.name == "new-pipeline")
            assert(capturedCommand.description == "New pipeline description")
            assert(capturedCommand.owner == "new-owner")
        }

        @Test
        @DisplayName("필수 필드 누락시 400 에러를 반환한다")
        fun `should return 400 when required fields are missing`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "",
                    "owner" to "",
                    "isActive" to true,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/pipelines")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/pipelines/{id}")
    inner class UpdatePipeline {
        @Test
        @DisplayName("파이프라인을 수정할 수 있다")
        fun `should update pipeline with command mapping`() {
            // Given
            val request =
                UpdatePipelineRequest(
                    name = "updated-pipeline",
                    description = "Updated description",
                    scheduleExpression = "0 0 6 * * ?",
                )

            val commandSlot = slot<UpdatePipelineCommand>()
            every { pipelineMapper.toCommand(1L, request) } returns
                UpdatePipelineCommand(
                    id = 1L,
                    name = request.name,
                    description = request.description,
                    scheduleExpression = request.scheduleExpression,
                )
            every { pipelineService.updatePipeline(capture(commandSlot)) } returns testPipelineEntity
            every { pipelineMapper.toResponse(testPipelineEntity) } returns testPipelineResponse

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/pipelines/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))

            verify { pipelineMapper.toCommand(1L, request) }
            verify { pipelineService.updatePipeline(any()) }

            // Verify command structure
            val capturedCommand = commandSlot.captured
            assert(capturedCommand.id == 1L)
            assert(capturedCommand.name == "updated-pipeline")
        }
    }

    @Nested
    @DisplayName("POST /api/v1/pipelines/{id}/execute")
    inner class ExecutePipeline {
        @Test
        @DisplayName("파이프라인을 실행할 수 있다")
        fun `should execute pipeline with command mapping`() {
            // Given
            val parameters = mapOf("param1" to "value1")
            val commandSlot = slot<ExecutePipelineCommand>()
            val execution =
                PipelineExecution(
                    executionId = "exec-12345",
                    pipelineId = 1L,
                    pipelineName = "test-pipeline",
                    status = "STARTED",
                    startedAt = LocalDateTime.now(),
                    message = "파이프라인 실행이 시작되었습니다.",
                    parameters = parameters,
                )

            every { pipelineMapper.toExecuteCommand(1L, parameters) } returns
                ExecutePipelineCommand(
                    id = 1L,
                    parameters = parameters,
                )
            every { pipelineService.executePipeline(capture(commandSlot)) } returns execution
            every { pipelineMapper.toResponse(execution) } returns
                PipelineExecutionResponse(
                    executionId = "exec-12345",
                    pipelineId = 1L,
                    pipelineName = "test-pipeline",
                    status = "STARTED",
                    startedAt = LocalDateTime.now(),
                    message = "파이프라인 실행이 시작되었습니다.",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/pipelines/1/execute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(parameters)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.executionId").value("exec-12345"))

            verify { pipelineMapper.toExecuteCommand(1L, parameters) }
            verify { pipelineService.executePipeline(any()) }
        }
    }

    @Nested
    @DisplayName("Security Tests")
    inner class SecurityTests {
        @Test
        @WithMockUser(roles = ["ADMIN"])
        @DisplayName("관리자는 모든 정보에 접근할 수 있다")
        fun `admin should access all information`() {
            // Given
            every { fieldAccessControl.getCurrentUserSecurityLevel() } returns SecurityLevel.ADMIN
            every { pipelineMapper.toQuery(1L, false) } returns GetPipelineQuery(id = 1L)
            every { pipelineService.getPipeline(any()) } returns testPipelineEntity
            every { pipelineMapper.toSecureResponse(testPipelineEntity, SecurityLevel.PUBLIC) } returns testPipelineResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/pipelines/public/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.owner").value("test-owner"))
                .andExpect(jsonPath("$.data.scheduleExpression").value("0 0 * * *"))

            verify { pipelineMapper.toSecureResponse(testPipelineEntity, SecurityLevel.PUBLIC) }
        }

        @Test
        @WithMockUser(roles = ["USER"])
        @DisplayName("일반 사용자는 제한된 정보에만 접근할 수 있다")
        fun `regular user should access limited information`() {
            // Given
            val maskedResponse =
                testPipelineResponse.copy(
                    owner = "tes***",
                    scheduleExpression = "0 0 * * *",
                )

            every { fieldAccessControl.getCurrentUserSecurityLevel() } returns SecurityLevel.INTERNAL
            every { pipelineMapper.toQuery(1L, false) } returns GetPipelineQuery(id = 1L)
            every { pipelineService.getPipeline(any()) } returns testPipelineEntity
            every { pipelineMapper.toSecureResponse(testPipelineEntity, SecurityLevel.PUBLIC) } returns maskedResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/pipelines/public/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.owner").value("tes***"))

            verify { pipelineMapper.toSecureResponse(testPipelineEntity, SecurityLevel.PUBLIC) }
        }

        @Test
        @DisplayName("공개 API는 민감한 정보를 노출하지 않는다")
        fun `public API should not expose sensitive information`() {
            // Given
            val publicResponse =
                testPipelineResponse.copy(
                    owner = null,
                    scheduleExpression = null,
                )

            every { pipelineMapper.toQuery(1L, false) } returns GetPipelineQuery(id = 1L)
            every { pipelineService.getPipeline(any()) } returns testPipelineEntity
            every { pipelineMapper.toSecureResponse(testPipelineEntity, SecurityLevel.PUBLIC) } returns publicResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/pipelines/public/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.owner").doesNotExist())
                .andExpect(jsonPath("$.data.scheduleExpression").doesNotExist())

            verify { pipelineMapper.toSecureResponse(testPipelineEntity, SecurityLevel.PUBLIC) }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        @DisplayName("전체 생성-조회-수정-삭제 플로우가 정상 작동한다")
        fun `should handle complete CRUD flow`() {
            // Given
            val createRequest =
                CreatePipelineRequest(
                    name = "integration-test-pipeline",
                    description = "Integration test description",
                    owner = "test-user",
                )

            val updateRequest =
                UpdatePipelineRequest(
                    name = "updated-integration-pipeline",
                    description = "Updated description",
                )

            // Mock all the necessary calls
            every { pipelineMapper.toCommand(createRequest) } returns
                CreatePipelineCommand(
                    name = createRequest.name,
                    description = createRequest.description,
                    owner = createRequest.owner,
                    scheduleExpression = "0 0 * * *",
                )
            every { pipelineService.createPipeline(any()) } returns testPipelineEntity
            every { pipelineMapper.toResponse(testPipelineEntity) } returns testPipelineResponse

            every { pipelineMapper.toCommand(1L, updateRequest) } returns
                UpdatePipelineCommand(
                    id = 1L,
                    name = updateRequest.name,
                    description = updateRequest.description,
                    scheduleExpression = "0 0 6 * * ?",
                )
            every { pipelineService.updatePipeline(any()) } returns testPipelineEntity

            every { pipelineMapper.toDeleteCommand(1L, "system", null) } returns
                DeletePipelineCommand(
                    id = 1L,
                    deletedBy = "system",
                )
            every { pipelineService.deletePipeline(any()) } returns true

            // When & Then - Create
            mockMvc
                .perform(
                    post("/api/v1/pipelines")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)

            // When & Then - Update
            mockMvc
                .perform(
                    put("/api/v1/pipelines/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)),
                ).andExpect(status().isOk)

            // When & Then - Delete
            mockMvc
                .perform(
                    delete("/api/v1/pipelines/1")
                        .with(csrf()),
                ).andExpect(status().isNoContent)

            verify { pipelineService.createPipeline(any()) }
            verify { pipelineService.updatePipeline(any()) }
            verify { pipelineService.deletePipeline(any()) }
        }
    }
}
