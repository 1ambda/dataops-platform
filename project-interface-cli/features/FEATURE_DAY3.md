# Day 3: Backend 분리 구현 가이드 (MVP)

> **MVP 범위**: 인증/인가, 결과 캐싱, 감사 로그는 Phase 2로 연기합니다.
> Day 3에서는 Spring ↔ Python 통신 및 핵심 파싱/검증 기능에 집중합니다.

## 아키텍처

```
┌─────────┐      ┌─────────────────────────┐      ┌─────────────────────────┐
│   UI    │ ──▶  │  project-basecamp-server │ ──▶  │  project-basecamp-parser │
│ (React) │      │     (Kotlin/Spring)      │      │        (Python)          │
└─────────┘      └─────────────────────────┘      └─────────────────────────┘
                         │                                   │
                         │ - 쿼리 메타데이터 CRUD              │ - Jinja 렌더링
                         │ - BigQuery/Snowflake 실행          │ - SQL 파싱 (SQLGlot)
                         │ - Dry-run 비용 추정                │ - SQL 검증
                         │ (Phase 2: 인증, 캐싱, 감사로그)     │ - 테이블 추출
                         │                                    │ - SQL 포맷팅
                         └─────────────────────────────────────┘
```

---

## 역할 분담

| 기능 | Spring (basecamp-server) | Python (basecamp-parser) | MVP |
|------|--------------------------|--------------------------|-----|
| 인증/인가 | ✅ JWT/OAuth | ❌ | ❌ Phase 2 |
| 쿼리 메타데이터 CRUD | ✅ DB 저장 | ❌ | ✅ |
| Jinja 렌더링 | ❌ | ✅ | ✅ |
| SQL 파싱/검증 | ❌ | ✅ SQLGlot | ✅ |
| 테이블 추출 | ❌ | ✅ | ✅ |
| SQL 포맷팅 | ❌ | ✅ | ✅ |
| BigQuery 실행 | ✅ | ❌ | ✅ |
| Dry-run | ✅ | ❌ | ✅ |
| 결과 캐싱 | ✅ | ❌ | ❌ Phase 2 |
| 감사 로그 | ✅ | ❌ | ❌ Phase 2 |

---

## 시간 배분 (Day 3: 8시간)

| 순서 | 항목 | 시간 | 설명 |
|------|------|------|------|
| 1 | Python API 스키마 | 0.5h | Request/Response 정의 |
| 2 | Python API 구현 | 2.5h | 4개 엔드포인트 |
| 3 | Python 테스트 | 1h | 단위/통합 테스트 |
| 4 | Spring API 스키마 | 0.5h | DTO 정의 |
| 5 | Spring Parser Client | 1.5h | Python API 호출 클라이언트 |
| 6 | Spring API 구현 | 2h | 엔드포인트 + 비즈니스 로직 |

---

## Python Backend (project-basecamp-parser)

### 디렉토리 구조

```
project-basecamp-parser/
├── pyproject.toml
├── src/
│   └── parser/
│       ├── __init__.py
│       ├── main.py           # FastAPI 앱
│       ├── schemas.py        # Request/Response
│       ├── renderer.py       # Jinja 렌더링
│       └── validator.py      # SQL 검증
└── tests/
    └── test_api.py
```

### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | /health | 헬스 체크 |
| POST | /render | Jinja 템플릿 렌더링 |
| POST | /validate | SQL 문법 검증 |
| POST | /parse | SQL 파싱 (테이블 추출) |
| POST | /format | SQL 포맷팅 |

---

### schemas.py

```python
from pydantic import BaseModel
from typing import Any, Optional

# === Render ===
class RenderRequest(BaseModel):
    template: str                    # SQL 템플릿 (Jinja)
    params: dict[str, Any] = {}      # 파라미터

class RenderResponse(BaseModel):
    success: bool
    rendered_sql: Optional[str] = None
    error: Optional[str] = None

# === Validate ===
class ValidateRequest(BaseModel):
    sql: str                         # 렌더링된 SQL
    dialect: str = "bigquery"        # bigquery, snowflake 등

class ValidateResponse(BaseModel):
    is_valid: bool
    errors: list[str] = []
    warnings: list[str] = []

# === Parse ===
class ParseRequest(BaseModel):
    sql: str
    dialect: str = "bigquery"

class ParseResponse(BaseModel):
    success: bool
    tables: list[str] = []           # 참조 테이블 목록
    columns: list[str] = []          # SELECT 컬럼 목록 (가능한 경우)
    error: Optional[str] = None

# === Format ===
class FormatRequest(BaseModel):
    sql: str
    dialect: str = "bigquery"

class FormatResponse(BaseModel):
    success: bool
    formatted_sql: Optional[str] = None
    error: Optional[str] = None
```

