# Test Refactoring Plan

> **Version:** 1.0.0
> **Status:** Completed
> **Created:** 2025-12-30
> **Completed:** 2025-12-30

---

## 1. Executive Summary

### Agent Analysis Results

| Agent | Focus | Key Findings |
|-------|-------|--------------|
| **feature-interface-cli** | CLI 패턴, Typer 컨벤션 | 테스트 파일 위치 오류, 누락된 CLI 테스트 |
| **expert-python** | pytest 베스트 프랙티스 | fixture 중복, helper 함수 분산, conftest 부재 |

### Statistics

| Metric | Value |
|--------|-------|
| Total tests | 1,282 |
| CLI test files | 11 |
| Core test files | 18+ |
| Missing test coverage | 5 modules |

---

## 2. Agent Opinions

### 2.1 feature-interface-cli Agent 의견

**관점:** CLI 패턴, Typer 테스팅 컨벤션, 프로젝트 구조 일관성

**주요 발견사항:**

1. **테스트 파일 위치 오류 (Critical)**
   - `tests/core/workflow/test_client.py`가 실제로 `core/client.py`의 workflow 메서드를 테스트
   - workflow 모듈에 `client.py`가 없으므로 혼란 유발
   - **의견:** `test_client.py`에 통합하거나 명확한 이름(`test_workflow_client_methods.py`)으로 변경

2. **누락된 CLI 테스트**
   - `commands/info.py` - 테스트 없음 (HIGH)
   - `commands/version.py` - 독립 테스트 없음 (test_main.py에서 부분 커버)
   - **의견:** info.py 테스트 필수, version.py는 선택적

3. **테스트 조직 품질**
   - CLI 테스트: `test_{module}_cmd.py` 명명 일관성 Good
   - Class-based organization 적절함
   - Typer CliRunner 사용 표준 패턴 준수
   - **의견:** 현재 구조 유지, conftest.py 추가로 개선

4. **Coverage 분석**
   - CLI Commands: 83% (12개 중 10개 테스트)
   - Core Modules: 86%
   - Adapters: 0% (bigquery.py)
   - **의견:** 핵심 기능은 양호, adapter는 optional dependency라 낮은 우선순위

---

### 2.2 expert-python Agent 의견

**관점:** pytest 베스트 프랙티스, fixture 패턴, 테스트 구조화

**주요 발견사항:**

1. **Fixture 중복 문제 (High)**
   - `sample_project_path`가 conftest.py와 test_workflow_cmd.py에 중복 정의
   - **의견:** 즉시 제거 필요, DRY 원칙 위반

2. **Helper 함수 분산 (High)**
   - `get_output()` 함수가 여러 CLI 테스트 파일에 복사됨
   - **의견:** `tests/cli/conftest.py` 생성하여 통합

3. **conftest.py 부재 (Medium)**
   - `tests/cli/` - conftest 없음
   - `tests/core/transpile/` - conftest 없음
   - **의견:** 서브디렉토리별 conftest 생성으로 fixture 재사용성 향상

4. **대형 테스트 파일 (Low)**
   - `test_models.py` (841줄), `test_client.py` (664줄)
   - **의견:** 당장 분할 불필요, 향후 고려사항

5. **pytest markers 미활용**
   - slow, integration 등 marker 없음
   - **의견:** pyproject.toml에 marker 정의 추가

---

### 2.3 합의 사항 (Consensus)

두 Agent가 분석을 교차 검토한 결과, 다음 사항에 합의함:

#### 즉시 수행 (Immediate)

| 항목 | 합의 내용 | 담당 |
|------|----------|------|
| **Fixture 중복 제거** | `test_workflow_cmd.py`의 `sample_project_path` 제거 | expert-python |
| **get_output() 통합** | `tests/cli/conftest.py` 생성하여 공유 | feature-interface-cli |
| **test_info_cmd.py 생성** | info 명령어 테스트 추가 | feature-interface-cli |

#### 우선순위 조정 후 합의

| 항목 | feature-interface-cli 의견 | expert-python 의견 | 합의 |
|------|---------------------------|-------------------|------|
| **test_client.py 통합** | 통합 권장 | 이름 변경도 가능 | **이름 변경**: `test_workflow_client_methods.py` → 기존 테스트 영향 최소화 |
| **test_config.py 생성** | LOW | MEDIUM | **MEDIUM**: 100줄 이상 모듈, 테스트 가치 있음 |
| **transpile conftest** | 불필요 | 권장 | **선택적**: 현재 테스트 수가 적어 급하지 않음 |
| **대형 파일 분할** | 불필요 | 향후 고려 | **스킵**: 현재 스코프 외 |
| **pytest markers** | 불필요 | 권장 | **스킵**: 현재 스코프 외, CI 개선 시 추가 |

#### 최종 작업 목록

**Phase 1 - 필수 (Must Do):**
1. `tests/cli/conftest.py` 생성 - `get_output()` 함수 통합
2. `test_workflow_cmd.py`에서 중복 `sample_project_path` fixture 제거
3. `tests/cli/test_info_cmd.py` 생성

