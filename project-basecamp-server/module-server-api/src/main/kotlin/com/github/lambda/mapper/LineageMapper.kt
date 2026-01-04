package com.github.lambda.mapper

import com.github.lambda.domain.entity.lineage.LineageEdgeEntity
import com.github.lambda.domain.entity.lineage.LineageNodeEntity
import com.github.lambda.domain.model.lineage.LineageGraphResult
import com.github.lambda.domain.repository.lineage.LineageEdgeRepositoryDsl.NodeWithDepth
import com.github.lambda.dto.lineage.LineageEdgeDto
import com.github.lambda.dto.lineage.LineageGraphDto
import com.github.lambda.dto.lineage.LineageNodeDto
import org.springframework.stereotype.Component

/**
 * Lineage Mapper
 *
 * Handles conversions between Domain models and API DTOs for lineage operations.
 */
@Component
class LineageMapper {
    /**
     * Convert LineageNodeEntity to LineageNodeDto (without depth)
     */
    fun toNodeDto(entity: LineageNodeEntity): LineageNodeDto =
        LineageNodeDto(
            name = entity.name,
            type = entity.type.name,
            owner = entity.owner ?: "unknown",
            team = entity.team,
            description = entity.description,
            tags = entity.tags.sorted(),
        )

    /**
     * Convert NodeWithDepth to LineageNodeDto (with depth information)
     */
    fun toNodeDto(nodeWithDepth: NodeWithDepth): LineageNodeDto {
        // Need to get the actual node entity from the repository
        // For now, return a placeholder - this will be fixed in the repository implementation
        return LineageNodeDto(
            name = nodeWithDepth.name,
            type = "UNKNOWN", // This will be properly implemented in the repository
            owner = "system",
            team = null,
            description = null,
            tags = emptyList(),
            depth = nodeWithDepth.depth,
        )
    }

    /**
     * Convert LineageEdgeEntity to LineageEdgeDto
     */
    fun toEdgeDto(entity: LineageEdgeEntity): LineageEdgeDto =
        LineageEdgeDto(
            source = entity.source,
            target = entity.target,
            edgeType = entity.edgeType.name,
        )

    /**
     * Convert LineageGraphResult to LineageGraphDto
     *
     * Main conversion method for the complete lineage graph response.
     */
    fun toGraphDto(result: LineageGraphResult): LineageGraphDto {
        val rootDto = toNodeDto(result.rootNode)
        val nodesDtos = result.nodes.map { toNodeDto(it) }
        val edgesDtos = result.edges.map { toEdgeDto(it) }

        return LineageGraphDto(
            root = rootDto,
            nodes = nodesDtos,
            edges = edgesDtos,
            totalUpstream = result.totalUpstream,
            totalDownstream = result.totalDownstream,
        )
    }

    /**
     * Convert list of LineageNodeEntity to list of LineageNodeDto
     */
    fun toNodeDtoList(entities: List<LineageNodeEntity>): List<LineageNodeDto> = entities.map { toNodeDto(it) }

    /**
     * Convert list of NodeWithDepth to list of LineageNodeDto
     */
    fun toNodeDtoListWithDepth(nodesWithDepth: List<NodeWithDepth>): List<LineageNodeDto> =
        nodesWithDepth.map { toNodeDto(it) }

    /**
     * Convert list of LineageEdgeEntity to list of LineageEdgeDto
     */
    fun toEdgeDtoList(entities: List<LineageEdgeEntity>): List<LineageEdgeDto> = entities.map { toEdgeDto(it) }
}