---

### renderer.py

```python
from jinja2 import Environment, BaseLoader, TemplateSyntaxError, UndefinedError
from typing import Any

class SQLRenderer:
    def __init__(self):
        self.env = Environment(
            loader=BaseLoader(),
            trim_blocks=True,
            lstrip_blocks=True,
        )
        self._register_filters()
    
    def _register_filters(self) -> None:
        # SQL 문자열 이스케이프: ' → ''
        self.env.filters["sql_string"] = lambda v: f"'{str(v).replace(chr(39), chr(39)*2)}'"
        
        # 리스트를 IN 절로: ['a','b'] → ('a', 'b')
        self.env.filters["sql_list"] = lambda v: f"({', '.join(repr(x) for x in v)})"
        
        # 날짜 포맷
        self.env.filters["sql_date"] = lambda v: f"DATE('{v}')"
    
    def render(self, template: str, params: dict[str, Any]) -> str:
        """Jinja 템플릿 렌더링"""
        tmpl = self.env.from_string(template)
        return tmpl.render(**params)


# 싱글톤
_renderer = SQLRenderer()

def render_sql(template: str, params: dict[str, Any]) -> str:
    return _renderer.render(template, params)
```

---

### validator.py

```python
import sqlglot
from sqlglot import exp
from sqlglot.errors import ParseError

class SQLValidator:
    def __init__(self, dialect: str = "bigquery"):
        self.dialect = dialect
    
    def validate(self, sql: str) -> tuple[bool, list[str], list[str]]:
        """
        SQL 검증
        Returns: (is_valid, errors, warnings)
        """
        errors, warnings = [], []
        
        if not sql or not sql.strip():
            return False, ["Empty SQL"], []
        
        try:
            parsed = sqlglot.parse(sql, dialect=self.dialect)
            
            if not parsed or parsed[0] is None:
                return False, ["Failed to parse SQL"], []
            
            # SELECT 없는 LIMIT 경고
            for stmt in parsed:
                if stmt and stmt.find(exp.Select) and not stmt.find(exp.Limit):
                    warnings.append("SELECT without LIMIT - may return large result")
            
            return True, [], warnings
            
        except ParseError as e:
            return False, [f"Syntax error: {e}"], []
    
    def extract_tables(self, sql: str) -> list[str]:
        """참조 테이블 추출"""
        try:
            parsed = sqlglot.parse_one(sql, dialect=self.dialect)
            tables = set()
            for table in parsed.find_all(exp.Table):
                # schema.table 형식 처리
                parts = []
                if table.catalog:
                    parts.append(table.catalog)
                if table.db:
                    parts.append(table.db)
                parts.append(table.name)
                tables.add(".".join(parts))
            return sorted(tables)
        except:
            return []
    
    def format_sql(self, sql: str) -> str:
        """SQL 포맷팅 (pretty print)"""
        try:
            return sqlglot.transpile(sql, read=self.dialect, pretty=True)[0]
        except:
            return sql
```

---

### main.py

```python
from fastapi import FastAPI, HTTPException
from jinja2 import TemplateSyntaxError, UndefinedError

from .schemas import (
    RenderRequest, RenderResponse,
    ValidateRequest, ValidateResponse,
    ParseRequest, ParseResponse,
    FormatRequest, FormatResponse,
)
from .renderer import render_sql
from .validator import SQLValidator

app = FastAPI(
    title="SQL Parser API",
    description="SQL 렌더링/파싱/검증 API (project-basecamp-parser)",
    version="0.1.0",
)


@app.get("/health")
async def health():
    return {"status": "healthy", "service": "basecamp-parser"}


@app.post("/render", response_model=RenderResponse)
async def render(request: RenderRequest):
    """Jinja 템플릿 렌더링"""
    try:
        rendered = render_sql(request.template, request.params)
        return RenderResponse(success=True, rendered_sql=rendered)
    except TemplateSyntaxError as e:
        return RenderResponse(success=False, error=f"Template syntax error: {e}")
    except UndefinedError as e:
        return RenderResponse(success=False, error=f"Missing parameter: {e}")
    except Exception as e:
        return RenderResponse(success=False, error=str(e))


@app.post("/validate", response_model=ValidateResponse)
async def validate(request: ValidateRequest):
    """SQL 문법 검증"""
    validator = SQLValidator(request.dialect)
    is_valid, errors, warnings = validator.validate(request.sql)
    return ValidateResponse(is_valid=is_valid, errors=errors, warnings=warnings)


@app.post("/parse", response_model=ParseResponse)
async def parse(request: ParseRequest):
    """SQL 파싱 (테이블 추출)"""
    try:
        validator = SQLValidator(request.dialect)
        tables = validator.extract_tables(request.sql)
        return ParseResponse(success=True, tables=tables)
    except Exception as e:
        return ParseResponse(success=False, error=str(e))


@app.post("/format", response_model=FormatResponse)
async def format_sql(request: FormatRequest):
    """SQL 포맷팅"""
    try:
        validator = SQLValidator(request.dialect)
        formatted = validator.format_sql(request.sql)
        return FormatResponse(success=True, formatted_sql=formatted)
    except Exception as e:
        return FormatResponse(success=False, error=str(e))
```

