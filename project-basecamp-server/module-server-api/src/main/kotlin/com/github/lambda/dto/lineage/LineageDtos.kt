package com.github.lambda.dto.lineage

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Lineage Node DTO
 *
 * Represents a node in the lineage graph with optional depth information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Lineage node with resource information and optional traversal depth")
data class LineageNodeDto(
    @Schema(description = "Unique resource name", example = "iceberg.analytics.users")
    val name: String,
    @Schema(description = "Resource type", example = "DATASET")
    val type: String,
    @Schema(description = "Resource owner", example = "data-team@company.com")
    val owner: String,
    @Schema(description = "Team responsible for the resource", example = "data-engineering")
    val team: String?,
    @Schema(description = "Resource description")
    val description: String?,
    @Schema(description = "Resource tags")
    val tags: List<String>,
    @Schema(
        description = "Depth from root node (negative=upstream, positive=downstream, 0=root)",
        example = "-1",
    )
    val depth: Int? = null,
)

/**
 * Lineage Edge DTO
 *
 * Represents a relationship between two resources in the lineage graph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Lineage edge representing dependency between resources")
data class LineageEdgeDto(
    @Schema(description = "Source resource name", example = "iceberg.raw.events")
    val source: String,
    @Schema(description = "Target resource name", example = "iceberg.analytics.users")
    val target: String,
    @Schema(description = "Type of dependency", example = "DIRECT")
    @JsonProperty("edge_type")
    val edgeType: String,
)

/**
 * Lineage Graph Response DTO
 *
 * Complete lineage graph response containing root node, related nodes, edges, and summary statistics.
 * Used for GET /api/v1/lineage/{resource_name} response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Complete lineage graph for a resource")
data class LineageGraphDto(
    @Schema(description = "Root resource node that was queried")
    val root: LineageNodeDto,
    @Schema(description = "All nodes in the lineage graph (including root)")
    val nodes: List<LineageNodeDto>,
    @Schema(description = "All edges connecting the nodes")
    val edges: List<LineageEdgeDto>,
    @Schema(description = "Number of upstream dependencies", example = "3")
    @JsonProperty("total_upstream")
    val totalUpstream: Int,
    @Schema(description = "Number of downstream dependents", example = "5")
    @JsonProperty("total_downstream")
    val totalDownstream: Int,
) {
    @Schema(description = "Total number of related resources (upstream + downstream)", example = "8")
    @JsonProperty("total_related")
    val totalRelated: Int = totalUpstream + totalDownstream

    @Schema(description = "Total number of nodes in the response", example = "9")
    @JsonProperty("total_nodes")
    val totalNodes: Int = nodes.size

    @Schema(description = "Total number of edges in the response", example = "12")
    @JsonProperty("total_edges")
    val totalEdges: Int = edges.size
}
