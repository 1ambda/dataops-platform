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
| [implementation-verification](./implementation-verification/SKILL.md) | 구현 완료 검증, 거짓 보고 방지 |

### Collaboration Skills

| Skill | Purpose |
|-------|---------|
| [agent-cross-review](./agent-cross-review/SKILL.md) | Structured cross-review protocol between agents |
| [context-synthesis](./context-synthesis/SKILL.md) | Context gathering from multiple sources |
| [spec-validation](./spec-validation/SKILL.md) | Specification quality validation |

### Quality Gate Skills (NEW 2026-01-01)

| Skill | Purpose |
|-------|---------|
| [completion-gate](./completion-gate/SKILL.md) | Pre-completion verification with phase boundary check |
| [implementation-checklist](./implementation-checklist/SKILL.md) | FEATURE → checklist generation |
| [docs-synchronize](./docs-synchronize/SKILL.md) | Document synchronization validation |
| [gap-analysis](./gap-analysis/SKILL.md) | FEATURE vs RELEASE systematic comparison |
| [phase-tracking](./phase-tracking/SKILL.md) | Multi-phase feature management |
| [dependency-coordination](./dependency-coordination/SKILL.md) | Cross-agent dependency tracking and requests |

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
