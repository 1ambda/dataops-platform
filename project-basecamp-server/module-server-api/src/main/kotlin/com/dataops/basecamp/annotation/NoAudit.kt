package com.dataops.basecamp.annotation

/**
 * No Audit Annotation
 *
 * Excludes the annotated endpoint from audit logging.
 * Can be applied at class or method level.
 *
 * @property reason Optional reason for excluding from audit (for documentation purposes)
 *
 * Usage:
 * ```
 * @NoAudit(reason = "Health check endpoint")
 * @GetMapping("/health")
 * fun health(): ResponseEntity<HealthResponse> {
 *     return ResponseEntity.ok(HealthResponse("UP"))
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NoAudit(
    /**
     * Reason for excluding from audit (documentation purpose)
     */
    val reason: String = "",
)
