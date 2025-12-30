---
name: mcp-efficiency
description: Token-efficient codebase exploration using MCP servers (Serena, Context7, JetBrains, Claude-mem). Reduces token usage by 80-90% through structured queries. Use ALWAYS before reading files to minimize context window usage.
---

# MCP Efficiency

Master MCP tools to reduce token usage while improving code understanding.

## Why This Matters

| Approach | Tokens | Use Case |
|----------|--------|----------|
| Read full file | 5000+ | Rarely needed |
| Serena overview | 200-500 | Structure understanding |
| Serena find_symbol | 100-300 | Specific signatures |
| Context7 docs | 500-1000 | Framework patterns |

**Rule**: Never read a file until MCP tools fail to answer the question.

## MCP Server Reference

| Server | Purpose | Best For |
|--------|---------|----------|
| **serena** | Code structure | Symbol navigation, references |
| **context7** | Framework docs | Best practices, API usage |
| **jetbrains** | IDE integration | Text search, file problems |
| **claude-mem** | Memory | Past decisions, context |

## Serena Workflow (Primary)

### Step 1: Understand Structure

```python
# Get file overview (no body content)
serena.get_symbols_overview(relative_path="src/services/")

# Result: Lists classes, functions with signatures only
# Token cost: ~200-500 (vs 5000+ for full file)
```

### Step 2: Find Specific Symbols

```python
# Find by name pattern
serena.find_symbol(
    name_path_pattern="UserService",
    depth=1,                    # Include immediate children
    include_body=False          # Signatures only
)

# Find with body (when needed)
serena.find_symbol(
    name_path_pattern="UserService/createUser",
    include_body=True           # Get implementation
)
```

### Step 3: Trace Dependencies

```python
# Who calls this method?
serena.find_referencing_symbols(
    name_path="createUser",
    relative_path="src/services/UserService.kt"
)

# Result: All callers with file:line references
```

### Step 4: Pattern Search

```python
# Find patterns across codebase
serena.search_for_pattern(
    substring_pattern=r"@Transactional",
    restrict_search_to_code_files=True,
    context_lines_before=1,
    context_lines_after=3
)
```

## Context7 Workflow (Framework Docs)

### Resolve Library First

```python
# Step 1: Get library ID
context7.resolve-library-id(
    query="How to configure transactions in Spring Boot",
    libraryName="spring-boot"
)
# Returns: /spring/spring-boot
```

### Query Documentation

```python
# Step 2: Get specific docs
context7.query-docs(
    libraryId="/spring/spring-boot",
    query="transaction propagation and isolation levels"
)
```

### Common Library IDs

| Library | ID |
|---------|-----|
| Spring Boot | `/spring/spring-boot` |
| React | `/facebook/react` |
| TanStack Query | `/tanstack/query` |
| Typer | `/tiangolo/typer` |
| pytest | `/pytest-dev/pytest` |

## JetBrains Integration

### Text Search (Fast)

```python
# Find text in files
jetbrains.search_in_files_by_text(
    searchText="@Repository",
    fileMask="*.kt",
    directoryToSearch="src/"
)
```

### File Problems

```python
# Get errors/warnings for file
jetbrains.get_file_problems(
    filePath="src/services/UserService.kt",
    errorsOnly=False  # Include warnings
)
```

### Symbol Info

```python
# Quick docs at position
jetbrains.get_symbol_info(
    filePath="src/services/UserService.kt",
    line=42,
    column=15
)
```

## Claude-mem (Memory Search)

```python
# Search past conversations
claude-mem.search(
    query="authentication decision",
    obs_type="decision"  # observation, decision, learning
)

# Recent context
claude-mem.get_recent_context()
```

## Decision Tree

```
Need to understand code?
├── What's in this file/directory?
│   └── serena.get_symbols_overview
├── How is X implemented?
│   └── serena.find_symbol(include_body=True)
├── Who uses X?
│   └── serena.find_referencing_symbols
├── Where is pattern X used?
│   └── serena.search_for_pattern
├── What's the best practice for X?
│   └── context7.query-docs
├── What did we decide before?
│   └── claude-mem.search
└── Is there a problem in this file?
    └── jetbrains.get_file_problems
```

## Progressive Disclosure Pattern

Always start narrow, expand only if needed:

```python
# Level 1: Structure only (cheapest)
serena.get_symbols_overview(relative_path="src/services/")

# Level 2: Signatures (if class identified)
serena.find_symbol("UserService", depth=1, include_body=False)

# Level 3: Specific method body (if needed)
serena.find_symbol("UserService/createUser", include_body=True)

# Level 4: Full file read (last resort)
Read(file_path="src/services/UserService.kt")
```

## Common Mistakes

| Mistake | Token Cost | Better Approach |
|---------|------------|-----------------|
| Read file first | High | Use serena.get_symbols_overview |
| Read all files in directory | Very High | Use serena.find_symbol with pattern |
| Multiple full file reads | Extreme | Use serena.find_referencing_symbols |
| Google for framework docs | Time waste | Use context7.query-docs |
| Ask user about past decision | Slow | Use claude-mem.search |

## MCP-First Checklist

Before reading ANY file, ask:

- [ ] Can `get_symbols_overview` answer this?
- [ ] Can `find_symbol` with signatures answer this?
- [ ] Do I need the full body, or just the signature?
- [ ] Is this a framework question? (use context7)
- [ ] Did we discuss this before? (use claude-mem)

## Token Savings Examples

| Task | Without MCP | With MCP | Savings |
|------|-------------|----------|---------|
| Find class structure | 5000 tokens | 300 tokens | 94% |
| Find all usages | 15000 tokens | 800 tokens | 95% |
| Understand API pattern | 3000 tokens | 500 tokens | 83% |
| Check framework docs | N/A | 600 tokens | Faster |
