# RELEASE: MODEL Abstraction & CLI Restructuring

> **Version:** 1.0.0
> **Status:** Implemented
> **Release Date:** 2025-12-30

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **MODEL_FEATURE.md** | ✅ | MODEL 추상화 개념 정의 (metric + dataset) |
| **`dli render` 제거** | ✅ | `dli dataset run --dry-run --show-sql`로 대체 |
| **`dli validate` 제거** | ✅ | 서브커맨드만 유지 (`dli dataset validate`, `dli metric validate`) |
| **`dli server` → `dli config` 개명** | ✅ | 설정 관리 명령어로 개명 |
| **docs/PATTERNS.md 업데이트** | ✅ | 제거/변경된 커맨드 문서화 |

### 1.2 Files Created

| File | Lines | Purpose |
|------|-------|---------|
| `features/MODEL_FEATURE.md` | ~350 | MODEL 추상화 개념 명세 |
| `commands/config.py` | 149 | 설정 관리 명령어 (server.py 대체) |
| `tests/cli/test_config_cmd.py` | 66 | config 명령어 테스트 |

### 1.3 Files Modified

| File | Changes |
|------|---------|
| `main.py` | render, validate 제거; server → config 변경 |
| `commands/__init__.py` | render, validate, server_app 제거; config_app 추가 |
| `README.md` | 커맨드 목록 업데이트, 프로젝트 구조 반영 |
| `CONTRIBUTING.md` | 커맨드/테스트 디렉토리 구조 업데이트 |
| `docs/PATTERNS.md` | Deprecated/Removed Commands 섹션 추가 |

### 1.4 Files Deleted

| File | Reason |
|------|--------|
| `commands/render.py` | `dli dataset run --dry-run --show-sql`로 대체 |
| `commands/validate.py` | 서브커맨드만 유지 결정 |
| `commands/server.py` | `config.py`로 개명 |
| `tests/cli/test_server_cmd.py` | 삭제된 server 명령어 테스트 |
| `tests/cli/test_validate_cmd.py` | 삭제된 validate 명령어 테스트 |

---

## 2. MODEL Abstraction

### 2.1 개념 정의

**MODEL**은 metric과 dataset을 포괄하는 상위 추상 개념입니다.

```
MODEL (추상 개념)
├── metric (SELECT / READ 중심)
│   └── 목적: 측정, 집계, 분석
└── dataset (DML / WRITE 중심)
    └── 목적: 데이터 생성, 변환, 적재
```

### 2.2 공통 속성

| 속성 | metric | dataset | 비고 |
|------|--------|---------|------|
| `name` | ✅ | ✅ | 고유 식별자 |
| `owner` | ✅ | ✅ | 소유자 |
| `tags` | ✅ | ✅ | 분류 태그 |
| `sql` | ✅ | ✅ | SQL 정의 |
| `quality_tests` | ✅ | ✅ | 품질 테스트 |
| `materialization` | ❌ | ✅ | table/view/incremental |
| `schedule` | Optional | ✅ | 실행 스케줄 |

### 2.3 향후 통합 가능성

Phase 2+에서 metric과 dataset을 통합하는 MODEL 명령어 도입 검토:

```bash
# 미래 가능성 (Phase 2+)
dli model list              # metric + dataset 통합 조회
dli model validate <name>   # 타입 자동 감지 후 검증
```

---

## 3. Migration Guide

### 3.1 Deprecated Commands

| Before (v0.x) | After (v1.0.0) | Notes |
|---------------|----------------|-------|
| `dli render <name>` | `dli dataset run <name> --dry-run --show-sql` | 기능 통합 |
| `dli validate <name>` | `dli dataset validate <name>` or `dli metric validate <name>` | 서브커맨드로 이동 |
| `dli server show` | `dli config show` | 명령어 개명 |
| `dli server status` | `dli config status` | 명령어 개명 |

### 3.2 Migration Examples

