# Skills

Language-neutral capabilities loaded by agents. See [../.claude/README.md](../README.md) for system overview.

## Available Skills

| Skill | Purpose |
|-------|---------|
| [code-search](./code-search/SKILL.md) | Token-efficient codebase exploration |
| [code-review](./code-review/SKILL.md) | PR review with security/performance analysis |
| [testing](./testing/SKILL.md) | TDD, unit, integration, E2E testing |
| [debugging](./debugging/SKILL.md) | Hypothesis-driven bug investigation |
| [architecture](./architecture/SKILL.md) | System architecture analysis |
| [refactoring](./refactoring/SKILL.md) | Safe code restructuring |
| [performance](./performance/SKILL.md) | Bottleneck detection and optimization |
| [documentation](./documentation/SKILL.md) | API docs, READMEs, changelogs |
| [git-workflow](./git-workflow/SKILL.md) | Commits, PRs, branch management |

## Design Principle

Skills focus on **principles and processes**, not implementation details. Language/framework-specific patterns are handled by feature agents (`.claude/agents/feature-*.md`).
