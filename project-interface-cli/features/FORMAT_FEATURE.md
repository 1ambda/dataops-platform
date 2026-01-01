# FEATURE: Format Command (SQL + YAML 포맷팅)

> **Version:** 1.0.0
> **Status:** ✅ Complete (v0.9.0)
> **Priority:** P1 (High)
> **Last Updated:** 2026-01-01

---

## Implementation Status

**✅ Phase 1 (MVP) - Complete (v0.9.0)**

All core functionality implemented and tested. See [FORMAT_RELEASE.md](./FORMAT_RELEASE.md) for implementation details.

| Component | Status |
|-----------|--------|
| CLI Commands | ✅ `dli dataset format`, `dli metric format` |
| API Methods | ✅ `DatasetAPI.format()`, `MetricAPI.format()` |
| SQL Formatter | ✅ sqlfluff integration, Jinja preservation |
| YAML Formatter | ✅ ruamel.yaml, DLI key ordering |
| Error Codes | ✅ DLI-1501 to DLI-1506 |
| Test Coverage | ✅ 274 tests passing |

**Implementation Details:** [FORMAT_RELEASE.md](./FORMAT_RELEASE.md)

---

## 1. 개요

### 1.1 목적

Dataset/Metric Spec의 SQL 및 YAML 파일을 일관된 스타일로 자동 포맷팅하여 코드 품질과 가독성을 향상시킵니다.

**핵심 문제:**
- 팀원 간 SQL/YAML 코딩 스타일 불일치로 코드 리뷰 시 불필요한 논쟁 발생
- Jinja 템플릿이 포함된 SQL 포맷팅이 어려움 (일반 SQL 포맷터는 Jinja 문법 미지원)
- YAML 파일의 키 순서가 일관되지 않아 diff 비교 시 혼란 발생
- CI 파이프라인에서 포맷 검증 자동화 필요

**해결 방향:**
- `sqlfluff` 기반 SQL 포맷팅 (Jinja 네이티브 지원)
- `ruamel.yaml` 기반 YAML 포맷팅 (주석 보존, 키 순서 정렬)
- 기존 validate 커맨드와 유사한 리소스 기반 인터페이스
- `--check` 옵션으로 CI 파이프라인 통합

### 1.2 핵심 원칙

1. **리소스 기반**: `dli dataset format <name>` (validate 패턴과 일관성)
2. **Jinja 보존**: SQL 내 Jinja 템플릿(`{{ ref() }}`, `{{ ds }}`) 유지
3. **비파괴적**: 원본 파일 백업 후 수정 (--check로 검증만 가능)
4. **확장 가능**: 프로젝트별 `.sqlfluff` 설정 지원
5. **선택적 Lint**: `--lint` 옵션으로 린트 규칙 적용 (기본 비활성화)

### 1.3 주요 기능

All MVP features implemented in v0.9.0:

| 기능 | 설명 | Status |
|------|------|--------|
| SQL 포맷팅 | sqlfluff 기반, Jinja 템플릿 보존 | ✅ v0.9.0 |
| YAML 포맷팅 | DLI 표준 키 순서, 주석 보존 | ✅ v0.9.0 |
| 검증 모드 | `--check` (CI용, 변경 없이 검증) | ✅ v0.9.0 |
| 방언 지원 | BigQuery, Trino, Snowflake 등 | ✅ v0.9.0 |
| Lint 규칙 | `--lint` 옵션으로 추가 검증 | ✅ v0.9.0 |
| 자동 수정 | `--lint --fix` 조합 | ✅ v0.9.0 |

### 1.4 유사 도구 참조

