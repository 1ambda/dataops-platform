# Basecamp Server API Features

> **Version:** 1.0.0 | **Status:** Specification Complete | **Created:** 2026-01-01

> **📝 Documentation Migration Note (2026-01-01):**
> Core patterns and guides have been promoted to platform-level documentation:
> - **Architecture & Design** → [docs/architecture.md](../../../docs/architecture.md)
> - **Implementation Patterns** → [docs/IMPLEMENTATION_GUIDE.md](../../../docs/IMPLEMENTATION_GUIDE.md)
> - **Error Handling** → [docs/ERROR_HANDLING.md](../../../docs/ERROR_HANDLING.md)
>
> This directory now focuses on **feature-specific API specifications**. For implementation patterns and architectural guidance, refer to the docs above.

---

A structured specification for implementing REST API endpoints in **project-basecamp-server** to support the CLI client (`dli`). This documentation is split into focused, developer-ready files for immediate implementation.

---

## 📁 Documentation Structure

### 📋 Core Specifications
| File | Purpose | Target Audience |
|------|---------|----------------|
| [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md) | Architecture & design principles | System architects, tech leads |
| [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | 12.5-week phased implementation roadmap | Project managers, developers |

### 🚀 Feature Specifications (CLI Command-Based)

| Priority | Feature | Specification | CLI Command |
|----------|---------|---------------|-------------|
| **P0** | Health | [`HEALTH_FEATURE.md`](./HEALTH_FEATURE.md) | `dli debug` |
| **P0** | Metrics | [`METRIC_FEATURE.md`](./METRIC_FEATURE.md) | `dli metric` |
| **P0** | Datasets | [`DATASET_FEATURE.md`](./DATASET_FEATURE.md) | `dli dataset` |
| **P1** | Catalog | [`CATALOG_FEATURE.md`](./CATALOG_FEATURE.md) | `dli catalog` |
| **P1** | Lineage | [`LINEAGE_FEATURE.md`](./LINEAGE_FEATURE.md) | `dli lineage` |
| **P2** | Workflow | [`WORKFLOW_FEATURE.md`](./WORKFLOW_FEATURE.md) | `dli workflow` |
| **P3** | Quality | [`QUALITY_FEATURE.md`](./QUALITY_FEATURE.md) | `dli quality` |
| **P3** | Query | [`QUERY_FEATURE.md`](./QUERY_FEATURE.md) | `dli query` |
| **P3** | Run | [`RUN_FEATURE.md`](./RUN_FEATURE.md) | `dli run` |
| **P3** | Transpile | [`TRANSPILE_FEATURE.md`](./TRANSPILE_FEATURE.md) | `dli transpile` |

### 🛠️ Implementation Guides (Migrated to docs/)
| File | Purpose | Key Content |
|------|---------|------------|
| [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md) | Spring Boot + Kotlin patterns (legacy) | Hexagonal architecture, service patterns |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error handling specification (legacy) | HTTP status codes, error response format |

**New Location (docs/):**
- [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI command → API endpoint mapping | Complete command-to-endpoint reference |

---

## 🎯 Quick Start for Developers

### 1. Understanding the System
```bash
# Start here for architecture overview
cat BASECAMP_OVERVIEW.md

# Review implementation roadmap
cat IMPLEMENTATION_PLAN.md
```

### 2. Choose Your Feature
```bash
# P0 Critical - Start with these
cat METRIC_FEATURE.md    # dli metric
cat DATASET_FEATURE.md   # dli dataset
cat HEALTH_FEATURE.md    # dli debug

# P1 High - Data discovery
cat CATALOG_FEATURE.md   # dli catalog
cat LINEAGE_FEATURE.md   # dli lineage

# P2 Medium - Workflow orchestration
cat WORKFLOW_FEATURE.md  # dli workflow

# P3 Low - Advanced features
cat QUALITY_FEATURE.md   # dli quality
cat QUERY_FEATURE.md     # dli query
cat RUN_FEATURE.md       # dli run
cat TRANSPILE_FEATURE.md # dli transpile
```

### 3. Implementation Patterns
```bash
# Review Spring Boot + Kotlin patterns
cat INTEGRATION_PATTERNS.md

# Understand error handling
cat ERROR_CODES.md

# Map CLI commands to your APIs (now in docs/)
cat ../docs/CLI_API_MAPPING.md
```

---

## 🔗 Cross-Reference Links

### From CLI Implementation
- **CLI Client Mock**: `project-interface-cli/src/dli/core/client.py`
- **CLI Commands**: `project-interface-cli/src/dli/commands/`
- **API Models**: `project-interface-cli/src/dli/api/`

### To Server Implementation
- **Existing Patterns**: `project-basecamp-server/module-server-api/`
- **Domain Services**: `project-basecamp-server/module-core-domain/service/`
- **Infrastructure**: `project-basecamp-server/module-core-infra/`

### Architecture References
- **Main README**: [`../../README.md`](../../README.md)
- **System Architecture**: [`../../docs/architecture.md`](../../docs/architecture.md)
- **Development Guide**: [`../../docs/development.md`](../../docs/development.md)

---

## 📊 Implementation Status

| Phase | APIs | Status | ETA |
|-------|------|--------|-----|
| **Phase 1** | P0 Critical | 🔄 Not Started | Week 1-2.5 |
| **Phase 2** | P1 High | ⏳ Pending P0 | Week 3-5 |
| **Phase 3** | P2 Medium | ⏳ Pending P1 | Week 6-9 |
| **Phase 4** | P3 Low | ⏳ Pending P2 | Week 10-12.5 |

**Total Effort**: 12.5 weeks | **Team Size**: 2-3 developers | **Success Rate**: 85%

---

## 🚨 Critical Notes

### Implementation Requirements
1. **Hexagonal Architecture Compliance**: Services as concrete classes, repository interfaces
2. **CLI Compatibility**: All endpoints must match `BasecampClient` mock expectations
3. **Error Handling**: Standard HTTP codes with structured error responses
4. **Authentication**: OAuth2 token-based (Keycloak integration)

### Before You Start
- [ ] Review [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md) for Spring Boot patterns
- [ ] Check [`ERROR_CODES.md`](./ERROR_CODES.md) for error handling requirements
- [ ] Validate CLI compatibility with [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md)
- [ ] Read Phase 1 implementation details in [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md)

### Success Criteria
- [ ] All P0 APIs fully functional
- [ ] CLI client integration tests pass
- [ ] Performance benchmarks met (< 2s response times)
- [ ] Error codes properly mapped to CLI expectations

---

## 📞 Support & Escalation

**Domain Expert**: feature-basecamp-server Agent
**Technical Review**: expert-spring-kotlin Agent
**Architecture Questions**: expert-hexagonal-architecture
**Original Specification**: [`archive/BASECAMP_FEATURE_ORIGINAL.md`](./archive/BASECAMP_FEATURE_ORIGINAL.md)

---

*This documentation structure follows the DataOps Platform [documentation conventions](../../docs/) for maximum developer productivity and immediate implementation readiness.*