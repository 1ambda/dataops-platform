# Basecamp Server API - Implementation Plan

> **Duration:** 12.5 weeks (3.1 months) | **Team Size:** 1.5 FTE | **Success Rate:** 85%
> **Target Audience:** Project managers, team leads, developers
> **Cross-Reference:** [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md) for architecture details

---

## 📋 Table of Contents

1. [Current Status Assessment](#1-current-status-assessment)
2. [Implementation Strategy](#2-implementation-strategy)
3. [Phase 1: P0 Critical APIs (2.5 weeks)](#3-phase-1-p0-critical-apis-25-weeks)
4. [Phase 2: P1 High Priority APIs (3 weeks)](#4-phase-2-p1-high-priority-apis-3-weeks)
5. [Phase 3: P2 Medium Priority APIs (4 weeks)](#5-phase-3-p2-medium-priority-apis-4-weeks)
6. [Phase 4: P3 Low Priority APIs (3 weeks)](#6-phase-4-p3-low-priority-apis-3-weeks)
7. [Risk Management](#7-risk-management)
8. [Resource Allocation](#8-resource-allocation)
9. [Quality Gates](#9-quality-gates)
10. [Success Metrics](#10-success-metrics)

---

## 1. Current Status Assessment

### 1.1 Implementation Status (2026-01-01)

| Category | Required | Completed | Partial | Missing | Progress |
|----------|----------|-----------|---------|---------|----------|
| **Health** | 2 | 2 | 0 | 0 | ✅ **100%** |
| **Metrics** | 5 | 0 | 0 | 5 | ❌ **0%** |
| **Datasets** | 5 | 0 | 1 | 4 | 🟡 **20%** |
| **Catalog** | 4 | 0 | 0 | 4 | ❌ **0%** |
| **Lineage** | 1 | 0 | 0 | 1 | ❌ **0%** |
| **Workflow** | 9 | 0 | 0 | 9 | ❌ **0%** |
| **Quality** | 3 | 0 | 0 | 3 | ❌ **0%** |
| **Query** | 3 | 0 | 0 | 3 | ❌ **0%** |
| **Transpile** | 2 | 0 | 0 | 2 | ❌ **0%** |
| **Run** | 2 | 0 | 0 | 2 | ❌ **0%** |
| **TOTAL** | **36** | **2** | **1** | **33** | 🟡 **8%** |

### 1.2 Infrastructure Readiness: 95% ✅

**Strengths (Ready for Implementation):**
- ✅ **Hexagonal Architecture**: Pure domain services, repository interfaces, infrastructure implementations
- ✅ **Multi-Module Structure**: Clean separation between domain, infrastructure, and API layers
- ✅ **Security**: OAuth2 + Keycloak integration complete
- ✅ **Database Layer**: JPA + QueryDSL with proper transaction management
- ✅ **Testing Infrastructure**: Unit, integration, and mock testing patterns established
- ✅ **Build System**: Gradle multi-module with proper dependency management
- ✅ **DatasetEntity**: Already implemented and can be leveraged for implementation

---

## 2. Implementation Strategy

### 2.1 Priority-Based Phased Approach

**Rationale**: Maximize CLI compatibility and user value delivery while minimizing technical risk.

| Phase | Priority | Focus | CLI Impact | Duration | Team Size |
|-------|----------|-------|------------|----------|-----------|
| **Phase 1** | P0 Critical | Metrics + Datasets CRUD | Enable `dli metric`, `dli dataset` basic commands | 2.5 weeks | 1.4 FTE |
| **Phase 2** | P1 High | Catalog + Lineage | Enable `dli catalog`, `dli lineage` | 3 weeks | 1.6 FTE |
| **Phase 3** | P2 Medium | Workflow Management | Enable `dli workflow` (server-mode) | 4 weeks | 1.8 FTE |
| **Phase 4** | P3 Low | Advanced Features | Enable `dli quality`, `dli query`, `dli run` | 3 weeks | 1.4 FTE |

**Total Implementation Timeline: 12.5 weeks (3.1 months)**

### 2.2 Module Reuse Strategy

**Leverage Existing Patterns:**
- **Copy Pattern**: PipelineController → MetricController (95% similar REST patterns)
- **Entity Extension**: DatasetEntity → extend for API requirements
- **Service Pattern**: PipelineService → MetricService, DatasetService (same transaction patterns)
- **Mapper Pattern**: PipelineMapper → MetricMapper, DatasetMapper (same DTO conversion logic)

**Code Reuse Estimate: 60%** - Significantly reduces development time

### 2.3 Implementation Dependencies

```
P0: Health → Metrics/Datasets (CRUD foundation)
       |
       v
P1: Catalog → Lineage (Data discovery requires catalog)
       |
       v
P2: Workflow (Requires Metrics/Datasets + Airflow integration)
       |
       v
P3: Quality/Query/Run (Advanced features, requires all above)
```

---

## 3. Phase 1: P0 Critical APIs (2.5 weeks)

### 3.1 Week 1: Metrics API Foundation
**Target**: Enable `dli metric list`, `dli metric get`, `dli metric register`

**Implementation Scope:**
```kotlin
// Domain Layer (module-core-domain/)
MetricEntity              // New entity (similar to DatasetEntity)
MetricRepositoryJpa       // Basic CRUD interface
MetricRepositoryDsl       // Complex queries interface
MetricService             // Business logic (concrete class)

// Infrastructure Layer (module-core-infra/)
MetricRepositoryJpaImpl   // JPA implementation
MetricRepositoryDslImpl   // QueryDSL implementation

// API Layer (module-server-api/)
MetricDto                 // Response model
CreateMetricRequest       // Request model
MetricMapper              // DTO ↔ Entity conversion
MetricController          // REST endpoints

// Tests
MetricEntityTest          // Domain model tests
MetricServiceTest         // Business logic tests
MetricControllerTest      // Integration tests
```

**API Endpoints (Week 1):**
- ✅ `GET /api/v1/metrics` - List with filtering
- ✅ `GET /api/v1/metrics/{name}` - Get details
- ✅ `POST /api/v1/metrics` - Register metric

**Dependencies:**
- Database schema: Add `metrics` table
- S3/Storage: Metric YAML file storage
- Validation: SQL expression validation

### 3.2 Week 2: Datasets API Completion + Execution
**Target**: Complete `dli dataset` commands + add execution endpoints

**Implementation Scope:**
```kotlin
// Extend existing DatasetEntity if needed
DatasetService            // Business logic (leverage existing entity)
DatasetController         // REST endpoints
DatasetDto               // Response model
DatasetMapper            // DTO conversion

// Execution endpoints (both Metric + Dataset)
ExecutionRequest         // Common execution model
ExecutionResult          // Common result model
MetricExecutionService   // Metric execution logic
DatasetExecutionService  // Dataset execution logic
```

**API Endpoints (Week 2):**
- ✅ `GET /api/v1/datasets` - List with filtering (reuse DatasetEntity)
- ✅ `GET /api/v1/datasets/{name}` - Get details
- ✅ `POST /api/v1/datasets` - Register dataset
- ✅ `POST /api/v1/metrics/{name}/run` - Execute metric (new)
- ✅ `POST /api/v1/datasets/{name}/run` - Execute dataset (new)

**Dependencies:**
- Query Engine: BigQuery/Trino execution client
- Airflow: Optional integration for dataset runs

### 3.3 Week 2.5: Extended Health API
**Target**: Enable `dli debug` command

**Implementation Scope:**
```kotlin
ExtendedHealthService     // System diagnostics
HealthController          // Add /health/extended endpoint
ComponentHealthCheck     // Database, Redis, Airflow health
```

**API Endpoints (Week 2.5):**
- ✅ `GET /api/v1/health/extended` - System diagnostics

**Phase 1 Deliverables:**
- [ ] All 9 P0 endpoints implemented and tested
- [ ] `dli metric list/get/register` commands work with server
- [ ] `dli dataset list/get/register` commands work with server
- [ ] `dli debug` shows extended health information
- [ ] 80%+ test coverage for new modules

---

## 4. Phase 2: P1 High Priority APIs (3 weeks)

### 4.1 Week 3-4: Catalog API
**Target**: Enable `dli catalog` browsing and search

**Implementation Scope:**
```kotlin
// External Integration Layer
CatalogMetadataClient     // BigQuery/Trino metadata client
TableInfoService          // Table information aggregation
ColumnInfoService         // Column metadata with PII detection
PIIMaskingService         // Sample data masking

// API Layer
CatalogController         // REST endpoints
TableInfoDto              // Table information response
ColumnInfoDto             // Column metadata response
CatalogSearchDto          // Search result response
```

**API Endpoints (Week 3-4):**
- ✅ `GET /api/v1/catalog/tables` - List tables with filters
- ✅ `GET /api/v1/catalog/search` - Search across tables/columns
- ✅ `GET /api/v1/catalog/tables/{table_ref}` - Table details + schema
- ✅ `GET /api/v1/catalog/tables/{table_ref}/queries` - Sample queries

**Major Dependencies:**
- **BigQuery API**: Table/column metadata access
- **Trino API**: Alternative metadata source
- **PII Detection**: Pattern-based + metadata-based masking
- **Sample Data**: Query result caching and masking

### 4.2 Week 5: Lineage API
**Target**: Enable `dli lineage` dependency visualization

**Implementation Scope:**
```kotlin
// Lineage Analysis
LineageGraphService       // Dependency graph construction
LineageTraversalService   // Upstream/downstream traversal
LineageStorageService     // Graph storage (Neo4j or in-memory)

// API Layer
LineageController         // REST endpoints
LineageGraphDto           // Graph response model
LineageNodeDto            // Node information
LineageEdgeDto            // Edge/relationship information
```

**API Endpoints (Week 5):**
- ✅ `GET /api/v1/lineage/{resource_name}` - Dependency graph

**Major Dependencies:**
- **SQL Parsing**: Extract table dependencies from SQL
- **Graph Storage**: Neo4j, PostgreSQL, or in-memory solution
- **Metadata Sync**: Keep lineage updated with metric/dataset changes

**Phase 2 Deliverables:**
- [ ] `dli catalog` commands work with BigQuery metadata
- [ ] `dli lineage show` displays dependency graph
- [ ] PII masking works for sample data
- [ ] Performance: Catalog list < 2 seconds for 1000+ tables

---

## 5. Phase 3: P2 Medium Priority APIs (4 weeks)

### 5.1 Week 6-7: Workflow Management Core
**Target**: Enable `dli workflow list`, `dli workflow status`

**Implementation Scope:**
```kotlin
// Workflow Domain
WorkflowEntity            // Workflow registration info
WorkflowRunEntity         // Execution history
WorkflowService           // Business logic
WorkflowRegistryService   // S3-based workflow storage

// Airflow Integration
AirflowClient            // Airflow REST API client
DAGManagementService     // DAG creation/deletion
WorkflowExecutionService // Run/stop/status management
```

**API Endpoints (Week 6-7):**
- ✅ `GET /api/v1/workflows` - List registered workflows
- ✅ `GET /api/v1/workflows/runs/{run_id}` - Get run status
- ✅ `GET /api/v1/workflows/history` - Execution history
- ✅ `POST /api/v1/workflows/{dataset_name}/pause` - Pause workflow
- ✅ `POST /api/v1/workflows/{dataset_name}/unpause` - Unpause workflow

### 5.2 Week 8-9: Workflow Execution & Registration
**Target**: Enable `dli workflow run`, `dli workflow register`

**Implementation Scope:**
```kotlin
// Workflow Execution
WorkflowTriggerService    // Manual execution triggering
BackfillService          // Date range backfill logic
WorkflowMonitoringService // Status monitoring

// Registration Management
WorkflowFileService      // S3 YAML file management
WorkflowValidationService // CRON + metadata validation
```

**API Endpoints (Week 8-9):**
- ✅ `POST /api/v1/workflows/{dataset_name}/run` - Trigger execution
- ✅ `POST /api/v1/workflows/{dataset_name}/backfill` - Date range execution
- ✅ `POST /api/v1/workflows/runs/{run_id}/stop` - Stop execution
- ✅ `POST /api/v1/workflows/register` - Register manual workflow
- ✅ `DELETE /api/v1/workflows/{dataset_name}` - Unregister workflow

**Major Dependencies:**
- **Airflow API**: Complete DAG lifecycle management
- **S3 Storage**: Workflow YAML file management
- **CRON Validation**: Schedule validation and parsing

**Phase 3 Deliverables:**
- [ ] `dli workflow` commands integrate with Airflow
- [ ] Workflow registration/unregistration works with S3
- [ ] Manual vs CODE workflow permissions enforced
- [ ] Performance: Workflow list < 1 second

---

## 6. Phase 4: P3 Low Priority APIs (3 weeks)

### 6.1 Week 10: Quality & Query APIs
**Target**: Enable `dli quality`, `dli query` commands

**Quality API Implementation:**
```kotlin
QualityTestService        // Test execution engine
QualityRuleEngine        // not_null, unique, accepted_values logic
QualityController        // REST endpoints
```

**Query Metadata Implementation:**
```kotlin
QueryHistoryService      // Query execution tracking
QueryMetadataController  // REST endpoints
QueryCancellationService // Query termination
```

**API Endpoints (Week 10):**
- ✅ `GET /api/v1/quality` - List quality specs
- ✅ `GET /api/v1/quality/{name}` - Get quality spec
- ✅ `POST /api/v1/quality/test/{resource_name}` - Execute test
- ✅ `GET /api/v1/catalog/queries` - Query execution history
- ✅ `GET /api/v1/catalog/queries/{query_id}` - Query details
- ✅ `POST /api/v1/catalog/queries/{query_id}/cancel` - Cancel query

### 6.2 Week 11-12.5: Transpile & Run APIs
**Target**: Enable `dli run` ad-hoc execution and transpile features

**Implementation Scope:**
```kotlin
// Transpile API
TranspileRuleService     // Rule management
SQLTransformationService // SQL dialect conversion
TranspileController      // REST endpoints

// Run API
AdHocExecutionService    // SQL file execution
ExecutionPolicyService   // Execution limits/permissions
RunController            // REST endpoints
```

**API Endpoints (Week 11-12.5):**
- ✅ `GET /api/v1/transpile/rules` - Get transpile rules
- ✅ `GET /api/v1/transpile/metrics/{metric_name}` - Get metric SQL
- ✅ `GET /api/v1/run/policy` - Execution policy
- ✅ `POST /api/v1/run/execute` - Execute ad-hoc SQL

**Phase 4 Deliverables:**
- [ ] All CLI commands have full server support
- [ ] Quality tests execute successfully
- [ ] Ad-hoc SQL execution works with proper limits
- [ ] Performance: Query execution < 30 seconds timeout

---

## 7. Risk Management

### 7.1 High-Risk Dependencies

| Risk | Probability | Impact | Mitigation Strategy |
|------|------------|--------|-------------------|
| **Airflow Integration Complexity** | Medium | High | Start with mock implementation, add real integration later |
| **BigQuery API Rate Limits** | Low | Medium | Implement caching layer for metadata |
| **Lineage Graph Complexity** | Medium | Medium | Use simple in-memory graph for MVP, add persistence later |
| **PII Detection Accuracy** | Medium | Low | Start with basic patterns, enhance iteratively |

### 7.2 Technical Debt Risks

| Area | Risk Level | Prevention Strategy |
|------|------------|-------------------|
| **Code Duplication** | Medium | Establish shared base classes early (BaseController, BaseService) |
| **Test Coverage** | Low | Enforce 80% coverage requirement per module |
| **API Consistency** | Low | Review API contracts before implementation |

### 7.3 External Dependencies & Mitigation

**External Service Dependencies:**
- **Airflow Server**: Required for Phase 3 (can be mocked initially)
- **BigQuery/Trino**: Required for Phase 2 (can use test projects)
- **S3 Storage**: Required for Phase 1 (can use local filesystem initially)

**Mitigation Strategy**: Implement adapter pattern for all external dependencies with local/mock implementations for development.

---

## 8. Resource Allocation

### 8.1 Team Composition (Recommended)

| Role | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|---------|---------|---------|---------|
| **Backend Developer** | 1.0 FTE | 1.0 FTE | 1.0 FTE | 1.0 FTE |
| **DevOps/Infrastructure** | 0.2 FTE | 0.3 FTE | 0.5 FTE | 0.1 FTE |
| **QA/Testing** | 0.1 FTE | 0.2 FTE | 0.3 FTE | 0.2 FTE |
| **Product Owner** | 0.1 FTE | 0.1 FTE | 0.2 FTE | 0.1 FTE |

**Total Effort: ~1.5 FTE over 12.5 weeks**

### 8.2 Development Environment Requirements

**Infrastructure Setup (Week 0):**
- ✅ Docker Compose stack (already available)
- ✅ MySQL + Redis (already configured)
- 🔧 Keycloak OAuth2 setup for API testing
- 🔧 Test BigQuery project for metadata access
- 🔧 Test Airflow instance for workflow testing

**Development Tools:**
- ✅ IntelliJ IDEA + Kotlin plugin
- ✅ Postman/Insomnia for API testing
- 🔧 Newman for automated API testing

---

## 9. Quality Gates

### 9.1 Performance Requirements

| API Endpoint | Response Time | Throughput | Load Test |
|--------------|---------------|------------|-----------|
| **GET /metrics** | < 500ms | 100 req/sec | 1000 metrics |
| **GET /catalog/tables** | < 2000ms | 50 req/sec | 1000+ tables |
| **POST /*/run** | < 30000ms | 10 req/sec | Concurrent executions |
| **GET /lineage/*** | < 1000ms | 25 req/sec | Complex graphs |

### 9.2 Security Requirements

| Requirement | Implementation | Testing |
|-------------|----------------|---------|
| **OAuth2 Authentication** | All endpoints require valid token | Automated token validation tests |
| **RBAC Authorization** | Role-based endpoint access | Permission matrix testing |
| **PII Masking** | Automatic data masking | Pattern detection validation |
| **Audit Logging** | All mutations logged | Log completeness verification |

### 9.3 Integration Testing

**CLI Integration Tests:**
- [ ] All `dli` commands work with server endpoints
- [ ] Error responses map correctly to CLI error codes
- [ ] Authentication flow works end-to-end
- [ ] Performance meets CLI user expectations

---

## 10. Success Metrics

### 10.1 Technical Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| **API Coverage** | 36/36 endpoints | Endpoint implementation checklist |
| **Test Coverage** | 80%+ code coverage | JaCoCo coverage reports |
| **Response Time** | 95th percentile < targets | Performance monitoring |
| **Error Rate** | < 1% API errors | Error rate monitoring |

### 10.2 Business Metrics

| Metric | Target | Benefit |
|--------|--------|---------|
| **CLI Command Support** | 100% server-mode support | Complete CLI functionality |
| **Developer Productivity** | 60% code reuse achieved | Faster development cycles |
| **System Integration** | All external systems connected | Unified data platform |

### 10.3 Risk Mitigation Metrics

| Risk Area | Success Metric | Monitoring |
|-----------|---------------|------------|
| **Dependency Management** | Zero blocking dependencies | Weekly risk assessment |
| **Technical Debt** | < 2 hours/week refactoring | Code quality metrics |
| **Performance** | No performance regressions | Continuous load testing |

---

## 🔗 Related Documentation

### Feature Specifications (Implementation-Ready)

| Priority | Feature | Specification | Review Rating |
|----------|---------|---------------|---------------|
| **P0** | Health API | [`HEALTH_FEATURE.md`](./HEALTH_FEATURE.md) | 4.5/5 |
| **P0** | Metric API | [`METRIC_FEATURE.md`](./METRIC_FEATURE.md) | 4.5/5 |
| **P0** | Dataset API | [`DATASET_FEATURE.md`](./DATASET_FEATURE.md) | 4.0/5 |
| **P1** | Catalog API | [`CATALOG_FEATURE.md`](./CATALOG_FEATURE.md) | 4.5/5 |
| **P1** | Lineage API | [`LINEAGE_FEATURE.md`](./LINEAGE_FEATURE.md) | 4.0/5 |
| **P2** | Workflow API | [`WORKFLOW_FEATURE.md`](./WORKFLOW_FEATURE.md) | 4.0/5 |
| **P3** | Quality API | [`QUALITY_FEATURE.md`](./QUALITY_FEATURE.md) | 4.5/5 |
| **P3** | Query API | [`QUERY_FEATURE.md`](./QUERY_FEATURE.md) | 4.0/5 |
| **P3** | Run API | [`RUN_FEATURE.md`](./RUN_FEATURE.md) | 3.5/5 |
| **P3** | Transpile API | [`TRANSPILE_FEATURE.md`](./TRANSPILE_FEATURE.md) | 4.5/5 |

### Supporting Documentation

- **Architecture Overview**: [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md)
- **Spring Boot Patterns**: [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md)
- **Error Handling**: [`ERROR_CODES.md`](./ERROR_CODES.md)
- **CLI Mapping**: [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md)

### Legacy Documentation (Archive)

- **P0-P3 Priority Specs**: [`archive/`](./archive/) - Original priority-based specifications (archived)

---

## 11. Recommended Implementation Order

Based on review feedback, the recommended implementation order within each phase:

### Phase 1 (P0 Critical) - Week 1-2.5
1. **METRIC_FEATURE.md** (4.5/5) - Start here, cleanest spec
2. **DATASET_FEATURE.md** (4.0/5) - Fix exception patterns first
3. **HEALTH_FEATURE.md** (4.5/5) - Refactor hexagonal violations

### Phase 2 (P1 High) - Week 3-5
1. **CATALOG_FEATURE.md** (4.5/5) - External integrations well-defined
2. **LINEAGE_FEATURE.md** (4.0/5) - Add missing controller first

### Phase 3 (P2 Medium) - Week 6-9
1. **WORKFLOW_FEATURE.md** (4.0/5) - Replace RestTemplate with RestClient first

### Phase 4 (P3 Low) - Week 10-12.5
1. **TRANSPILE_FEATURE.md** (4.5/5) - Cleanest coroutine patterns
2. **QUALITY_FEATURE.md** (4.5/5) - Fix SQL injection before implementation
3. **QUERY_FEATURE.md** (4.0/5) - Fix SecurityContextHolder issue
4. **RUN_FEATURE.md** (3.5/5) - Needs most pre-work (entity fixes)

### Cross-Cutting Fixes (Apply Before Each Phase)
- [ ] Ensure all exceptions extend `BasecampException` with `errorCode` + `httpStatus`
- [ ] Use HTTP 400 (not 422) for validation errors
- [ ] Add `@Repository("beanName")` to all repository implementations
- [ ] Replace `RestTemplate` with `RestClient` for Spring Boot 4

---

*This implementation plan provides a structured roadmap for delivering the Basecamp Server API in 12.5 weeks with high confidence and minimal technical risk.*

*Last Updated: 2026-01-01 | Review Feedback Integrated*