| 도구 | 특징 | 참조 포인트 |
|------|------|------------|
| [SQLMesh format](https://sqlmesh.readthedocs.io/en/stable/reference/cli/#format) | `--check`, `--normalize`, `--transpile` | CI 통합 패턴 |
| [sqlfluff](https://docs.sqlfluff.com/) | Jinja 네이티브, 27개 방언 | 핵심 포맷팅 엔진 |
| [dbt SQL Style Guide](https://docs.getdbt.com/best-practices/how-we-style/2-how-we-style-our-sql) | 업계 표준 스타일 | 규칙 참조 |

---

## 2. 설계 결정

### 2.1 도구 선택: sqlfluff

**선택 근거:**

| 대안 | 장점 | 단점 | 결론 |
|------|------|------|------|
| **sqlfluff** | Jinja 네이티브, 27개 방언, Lint 통합 | 의존성 추가 | **선택** |
| sqlglot (기존) | 이미 사용 중, 가벼움 | Jinja 미지원, Lint 없음 | 보류 |
| sqlfmt | 빠름, 간단 | 방언 제한, Lint 없음 | 탈락 |

**sqlfluff 장점:**
- Jinja2 템플릿 네이티브 지원 (`templater = jinja`)
- BigQuery, Trino, Snowflake 등 27개 SQL 방언 지원
- Lint 규칙 내장 (포맷팅 + 품질 검증 통합)
- `.sqlfluff` 설정 파일로 프로젝트별 커스터마이징

### 2.2 인터페이스 패턴: 리소스 기반

**기존 패턴과의 일관성:**

| Command | Pattern | 예시 |
|---------|---------|------|
| `dli dataset validate` | 리소스 기반 | `dli dataset validate my_dataset` |
| `dli dataset format` | 리소스 기반 (NEW) | `dli dataset format my_dataset` |
| `dli transpile` | SQL 문자열/파일 기반 | `dli transpile --sql "SELECT 1"` |

**결정:** validate 패턴을 따라 리소스 기반으로 구현

### 2.3 핵심 결정 사항

| 결정 | 선택 | 근거 |
|------|------|------|
| 포맷팅 엔진 | sqlfluff | Jinja 네이티브, Lint 통합 |
| CLI 패턴 | 리소스 기반 (`dataset format`, `metric format`) | validate 패턴 일관성 |
| YAML 라이브러리 | ruamel.yaml | 주석 보존, round-trip 지원 |
| 기본 Lint | 비활성화 (`--lint`로 활성화) | 포맷팅과 린트 분리 |
| 방언 기본값 | 프로젝트 설정 또는 BigQuery | 가장 일반적인 사용 사례 |

---

## 3. 기능 명세 요약

**Note:** 상세 구현 내용은 [FORMAT_RELEASE.md](./FORMAT_RELEASE.md)를 참조하세요.

### 3.1 CLI 인터페이스

```bash
# Dataset/Metric 포맷팅 커맨드
dli dataset format <name> [OPTIONS]
dli metric format <name> [OPTIONS]

# 주요 옵션
--check           # CI용 검증 모드 (파일 수정 없음)
--sql-only        # SQL 파일만 포맷팅
--yaml-only       # YAML 파일만 포맷팅
--dialect <name>  # SQL 방언 지정 (bigquery, trino, snowflake 등)
--lint            # Lint 규칙 적용
--fix             # Lint 오류 자동 수정
--diff            # 변경 내역 diff 출력
```

### 3.2 Library API

```python
from dli import DatasetAPI, MetricAPI, ExecutionContext

# API 사용
api = DatasetAPI(context=ExecutionContext(project_path=Path(".")))
result = api.format(
    "catalog.schema.dataset",
    check_only=False,
    sql_only=False,
    yaml_only=False,
    dialect="bigquery",
    lint=False,
)
```

### 3.3 핵심 기능

| 기능 | 설명 |
|------|------|
| **SQL 포맷팅** | sqlfluff 기반, Jinja 템플릿 보존 ({{ ref() }}, {{ ds }}) |
| **YAML 포맷팅** | DLI 표준 키 순서, 주석 보존 (ruamel.yaml) |
| **Lint 규칙** | sqlfluff lint rules (선택적, --lint 옵션) |
| **다양한 방언** | BigQuery, Trino, Snowflake, Spark, Hive, PostgreSQL |
| **설정 파일** | .sqlfluff, .dli-format.yaml 계층적 로딩 |

### 3.4 에러 코드

DLI-15xx 범위 (6개 코드):
- DLI-1501: FORMAT_ERROR (일반 포맷팅 오류)
- DLI-1502: FORMAT_SQL_ERROR (SQL 포맷팅 실패)
- DLI-1503: FORMAT_YAML_ERROR (YAML 포맷팅 실패)
- DLI-1504: FORMAT_DIALECT_ERROR (지원하지 않는 방언)
- DLI-1505: FORMAT_CONFIG_ERROR (설정 파일 오류)
- DLI-1506: FORMAT_LINT_ERROR (Lint 규칙 위반)

---

## 12. 구현 우선순위

### ✅ Phase 1 (MVP) - Complete (v0.9.0)

All core functionality implemented:

1. **Core Formatters** ✅
   - SqlFormatter (sqlfluff wrapper)
   - YamlFormatter (ruamel.yaml wrapper)
   - FormatConfig (.sqlfluff, .dli-format.yaml)

2. **API Integration** ✅
   - DatasetAPI.format()
   - MetricAPI.format()
   - FormatResult models

3. **CLI Commands** ✅
   - `dli dataset format`
   - `dli metric format`
   - All options: --check, --sql-only, --yaml-only, --dialect, --lint, --fix, --diff

4. **Error Handling** ✅
   - DLI-1501 to DLI-1506 error codes
   - 6 exception classes

5. **Testing** ✅
   - 274 tests (239 new + existing)
   - Model, API, CLI, integration coverage

### Phase 2 (Future Enhancements)

Potential future improvements:

1. **Configuration Extensions**
   - Custom key ordering per project
   - Additional SQL dialects on demand

2. **CI/CD Integration**
   - GitHub Actions examples
   - pre-commit hook templates

3. **Performance**
   - `--parallel` for batch formatting
   - Incremental formatting (git diff based)

---

## Appendix: 주요 설계 결정

### A.1 포맷팅 엔진 선택

**결정:** sqlfluff (Jinja 네이티브 지원)

**근거:**
- SQLglot은 포맷팅 기능이 제한적이고 Jinja 미지원
- sqlfluff는 27개 방언과 Jinja 템플릿 지원
- Lint 기능 내장 (포맷팅 + 품질 검증 통합)

### A.2 CLI 패턴

**결정:** 리소스 기반 (`dli dataset format <name>`)

**근거:**
- 기존 validate 패턴과 일관성 유지
- 사용자 학습 곡선 최소화
- 리소스 중심 인터페이스 철학 유지

### A.3 API 구조

**결정:** 기존 DatasetAPI/MetricAPI 확장 (별도 FormatAPI 생성 안함)

**근거:**
- validate/run 등 기존 메서드와 일관성 유지
- API 클래스 수 증가 방지
- 리소스 중심 인터페이스 패턴 유지

### A.4 Lint 기본값

**결정:** 기본 비활성화 (`--lint`로 활성화)

**근거:**
- 포맷팅과 린트 분리
- 점진적 도입 지원
- CI에서 선택적 적용 가능

### A.5 YAML 키 순서

**결정:** DLI 표준 순서 (name -> owner -> team -> ...)

**근거:**
- 일관된 스타일로 코드 리뷰 효율화
- diff 비교 용이
- 명세서 구조와 일치

### A.6 에러 코드 범위

**결정:** DLI-15xx 범위 사용

**근거:**
- 기존 에러 코드 패턴 (0xx~14xx) 유지
- Format 전용 범위 확보
- 향후 확장 가능성 고려

### A.7 의존성 관리

**결정:** Optional dependencies (pip install dli[format])

**근거:**
- 포맷팅 기능을 필요로 하지 않는 사용자의 설치 부담 최소화
- sqlfluff와 ruamel.yaml은 비교적 큰 의존성
- 핵심 기능(validate, run)과 분리

---

**Document Complete**
