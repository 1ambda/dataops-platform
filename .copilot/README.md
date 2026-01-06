# Github Copilot Agent System

Token-efficient, MCP-first agent architecture for the dataops-platform.

## Architecture

```
.copilot/
├── agents/           # Task-specific agents
│   ├── feature-*     # Project implementation (basecamp-server, ui, parser, cli)
│   ├── expert-*      # Language/framework specialists (spring-kotlin, react-ts, python)
│   ├── code-*        # Cross-cutting (code-review, code-searcher)
│   └── memory-*      # Documentation sync
└── skills/           # Language-neutral capabilities (loaded by agents)
    ├── code-search, testing, debugging, refactoring
    ├── architecture, performance, documentation
    └── git-workflow, code-review
```

## Agent Types

| Type | Purpose | Examples |
|------|---------|----------|
| `feature-*` | Project-specific implementation | basecamp-server, basecamp-ui |
| `expert-*` | Language/framework expertise | spring-kotlin, react-typescript |
| `code-*` | Cross-cutting workflows | code-review, code-searcher |

## Skills (Language-Neutral)

| Skill | Triggers | Purpose |
|-------|----------|---------|
| code-search | find, where, search | Token-efficient exploration |
| testing | test, TDD, coverage | TDD and test strategies |
| debugging | debug, error, fix | Hypothesis-driven investigation |
| refactoring | refactor, extract | Safe code restructuring |
| architecture | architecture, layer | System design analysis |
| performance | slow, optimize, N+1 | Bottleneck detection |
| documentation | document, readme | API docs, changelogs |
| git-workflow | commit, PR, merge | Version control workflows |
| code-review | review, PR | Quality and security analysis |

## MCP Tools Priority

All agents prefer MCP tools for token efficiency:

| Tool | Purpose | Token Savings |
|------|---------|---------------|
| `serena.get_symbols_overview` | File structure | ~90% |
| `serena.find_symbol(include_body=False)` | Signatures only | ~85% |
| `serena.find_referencing_symbols` | Dependencies | ~80% |
| `context7.get-library-docs` | Framework docs | N/A |
| `claude-mem.search` | Past decisions | N/A |

## Workflow Patterns

```
Feature Development:  feature-* → skills (testing, debugging)
Code Review:          code-review → expert-* (validation)
Bug Investigation:    debugging → code-search → testing
Refactoring:          code-search → testing → refactoring
```

## Core Principles

1. **Clarify** - Understand requirements, ask if ambiguous, no over-engineering
2. **Design** - Verify approach against patterns (MCP/docs)
3. **TDD** - Write test → implement → refine
4. **Document** - Update docs when behavior changes
5. **Self-Review** - Iterate if issues found