---

### 테스트: tests/test_api.py

```python
import pytest
from fastapi.testclient import TestClient
from parser.main import app

client = TestClient(app)


class TestHealthEndpoint:
    def test_health(self):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "healthy"


class TestRenderEndpoint:
    def test_render_success(self):
        response = client.post("/render", json={
            "template": "SELECT * FROM users WHERE date = '{{ date }}'",
            "params": {"date": "2024-01-01"}
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "2024-01-01" in data["rendered_sql"]
    
    def test_render_with_filter(self):
        response = client.post("/render", json={
            "template": "SELECT * FROM users WHERE name = {{ name | sql_string }}",
            "params": {"name": "O'Brien"}
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "O''Brien" in data["rendered_sql"]  # 이스케이프 확인
    
    def test_render_missing_param(self):
        response = client.post("/render", json={
            "template": "SELECT * FROM users WHERE date = '{{ date }}'",
            "params": {}
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is False
        assert "Missing parameter" in data["error"]
    
    def test_render_syntax_error(self):
        response = client.post("/render", json={
            "template": "SELECT * FROM users WHERE {{ % invalid }",
            "params": {}
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is False


class TestValidateEndpoint:
    def test_validate_valid_sql(self):
        response = client.post("/validate", json={
            "sql": "SELECT id, name FROM users WHERE active = true",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["is_valid"] is True
        assert len(data["errors"]) == 0
    
    def test_validate_invalid_sql(self):
        response = client.post("/validate", json={
            "sql": "SELECT * FROM",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["is_valid"] is False
        assert len(data["errors"]) > 0
    
    def test_validate_empty_sql(self):
        response = client.post("/validate", json={
            "sql": "",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["is_valid"] is False
    
    def test_validate_warning_no_limit(self):
        response = client.post("/validate", json={
            "sql": "SELECT * FROM users",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["is_valid"] is True
        assert len(data["warnings"]) > 0


class TestParseEndpoint:
    def test_parse_single_table(self):
        response = client.post("/parse", json={
            "sql": "SELECT * FROM users",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "users" in data["tables"]
    
    def test_parse_multiple_tables(self):
        response = client.post("/parse", json={
            "sql": "SELECT * FROM users u JOIN orders o ON u.id = o.user_id",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "users" in data["tables"]
        assert "orders" in data["tables"]
    
    def test_parse_with_schema(self):
        response = client.post("/parse", json={
            "sql": "SELECT * FROM analytics.events",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "analytics.events" in data["tables"]


class TestFormatEndpoint:
    def test_format_sql(self):
        response = client.post("/format", json={
            "sql": "select id,name from users where active=true",
            "dialect": "bigquery"
        })
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "SELECT" in data["formatted_sql"]  # 대문자로 변환
```

---

## Spring Backend (project-basecamp-server)

### Parser Client 인터페이스

```kotlin
// ParserClient.kt
interface ParserClient {
    fun render(template: String, params: Map<String, Any>): RenderResponse
    fun validate(sql: String, dialect: String = "bigquery"): ValidateResponse
    fun parse(sql: String, dialect: String = "bigquery"): ParseResponse
    fun format(sql: String, dialect: String = "bigquery"): FormatResponse
}

// DTOs
data class RenderResponse(
    val success: Boolean,
    val renderedSql: String?,
    val error: String?
)

data class ValidateResponse(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

data class ParseResponse(
    val success: Boolean,
    val tables: List<String>,
    val columns: List<String>,
    val error: String?
)

data class FormatResponse(
    val success: Boolean,
    val formattedSql: String?,
    val error: String?
)
```

### Parser Client 구현

