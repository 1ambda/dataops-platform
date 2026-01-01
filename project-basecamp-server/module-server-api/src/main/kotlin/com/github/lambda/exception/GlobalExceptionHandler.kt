package com.github.lambda.exception

import com.github.lambda.common.exception.BusinessException
import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.domain.exception.MetricAlreadyExistsException
import com.github.lambda.domain.exception.MetricExecutionTimeoutException
import com.github.lambda.domain.exception.MetricNotFoundException
import com.github.lambda.dto.ApiResponse
import com.github.lambda.dto.ErrorDetails
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
}
