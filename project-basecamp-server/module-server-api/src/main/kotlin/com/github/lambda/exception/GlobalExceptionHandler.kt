package com.github.lambda.exception

import com.github.lambda.common.exception.BusinessException
import com.github.lambda.common.exception.CatalogServiceException
import com.github.lambda.common.exception.CatalogTimeoutException
import com.github.lambda.common.exception.DatasetAlreadyExistsException
import com.github.lambda.common.exception.DatasetExecutionFailedException
import com.github.lambda.common.exception.DatasetExecutionTimeoutException
import com.github.lambda.common.exception.DatasetNotFoundException
import com.github.lambda.common.exception.InvalidCronException
import com.github.lambda.common.exception.InvalidDatasetNameException
import com.github.lambda.common.exception.InvalidDownloadTokenException
import com.github.lambda.common.exception.InvalidOwnerEmailException
import com.github.lambda.common.exception.InvalidSqlException
import com.github.lambda.common.exception.InvalidTableReferenceException
import com.github.lambda.common.exception.MetricAlreadyExistsException
import com.github.lambda.common.exception.MetricExecutionTimeoutException
import com.github.lambda.common.exception.MetricNotFoundException
import com.github.lambda.common.exception.QualityRunNotFoundException
import com.github.lambda.common.exception.QualitySpecAlreadyExistsException
import com.github.lambda.common.exception.QualitySpecNotFoundException
import com.github.lambda.common.exception.QueryExecutionTimeoutException
import com.github.lambda.common.exception.QueryTooLargeException
import com.github.lambda.common.exception.RateLimitExceededException
import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.common.exception.ResultNotFoundException
import com.github.lambda.common.exception.ResultSizeLimitExceededException
import com.github.lambda.common.exception.TableNotFoundException
import com.github.lambda.common.exception.TooManyTagsException
import com.github.lambda.common.exception.WorkflowAlreadyExistsException
import com.github.lambda.common.exception.WorkflowNotFoundException
import com.github.lambda.common.exception.WorkflowRunNotFoundException
import com.github.lambda.domain.service.AccessDeniedException
import com.github.lambda.domain.service.QueryNotCancellableException
import com.github.lambda.domain.service.QueryNotFoundException
import com.github.lambda.dto.ApiResponse
import com.github.lambda.dto.ErrorDetails
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

