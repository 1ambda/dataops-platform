package com.dataops.basecamp.annotation

/**
 * Annotation to specify request body keys to exclude from audit logging.
 *
 * Used together with global exclude keys (password, token, secret, api_key, etc.)
 * which are always filtered.
 *
 * @param keys Array of key names to exclude from request body logging
 *
 * Usage:
 * ```
 * @AuditExcludeKeys(["rendered_sql", "sql", "sqlTemplate"])
 * @PostMapping("/execute")
 * fun executeSql(@RequestBody request: SqlExecutionRequest): ResponseEntity<Result> {
 *     // ...
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class AuditExcludeKeys(
    val keys: Array<String> = [],
)
