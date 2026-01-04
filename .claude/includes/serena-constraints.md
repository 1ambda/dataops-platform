# Serena Token Constraints (CRITICAL)

> **WARNING: Ignoring these rules causes 20k+ token responses that fill context quickly!**

## search_for_pattern Rules (MANDATORY)

| Parameter | REQUIRED Value | Why |
|-----------|----------------|-----|
| `context_lines_before` | `0` | Each +1 = 2x tokens |
| `context_lines_after` | `0-2` max | Each +1 = 2x tokens |
| `relative_path` | ALWAYS specify | Scope to subdirectory |
| `max_answer_chars` | `3000-5000` | Hard limit output |

### BAD Examples (Will Trigger 20k+ Token Warning)

```python
# BAD: No scope, no limits
search_for_pattern(substring_pattern=r".*Controller.*")

# BAD: High context lines
search_for_pattern(substring_pattern=r"@Service", context_lines_after=10)

# BAD: OR pattern without limits
search_for_pattern(substring_pattern=r"import.*DtoA|import.*DtoB|import.*DtoC")

# BAD: Using for file search (wrong tool!)
search_for_pattern(substring_pattern=r".*Mapper\.kt")
```

### GOOD Examples

```python
# GOOD: Scoped, limited, minimal context
search_for_pattern(
    substring_pattern=r"@Transactional",
    relative_path="module-core-domain/",
    context_lines_before=0,
    context_lines_after=1,
    max_answer_chars=3000
)

# GOOD: Single pattern, specific directory
search_for_pattern(
    substring_pattern=r"class.*Service\(",
    relative_path="module-core-domain/service/",
    max_answer_chars=4000
)
```

## Use Correct Tool for Task

| Need | WRONG Tool | CORRECT Tool |
|------|------------|--------------|
| Find files by name | `search_for_pattern(r".*Mapper\.kt")` | `find_file(file_mask="*Mapper.kt")` |
| Find imports | Broad OR patterns | Single import + specific path |
| File structure | `search_for_pattern` | `get_symbols_overview` |
| Method signatures | `search_for_pattern` | `find_symbol(include_body=False)` |

## Token Cost Reference

| Query Type | Est. Tokens | Status |
|------------|-------------|--------|
| `find_symbol` (no body) | 100-300 | OK |
| `get_symbols_overview` | 200-500 | OK |
| `search_for_pattern` (scoped, limited) | 500-2000 | OK |
| `search_for_pattern` (broad, no limits) | 15,000-50,000 | DANGER |

## Quick Checklist Before search_for_pattern

```
[ ] relative_path is set (not empty)
[ ] context_lines_after <= 2
[ ] max_answer_chars is set (3000-5000)
[ ] NOT searching for file names (use find_file instead)
[ ] NOT using broad OR patterns
```
