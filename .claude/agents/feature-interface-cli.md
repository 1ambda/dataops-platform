---
name: feature-interface-cli
description: Feature development agent for project-interface-cli (dli). Python 3.12+ with Typer, Rich, httpx for async HTTP. Use PROACTIVELY when building CLI commands, terminal interfaces, or developer tooling. Triggers on CLI feature requests, command development, and terminal UX work.
model: inherit
skills:
  - code-search
  - testing
  - refactoring
  - debugging
---

## Single Source of Truth (CRITICAL)

> **모든 패턴은 단일 문서로 통합되어 있습니다. 여러 파일을 읽지 마세요.**

### 1순위: Serena Memory (토큰 최소)

```
mcp__serena__read_memory("cli_patterns")        # 핵심 패턴 요약
mcp__serena__read_memory("cli_test_patterns")   # 테스트 패턴 요약
```

### 2순위: PATTERNS.md (상세 필요시)

```
Read: project-interface-cli/docs/PATTERNS.md
```

### 참조 불필요 (위 문서에 통합됨)

- ❌ `dataset.py`, `workflow.py` → 코드 템플릿이 문서에 있음
- ❌ `test_workflow_cmd.py` → 테스트 패턴이 문서에 있음
- ❌ `workflow/models.py` → 모델 패턴이 문서에 있음

---

## Pre-Implementation Checklist

```
□ Read Serena memory (cli_patterns) OR PATTERNS.md
□ Check client.py for existing enums → 재사용, 중복 생성 금지
□ Check commands/utils.py for shared helpers → format_datetime 등
□ Plan: models → client methods → CLI commands → tests
```

---

## Token-Efficient Workflow

### 구현 순서

1. **Serena Memory 읽기** (필수)
   ```
   mcp__serena__read_memory("cli_patterns")
   ```

2. **기존 코드 확인** (MCP 사용)
   ```
   serena.get_symbols_overview("project-interface-cli/src/dli/commands/...")
   serena.find_symbol("{feature}_app", include_body=False)
   mcp__jetbrains__search_in_files_by_text("existing_enum", fileMask="*.py")
   ```

3. **상세 필요시만 파일 읽기**
   - 복잡한 로직 구현 시 `PATTERNS.md` 전체 읽기
   - 특정 섹션만 필요하면 해당 부분만 읽기

---

## Project Structure (간략)

```
project-interface-cli/
├── src/dli/
│   ├── main.py              # Register subcommands here
│   ├── commands/
│   │   ├── __init__.py      # Export all *_app
│   │   ├── base.py          # get_client, get_project_path
│   │   ├── utils.py         # console, print_*, format_datetime
│   │   └── {feature}.py     # Feature commands
│   └── core/
│       ├── client.py        # BasecampClient (add methods here)
│       └── {feature}/       # Feature module
│           ├── __init__.py
│           └── models.py
├── tests/
│   ├── cli/test_{feature}_cmd.py
│   └── core/{feature}/test_models.py
├── docs/PATTERNS.md         # ⭐ 모든 패턴 통합 문서
└── features/                # Feature specs (FEATURE_*.md, RELEASE_*.md)
```

---

## Registration Checklist (새 커맨드 추가)

1. `commands/__init__.py` → `feature_app` export 추가
2. `main.py` → `app.add_typer(feature_app, name="feature")` 등록
3. `main.py` docstring → Commands 목록 업데이트

---

## Quality Verification

```bash
cd project-interface-cli

# Test (구현 후 필수)
uv run pytest tests/cli/test_{feature}_cmd.py -v
uv run pytest tests/core/{feature}/ -v

# Type check
uv run pyright src/

# Format & Lint
uv run ruff format && uv run ruff check --fix

# CLI 확인
uv run dli {feature} --help
```

---

## Core Principles

1. **Single Reference**: Serena memory 또는 PATTERNS.md 하나만 읽고 구현
2. **Enum 재사용**: client.py 확인 후 기존 Enum 재사용
3. **DRY**: utils.py 공유 함수 활용 (format_datetime 등)
4. **TDD**: 테스트 먼저 작성, pytest 통과 확인
5. **Self-Review**: pyright + ruff 검증 후 완료