```bash
# Before
dli render daily_users

# After
dli dataset run daily_users --dry-run --show-sql
```

```bash
# Before
dli validate daily_users

# After
dli dataset validate daily_users
# or
dli metric validate daily_active_users
```

```bash
# Before
dli server show

# After
dli config show
```

---

## 4. Usage Guide

### 4.1 `dli config` Command

```bash
# Show current configuration
dli config show
dli config show --format json

# Check server status
dli config status
```

#### Options

| Option | Description | Default |
|--------|-------------|---------|
| `--path, -p` | Project path | Current directory |
| `--format, -f` | Output format (table/json) | `table` |

### 4.2 Validation Commands

```bash
# Dataset validation
dli dataset validate <name>
dli dataset validate <name> --path /path/to/project

# Metric validation
dli metric validate <name>
```

### 4.3 SQL Preview (replaces `dli render`)

```bash
# Show rendered SQL without execution
dli dataset run <name> --dry-run --show-sql

# With date parameter
dli dataset run <name> --ds 2024-01-15 --dry-run --show-sql
```

---

## 5. CLI Command Structure (v1.0.0)

```
dli
├── version / info              # Basic info commands
├── config (show, status)       # Configuration management [NEW: renamed from server]
├── metric (list, get, run, validate, register)
├── dataset (list, get, run, validate, register)
├── workflow (run, backfill, stop, status, list, history, pause, unpause)
├── quality (list, run, show)
├── lineage (show, upstream, downstream)   # Top-level
├── catalog                                # Top-level
└── transpile                              # Top-level
```

---

## 6. Decision Rationale

### 6.1 Why Remove `dli render`?

- **중복 기능**: `dli dataset run --dry-run --show-sql`이 동일한 기능 제공
- **유지보수 비용**: 별도 명령어 유지 필요 없음
- **사용자 혼란 감소**: 단일 진입점(`dataset run`)으로 통일

### 6.2 Why Remove Top-level `dli validate`?

- **명확한 컨텍스트**: `dli dataset validate` vs `dli metric validate`로 대상 명확화
- **확장성**: 각 리소스 타입별 검증 로직 분리 용이
- **일관성**: 다른 명령어들도 리소스별 서브커맨드 구조 사용

### 6.3 Why Rename `dli server` to `dli config`?

- **명확한 목적**: "server"보다 "config"가 설정 관리 의도 명확
- **사용자 친화성**: 설정 확인/관리에 적합한 이름
- **표준 CLI 관례**: `kubectl config`, `git config` 등과 일관성

---

## 7. Test Results

```bash
$ cd project-interface-cli && uv run pytest tests/cli/test_config_cmd.py -v

6 passed in 0.52s
```

### Test Coverage

| Test Class | Tests | Description |
|------------|-------|-------------|
| `TestConfigShow` | 3 | show 명령어 테스트 |
| `TestConfigStatus` | 2 | status 명령어 테스트 |

---

## 8. Quality Metrics

| Metric | Value |
|--------|-------|
| pyright errors | 0 |
| ruff violations | 0 |
| Test pass rate | 100% |
| Files removed | 5 |
| Files created | 3 |
| Files modified | 5 |

---

## 9. Review Summary

### Requirements Discovery (2025-12-30)

**Interview Rounds:** 4
**Agent Used:** expert-spec (requirements-discovery skill)

**Key Decisions:**
1. metric/dataset 분리 구조 유지 (현재 구조 선호)
2. render 명령어 제거 (dataset run --show-sql로 통합)
3. validate 서브커맨드만 유지
4. server → config 개명
5. workflow는 현재 Dataset 전용, 미래 MODEL 통합 지원 설계
6. MODEL = metric + dataset의 추상 개념으로 정의

**Documentation Updated:**
- MODEL_FEATURE.md 생성
- README.md 업데이트
- CONTRIBUTING.md 업데이트
- docs/PATTERNS.md Deprecated Commands 섹션 추가

---

**Last Updated:** 2025-12-30
