package com.dataops.basecamp.common.enums

/**
 * Types of resources that can be shared between teams.
 * Note: Catalog columns are handled via CATALOG_TABLE, not separately.
 */
enum class ShareableResourceType {
    /** SqlWorksheetEntity */
    WORKSHEET,

    /** WorksheetFolderEntity */
    WORKSHEET_FOLDER,

    /** DatasetEntity */
    DATASET,

    /** MetricEntity */
    METRIC,

    /** WorkflowEntity */
    WORKFLOW,

    /** QualitySpecEntity */
    QUALITY,
}

/**
 * Permission level for shared resources and user grants.
 */
enum class ResourcePermission {
    /** Read-only access, can execute but not modify */
    VIEWER,

    /** Read + write access, cannot delete */
    EDITOR,
}
