package com.github.lambda.domain.model.workflow

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * AirflowClusterEntity Unit Tests
 *
 * Tests for Airflow cluster entity and its helper methods.
 */
@DisplayName("AirflowClusterEntity Unit Tests")
class AirflowClusterEntityTest {
    @Nested
    @DisplayName("Entity Creation")
    inner class EntityCreation {
        @Test
        @DisplayName("should create entity with all required fields")
        fun `should create entity with all required fields`() {
            // Given & When
            val cluster =
                AirflowClusterEntity(
                    id = 1L,
                    team = "data-platform",
                    clusterName = "data-platform-airflow",
                    airflowUrl = "https://airflow.example.com",
                    environment = AirflowEnvironment.PRODUCTION,
                    dagS3Path = "s3://bucket/dags/data-platform",
                    dagNamePrefix = "dp_",
                    isActive = true,
                    apiKey = "test-api-key-12345",
                    description = "Data Platform Airflow Cluster for production workloads",
                )

            // Then
            assertThat(cluster.id).isEqualTo(1L)
            assertThat(cluster.team).isEqualTo("data-platform")
            assertThat(cluster.clusterName).isEqualTo("data-platform-airflow")
            assertThat(cluster.airflowUrl).isEqualTo("https://airflow.example.com")
            assertThat(cluster.environment).isEqualTo(AirflowEnvironment.PRODUCTION)
            assertThat(cluster.dagS3Path).isEqualTo("s3://bucket/dags/data-platform")
            assertThat(cluster.dagNamePrefix).isEqualTo("dp_")
            assertThat(cluster.isActive).isTrue()
            assertThat(cluster.apiKey).isEqualTo("test-api-key-12345")
            assertThat(cluster.description).isEqualTo("Data Platform Airflow Cluster for production workloads")
        }

        @Test
        @DisplayName("should create entity with default values")
        fun `should create entity with default values`() {
            // Given & When
            val cluster =
                AirflowClusterEntity(
                    team = "analytics",
                    clusterName = "analytics-airflow",
                    airflowUrl = "https://analytics-airflow.example.com",
                    environment = AirflowEnvironment.DEVELOPMENT,
                    dagS3Path = "s3://bucket/analytics-dags",
                    dagNamePrefix = "an_",
                    apiKey = "test-key",
                )

            // Then
            assertThat(cluster.id).isEqualTo(0L)
            assertThat(cluster.isActive).isTrue()
            assertThat(cluster.description).isNull()
        }

        @Test
        @DisplayName("should create inactive cluster")
        fun `should create inactive cluster`() {
            // Given & When
            val cluster =
                AirflowClusterEntity(
                    team = "deprecated-team",
                    clusterName = "old-airflow",
                    airflowUrl = "https://old-airflow.example.com",
                    environment = AirflowEnvironment.DEVELOPMENT,
                    dagS3Path = "s3://bucket/old-dags",
                    dagNamePrefix = "old_",
                    isActive = false,
                    apiKey = "old-key",
                )

            // Then
            assertThat(cluster.isActive).isFalse()
        }
    }

    @Nested
    @DisplayName("isActiveCluster")
    inner class IsActiveCluster {
        @Test
        @DisplayName("should return true for active cluster")
        fun `should return true for active cluster`() {
            // Given
            val cluster = createTestCluster(isActive = true)

            // When & Then
            assertThat(cluster.isActiveCluster()).isTrue()
        }

        @Test
        @DisplayName("should return false for inactive cluster")
        fun `should return false for inactive cluster`() {
            // Given
            val cluster = createTestCluster(isActive = false)

            // When & Then
            assertThat(cluster.isActiveCluster()).isFalse()
        }
    }

    @Nested
    @DisplayName("isDevelopment")
    inner class IsDevelopment {
        @Test
        @DisplayName("should return true for development environment")
        fun `should return true for development environment`() {
            // Given
            val cluster = createTestCluster(environment = AirflowEnvironment.DEVELOPMENT)

            // When & Then
            assertThat(cluster.isDevelopment()).isTrue()
            assertThat(cluster.isProduction()).isFalse()
        }

        @Test
        @DisplayName("should return false for production environment")
        fun `should return false for production environment`() {
            // Given
            val cluster = createTestCluster(environment = AirflowEnvironment.PRODUCTION)

            // When & Then
            assertThat(cluster.isDevelopment()).isFalse()
        }
    }

    @Nested
    @DisplayName("isProduction")
    inner class IsProduction {
        @Test
        @DisplayName("should return true for production environment")
        fun `should return true for production environment`() {
            // Given
            val cluster = createTestCluster(environment = AirflowEnvironment.PRODUCTION)

            // When & Then
            assertThat(cluster.isProduction()).isTrue()
            assertThat(cluster.isDevelopment()).isFalse()
        }

        @Test
        @DisplayName("should return false for development environment")
        fun `should return false for development environment`() {
            // Given
            val cluster = createTestCluster(environment = AirflowEnvironment.DEVELOPMENT)

            // When & Then
            assertThat(cluster.isProduction()).isFalse()
        }
    }

    @Nested
    @DisplayName("generateDagId")
    inner class GenerateDagId {
        @Test
        @DisplayName("should generate DAG ID with prefix")
        fun `should generate DAG ID with prefix`() {
            // Given
            val cluster = createTestCluster(dagNamePrefix = "dp_")

            // When
            val dagId = cluster.generateDagId("my_workflow")

            // Then
            assertThat(dagId).isEqualTo("dp_my_workflow")
        }

        @Test
        @DisplayName("should handle empty prefix")
        fun `should handle empty prefix`() {
            // Given
            val cluster = createTestCluster(dagNamePrefix = "")

            // When
            val dagId = cluster.generateDagId("workflow_name")

            // Then
            assertThat(dagId).isEqualTo("workflow_name")
        }

        @Test
        @DisplayName("should handle complex DAG names")
        fun `should handle complex DAG names`() {
            // Given
            val cluster = createTestCluster(dagNamePrefix = "team_")

            // When
            val dagId = cluster.generateDagId("catalog_schema_dataset_daily")

            // Then
            assertThat(dagId).isEqualTo("team_catalog_schema_dataset_daily")
        }
    }

    // Helper method to create test clusters
    private fun createTestCluster(
        id: Long = 1L,
        team: String = "test-team",
        clusterName: String = "test-cluster",
        airflowUrl: String = "https://test-airflow.example.com",
        environment: AirflowEnvironment = AirflowEnvironment.PRODUCTION,
        dagS3Path: String = "s3://bucket/dags",
        dagNamePrefix: String = "test_",
        isActive: Boolean = true,
        apiKey: String = "test-key",
        description: String? = null,
    ): AirflowClusterEntity =
        AirflowClusterEntity(
            id = id,
            team = team,
            clusterName = clusterName,
            airflowUrl = airflowUrl,
            environment = environment,
            dagS3Path = dagS3Path,
            dagNamePrefix = dagNamePrefix,
            isActive = isActive,
            apiKey = apiKey,
            description = description,
        )
}