/**
 * 전역 예외 처리기
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger {}

    // === Metric-specific exception handlers ===

    /**
     * Metric not found exception (404)
     */
    @ExceptionHandler(MetricNotFoundException::class)
    fun handleMetricNotFoundException(
        ex: MetricNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Metric not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Metric not found", errorDetails))
    }

    /**
     * Metric already exists exception (409 Conflict)
     */
    @ExceptionHandler(MetricAlreadyExistsException::class)
    fun handleMetricAlreadyExistsException(
        ex: MetricAlreadyExistsException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Metric already exists: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message ?: "Metric already exists", errorDetails))
    }

    /**
     * Metric execution timeout exception (408 Request Timeout)
     */
    @ExceptionHandler(MetricExecutionTimeoutException::class)
    fun handleMetricExecutionTimeoutException(
        ex: MetricExecutionTimeoutException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Metric execution timeout: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.REQUEST_TIMEOUT)
            .body(ApiResponse.error(ex.message ?: "Metric execution timeout", errorDetails))
    }

    // === Quality-specific exception handlers ===

    /**
     * Quality spec not found exception (404)
     */
    @ExceptionHandler(QualitySpecNotFoundException::class)
    fun handleQualitySpecNotFoundException(
        ex: QualitySpecNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Quality spec not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Quality spec not found", errorDetails))
    }

    /**
     * Quality spec already exists exception (409 Conflict)
     */
    @ExceptionHandler(QualitySpecAlreadyExistsException::class)
    fun handleQualitySpecAlreadyExistsException(
        ex: QualitySpecAlreadyExistsException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Quality spec already exists: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message ?: "Quality spec already exists", errorDetails))
    }

    /**
     * Quality run not found exception (404)
     */
    @ExceptionHandler(QualityRunNotFoundException::class)
    fun handleQualityRunNotFoundException(
        ex: QualityRunNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Quality run not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Quality run not found", errorDetails))
    }

    // === Workflow-specific exception handlers ===

    /**
     * Workflow not found exception (404)
     */
    @ExceptionHandler(WorkflowNotFoundException::class)
    fun handleWorkflowNotFoundException(
        ex: WorkflowNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Workflow not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Workflow not found", errorDetails))
    }

    /**
     * Workflow run not found exception (404)
     */
    @ExceptionHandler(WorkflowRunNotFoundException::class)
    fun handleWorkflowRunNotFoundException(
        ex: WorkflowRunNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Workflow run not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "run_id" to ex.runId,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Workflow run not found", errorDetails))
    }

    /**
     * Workflow already exists exception (409 Conflict)
     */
    @ExceptionHandler(WorkflowAlreadyExistsException::class)
    fun handleWorkflowAlreadyExistsException(
        ex: WorkflowAlreadyExistsException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Workflow already exists: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message ?: "Workflow already exists", errorDetails))
    }

    // === Dataset-specific exception handlers ===

    /**
     * Dataset not found exception (404)
     */
    @ExceptionHandler(DatasetNotFoundException::class)
    fun handleDatasetNotFoundException(
        ex: DatasetNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Dataset not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Dataset not found", errorDetails))
    }

    /**
     * Dataset already exists exception (409 Conflict)
     */
    @ExceptionHandler(DatasetAlreadyExistsException::class)
    fun handleDatasetAlreadyExistsException(
        ex: DatasetAlreadyExistsException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Dataset already exists: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message ?: "Dataset already exists", errorDetails))
    }

    /**
     * Dataset execution timeout exception (408 Request Timeout)
     */
    @ExceptionHandler(DatasetExecutionTimeoutException::class)
    fun handleDatasetExecutionTimeoutException(
        ex: DatasetExecutionTimeoutException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Dataset execution timeout: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                        "timeout_seconds" to ex.timeoutSeconds,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.REQUEST_TIMEOUT)
            .body(ApiResponse.error(ex.message ?: "Dataset execution timeout", errorDetails))
    }

    /**
     * Dataset execution failed exception (500 Internal Server Error)
     */
    @ExceptionHandler(DatasetExecutionFailedException::class)
    fun handleDatasetExecutionFailedException(
        ex: DatasetExecutionFailedException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error { "Dataset execution failed: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                        "sql_error" to ex.sqlError,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ex.message ?: "Dataset execution failed", errorDetails))
    }

    /**
     * Invalid dataset name exception (422 Unprocessable Entity)
     */
    @ExceptionHandler(InvalidDatasetNameException::class)
    fun handleInvalidDatasetNameException(
        ex: InvalidDatasetNameException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid dataset name: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "dataset_name" to ex.datasetName,
                        "expected_pattern" to "[catalog].[schema].[name]",
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(ex.message ?: "Invalid dataset name", errorDetails))
    }

    /**
     * Invalid SQL exception (422 Unprocessable Entity)
     */
    @ExceptionHandler(InvalidSqlException::class)
    fun handleInvalidSqlException(
        ex: InvalidSqlException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid SQL: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "sql_error" to ex.sqlError,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(ex.message ?: "Invalid SQL", errorDetails))
    }

    /**
     * Invalid owner email exception (422 Unprocessable Entity)
     */
    @ExceptionHandler(InvalidOwnerEmailException::class)
    fun handleInvalidOwnerEmailException(
        ex: InvalidOwnerEmailException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid owner email: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "email" to ex.email,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(ex.message ?: "Invalid owner email", errorDetails))
    }

    /**
     * Too many tags exception (422 Unprocessable Entity)
     */
    @ExceptionHandler(TooManyTagsException::class)
    fun handleTooManyTagsException(
        ex: TooManyTagsException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Too many tags: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "tag_count" to ex.tagCount,
                        "max_allowed" to ex.maxAllowed,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(ex.message ?: "Too many tags", errorDetails))
    }

    /**
     * Invalid cron exception (422 Unprocessable Entity)
     */
    @ExceptionHandler(InvalidCronException::class)
    fun handleInvalidCronException(
        ex: InvalidCronException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid cron expression: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "cron_expression" to ex.cronExpression,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error(ex.message ?: "Invalid cron expression", errorDetails))
    }

    // === Catalog-specific exception handlers ===

    /**
     * Table not found exception (404)
     */
    @ExceptionHandler(TableNotFoundException::class)
    fun handleTableNotFoundException(
        ex: TableNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Table not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "table_ref" to ex.tableRef,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Table not found", errorDetails))
    }

    /**
     * Invalid table reference exception (400 Bad Request)
     */
    @ExceptionHandler(InvalidTableReferenceException::class)
    fun handleInvalidTableReferenceException(
        ex: InvalidTableReferenceException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid table reference: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "table_ref" to ex.tableRef,
                        "expected_format" to "project.dataset.table",
                    ),
            )

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ex.message ?: "Invalid table reference", errorDetails))
    }

    /**
     * Catalog service exception (502 Bad Gateway)
     */
    @ExceptionHandler(CatalogServiceException::class)
    fun handleCatalogServiceException(
        ex: CatalogServiceException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error { "Catalog service error: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "operation" to ex.operation,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ApiResponse.error(ex.message ?: "Catalog service error", errorDetails))
    }

    /**
     * Catalog timeout exception (504 Gateway Timeout)
     */
    @ExceptionHandler(CatalogTimeoutException::class)
    fun handleCatalogTimeoutException(
        ex: CatalogTimeoutException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Catalog timeout: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "table_ref" to ex.tableRef,
                        "timeout_seconds" to ex.timeoutSeconds,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ApiResponse.error(ex.message ?: "Catalog timeout", errorDetails))
    }

    // === Ad-Hoc Execution (Run API) exception handlers ===

    /**
     * Rate limit exceeded exception (429 Too Many Requests)
     */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceededException(
        ex: RateLimitExceededException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Rate limit exceeded: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    buildMap {
                        put("path", request.getDescription(false))
                        put("limit_type", ex.limitType)
                        put("limit", ex.limit)
                        put("current_usage", ex.currentUsage)
                        ex.resetAt?.let { put("reset_at", it.toString()) }
                    },
            )

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error(ex.message ?: "Rate limit exceeded", errorDetails))
    }

    /**
     * Result not found exception (404 Not Found)
     */
    @ExceptionHandler(ResultNotFoundException::class)
    fun handleResultNotFoundException(
        ex: ResultNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Result not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "query_id" to ex.queryId,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Result not found", errorDetails))
    }

    /**
     * Query too large exception (413 Payload Too Large)
     */
    @ExceptionHandler(QueryTooLargeException::class)
    fun handleQueryTooLargeException(
        ex: QueryTooLargeException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Query too large: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "actual_size_bytes" to ex.actualSizeBytes,
                        "max_size_bytes" to ex.maxSizeBytes,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error(ex.message ?: "Query too large", errorDetails))
    }

    /**
     * Result size limit exceeded exception (413 Payload Too Large)
     */
    @ExceptionHandler(ResultSizeLimitExceededException::class)
    fun handleResultSizeLimitExceededException(
        ex: ResultSizeLimitExceededException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Result size limit exceeded: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "result_size_mb" to ex.resultSizeMb,
                        "limit_mb" to ex.limitMb,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error(ex.message ?: "Result size limit exceeded", errorDetails))
    }

    /**
     * Query execution timeout exception (408 Request Timeout)
     */
    @ExceptionHandler(QueryExecutionTimeoutException::class)
    fun handleQueryExecutionTimeoutException(
        ex: QueryExecutionTimeoutException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Query execution timeout: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "query_id" to ex.queryId,
                        "timeout_seconds" to ex.timeoutSeconds,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.REQUEST_TIMEOUT)
            .body(ApiResponse.error(ex.message ?: "Query execution timeout", errorDetails))
    }

    /**
     * Invalid download token exception (400 Bad Request)
     */
    @ExceptionHandler(InvalidDownloadTokenException::class)
    fun handleInvalidDownloadTokenException(
        ex: InvalidDownloadTokenException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid download token: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "query_id" to ex.queryId,
                    ),
            )

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ex.message ?: "Invalid download token", errorDetails))
    }

    // === General exception handlers ===

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Business exception: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ex.message ?: "Business rule violation", errorDetails))
    }

    /**
     * 리소스 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(
        ex: ResourceNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Resource not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = ex.errorCode,
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Resource not found", errorDetails))
    }

    /**
     * 유효성 검사 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Validation failed: ${ex.message}" }

        val errors =
            ex.bindingResult.allErrors.map { error ->
                when (error) {
                    is FieldError ->
                        ErrorDetails(
                            code = "VALIDATION_ERROR",
                            field = error.field,
                            rejectedValue = error.rejectedValue,
                            details = mapOf("message" to (error.defaultMessage ?: "Invalid value")),
                        )
                    else ->
                        ErrorDetails(
                            code = "VALIDATION_ERROR",
                            details = mapOf("message" to (error.defaultMessage ?: "Invalid value")),
                        )
                }
            }

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("Validation failed", errors.firstOrNull()))
    }

    /**
     * Missing request parameter exception handling
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        ex: org.springframework.web.bind.MissingServletRequestParameterException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Missing request parameter: ${ex.parameterName}" }

        val errorDetails =
            ErrorDetails(
                code = "MISSING_PARAMETER",
                field = ex.parameterName,
                details = mapOf("message" to "Required parameter '${ex.parameterName}' is missing"),
            )

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("Missing required parameter", errorDetails))
    }

    /**
     * HTTP message not readable exception handling (JSON parsing errors)
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        ex: org.springframework.http.converter.HttpMessageNotReadableException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "HTTP message not readable: ${ex.message}" }

        val errorMessage =
            ex.mostSpecificCause.message
                ?: "Request body is malformed or missing required fields"

        val errorDetails =
            ErrorDetails(
                code = "MALFORMED_REQUEST",
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "message" to errorMessage,
                    ),
            )

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("Malformed request body", errorDetails))
    }

    /**
     * Constraint violation exception handling (for @Validated parameter validation)
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Constraint violation: ${ex.message}" }

        val violations =
            ex.constraintViolations.map { violation ->
                ErrorDetails(
                    code = "CONSTRAINT_VIOLATION",
                    field = violation.propertyPath.toString(),
                    rejectedValue = violation.invalidValue,
                    details = mapOf("message" to violation.message),
                )
            }

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("Constraint violation", violations.firstOrNull()))
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Unexpected error: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = "INTERNAL_ERROR",
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "type" to ex.javaClass.simpleName,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Internal server error", errorDetails))
    }

    /**
     * 잘못된 요청 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid argument: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = "INVALID_ARGUMENT",
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ex.message ?: "Invalid argument", errorDetails))
    }

    /**
     * Access denied exception (403 Forbidden)
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Access denied: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = "ACCESS_DENIED",
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.message ?: "Access denied", errorDetails))
    }

    /**
     * Query not found exception (404 Not Found)
     */
    @ExceptionHandler(QueryNotFoundException::class)
    fun handleQueryNotFoundException(
        ex: QueryNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Query not found: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = "QUERY_NOT_FOUND",
                details = mapOf("path" to request.getDescription(false)),
            )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Query not found", errorDetails))
    }

    /**
     * Query not cancellable exception (409 Conflict)
     */
    @ExceptionHandler(QueryNotCancellableException::class)
    fun handleQueryNotCancellableException(
        ex: QueryNotCancellableException,
        request: WebRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Query not cancellable: ${ex.message}" }

        val errorDetails =
            ErrorDetails(
                code = "QUERY_NOT_CANCELLABLE",
                details =
                    mapOf(
                        "path" to request.getDescription(false),
                        "query_id" to ex.queryId,
                        "current_status" to ex.currentStatus,
                    ),
            )

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message ?: "Query not cancellable", errorDetails))
    }
}
