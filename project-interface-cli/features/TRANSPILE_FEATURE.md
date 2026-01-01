# FEATURE: SQL Transpile 기능

> **Version:** 1.2.0
> **Status:** Refactored to Subcommands
> **Last Updated:** 2026-01-01

### Recent Updates (2026-01-01)

| Item | Status | Description |
|------|--------|-------------|
| **Refactoring to Subcommands** | ✅ Done | `dli dataset transpile` / `dli metric transpile` |
| **~~Top-level Command~~** | ⚠️ **Removed** | `dli transpile` removed in v1.2.0 |
| **Jinja Integration** | ✅ Done | `TranspileEngine.transpile(sql, jinja_context=...)` 지원 |
| **--transpile-retry CLI** | ✅ Done | `--transpile-retry [0-5]` 옵션 추가 |

---

## 1. 개요

### 1.1 목적

`dli transpile` 및 `dli dataset run` 커맨드는 SQL 변환(Transpile) 기능을 제공합니다. 사용자의 SQL을 Basecamp Server에서 정의된 규칙에 따라 변환하여 실행하거나, 디버깅 목적으로 변환된 SQL을 확인할 수 있습니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **서버 규칙 기반** | Transpile 규칙은 Basecamp Server에서 조회, CLI는 SQLGlot으로 변환 수행 |
| **암시적 적용** | `dataset run` 실행 시 자동으로 Transpile 적용 |
| **Graceful Degradation** | 규칙 조회 실패 시 원본 SQL 실행 + 경고 출력 |
| **확장 가능 설계** | Multi-dialect, Full Metric Expansion 등 향후 확장 고려 |

### 1.3 주요 기능

- **테이블 치환 (Table Substitution)**: 소스 테이블을 타겟 테이블로 치환 (Explicit Mapping)
- **SQL 최적화 가이드**: CTE 구조, LIMIT 미사용 등 경고 출력 (Advisory Only)
- **Semantic Layer**: `METRIC(name)` 함수를 실제 SQL 표현식으로 치환
- **라이브러리 API**: `TranspileEngine` 클래스로 프로그래매틱 사용 지원

### 1.4 유사 도구 참조

