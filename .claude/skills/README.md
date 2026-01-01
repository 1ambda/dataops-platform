# Skills

Language-neutral capabilities loaded by agents. See [../.claude/README.md](../README.md) for system overview.

## Available Skills

### Core Skills (All Agents)

| Skill | Purpose |
|-------|---------|
| [mcp-efficiency](./mcp-efficiency/SKILL.md) | Token-efficient MCP usage (80-90% savings) |
| [code-search](./code-search/SKILL.md) | Codebase exploration with exact locations |
| [debugging](./debugging/SKILL.md) | Hypothesis-driven bug investigation |
| [architecture](./architecture/SKILL.md) | System architecture analysis |
| [refactoring](./refactoring/SKILL.md) | Safe code restructuring |
| [performance](./performance/SKILL.md) | Bottleneck detection and optimization |

### Testing Skills

| Skill | Purpose | For |
|-------|---------|-----|
| [testing](./testing/SKILL.md) | Generic TDD workflow and test pyramid | All |
| [kotlin-testing](./kotlin-testing/SKILL.md) | JUnit 5, MockK, Spring test slices | Kotlin/Spring |
| [react-testing](./react-testing/SKILL.md) | Vitest, React Testing Library, MSW | React/TypeScript |
| [pytest-fixtures](./pytest-fixtures/SKILL.md) | Fixture design, conftest.py hierarchy | Python |
| [test-structure-analysis](./test-structure-analysis/SKILL.md) | Test organization, coverage gaps | Python |

### Workflow Skills

| Skill | Purpose |
|-------|---------|
| [code-review](./code-review/SKILL.md) | PR review with security/performance analysis |
| [git-workflow](./git-workflow/SKILL.md) | Commits, PRs, branch management |
| [ci-pipeline](./ci-pipeline/SKILL.md) | GitHub Actions, caching, deployment strategies |
| [documentation](./documentation/SKILL.md) | API docs, READMEs, changelogs |
| [requirements-discovery](./requirements-discovery/SKILL.md) | Stakeholder interviews, PRD writing |

### Collaboration Skills

| Skill | Purpose |
|-------|---------|
| [agent-cross-review](./agent-cross-review/SKILL.md) | Structured cross-review protocol between agents |
| [context-synthesis](./context-synthesis/SKILL.md) | Context gathering from multiple sources |
| [spec-validation](./spec-validation/SKILL.md) | Specification quality validation |

### Quality Gate Skills (Feature Agents 공통)

> 모든 feature-* agent가 공유하는 검증 워크플로우 skills

| Skill | Purpose | 트리거 |
|-------|---------|--------|
| [completion-gate](./completion-gate/SKILL.md) | 완료 Gate + 코드 존재 검증 (통합됨) | "완료", "done" 선언 시 |
| [implementation-checklist](./implementation-checklist/SKILL.md) | FEATURE → 체크리스트 생성 | 구현 시작 시 |
| [gap-analysis](./gap-analysis/SKILL.md) | FEATURE vs RELEASE 비교 | 리뷰 시 |
| [phase-tracking](./phase-tracking/SKILL.md) | Phase 1/2 관리 | Phase 구분 시 |
| [dependency-coordination](./dependency-coordination/SKILL.md) | 크로스 Agent 의존성 추적 | 연동 필요 시 |
| [docs-synchronize](./docs-synchronize/SKILL.md) | 문서 동기화 검증 | Gate 통과 후 |
| [api-parity](./api-parity/SKILL.md) | CLI ↔ Library API 1:1 매핑 검증 | API 구현 시 |
| [integration-finder](./integration-finder/SKILL.md) | 기존 모듈 연동점 탐색 | 새 모듈 추가 시 |

## Design Principle

Skills focus on **principles and processes**, not implementation details. Language/framework-specific patterns are handled by feature agents (`.claude/agents/feature-*.md`).

## Skill Selection Guide

| Agent Type | Essential Skills | Why |
|------------|------------------|-----|
| Kotlin/Spring | `mcp-efficiency`, `kotlin-testing`, `architecture` | Type-safe testing, hexagonal validation |
| React/TypeScript | `mcp-efficiency`, `react-testing`, `performance` | User-centric tests, re-render analysis |
| Python CLI | `mcp-efficiency`, `pytest-fixtures`, `testing` | Fixture DRY, TDD workflow |
| DevOps | `mcp-efficiency`, `ci-pipeline`, `git-workflow` | Fast builds, safe deployments |
| Review | `mcp-efficiency`, `code-review`, `architecture` | Deep analysis, pattern validation |