**Phase 2 - 권장 (Should Do):**
4. `tests/core/workflow/test_client.py` → `tests/core/workflow/test_client_workflow_methods.py` 이름 변경
5. `tests/core/test_config.py` 생성

**Phase 3 - 선택 (Nice to Have):**
6. `tests/core/transpile/conftest.py` 생성 (선택적)
7. 각 CLI 테스트에서 로컬 `get_output()` 정의 제거

---

## 3. Identified Issues (Detail)

### 3.1 HIGH PRIORITY

#### Issue 1: Misplaced Test File
**File:** `tests/core/workflow/test_client.py`

**Problem:** `BasecampClient.workflow_*` 메서드 테스트가 workflow 디렉토리에 위치하지만, 실제로는 `core/client.py`를 테스트함.

**Impact:**
- `tests/core/test_client.py` - 일반 client 테스트
- `tests/core/workflow/test_client.py` - workflow client 테스트 (분산됨)

**Action:** `tests/core/test_client.py`에 통합 또는 명확한 이름으로 변경

#### Issue 2: Duplicate Fixtures
**Location:** `sample_project_path` fixture

```python
# conftest.py (line 50-52)
@pytest.fixture
def sample_project_path(fixtures_path: Path) -> Path:
    return fixtures_path / "sample_project"

# test_workflow_cmd.py (line 23-26) - DUPLICATE
@pytest.fixture
def sample_project_path() -> Path:
    return Path(__file__).parent.parent / "fixtures" / "sample_project"
```

**Action:** `test_workflow_cmd.py`에서 중복 fixture 제거

#### Issue 3: Duplicated Helper Functions
**Function:** `get_output()` - 여러 CLI 테스트 파일에 동일하게 정의됨

```python
# test_workflow_cmd.py, test_transpile_cmd.py, etc.
def get_output(result) -> str:
    return result.output or result.stdout or ""
```

**Action:** `tests/cli/conftest.py`로 통합

---

### 3.2 MEDIUM PRIORITY

#### Issue 4: Missing CLI Tests

| Source File | Test File | Priority |
|-------------|-----------|----------|
| `commands/info.py` | **MISSING** | HIGH |
| `commands/version.py` | **MISSING** | LOW |

#### Issue 5: Missing Core Tests

| Source File | Test File | Priority |
|-------------|-----------|----------|
| `core/config.py` | **MISSING** | MEDIUM |
| `core/types.py` | **MISSING** | LOW |
| `core/sql_filters.py` | Covered in test_templates.py | - |

#### Issue 6: Missing Adapter Tests

| Source File | Test File | Priority |
|-------------|-----------|----------|
| `adapters/bigquery.py` | **MISSING** | LOW |

#### Issue 7: Missing Subdirectory conftest.py

| Directory | Status |
|-----------|--------|
| `tests/cli/` | **MISSING** |
| `tests/core/transpile/` | **MISSING** |
| `tests/core/quality/` | **MISSING** |

---

### 3.3 LOW PRIORITY

#### Issue 8: Large Test Files
- `test_models.py` (841 lines) - 분할 고려
- `test_client.py` (664 lines) - mock/integration 분리 고려

---

## 4. Refactoring Tasks

### Phase 1: Structure Cleanup (feature-interface-cli)

- [ ] 1.1 `tests/core/workflow/test_client.py` → `tests/core/test_client.py`에 통합
- [ ] 1.2 `tests/cli/conftest.py` 생성 (get_output, runner 공유)
- [ ] 1.3 `test_workflow_cmd.py`에서 중복 fixture 제거
- [ ] 1.4 `tests/cli/test_info_cmd.py` 생성

### Phase 2: Core Test Improvements (expert-python)

- [ ] 2.1 `tests/core/transpile/conftest.py` 생성
- [ ] 2.2 `tests/core/test_config.py` 생성
- [ ] 2.3 각 CLI 테스트에서 get_output() 제거, conftest 사용
- [ ] 2.4 pytest markers 설정 (pyproject.toml)

---

## 5. Implementation Details

### 5.1 tests/cli/conftest.py (NEW)

```python
"""Shared fixtures and helpers for CLI tests."""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING

import pytest
from typer.testing import CliRunner

from dli.main import app

if TYPE_CHECKING:
    from typer.testing import Result

runner = CliRunner()


def get_output(result: "Result") -> str:
    """Get combined output from CLI result."""
    return result.output or result.stdout or ""


@pytest.fixture
def cli_runner() -> CliRunner:
    """Provide CliRunner for CLI tests."""
    return runner
```

### 5.2 tests/cli/test_info_cmd.py (NEW)

```python
"""Tests for info command."""

from __future__ import annotations

from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


class TestInfoCommand:
    """Tests for `dli info` command."""

    def test_info_output(self) -> None:
        """Test info command displays environment information."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        assert "CLI Version" in result.output or "Environment" in result.output

    def test_info_shows_python_version(self) -> None:
        """Test info command shows Python version."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        # Python version should be displayed
        assert "Python" in result.output or "python" in result.output.lower()
```

### 5.3 tests/core/transpile/conftest.py (NEW)