| 도구 | 참조 포인트 | 출처 |
|------|-------------|------|
| **[SQLGlot](https://github.com/tobymao/sqlglot)** | AST 기반 SQL 파싱, 31개 다이얼렉트 변환 | Python 라이브러리 |
| **[SQLMesh](https://sqlmesh.com/)** | AST 레벨 시맨틱 이해, Column-level Lineage | Tobiko Data |
| **[dbt Semantic Layer](https://www.getdbt.com/blog/dbt-semantic-layer)** | 메트릭 정의, YAML 기반 메트릭 관리 | dbt Labs |
| **[Cube.dev](https://cube.dev/)** | Headless Semantic Layer, LookML 대안 | Open Source |

---

## 2. 아키텍처

### 2.1 컴포넌트 관계

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLI Flow                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │ User SQL    │───▶│ Jinja Render     │───▶│ TranspileEngine│  │
│  │ (Inline/    │    │ (Local)          │    │ (SQLGlot)     │  │
│  │  File)      │    └──────────────────┘    └───────┬───────┘  │
│  └─────────────┘                                    │          │
│                                                     ▼          │
│                              ┌──────────────────────────────┐  │
│                              │ Basecamp Server API          │  │
│                              │ - GET /transpile/rules       │  │
│                              │ - GET /metrics/{name}/sql    │  │
│                              └──────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                          UI Flow                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │ User SQL    │───▶│ Basecamp Server  │───▶│ Basecamp      │  │
│  │ (Web UI)    │    │ API              │    │ Parser        │  │
│  └─────────────┘    └──────────────────┘    │ (SQLGlot)     │  │
│                                              └───────────────┘  │
│                                                                 │
│  Note: CLI와 Parser는 직접 통신하지 않음                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 핵심 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| Transpile 실행 위치 | CLI 내 SQLGlot 직접 사용 | 네트워크 왕복 최소화, 오프라인 부분 지원 |
| 규칙 저장소 | Basecamp Server | 중앙 집중식 관리, 규칙 변경 시 CLI 재배포 불필요 |
| 규칙 캐싱 | 없음 (Always Fetch) | 항상 최신 규칙 보장, 단순화 |
| CLI ↔ Parser 통신 | 불가 | 아키텍처 분리, UI 경유 시에만 Parser 사용 |
| 개발 순서 | Mock-first (CLI 선 개발) | Server API 미완성 상태에서 CLI 독립 개발 가능 |
| 성능 최적화 | 고려하지 않음 | BigQuery/Trino 쿼리(초~분) 대비 Transpile 지연(~3초)은 무시 가능 |

### 2.3 기존 시스템 통합 지점

| 통합 영역 | 기존 패턴 | 새 기능 적용 |
|-----------|-----------|-------------|
| **CLI 커맨드** | `commands/dataset.py`, `workflow.py` | `commands/transpile.py` 신규 + `dataset.py` 확장 |
| **출력 유틸리티** | `commands/utils.py` (print_error, print_sql) | Rich 패턴 재사용 |
| **옵션/Enum** | `base.py` (ListOutputFormat, SourceType) | `Dialect` enum 추가 |
| **API 클라이언트** | `core/client.py` (BasecampClient) | `transpile_get_rules()`, `transpile_get_metric_sql()` 메서드 추가 |
| **Core 모듈** | `core/workflow/`, `core/quality/` 구조 | `core/transpile/` 신규 모듈 |

### 2.4 개발 불확실성 및 해결 전략

| 불확실성 | 심각도 | 해결 전략 |
|----------|--------|-----------|
| Server API 미구현 | 🔴 높음 | Mock 데이터로 CLI 선 개발 → 나중에 통합 |
| SQLglot 버전 동기화 | 🟡 중간 | Parser(28.5)와 동일 버전 권장, 호환성 테스트 필수 |
| METRIC() 함수 파싱 | 🟡 중간 | SQLglot 커스텀 함수 vs 문자열 치환 → 문자열 치환 우선 (단순성) |
| Jinja → Transpile 순서 | 🟢 낮음 | 현재 스펙대로 Jinja 먼저 → Transpile 적용 |
| 규칙 충돌 처리 | 🟢 낮음 | 에러 발생 (충돌 방지) - 규칙 생성 시 검증 |

---

## 3. Use Cases

### 3.1 Use-case 1: 테이블 치환 (Table Substitution)

사용자의 SQL 내 테이블을 규칙에 따라 다른 테이블로 치환합니다.

**예시:**
```sql
-- 원본 SQL
SELECT * FROM analytics.users WHERE created_at > '2024-01-01'

-- 치환 규칙: analytics.users → analytics.users_v2
-- 변환 후
SELECT * FROM analytics.users_v2 WHERE created_at > '2024-01-01'
```

**규칙 형식 (Explicit Mapping Table):**
```json
{
  "rules": [
    {
      "id": "rule-001",
      "type": "table_substitution",
      "source": "analytics.users",
      "target": "analytics.users_v2",
      "enabled": true,
      "description": "Users table migration to v2"
    }
  ]
}
```

### 3.2 Use-case 2: SQL 최적화 가이드 (Advisory)

SQL 패턴을 분석하여 개선 권고사항을 경고로 출력합니다. **SQL 자체는 변경하지 않습니다.**

| 감지 패턴 | 경고 메시지 |
|-----------|-------------|
| `SELECT *` | `Warning: Consider specifying columns instead of SELECT *` |
| LIMIT 없는 대용량 조회 | `Warning: No LIMIT clause detected. Consider adding LIMIT for safety.` |
| 중복 CTE 정의 | `Warning: Duplicate CTE 'cte_name' detected. Consider refactoring.` |
| 비효율적 서브쿼리 | `Warning: Correlated subquery detected. Consider using JOIN.` |

### 3.3 Use-case 3: Semantic Layer (METRIC 함수)

`METRIC(name)` 가상 함수와 `__semantic.__table` 가상 테이블을 지원합니다.

**예시:**
```sql
-- 원본 SQL (가상 문법)
SELECT
  ds,
  METRIC(total_orders_from_active_customers)
FROM __semantic.__table
GROUP BY ds

-- 변환 후 (Server에서 metric SQL 조회)
SELECT
  ds,
  SUM(CASE WHEN customer_status = 'active' THEN order_count ELSE 0 END) AS total_orders_from_active_customers
FROM analytics.orders
GROUP BY ds
```

**MVP 범위:**
- `METRIC(name)` → Simple Substitution (Server-resolved SQL 표현식으로 치환)
- 확장 가능 설계: dimensions, time_grains, filters 지원 (Phase 2+)

### 3.4 METRIC() 함수 파싱 알고리즘

> **⚠️ P0 설계 결정: 파싱 방식 선택**
>
> | 방식 | 장점 | 단점 | 결정 |
> |------|------|------|------|
> | SQLGlot 커스텀 함수 | AST 정확성, 위치 정보 | 복잡한 구현, SQLGlot 버전 종속 | ❌ |
> | 정규식 문자열 치환 | 단순함, 빠른 구현 | Edge case 취약 | ✅ MVP |
>
> **MVP는 정규식 문자열 치환을 사용합니다.** (Phase 2에서 SQLGlot 커스텀 함수 고려)

#### 3.4.1 파싱 알고리즘 (MVP)

```python
import re
from typing import NamedTuple

class MetricMatch(NamedTuple):
    """METRIC() 함수 매치 결과"""
    full_match: str      # "METRIC(revenue)"
    metric_name: str     # "revenue"
    start_pos: int       # 시작 위치
    end_pos: int         # 끝 위치

# 정규식 패턴: METRIC('name') 또는 METRIC("name") 또는 METRIC(name)
METRIC_PATTERN = re.compile(
    r"METRIC\s*\(\s*(?:'([^']+)'|\"([^\"]+)\"|([a-zA-Z_][a-zA-Z0-9_]*))\s*\)",
    re.IGNORECASE
)

def find_metric_functions(sql: str) -> list[MetricMatch]:
    """SQL에서 METRIC() 함수를 모두 찾아 반환"""
    matches = []
    for match in METRIC_PATTERN.finditer(sql):
        # 세 가지 캡처 그룹 중 하나에서 이름 추출
        metric_name = match.group(1) or match.group(2) or match.group(3)
        matches.append(MetricMatch(
            full_match=match.group(0),
            metric_name=metric_name,
            start_pos=match.start(),
            end_pos=match.end(),
        ))
    return matches

def expand_metrics(sql: str, metric_resolver: Callable[[str], str | None]) -> tuple[str, list[str]]:
    """
    METRIC() 함수를 실제 SQL 표현식으로 치환

    Returns:
        tuple[str, list[str]]: (치환된 SQL, 에러 목록)
    """
    matches = find_metric_functions(sql)
    errors: list[str] = []

    # MVP 제한: SQL당 1개의 METRIC() 함수만 허용
    if len(matches) > 1:
        errors.append(f"MVP limitation: Only 1 METRIC() per SQL allowed, found {len(matches)}")
        return sql, errors

    if not matches:
        return sql, errors

    # 역순으로 치환 (위치 인덱스 보존)
    result = sql
    for match in reversed(matches):
        expression = metric_resolver(match.metric_name)
        if expression is None:
            errors.append(f"Metric '{match.metric_name}' not found")
            continue
        result = result[:match.start_pos] + expression + result[match.end_pos:]

    return result, errors
```

#### 3.4.2 Edge Case 처리

| Case | 입력 | 처리 |
|------|------|------|
| 문자열 내 METRIC | `"SELECT 'METRIC(x)' FROM t"` | 무시 (문자열 리터럴) - **MVP: 미지원, Phase 2** |
| 주석 내 METRIC | `"-- METRIC(x)\nSELECT 1"` | 무시 (주석) - **MVP: 미지원, Phase 2** |
| 중첩 괄호 | `METRIC((revenue))` | 에러 (잘못된 문법) |
| 공백 포함 | `METRIC( revenue )` | 정상 처리 |
| 대소문자 혼용 | `Metric(Revenue)` | 정상 처리 (case-insensitive 매칭, name은 원본 유지) |
| 미존재 메트릭 | `METRIC(unknown)` | **에러 반환** (Silent 아님) |
| 복수 METRIC | `METRIC(a) + METRIC(b)` | **MVP: 에러** (1개만 허용) |

#### 3.4.3 에러 메시지 예시

```python
# 미존재 메트릭
TranspileError: Metric 'unknown_metric' not found.
  Available metrics: revenue, orders, users (use `dli metric list` to see all)

# MVP 제한 초과
TranspileError: MVP limitation: Only 1 METRIC() function per SQL allowed.
  Found 2 METRIC() calls: METRIC(revenue), METRIC(orders)
  Hint: Split into separate queries or wait for Phase 2 support.
```

---

## 4. CLI 설계

### 4.1 커맨드 구조

#### 4.1.1 `dli dataset run` (암시적 Transpile)

> **⚠️ 설계 결정: 기존 `dataset run` 시그니처와의 호환성**
>
> 기존 `run_dataset(name: str)` 메서드는 Dataset Spec 이름으로 실행하는 방식입니다.
> 새로운 `--sql` 옵션은 **별도의 실행 경로**로 처리됩니다:
> - `dli dataset run <name>` → 기존: Spec 기반 실행 (변경 없음)
> - `dli dataset run --sql "..."` → 신규: Ad-hoc SQL 실행 (Transpile 적용)
>
> 두 옵션은 상호 배타적이며, 동시 사용 시 에러 반환합니다.

```bash
# 기존 방식 (Spec 기반 - 변경 없음)
dli dataset run my_dataset_spec

# 신규 방식 (Ad-hoc SQL - Transpile 적용)
dli dataset run --sql "SELECT * FROM analytics.users"

# 파일 기반 실행
dli dataset run -f query.sql

# Transpile 관련 옵션 (--sql 또는 -f 사용 시에만 유효)
dli dataset run --sql "..." --transpile-strict    # Strict 모드 (변환 실패 시 에러)
dli dataset run --sql "..." --transpile-retry 3   # 재시도 횟수 (기본: 1)
dli dataset run --sql "..." --no-transpile        # Transpile 비활성화
```

**구현 시그니처:**
```python
@dataset_app.command("run")
def run_dataset(
    name: Annotated[str | None, typer.Argument(help="Dataset spec name")] = None,
    sql: Annotated[str | None, typer.Option("--sql", help="Ad-hoc SQL (mutually exclusive with name)")] = None,
    file: Annotated[Path | None, typer.Option("-f", "--file", help="SQL file path")] = None,
    transpile_strict: Annotated[bool, typer.Option("--transpile-strict")] = False,
    # ...
):
    # 상호 배타 검증
    if name and (sql or file):
        raise typer.BadParameter("Cannot use both spec name and --sql/--file")
    if not name and not sql and not file:
        raise typer.BadParameter("Either spec name or --sql/--file required")
```

#### 4.1.2 `dli dataset transpile` (Spec-based Transpile)

```bash
# Transpile dataset SQL
dli dataset transpile iceberg.analytics.daily_clicks

# 옵션
dli dataset transpile iceberg.analytics.daily_clicks --validate
dli dataset transpile iceberg.analytics.daily_clicks --strict
dli dataset transpile iceberg.analytics.daily_clicks --format json
dli dataset transpile iceberg.analytics.daily_clicks --show-rules
dli dataset transpile iceberg.analytics.daily_clicks --dialect trino
```

#### 4.1.3 `dli metric transpile` (Spec-based Transpile)

```bash
# Transpile metric SQL
dli metric transpile iceberg.analytics.daily_active_users

# 옵션
dli metric transpile iceberg.analytics.daily_active_users --validate
dli metric transpile iceberg.analytics.daily_active_users --strict
dli metric transpile iceberg.analytics.daily_active_users --format json
```

#### 4.1.4 ~~`dli transpile`~~ (Deprecated in v1.2.0)

⚠️ **This command has been removed.** Use resource-specific subcommands instead:

| Old Usage | New Usage |
|-----------|-----------|
| `dli transpile "SELECT ..."` | Use `TranspileAPI` or `dataset run --sql` |
| `dli transpile -f query.sql` | Use `TranspileAPI` or `dataset run --sql` |

### 4.2 공통 옵션

| 옵션 | 단축 | 설명 | 기본값 |
|------|------|------|--------|
| `--transpile-strict` | | 변환 실패 시 에러 반환 | `false` |
| `--transpile-retry` | | API 재시도 횟수 | `1` |
| `--no-transpile` | | Transpile 비활성화 | `false` |
| `--dialect` | `-d` | 입력 SQL 다이얼렉트 | `trino` |

### 4.3 Transpile 서브커맨드 전용 옵션

| 옵션 | 설명 |
|------|------|
| `--validate` | SQLGlot 문법 검증 수행 |
| `--show-rules` | 적용된 규칙 상세 출력 |
| `--format` | 출력 형식 (`table`/`json`) |

### 4.4 출력 예시

#### 성공 시 (Silent + Log)
```
$ dli dataset run --sql "SELECT * FROM analytics.users"
[INFO] Transpile applied: 1 table substitution, 2 warnings
[WARN] No LIMIT clause detected. Consider adding LIMIT for safety.
[WARN] Consider specifying columns instead of SELECT *

Executing query...
✓ Query completed in 2.3s (1,234 rows)
```

#### `dli dataset transpile` (Spec-based)
```
$ dli dataset transpile iceberg.analytics.daily_clicks --show-rules

┌─────────────────────────────────────────────────────────────┐
│ Transpile Result: iceberg.analytics.daily_clicks            │
├─────────────────────────────────────────────────────────────┤
│ Original SQL:                                               │
│   SELECT * FROM analytics.users                             │
├─────────────────────────────────────────────────────────────┤
│ Transpiled SQL:                                             │
│   SELECT * FROM analytics.users_v2                          │
├─────────────────────────────────────────────────────────────┤
│ Applied Rules:                                              │
│   • [rule-001] Table Substitution: analytics.users → users_v2│
├─────────────────────────────────────────────────────────────┤
│ Warnings:                                                   │
│   ⚠ No LIMIT clause detected                                │
│   ⚠ Consider specifying columns instead of SELECT *         │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. 라이브러리 API

### 5.1 TranspileEngine 클래스

```python
from dli.core.transpile import TranspileEngine, TranspileConfig, TranspileResult

# 설정 생성
config = TranspileConfig(
    dialect="trino",
    strict_mode=False,
    validate=True,
    retry_count=1,
)

# 엔진 초기화
engine = TranspileEngine(config)

# SQL 변환
result: TranspileResult = engine.transpile(
    sql="SELECT * FROM analytics.users",
    context={"environment": "production"},  # 선택적 컨텍스트
)

# 결과 접근
print(result.sql)           # 변환된 SQL
print(result.applied_rules) # 적용된 규칙 목록
print(result.warnings)      # 경고 목록
print(result.metadata)      # 메타데이터 (audit trail)
print(result.to_json())     # JSON 직렬화
```

### 5.2 데이터 모델

```python
from pydantic import BaseModel, Field
from datetime import datetime
from enum import Enum

__all__ = [
    "TranspileConfig",
    "TranspileResult",
    "TranspileRule",
    "TranspileWarning",
    "RuleType",
    "WarningType",
    "Dialect",
]


class Dialect(str, Enum):
    """지원 SQL 다이얼렉트"""
    TRINO = "trino"
    BIGQUERY = "bigquery"


class RuleType(str, Enum):
    """Transpile 규칙 타입"""
    TABLE_SUBSTITUTION = "table_substitution"
    METRIC_EXPANSION = "metric_expansion"


class WarningType(str, Enum):
    """경고 타입"""
    NO_LIMIT = "no_limit"
    SELECT_STAR = "select_star"
    DUPLICATE_CTE = "duplicate_cte"
    CORRELATED_SUBQUERY = "correlated_subquery"


class TranspileConfig(BaseModel):
    """Transpile 엔진 설정"""
    dialect: Dialect = Field(default=Dialect.TRINO, description="입력 SQL 다이얼렉트")
    strict_mode: bool = Field(default=False, description="Strict 모드 (실패 시 예외)")
    validate: bool = Field(default=False, description="문법 검증 수행")
    retry_count: int = Field(default=1, ge=0, le=5, description="API 재시도 횟수")
    server_url: str | None = Field(default=None, description="Basecamp Server URL (None=자동)")


class TranspileRule(BaseModel):
    """적용된 Transpile 규칙"""
    id: str = Field(..., description="규칙 ID")
    type: RuleType = Field(..., description="규칙 타입")
    source: str = Field(..., description="원본 (테이블명 또는 함수명)")
    target: str = Field(..., description="변환 결과")
    description: str | None = Field(default=None, description="규칙 설명")


class TranspileWarning(BaseModel):
    """Transpile 경고"""
    type: WarningType = Field(..., description="경고 타입")
    message: str = Field(..., description="경고 메시지")
    line: int | None = Field(default=None, description="해당 라인 번호")
    column: int | None = Field(default=None, description="해당 컬럼 번호")


class TranspileMetadata(BaseModel):
    """Audit Trail 메타데이터"""
    original_sql: str = Field(..., description="원본 SQL")
    transpiled_at: datetime = Field(..., description="변환 시각")
    dialect: Dialect = Field(..., description="사용된 다이얼렉트")
    rules_version: str | None = Field(default=None, description="규칙 버전 (Server 제공)")
    duration_ms: int = Field(..., description="변환 소요 시간 (ms)")


class TranspileResult(BaseModel):
    """Transpile 결과"""
    success: bool = Field(..., description="성공 여부")
    sql: str = Field(..., description="변환된 SQL (실패 시 원본)")
    applied_rules: list[TranspileRule] = Field(default_factory=list, description="적용된 규칙")
    warnings: list[TranspileWarning] = Field(default_factory=list, description="경고 목록")
    metadata: TranspileMetadata = Field(..., description="메타데이터")
    error: str | None = Field(default=None, description="에러 메시지 (실패 시)")

    def to_json(self) -> str:
        """JSON 직렬화"""
        return self.model_dump_json(indent=2)
```

---

## 6. Basecamp API

### 6.1 엔드포인트

| 동작 | Method | Endpoint | 설명 |
|------|--------|----------|------|
| 규칙 조회 | GET | `/api/v1/transpile/rules` | 전체 Transpile 규칙 목록 |
| 메트릭 SQL 조회 | GET | `/api/v1/metrics/{name}/sql` | 메트릭의 SQL 표현식 조회 |

### 6.2 요청/응답 예시

#### GET /api/v1/transpile/rules

**Request:**
```http
GET /api/v1/transpile/rules HTTP/1.1
Authorization: Bearer <token>
```

**Response:**
```json
{
  "rules": [
    {
      "id": "rule-001",
      "type": "table_substitution",
      "source": "analytics.users",
      "target": "analytics.users_v2",
      "enabled": true,
      "description": "Users table migration to v2",
      "created_at": "2025-01-01T00:00:00Z"
    }
  ],
  "version": "2025-01-01-001"
}
```

#### GET /api/v1/metrics/{name}/sql

**Request:**
```http
GET /api/v1/metrics/total_orders_from_active_customers/sql HTTP/1.1
Authorization: Bearer <token>
```

**Response:**
```json
{
  "name": "total_orders_from_active_customers",
  "sql_expression": "SUM(CASE WHEN customer_status = 'active' THEN order_count ELSE 0 END)",
  "source_table": "analytics.orders",
  "description": "Total orders from active customers"
}
```

### 6.3 클라이언트 메서드 (BasecampClient 확장)

```python
# client.py에 추가
def transpile_get_rules(self) -> ServerResponse:
    """Transpile 규칙 조회"""
    if self.mock_mode:
        return ServerResponse(success=True, data=self._mock_data.get("transpile_rules", []))
    return self._get("/api/v1/transpile/rules")

def transpile_get_metric_sql(self, metric_name: str) -> ServerResponse:
    """메트릭 SQL 표현식 조회"""
    if self.mock_mode:
        metrics = self._mock_data.get("metrics", {})
        if metric_name in metrics:
            return ServerResponse(success=True, data=metrics[metric_name])
        return ServerResponse(success=False, error=f"Metric '{metric_name}' not found", status_code=404)
    return self._get(f"/api/v1/metrics/{metric_name}/sql")
```

---

## 7. Jinja Template 지원

### 7.1 지원 범위

`dli dataset run` 실행 시 Jinja Template을 **로컬 CLI에서 렌더링**합니다.

```sql
-- query.sql (Jinja Template)
SELECT
  {{ column_list | default('*') }}
FROM {{ ref('users') }}
WHERE created_at > '{{ start_date }}'
{% if limit %}
LIMIT {{ limit }}
{% endif %}
```

```bash
# 실행
dli dataset run -f query.sql \
  --var column_list="id, name, email" \
  --var start_date="2024-01-01" \
  --var limit=100
```

### 7.2 지원 함수

| 함수 | 설명 | 예시 |
|------|------|------|
| `ref(name)` | 테이블 참조 (dbt 호환) | `{{ ref('users') }}` |
| `var(name, default)` | 변수 참조 | `{{ var('limit', 100) }}` |
| `env(name)` | 환경변수 참조 | `{{ env('DB_NAME') }}` |

### 7.3 렌더링 순서

```
1. Jinja Template 렌더링 (CLI 로컬)
2. Transpile 규칙 적용 (SQLGlot)
3. SQL 실행
```

---

## 8. 에러 처리

### 8.1 에러 유형 및 처리

| 상황 | 기본 동작 | Strict 모드 |
|------|-----------|-------------|
| Server 연결 실패 | 원본 SQL 실행 + 경고 | 에러 반환 |
| 규칙 조회 실패 | 원본 SQL 실행 + 경고 | 에러 반환 |
| 메트릭 조회 실패 | 원본 SQL 실행 + 경고 | 에러 반환 |
| SQL 파싱 실패 | 원본 SQL 실행 + 경고 | 에러 반환 |
| 문법 검증 실패 | 경고 출력 (실행 계속) | 에러 반환 |

### 8.2 예외 계층 구조

> **⚠️ P0 요구사항: 완전한 예외 계층 정의**
>
> 구현자가 예외 처리를 명확히 할 수 있도록 모든 예외 타입을 정의합니다.

```python
from typing import Any


class TranspileError(Exception):
    """Transpile 관련 기본 에러"""

    def __init__(self, message: str, details: dict[str, Any] | None = None):
        self.message = message
        self.details = details or {}
        super().__init__(message)


# === 네트워크 관련 에러 ===

class NetworkError(TranspileError):
    """네트워크 통신 에러 (연결 실패, DNS 오류 등)"""

    def __init__(self, message: str = "Network connection failed", cause: Exception | None = None):
        super().__init__(message, {"cause": str(cause) if cause else None})
        self.cause = cause


class TimeoutError(TranspileError):
    """API 요청 타임아웃"""

    def __init__(self, timeout_seconds: float, endpoint: str):
        super().__init__(
            f"Request to {endpoint} timed out after {timeout_seconds}s",
            {"timeout_seconds": timeout_seconds, "endpoint": endpoint}
        )
        self.timeout_seconds = timeout_seconds
        self.endpoint = endpoint


class RuleFetchError(TranspileError):
    """규칙 조회 실패 (서버 응답 에러)"""

    def __init__(self, status_code: int | None = None, detail: str | None = None):
        message = "Failed to fetch transpile rules from server"
        if status_code:
            message += f" (HTTP {status_code})"
        if detail:
            message += f": {detail}"
        super().__init__(message, {"status_code": status_code, "detail": detail})
        self.status_code = status_code


# === 검증 관련 에러 ===

class ValidationError(TranspileError):
    """입력값 검증 실패"""

    def __init__(self, field: str, value: Any, reason: str):
        super().__init__(
            f"Validation failed for '{field}': {reason}",
            {"field": field, "value": value, "reason": reason}
        )
        self.field = field
        self.value = value
        self.reason = reason


class SqlParseError(TranspileError):
    """SQL 파싱 실패 (SQLGlot 에러)"""

    def __init__(self, sql: str, detail: str, line: int | None = None, column: int | None = None):
        message = f"Failed to parse SQL: {detail}"
        if line:
            message += f" (line {line}"
            if column:
                message += f", column {column}"
            message += ")"
        super().__init__(message, {"sql": sql, "line": line, "column": column})
        self.sql = sql
        self.line = line
        self.column = column


class SqlValidationError(ValidationError):
    """SQL 문법 검증 실패 (--validate 옵션)"""

    def __init__(self, sql: str, errors: list[str]):
        super().__init__(
            field="sql",
            value=sql[:100] + "..." if len(sql) > 100 else sql,
            reason=f"{len(errors)} validation error(s)"
        )
        self.errors = errors


# === 비즈니스 로직 에러 ===

class MetricNotFoundError(TranspileError):
    """메트릭 없음"""

    def __init__(self, metric_name: str, available_metrics: list[str] | None = None):
        message = f"Metric '{metric_name}' not found"
        if available_metrics:
            message += f". Available: {', '.join(available_metrics[:5])}"
            if len(available_metrics) > 5:
                message += f" (+{len(available_metrics) - 5} more)"
        super().__init__(message, {"metric_name": metric_name, "available": available_metrics})
        self.metric_name = metric_name
        self.available_metrics = available_metrics


class MetricLimitExceededError(TranspileError):
    """METRIC() 함수 개수 제한 초과 (MVP: 1개)"""

    def __init__(self, found_count: int, max_allowed: int = 1):
        super().__init__(
            f"MVP limitation: Only {max_allowed} METRIC() per SQL allowed, found {found_count}",
            {"found_count": found_count, "max_allowed": max_allowed}
        )
        self.found_count = found_count
        self.max_allowed = max_allowed


class RuleConflictError(TranspileError):
    """규칙 충돌 (동일 소스에 복수 규칙)"""

    def __init__(self, source: str, conflicting_rules: list[str]):
        super().__init__(
            f"Conflicting rules for '{source}': {', '.join(conflicting_rules)}",
            {"source": source, "rules": conflicting_rules}
        )
        self.source = source
        self.conflicting_rules = conflicting_rules
```

**예외 사용 가이드:**

| 상황 | 예외 타입 | Strict 모드 |
|------|-----------|-------------|
| 서버 연결 불가 | `NetworkError` | 즉시 raise |
| API 응답 지연 | `TimeoutError` | 즉시 raise |
| 서버 4xx/5xx | `RuleFetchError` | 즉시 raise |
| 잘못된 SQL 구문 | `SqlParseError` | 즉시 raise |
| 문법 검증 실패 | `SqlValidationError` | 즉시 raise |
| 입력 파라미터 오류 | `ValidationError` | 항상 raise |
| 메트릭 미존재 | `MetricNotFoundError` | 즉시 raise |
| METRIC() 2개+ | `MetricLimitExceededError` | 항상 raise |

### 8.3 Retry 로직

```python
def fetch_rules_with_retry(self, retry_count: int = 1) -> list[TranspileRule]:
    """재시도 로직을 포함한 규칙 조회"""
    last_error = None
    for attempt in range(retry_count + 1):
        try:
            response = self.client.transpile_get_rules()
            if response.success:
                return [TranspileRule(**r) for r in response.data["rules"]]
            last_error = response.error
        except Exception as e:
            last_error = str(e)
            if attempt < retry_count:
                logger.warning(f"Retry {attempt + 1}/{retry_count}: {last_error}")
                time.sleep(0.5 * (attempt + 1))  # Exponential backoff

    logger.warning(f"Failed to fetch rules after {retry_count + 1} attempts: {last_error}")
    return []  # Graceful degradation
```

---

## 9. 보안

### 9.1 기본 방어 전략

| 항목 | 구현 | 비고 |
|------|------|------|
| SQL Injection | SQLGlot AST 파싱 | 문자열 조작 아닌 구조적 변환 |
| 위험 문법 감지 | `DROP`, `TRUNCATE` 경고 | Advisory only (차단은 서버/DB 책임) |
| 권한 관리 | BigQuery/Trino 위임 | CLI는 권한 검증 미수행 |

### 9.2 구현 가이드

```python
def detect_dangerous_patterns(self, parsed: exp.Expression) -> list[TranspileWarning]:
    """위험 패턴 감지 (Advisory)"""
    warnings = []
    dangerous_statements = (exp.Drop, exp.Truncate, exp.Delete)

    for stmt in parsed.find_all(*dangerous_statements):
        warnings.append(TranspileWarning(
            type=WarningType.DANGEROUS_STATEMENT,
            message=f"Detected {stmt.__class__.__name__} statement",
            line=stmt.meta.get("line"),
        ))

    return warnings
```

---

## 10. 로깅

### 10.1 로깅 구조

| 레벨 | 출력 위치 | 내용 |
|------|-----------|------|
| INFO | Console | 변환 요약 (적용 규칙 수, 경고 수) |
| WARNING | Console | 최적화 권고, 위험 패턴 감지 |
| DEBUG | File | 상세 변환 내역, API 요청/응답 |
| ERROR | Console + File | 변환 실패, API 오류 |

### 10.2 로그 파일

```
~/.dli/logs/transpile-YYYY-MM-DD.log
```

### 10.3 로그 포맷

```json
{
  "timestamp": "2025-01-01T12:00:00.000Z",
  "level": "DEBUG",
  "event": "transpile_complete",
  "data": {
    "original_sql": "SELECT * FROM users",
    "transpiled_sql": "SELECT * FROM users_v2",
    "applied_rules": ["rule-001"],
    "warnings": ["no_limit"],
    "duration_ms": 45
  }
}
```

---

## 11. 구현 전략 (Mock-First Approach)

### 11.0 개발 순서

```
Phase 0: 환경 준비 (선행 조건)
─────────────────────────────────────────────────────────────
├─ [ ] pyproject.toml에 sqlglot 의존성 추가
├─ [ ] core/transpile/ 디렉토리 구조 생성
└─ [ ] Mock 규칙 데이터 구조 정의

Phase 1: CLI Mock 모드 개발 (Server 의존 없음) ← 현재 단계
─────────────────────────────────────────────────────────────
├─ TranspileEngine 핵심 로직 구현
├─ Mock 규칙 데이터로 테스트
├─ CLI 커맨드 (`dli transpile`) 완성
└─ 단위/통합 테스트 완료

Phase 2: Server API 개발 (별도 진행)
─────────────────────────────────────────────────────────────
├─ /api/v1/transpile/rules 엔드포인트
├─ /api/v1/metrics/{name}/sql 엔드포인트
└─ 규칙 저장소 구현

Phase 3: CLI ↔ Server 연동
─────────────────────────────────────────────────────────────
├─ Mock Client → Real Client 교체
├─ 통합 테스트 추가
└─ Graceful Degradation 검증
```

### 11.1 Mock 모드 설계

> **⚠️ P0 요구사항: Protocol 시그니처에 에러 타입 명시**
>
> 구현자가 예외 처리 로직을 명확히 작성할 수 있도록 메서드별 발생 가능 예외를 문서화합니다.

```python
# core/transpile/client.py

from typing import Protocol
from pathlib import Path

from .models import TranspileRule, MetricDefinition
from .exceptions import (
    NetworkError,
    TimeoutError,
    RuleFetchError,
    MetricNotFoundError,
    ValidationError,
)


class TranspileRuleClient(Protocol):
    """
    규칙 조회 인터페이스 (의존성 역전)

    구현체:
    - MockTranspileClient: 테스트/개발용 (Phase 1)
    - BasecampTranspileClient: 운영용 (Phase 3)
    """

    def get_rules(self, project_id: str) -> list[TranspileRule]:
        """
        프로젝트의 Transpile 규칙 목록 조회

        Args:
            project_id: 프로젝트 식별자

        Returns:
            TranspileRule 목록 (빈 리스트 가능)

        Raises:
            NetworkError: 서버 연결 실패
            TimeoutError: 요청 타임아웃 (기본 10초)
            RuleFetchError: 서버 응답 오류 (4xx/5xx)
            ValidationError: project_id 형식 오류
        """
        ...

    def get_metric(self, name: str) -> MetricDefinition:
        """
        메트릭 정의 조회

        Args:
            name: 메트릭 이름 (case-sensitive)

        Returns:
            MetricDefinition (expression, source_table 포함)

        Raises:
            NetworkError: 서버 연결 실패
            TimeoutError: 요청 타임아웃 (기본 10초)
            MetricNotFoundError: 메트릭 미존재 (404)
            RuleFetchError: 서버 응답 오류 (4xx/5xx, 404 제외)
            ValidationError: name 형식 오류 (빈 문자열, 특수문자 등)
        """
        ...


class MockTranspileClient:
    """
    Phase 1: 로컬 파일 기반 Mock 클라이언트

    테스트 및 Server 미구현 상태에서 CLI 개발용.
    YAML/JSON 파일에서 규칙을 로드합니다.
    """

    def __init__(self, rules_file: Path | None = None):
        self._rules_file = rules_file or Path("tests/fixtures/transpile_rules.yaml")
        self._rules: dict[str, list[TranspileRule]] = {}
        self._metrics: dict[str, MetricDefinition] = {}
        self._load_mock_data()

    def _load_mock_data(self) -> None:
        """Mock 데이터 로드 (YAML/JSON 지원)"""
        if not self._rules_file.exists():
            return
        # 구현: yaml.safe_load() 또는 json.load()

    def get_rules(self, project_id: str) -> list[TranspileRule]:
        """Mock: 로컬 파일에서 규칙 반환 (예외 발생 없음)"""
        if not project_id:
            raise ValidationError("project_id", project_id, "cannot be empty")
        return self._rules.get(project_id, [])

    def get_metric(self, name: str) -> MetricDefinition:
        """Mock: 로컬 파일에서 메트릭 반환"""
        if not name:
            raise ValidationError("name", name, "cannot be empty")
        if name not in self._metrics:
            raise MetricNotFoundError(name, list(self._metrics.keys()))
        return self._metrics[name]


class BasecampTranspileClient:
    """
    Phase 3: Basecamp Server 연동 클라이언트

    실제 Server API와 통신합니다.
    """

    DEFAULT_TIMEOUT = 10.0  # seconds

    def __init__(self, base_url: str, token: str, timeout: float = DEFAULT_TIMEOUT):
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._timeout = timeout

    def get_rules(self, project_id: str) -> list[TranspileRule]:
        """Server API에서 규칙 조회"""
        if not project_id:
            raise ValidationError("project_id", project_id, "cannot be empty")

        try:
            response = httpx.get(
                f"{self._base_url}/api/v1/transpile/rules",
                params={"project_id": project_id},
                headers={"Authorization": f"Bearer {self._token}"},
                timeout=self._timeout,
            )
        except httpx.ConnectError as e:
            raise NetworkError("Failed to connect to Basecamp Server", cause=e)
        except httpx.TimeoutException:
            raise TimeoutError(self._timeout, "/api/v1/transpile/rules")

        if response.status_code != 200:
            raise RuleFetchError(response.status_code, response.text)

        return [TranspileRule(**r) for r in response.json()["rules"]]

    def get_metric(self, name: str) -> MetricDefinition:
        """Server API에서 메트릭 조회"""
        if not name:
            raise ValidationError("name", name, "cannot be empty")

        try:
            response = httpx.get(
                f"{self._base_url}/api/v1/metrics/{name}/sql",
                headers={"Authorization": f"Bearer {self._token}"},
                timeout=self._timeout,
            )
        except httpx.ConnectError as e:
            raise NetworkError("Failed to connect to Basecamp Server", cause=e)
        except httpx.TimeoutException:
            raise TimeoutError(self._timeout, f"/api/v1/metrics/{name}/sql")

        if response.status_code == 404:
            raise MetricNotFoundError(name)
        if response.status_code != 200:
            raise RuleFetchError(response.status_code, response.text)

        return MetricDefinition(**response.json())
```

### 11.2 Mock 데이터 구조

```yaml
# tests/fixtures/transpile_rules.yaml
project_default:
  rules:
    - id: "rule-001"
      type: table_substitution
      source: "raw.events"
      target: "warehouse.events_v2"
      enabled: true

  metrics:
    revenue:
      expression: "SUM(amount * quantity)"
      source_table: "analytics.orders"
      description: "Total revenue"
```

### 11.3 환경별 Client 선택

```python
def get_transpile_client() -> TranspileRuleClient:
    """환경에 따라 적절한 Client 반환"""
    if settings.TRANSPILE_MODE == "mock":
        return MockTranspileClient(rules_file=settings.MOCK_RULES_PATH)
    else:
        return BasecampTranspileClient(
            base_url=settings.BASECAMP_URL,
            token=settings.BASECAMP_TOKEN,
        )
```

---

## 12. 구현 가이드

### 12.1 디렉토리 구조

```
src/dli/
├── commands/
│   ├── dataset.py         # dataset run 커맨드 (Transpile 통합)
│   └── transpile.py       # transpile 커맨드 (NEW)
└── core/
    └── transpile/
        ├── __init__.py
        ├── engine.py      # TranspileEngine 클래스
        ├── models.py      # Pydantic 모델
        ├── rules.py       # 규칙 적용 로직
        ├── metrics.py     # 메트릭 확장 로직
        ├── warnings.py    # 경고 감지 로직
        └── jinja.py       # Jinja 렌더링 (선택적)
```

### 11.2 참조 패턴

| 구현 항목 | 참조 파일 |
|-----------|-----------|
| CLI 커맨드 구조 | `commands/dataset.py`, `commands/workflow.py` |
| Rich 출력 | `commands/utils.py` |
| API 클라이언트 메서드 | `core/client.py` |
| Pydantic 모델 | `core/workflow/models.py` |
| SQLGlot 사용 | `project-basecamp-parser/src/parser/sql_parser.py` |

### 11.3 테스트 참조

| 테스트 항목 | 참조 파일 |
|-------------|-----------|
| CLI 테스트 | `tests/cli/test_workflow_cmd.py` |
| 모델 테스트 | `tests/core/workflow/test_models.py` |
| SQLGlot 테스트 | `project-basecamp-parser/tests/test_sql_parser.py` |

---

## 12. 구현 우선순위

### Phase 1 (MVP)

- [ ] `TranspileEngine` 클래스 구현
- [ ] 테이블 치환 (Table Substitution) 기능
- [ ] `METRIC()` 함수 Simple Substitution
- [ ] `dli transpile` 커맨드 (Inline SQL + File)
- [ ] `dli dataset run` Transpile 통합
- [ ] Server API 클라이언트 (`transpile_get_rules`, `transpile_get_metric_sql`)
- [ ] Graceful Degradation (규칙 조회 실패 시 원본 실행)
- [ ] 기본 경고 출력 (SELECT *, LIMIT 미사용)
- [ ] Strict Mode 옵션
- [ ] Mock 모드 지원

### Phase 2

- [ ] Jinja Template 렌더링
- [ ] 추가 경고 패턴 (중복 CTE, 상관 서브쿼리)
- [ ] `--validate` 옵션 (문법 검증)
- [ ] 상세 로깅 (File 로그)
- [ ] BigQuery 다이얼렉트 지원

### Phase 3+

- [ ] Full Metric Expansion (dimensions, time_grains, filters)
- [ ] Column-level Lineage
- [ ] Cost Estimation
- [ ] Multi-statement Support
- [ ] Custom Rule DSL

---

## 13. 테스트 전략

> **⚠️ P0 요구사항: 테스트 전략 명세**
>
> 구현 전 테스트 구조와 커버리지 목표를 정의합니다.

### 13.1 테스트 구조

```
tests/
├── cli/
│   ├── test_transpile_cmd.py      # CLI 커맨드 통합 테스트
│   └── test_dataset_run_sql.py    # dataset run --sql 옵션 테스트
├── core/
│   └── transpile/
│       ├── test_engine.py         # TranspileEngine 단위 테스트
│       ├── test_models.py         # Pydantic 모델 테스트
│       ├── test_metrics.py        # METRIC() 파싱 테스트
│       ├── test_rules.py          # 규칙 적용 테스트
│       ├── test_warnings.py       # 경고 감지 테스트
│       ├── test_exceptions.py     # 예외 클래스 테스트
│       └── test_client.py         # Protocol 구현체 테스트
└── fixtures/
    └── transpile/
        ├── rules.yaml             # 규칙 Mock 데이터
        ├── metrics.yaml           # 메트릭 Mock 데이터
        ├── sql_samples/           # 테스트용 SQL 파일
        │   ├── simple_select.sql
        │   ├── with_metric.sql
        │   ├── complex_cte.sql
        │   └── invalid_syntax.sql
        └── expected/              # 예상 결과
            ├── simple_select_result.sql
            └── with_metric_result.sql
```

### 13.2 테스트 커버리지 목표

| 모듈 | 목표 커버리지 | 우선순위 |
|------|---------------|----------|
| `core/transpile/engine.py` | 90%+ | P0 |
| `core/transpile/metrics.py` | 95%+ | P0 |
| `core/transpile/rules.py` | 85%+ | P1 |
| `core/transpile/client.py` | 80%+ | P1 |
| `commands/transpile.py` | 75%+ | P2 |
| `core/transpile/warnings.py` | 70%+ | P2 |

**커버리지 측정:**
```bash
uv run pytest tests/core/transpile --cov=src/dli/core/transpile --cov-report=term-missing
```

### 13.3 테스트 케이스 분류

#### 13.3.1 단위 테스트 (Unit Tests)

**TranspileEngine:**
```python
class TestTranspileEngine:
    """TranspileEngine 핵심 로직 테스트"""

    def test_transpile_simple_select(self, mock_client: MockTranspileClient):
        """기본 SELECT 문 변환"""
        engine = TranspileEngine(client=mock_client)
        result = engine.transpile("SELECT * FROM users")
        assert result.success is True
        assert "users_v2" in result.sql  # 규칙에 따라 치환

    def test_transpile_with_metric(self, mock_client: MockTranspileClient):
        """METRIC() 함수 치환"""
        engine = TranspileEngine(client=mock_client)
        result = engine.transpile("SELECT METRIC(revenue) FROM __semantic.__table")
        assert result.success is True
        assert "METRIC" not in result.sql
        assert "SUM(" in result.sql  # 실제 표현식으로 치환

    def test_transpile_metric_not_found_strict(self, mock_client: MockTranspileClient):
        """Strict 모드에서 미존재 메트릭 에러"""
        config = TranspileConfig(strict_mode=True)
        engine = TranspileEngine(client=mock_client, config=config)
        with pytest.raises(MetricNotFoundError) as exc_info:
            engine.transpile("SELECT METRIC(unknown) FROM t")
        assert "unknown" in str(exc_info.value)

    def test_transpile_metric_not_found_graceful(self, mock_client: MockTranspileClient):
        """Graceful 모드에서 미존재 메트릭 경고"""
        config = TranspileConfig(strict_mode=False)
        engine = TranspileEngine(client=mock_client, config=config)
        result = engine.transpile("SELECT METRIC(unknown) FROM t")
        assert result.success is True  # 에러 없이 계속
        assert len(result.warnings) > 0

    def test_transpile_multiple_metrics_error(self, mock_client: MockTranspileClient):
        """MVP: 복수 METRIC() 에러"""
        engine = TranspileEngine(client=mock_client)
        with pytest.raises(MetricLimitExceededError):
            engine.transpile("SELECT METRIC(a), METRIC(b) FROM t")


class TestMetricParsing:
    """METRIC() 함수 파싱 테스트"""

    @pytest.mark.parametrize("sql,expected_name", [
        ("METRIC(revenue)", "revenue"),
        ("METRIC('revenue')", "revenue"),
        ('METRIC("revenue")', "revenue"),
        ("METRIC( revenue )", "revenue"),
        ("metric(Revenue)", "Revenue"),  # case-insensitive match, preserve name
    ])
    def test_valid_metric_patterns(self, sql: str, expected_name: str):
        """유효한 METRIC 패턴 파싱"""
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        assert matches[0].metric_name == expected_name

    @pytest.mark.parametrize("sql", [
        "METRIC()",           # 빈 이름
        "METRIC((revenue))",  # 중첩 괄호
        "METRIC(123abc)",     # 숫자로 시작
    ])
    def test_invalid_metric_patterns(self, sql: str):
        """무효한 METRIC 패턴 무시"""
        matches = find_metric_functions(sql)
        assert len(matches) == 0
```

#### 13.3.2 통합 테스트 (Integration Tests)

**CLI 통합 테스트:**
```python
class TestTranspileCommand:
    """dli transpile 커맨드 통합 테스트"""

    def test_transpile_inline_sql(self, cli_runner: CliRunner):
        """인라인 SQL 변환"""
        result = cli_runner.invoke(app, ["transpile", "SELECT * FROM users"])
        assert result.exit_code == 0
        assert "Transpiled SQL" in result.output

    def test_transpile_file(self, cli_runner: CliRunner, tmp_path: Path):
        """파일 기반 SQL 변환"""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM users")
        result = cli_runner.invoke(app, ["transpile", "-f", str(sql_file)])
        assert result.exit_code == 0

    def test_transpile_strict_mode_error(self, cli_runner: CliRunner):
        """Strict 모드 에러 출력"""
        result = cli_runner.invoke(
            app, ["transpile", "SELECT METRIC(unknown) FROM t", "--strict"]
        )
        assert result.exit_code != 0
        assert "MetricNotFoundError" in result.output or "not found" in result.output

    def test_transpile_json_output(self, cli_runner: CliRunner):
        """JSON 형식 출력"""
        result = cli_runner.invoke(
            app, ["transpile", "SELECT * FROM users", "--format", "json"]
        )
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert "sql" in data
        assert "applied_rules" in data


class TestDatasetRunSql:
    """dli dataset run --sql 옵션 테스트"""

    def test_run_sql_basic(self, cli_runner: CliRunner):
        """기본 SQL 실행 (Transpile 적용)"""
        result = cli_runner.invoke(
            app, ["dataset", "run", "--sql", "SELECT 1"]
        )
        assert result.exit_code == 0

    def test_run_sql_mutual_exclusion(self, cli_runner: CliRunner):
        """name과 --sql 동시 사용 에러"""
        result = cli_runner.invoke(
            app, ["dataset", "run", "my_spec", "--sql", "SELECT 1"]
        )
        assert result.exit_code != 0
        assert "mutually exclusive" in result.output.lower() or "Cannot use both" in result.output

    def test_run_sql_no_transpile(self, cli_runner: CliRunner):
        """--no-transpile 옵션"""
        result = cli_runner.invoke(
            app, ["dataset", "run", "--sql", "SELECT * FROM users", "--no-transpile"]
        )
        assert result.exit_code == 0
        assert "Transpile" not in result.output  # Transpile 비활성화 확인
```

#### 13.3.3 예외 테스트 (Exception Tests)

```python
class TestExceptions:
    """예외 클래스 테스트"""

    def test_network_error_with_cause(self):
        """NetworkError 원인 예외 포함"""
        cause = ConnectionError("Connection refused")
        error = NetworkError("Failed to connect", cause=cause)
        assert error.cause is cause
        assert "Connection refused" in error.details["cause"]

    def test_timeout_error_details(self):
        """TimeoutError 상세 정보"""
        error = TimeoutError(10.0, "/api/v1/transpile/rules")
        assert error.timeout_seconds == 10.0
        assert "/api/v1/transpile/rules" in str(error)

    def test_metric_not_found_suggestions(self):
        """MetricNotFoundError 가용 메트릭 제안"""
        error = MetricNotFoundError("revenu", ["revenue", "orders", "users"])
        assert "revenu" in str(error)
        assert "revenue" in str(error)  # 제안 포함

    def test_validation_error_fields(self):
        """ValidationError 필드 정보"""
        error = ValidationError("project_id", "", "cannot be empty")
        assert error.field == "project_id"
        assert error.reason == "cannot be empty"
```

### 13.4 Mock 전략

```python
# conftest.py

import pytest
from pathlib import Path
from dli.core.transpile.client import MockTranspileClient

@pytest.fixture
def mock_client() -> MockTranspileClient:
    """기본 Mock 클라이언트 (fixtures 데이터 사용)"""
    return MockTranspileClient(
        rules_file=Path(__file__).parent / "fixtures/transpile/rules.yaml"
    )

@pytest.fixture
def mock_client_empty() -> MockTranspileClient:
    """빈 규칙 Mock 클라이언트"""
    return MockTranspileClient(rules_file=None)

@pytest.fixture
def cli_runner() -> CliRunner:
    """Typer CLI 테스트 러너"""
    return CliRunner()
```

### 13.5 테스트 실행 가이드

```bash
# 전체 Transpile 테스트
uv run pytest tests/core/transpile -v

# 특정 테스트 클래스
uv run pytest tests/core/transpile/test_engine.py::TestTranspileEngine -v

# 커버리지 포함
uv run pytest tests/core/transpile --cov=src/dli/core/transpile --cov-report=html

# CLI 통합 테스트만
uv run pytest tests/cli/test_transpile_cmd.py -v

# 빠른 단위 테스트 (Mock only)
uv run pytest tests/core/transpile -m "not integration" -v
```

### 13.6 CI 파이프라인 통합

```yaml
# .github/workflows/test.yml (추가)
jobs:
  test-transpile:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - name: Install uv
        run: pip install uv
      - name: Install dependencies
        run: cd project-interface-cli && uv sync
      - name: Run Transpile tests
        run: |
          cd project-interface-cli
          uv run pytest tests/core/transpile --cov=src/dli/core/transpile --cov-fail-under=80
      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: project-interface-cli/coverage.xml
```

---

## Appendix: 커맨드 요약

```bash
# dli dataset run (암시적 Transpile)
dli dataset run --sql "SELECT ..."          # 기본 (Transpile 자동)
dli dataset run -f query.sql                # 파일 기반
dli dataset run --sql "..." --transpile-strict
dli dataset run --sql "..." --transpile-retry 3
dli dataset run --sql "..." --no-transpile

# dli transpile (디버깅용)
dli transpile "SELECT ..."                  # Inline SQL
dli transpile -f query.sql                  # 파일 기반
dli transpile "..." --validate              # 문법 검증
dli transpile "..." --strict                # Strict 모드
dli transpile "..." --format json           # JSON 출력
dli transpile "..." --show-rules            # 규칙 상세
dli transpile "..." --dialect bigquery      # 다이얼렉트 지정
```

---

## Appendix: 결정 사항 (인터뷰 기반)

### A.1 아키텍처 결정

| 항목 | 결정 | Trade-off 분석 | 최종 근거 |
|------|------|----------------|-----------|
| Transpile 실행 위치 | CLI 내 SQLGlot | 네트워크 호출 vs 오프라인 지원 | 네트워크 최소화, CLI 독립성 |
| 규칙 저장소 | Basecamp Server | 중앙집중 vs 분산 | 중앙 집중 관리, 규칙 일관성 |
| 캐싱 | 없음 (Always Fetch) | 성능 vs 단순성 | 단순성 우선, BigQuery/Trino 대비 무시 가능 |
| 개발 순서 | Mock-first | 직렬 vs 병렬 개발 | Server 없이 CLI 독립 개발 가능 |
| 성능 최적화 | 고려하지 않음 | 최적화 vs 빠른 개발 | 쿼리 실행(초~분) 대비 Transpile(~3초) 무시 |

### A.2 기능 정책 결정

| 항목 | 결정 | Trade-off 분석 | 최종 근거 |
|------|------|----------------|-----------|
| 테이블 치환 방식 | Explicit Mapping | 자동감지 vs 명시적 매핑 | 명확성, 예측 가능성 |
| Metric 해석 | Server-resolved | CLI 내장 vs Server 위임 | CLI metric 로직 미보유, 중앙 관리 |
| Fallback | Graceful (기본) | Silent vs Fail-fast | UX 우선, Strict 옵션으로 선택 가능 |
| SQL 최적화 | Advisory Only | 자동수정 vs 경고만 | SQL 변경 없이 경고만, 안전성 |
| METRIC 개수 | SQL당 1개 (MVP) | 단일 vs 복수 지원 | MVP 단순화, 추후 확장 가능 |
| 미존재 메트릭 | 에러 발생 | Silent vs 명시적 오류 | 디버깅 용이성 |

### A.3 기술 스택 결정

| 항목 | 결정 | 근거 |
|------|------|------|
| Jinja | CLI 로컬 렌더링 | dataset run 시 템플릿 지원 |
| 검증 | transpile: 옵션, run: 자동 | 디버깅 vs 실행 구분 |
| 로깅 | Console 요약 + File 상세 | UX + 재현성 |
| 보안 | 비용 효율적 방어 | DB/서버 권한 위임 |
| 다이얼렉트 | Trino (MVP), BigQuery (Phase 2) | 현재 Adapter 기준 |
| 라이브러리 API | TranspileEngine 클래스 | 확장성, 설정 재사용 |
| 결과 타입 | TranspileResult + JSON | 구조화 + 직렬화 |
| SQLglot 버전 | Parser와 동일 (28.5 권장) | 호환성 보장 |

---

## Appendix: 외부 참조

### SQLGlot
- [GitHub Repository](https://github.com/tobymao/sqlglot)
- [API Documentation](https://sqlglot.com/sqlglot.html)
- [SQL Transpilation Guide](https://deepwiki.com/tobymao/sqlglot/5-sql-transpilation)

### Semantic Layer
- [dbt Semantic Layer](https://www.getdbt.com/blog/dbt-semantic-layer)
- [Cube.dev Documentation](https://cube.dev/docs)
- [SQLMesh Overview](https://tobikodata.com/sqlmesh_for_dbt_1.html)

### Metric Store
- [MetricFlow (dbt)](https://docs.getdbt.com/docs/build/build-metrics-intro)
- [Sidemantic (Universal Metrics Layer)](https://github.com/sidequery/sidemantic)

---

## Appendix: Implementation Agent Review

### 도메인 구현자 리뷰 (feature-interface-cli)

**리뷰어**: `feature-interface-cli` Agent
**리뷰 일자**: 2025-12-30
**핵심 관점**: "신규 기능을 어떻게 빠르게 추가하는가?"

| Priority | Issue | Resolution |
|----------|-------|------------|
| P0 | `dataset run --sql` 옵션이 기존 `run_dataset(name)` 시그니처와 충돌 | ✅ Section 4.1.1에 상호 배타적 옵션 설계 및 구현 시그니처 추가 |
| P0 | METRIC() 함수 파싱 알고리즘 미명시 ("문자열 치환 선호"만 언급) | ✅ Section 3.4 신규 추가: 정규식 패턴, 알고리즘 코드, Edge case 테이블 |
| P0 | 테스트 전략 섹션 완전 누락 | ✅ Section 13 신규 추가: 테스트 구조, 커버리지 목표, 단위/통합/예외 테스트 코드 예시, CI 설정 |
| P1 | Mock 데이터 파일 경로 하드코딩 | Section 11.2에 fixtures 경로 패턴 명시 |
| P2 | CLI 출력 형식 상세 미정의 | Section 4.4에 Rich 출력 예시 포함 |

### 기술 시니어 리뷰 (expert-python)

**리뷰어**: `expert-python` Agent
**리뷰 일자**: 2025-12-30
**핵심 관점**: "내부 구조 개선과 시스템 확장 가능성"

| Priority | Issue | Resolution |
|----------|-------|------------|
| P0 | 예외 계층 불완전 (NetworkError, ValidationError, TimeoutError 누락) | ✅ Section 8.2 확장: 9개 예외 클래스 정의 (네트워크/검증/비즈니스 분류) |
| P0 | Protocol 시그니처에 에러 타입 미명시 | ✅ Section 11.1 확장: `get_rules()`, `get_metric()` Raises 문서화 |
| P1 | API 응답 Pydantic 모델 미정의 | Section 5.2에 TranspileResult, TranspileRule 등 모델 정의됨 |
| P1 | 의존성 주입 패턴 불명확 | ✅ Section 11.1에 Protocol 기반 DI 패턴 및 Mock/Real 클라이언트 예시 추가 |
| P2 | 모듈 구조 평면적 (models/, client/, processors/ 권장) | Section 12.1에 core/transpile/ 구조 명시, Phase 2에서 세분화 고려 |
| P2 | tenacity, structlog 등 현대 라이브러리 활용 권장 | Phase 2 고려사항으로 기록 |

### 리뷰 요약

| 지표 | 값 |
|------|-----|
| 총 P0 이슈 | 5개 |
| 해결된 P0 | 5개 (100%) |
| 총 P1/P2 이슈 | 6개 |
| 해결/반영된 P1/P2 | 4개 |

> **결론**: 모든 P0 이슈가 FEATURE 문서에 반영되어 구현 준비 완료.

---

**Last Updated:** 2025-12-30
