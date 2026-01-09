package com.dataops.basecamp.annotation

/**
 * Feature Flag 검증 어노테이션
 *
 * Controller 메서드나 클래스에 적용하여 특정 Feature Flag가 활성화되어 있는지 검증합니다.
 * Flag가 비활성화된 경우 FlagDisabledException이 발생합니다.
 *
 * @property key Feature Flag 키 (e.g., "query_editor_v2")
 * @property fallbackMessage Flag가 비활성화된 경우 반환할 메시지
 *
 * 사용 예시:
 * ```
 * @RequireFlag("advanced_lineage")
 * @GetMapping("/lineage/advanced")
 * fun getAdvancedLineage(): ResponseEntity<LineageDto> {
 *     return ResponseEntity.ok(lineageService.getAdvanced())
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireFlag(
    /**
     * Feature Flag 키
     */
    val key: String,
    /**
     * Flag가 비활성화된 경우 반환할 메시지
     */
    val fallbackMessage: String = "This feature is not available",
    // Phase 2
    // val permission: String = "",
)