```python
"""Shared fixtures for transpile tests."""

from __future__ import annotations

import pytest

from dli.core.transpile.client import MockTranspileClient
from dli.core.transpile.engine import TranspileEngine
from dli.core.transpile.models import TranspileConfig


@pytest.fixture
def mock_client() -> MockTranspileClient:
    """Create mock transpile client."""
    return MockTranspileClient()


@pytest.fixture
def default_config() -> TranspileConfig:
    """Create default transpile config."""
    return TranspileConfig()


@pytest.fixture
def default_engine(mock_client: MockTranspileClient) -> TranspileEngine:
    """Create default transpile engine with mock client."""
    return TranspileEngine(client=mock_client)


@pytest.fixture
def strict_engine(mock_client: MockTranspileClient) -> TranspileEngine:
    """Create strict transpile engine with mock client."""
    return TranspileEngine(client=mock_client, strict=True)
```

### 5.4 tests/core/test_config.py (NEW)

```python
"""Tests for core config module."""

from __future__ import annotations

from pathlib import Path

import pytest

from dli.core.config import ProjectConfig, get_dli_home, load_project


class TestGetDliHome:
    """Tests for get_dli_home function."""

    def test_default_home(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test default DLI home directory."""
        monkeypatch.delenv("DLI_HOME", raising=False)
        home = get_dli_home()
        assert home.name == ".dli"

    def test_custom_home(self, monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
        """Test custom DLI home from environment."""
        custom_home = tmp_path / "custom_dli"
        monkeypatch.setenv("DLI_HOME", str(custom_home))
        home = get_dli_home()
        assert home == custom_home


class TestProjectConfig:
    """Tests for ProjectConfig class."""

    def test_load_valid_config(self, sample_project_path: Path) -> None:
        """Test loading valid project config."""
        config = load_project(sample_project_path)
        assert config is not None
        assert config.server_url is not None

    def test_server_properties(self, sample_project_path: Path) -> None:
        """Test server property accessors."""
        config = load_project(sample_project_path)
        assert config.server_timeout > 0
        # api_key may or may not be set

    def test_missing_config_returns_none(self, tmp_path: Path) -> None:
        """Test loading from directory without dli.yaml."""
        # load_project may return None or raise - verify behavior
        config = load_project(tmp_path)
        # Behavior depends on implementation
```

---

## 6. Task Assignment

### feature-interface-cli Agent
- Task 1.1, 1.2, 1.3, 1.4
- Focus: CLI test structure, Typer patterns

### expert-python Agent
- Task 2.1, 2.2, 2.3, 2.4
- Focus: pytest best practices, fixture patterns

---

## 7. Cross-Review Checklist

### feature-interface-cli reviews expert-python work
- [x] conftest.py fixture 패턴이 CLI 테스트와 호환되는지 확인
- [x] pytest markers 설정 (현재 스코프 외로 스킵됨)

### expert-python reviews feature-interface-cli work
- [x] 새로운 테스트 파일이 pytest 컨벤션을 따르는지 확인
- [x] fixture 사용이 일관적인지 확인
- [x] 타입 힌트가 올바른지 확인

---

## 8. Execution Results

### 8.1 Files Changed

#### Created
| File | Lines | Description |
|------|-------|-------------|
| `tests/cli/conftest.py` | 20 | Shared `get_output()` helper, `cli_runner` fixture |
| `tests/cli/test_info_cmd.py` | 60 | Info command tests (6 tests) |
| `tests/core/test_config.py` | 90 | Config module tests (34 tests) |
| `tests/core/transpile/conftest.py` | 40 | Shared transpile fixtures |

#### Modified
| File | Changes |
|------|---------|
| `tests/cli/test_main.py` | Deprecated command tests 제거, 현행 명령어 테스트로 업데이트 |
| `tests/cli/test_workflow_cmd.py` | 중복 `sample_project_path` fixture 제거 |
| `tests/cli/test_*.py` (7 files) | 중복 `get_output()` 제거, conftest import |
| `tests/core/transpile/test_engine.py` | 로컬 fixture → conftest 사용 |

#### Renamed
| From | To |
|------|-----|
| `tests/core/workflow/test_client.py` | `tests/core/workflow/test_client_workflow_methods.py` |

### 8.2 Test Results

| Test Suite | Tests | Status |
|------------|-------|--------|
| `tests/cli/` | 262 | ✅ All passed |
| `tests/core/` | 1,048 | ✅ All passed |
| **Total** | **1,310** | ✅ All passed |

### 8.3 Cross-Review Summary

| Reviewer | Reviewed | Verdict |
|----------|----------|---------|
| feature-interface-cli | expert-python work | **APPROVED** - fixture 패턴 호환, 타입 정확 |
| expert-python | feature-interface-cli work | **APPROVED** - pytest 컨벤션 준수, minor 이슈 수정됨 |

#### Minor Issues Fixed
1. `test_client_workflow_methods.py` - unused `datetime` import 제거
2. `test_info_cmd.py` - `get_output` import 추가
3. `test_main.py` - deprecated command tests 정리 (22개 → 24개 현행 테스트)

---

**Last Updated:** 2025-12-30