```kotlin
// ParserClientImpl.kt
@Component
class ParserClientImpl(
    private val webClient: WebClient,
    @Value("\${parser.base-url}") private val baseUrl: String
) : ParserClient {

    override fun render(template: String, params: Map<String, Any>): RenderResponse {
        return webClient.post()
            .uri("$baseUrl/render")
            .bodyValue(mapOf("template" to template, "params" to params))
            .retrieve()
            .bodyToMono(RenderResponse::class.java)
            .block() ?: throw ParserException("Render failed")
    }

    override fun validate(sql: String, dialect: String): ValidateResponse {
        return webClient.post()
            .uri("$baseUrl/validate")
            .bodyValue(mapOf("sql" to sql, "dialect" to dialect))
            .retrieve()
            .bodyToMono(ValidateResponse::class.java)
            .block() ?: throw ParserException("Validate failed")
    }

    override fun parse(sql: String, dialect: String): ParseResponse {
        return webClient.post()
            .uri("$baseUrl/parse")
            .bodyValue(mapOf("sql" to sql, "dialect" to dialect))
            .retrieve()
            .bodyToMono(ParseResponse::class.java)
            .block() ?: throw ParserException("Parse failed")
    }

    override fun format(sql: String, dialect: String): FormatResponse {
        return webClient.post()
            .uri("$baseUrl/format")
            .bodyValue(mapOf("sql" to sql, "dialect" to dialect))
            .retrieve()
            .bodyToMono(FormatResponse::class.java)
            .block() ?: throw ParserException("Format failed")
    }
}
```

### Spring Service 예시

```kotlin
// QueryService.kt
@Service
class QueryService(
    private val parserClient: ParserClient,
    private val bigQueryExecutor: BigQueryExecutor,
    private val queryRepository: QueryRepository
) {

    fun validateQuery(queryId: Long, params: Map<String, Any>): ValidationResult {
        val query = queryRepository.findById(queryId)
            ?: throw QueryNotFoundException(queryId)
        
        // 1. Python Parser로 렌더링
        val renderResult = parserClient.render(query.template, params)
        if (!renderResult.success) {
            return ValidationResult(false, listOf(renderResult.error!!), emptyList(), null)
        }
        
        // 2. Python Parser로 검증
        val validateResult = parserClient.validate(renderResult.renderedSql!!, query.dialect)
        
        return ValidationResult(
            isValid = validateResult.isValid,
            errors = validateResult.errors,
            warnings = validateResult.warnings,
            renderedSql = renderResult.renderedSql
        )
    }

    fun executeQuery(queryId: Long, params: Map<String, Any>): ExecutionResult {
        val validation = validateQuery(queryId, params)
        if (!validation.isValid) {
            throw ValidationException(validation.errors)
        }
        
        // Spring에서 BigQuery 실행
        return bigQueryExecutor.execute(validation.renderedSql!!)
    }
}
```

---

## 통신 흐름 예시

### 쿼리 검증 흐름

```
1. UI → Spring: POST /api/queries/{id}/validate
   { "params": { "date": "2024-01-01" } }

2. Spring → Python: POST /render
   { "template": "SELECT * FROM t WHERE dt='{{date}}'", "params": {"date":"2024-01-01"} }

3. Python → Spring: 
   { "success": true, "rendered_sql": "SELECT * FROM t WHERE dt='2024-01-01'" }

4. Spring → Python: POST /validate
   { "sql": "SELECT * FROM t WHERE dt='2024-01-01'", "dialect": "bigquery" }

5. Python → Spring:
   { "is_valid": true, "errors": [], "warnings": [] }

6. Spring → UI:
   { "isValid": true, "renderedSql": "SELECT * FROM t WHERE dt='2024-01-01'" }
```

### 쿼리 실행 흐름

```
1. UI → Spring: POST /api/queries/{id}/execute
   { "params": { "date": "2024-01-01" } }

2. Spring → Python: POST /render (렌더링)
3. Spring → Python: POST /validate (검증)
4. Spring → BigQuery: 실행 (Spring이 직접)
5. Spring → UI: 결과 반환
```

---

## 환경 설정

### Python (basecamp-parser)

```bash
# 실행
uvicorn parser.main:app --host 0.0.0.0 --port 8001

# 환경변수
PARSER_PORT=8001
```

### Spring (basecamp-server)

```yaml
# application.yml
parser:
  base-url: http://localhost:8001

spring:
  webflux:
    timeout: 30s
```

---

## Day 3 체크리스트

### Python (basecamp-parser)
- [ ] schemas.py (4개 Request/Response)
- [ ] renderer.py (Jinja 렌더링 + 필터)
- [ ] validator.py (SQLGlot 검증 + 테이블 추출)
- [ ] main.py (4개 엔드포인트)
- [ ] 테스트 코드
- [ ] Dockerfile

### Spring (basecamp-server)
- [ ] ParserClient 인터페이스
- [ ] ParserClientImpl (WebClient)
- [ ] DTO 클래스
- [ ] QueryService 연동
- [ ] 에러 핸들링
- [ ] application.yml 설정

---

## 참고 코드

| 참고 | URL |
|------|-----|
| FastAPI | https://fastapi.tiangolo.com/ |
| SQLGlot | https://github.com/tobymao/sqlglot |
| Spring WebClient | https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html |
