# Project Interface Library

**다중 언어 공유 라이브러리 - DataOps 플랫폼의 공통 컴포넌트**

> **⚠️ 프로젝트 상태**: 계획 단계 (구현 예정)

## 개요

`project-interface-library`는 DataOps 플랫폼의 모든 서비스에서 공유하는 라이브러리 컬렉션입니다. 데이터 모델, API 클라이언트, 공통 유틸리티, 검증기 등을 제공하여 서비스 간 일관성과 코드 재사용성을 높이는 것이 목표입니다.

## 목적

- **코드 재사용성**: 모든 서비스에서 사용할 수 있는 공통 컴포넌트 제공
- **데이터 일관성**: 표준화된 데이터 모델 및 DTO를 통한 서비스 간 일관성
- **API 통신**: 내부 서비스 간 통신을 위한 표준 클라이언트 라이브러리
- **공통 기능**: 로깅, 검증, 설정 관리 등 공통 기능의 중앙화
- **타입 안전성**: 강타입 언어에서 공유 타입 정의

## 계획된 구조

```
project-interface-library/
├── python/                           # Python 패키지
│   ├── dataops_common/
│   │   ├── __init__.py
│   │   ├── models/                   # 공통 데이터 모델
│   │   │   ├── __init__.py
│   │   │   ├── pipeline.py           # 파이프라인 모델
│   │   │   ├── job.py                # 작업 모델
│   │   │   ├── dataset.py            # 데이터셋 모델
│   │   │   └── base.py               # 기본 모델 클래스
│   │   ├── clients/                  # API 클라이언트
│   │   │   ├── __init__.py
│   │   │   ├── basecamp_client.py    # Basecamp 서버 클라이언트
│   │   │   ├── parser_client.py      # Parser 서비스 클라이언트
│   │   │   └── base_client.py        # 기본 HTTP 클라이언트
│   │   ├── validators/               # 검증 유틸리티
│   │   │   ├── __init__.py
│   │   │   ├── sql_validator.py      # SQL 검증
│   │   │   ├── config_validator.py   # 설정 검증
│   │   │   └── data_validator.py     # 데이터 검증
│   │   ├── utils/                    # 공통 유틸리티
│   │   │   ├── __init__.py
│   │   │   ├── logging.py            # 로깅 유틸리티
│   │   │   ├── config.py             # 설정 관리
│   │   │   ├── datetime_utils.py     # 날짜/시간 유틸리티
│   │   │   └── string_utils.py       # 문자열 유틸리티
│   │   └── exceptions/               # 공통 예외
│   │       ├── __init__.py
│   │       ├── api_exceptions.py     # API 관련 예외
│   │       ├── validation_exceptions.py # 검증 예외
│   │       └── common_exceptions.py  # 일반 예외
│   ├── pyproject.toml                # Python 패키지 설정
│   ├── README.md                     # Python 패키지 문서
│   └── tests/                        # Python 테스트
│       ├── test_models.py
│       ├── test_clients.py
│       ├── test_validators.py
│       └── test_utils.py
│
├── jvm/                              # Kotlin/Java 라이브러리
│   ├── build.gradle.kts              # Gradle 빌드 설정
│   ├── src/main/kotlin/com/github/lambda/common/
│   │   ├── models/                   # 공통 데이터 모델
│   │   │   ├── Pipeline.kt           # 파이프라인 모델
│   │   │   ├── Job.kt                # 작업 모델
│   │   │   ├── Dataset.kt            # 데이터셋 모델
│   │   │   └── BaseModel.kt          # 기본 모델 클래스
│   │   ├── clients/                  # API 클라이언트
│   │   │   ├── BasecampClient.kt     # Basecamp 서버 클라이언트
│   │   │   ├── ParserClient.kt       # Parser 서비스 클라이언트
│   │   │   └── BaseHttpClient.kt     # 기본 HTTP 클라이언트
│   │   ├── validators/               # 검증 유틸리티
│   │   │   ├── SqlValidator.kt       # SQL 검증
│   │   │   ├── ConfigValidator.kt    # 설정 검증
│   │   │   └── DataValidator.kt      # 데이터 검증
│   │   ├── utils/                    # 공통 유틸리티
│   │   │   ├── LoggingUtils.kt       # 로깅 유틸리티
│   │   │   ├── ConfigUtils.kt        # 설정 관리
│   │   │   ├── DateTimeUtils.kt      # 날짜/시간 유틸리티
│   │   │   └── StringUtils.kt        # 문자열 유틸리티
│   │   └── exceptions/               # 공통 예외
│   │       ├── ApiExceptions.kt      # API 관련 예외
│   │       ├── ValidationExceptions.kt # 검증 예외
│   │       └── CommonExceptions.kt   # 일반 예외
│   ├── src/test/kotlin/              # Kotlin 테스트
│   └── README.md                     # JVM 라이브러리 문서
│
├── node/                             # TypeScript/JavaScript 모듈
│   ├── package.json                  # Node.js 패키지 설정
│   ├── tsconfig.json                 # TypeScript 설정
│   ├── src/
│   │   ├── models/                   # 공통 데이터 모델
│   │   │   ├── index.ts
│   │   │   ├── Pipeline.ts           # 파이프라인 모델
│   │   │   ├── Job.ts                # 작업 모델
│   │   │   ├── Dataset.ts            # 데이터셋 모델
│   │   │   └── BaseModel.ts          # 기본 모델 클래스
│   │   ├── clients/                  # API 클라이언트
│   │   │   ├── index.ts
│   │   │   ├── BasecampClient.ts     # Basecamp 서버 클라이언트
│   │   │   ├── ParserClient.ts       # Parser 서비스 클라이언트
│   │   │   └── BaseHttpClient.ts     # 기본 HTTP 클라이언트
│   │   ├── validators/               # 검증 유틸리티
│   │   │   ├── index.ts
│   │   │   ├── sqlValidator.ts       # SQL 검증
│   │   │   ├── configValidator.ts    # 설정 검증
│   │   │   └── dataValidator.ts      # 데이터 검증
│   │   ├── utils/                    # 공통 유틸리티
│   │   │   ├── index.ts
│   │   │   ├── logging.ts            # 로깅 유틸리티
│   │   │   ├── config.ts             # 설정 관리
│   │   │   ├── dateTimeUtils.ts      # 날짜/시간 유틸리티
│   │   │   └── stringUtils.ts        # 문자열 유틸리티
│   │   └── exceptions/               # 공통 예외
│   │       ├── index.ts
│   │       ├── ApiExceptions.ts      # API 관련 예외
│   │       ├── ValidationExceptions.ts # 검증 예외
│   │       └── CommonExceptions.ts   # 일반 예외
│   ├── tests/                        # TypeScript 테스트
│   ├── dist/                         # 컴파일된 JavaScript
│   └── README.md                     # Node.js 모듈 문서
│
├── docs/                             # 공통 문서
│   ├── api-guide.md                  # API 사용 가이드
│   ├── models-guide.md               # 데이터 모델 가이드
│   ├── validation-guide.md           # 검증 가이드
│   └── migration-guide.md            # 마이그레이션 가이드
│
├── scripts/                          # 빌드/배포 스크립트
│   ├── build-all.sh                  # 전체 빌드
│   ├── test-all.sh                   # 전체 테스트
│   ├── publish-python.sh             # Python 패키지 배포
│   ├── publish-jvm.sh                # JVM 라이브러리 배포
│   └── publish-node.sh               # Node.js 모듈 배포
│
├── .gitignore                        # Git 무시 파일
├── LICENSE                           # 라이선스
└── README.md                         # 프로젝트 메인 문서
```

## 계획된 기술 스택

### Python 패키지
- **Pydantic 2.x**: 데이터 모델 및 검증
- **httpx**: 비동기 HTTP 클라이언트
- **structlog**: 구조화된 로깅
- **pytest**: 테스트 프레임워크
- **uv**: 패키지 관리 및 빌드

### JVM 라이브러리 (Kotlin)
- **Kotlin 2.x**: 주 개발 언어
- **Jackson**: JSON 시리얼라이제이션
- **OkHttp**: HTTP 클라이언트
- **SLF4J**: 로깅 인터페이스
- **JUnit 5**: 테스트 프레임워크
- **Gradle**: 빌드 도구

### Node.js 모듈 (TypeScript)
- **TypeScript 5.x**: 타입 안전성
- **Zod**: 스키마 검증
- **axios**: HTTP 클라이언트
- **winston**: 로깅 라이브러리
- **jest**: 테스트 프레임워크
- **npm/pnpm**: 패키지 관리

## 계획된 주요 컴포넌트

### 1. 데이터 모델 (models/)

#### 파이프라인 모델
```python
# Python 예시
from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class Pipeline(BaseModel):
    id: Optional[int] = None
    name: str
    description: Optional[str] = None
    status: str
    config: dict
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
```

```kotlin
// Kotlin 예시
data class Pipeline(
    val id: Long? = null,
    val name: String,
    val description: String? = null,
    val status: String,
    val config: Map<String, Any>,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)
```

```typescript
// TypeScript 예시
interface Pipeline {
  id?: number;
  name: string;
  description?: string;
  status: string;
  config: Record<string, any>;
  createdAt?: Date;
  updatedAt?: Date;
}
```

### 2. API 클라이언트 (clients/)

#### Basecamp 서버 클라이언트
```python
# Python 예시
class BasecampClient:
    def __init__(self, base_url: str, timeout: int = 30):
        self.base_url = base_url
        self.client = httpx.AsyncClient(timeout=timeout)

    async def get_pipelines(self) -> List[Pipeline]:
        response = await self.client.get(f"{self.base_url}/api/pipelines")
        return [Pipeline.model_validate(p) for p in response.json()]

    async def create_pipeline(self, pipeline: Pipeline) -> Pipeline:
        response = await self.client.post(
            f"{self.base_url}/api/pipelines",
            json=pipeline.model_dump()
        )
        return Pipeline.model_validate(response.json())
```

### 3. 검증 유틸리티 (validators/)

#### SQL 검증기
```python
# Python 예시
from sqlglot import parse, ParseError

class SqlValidator:
    @staticmethod
    def validate_sql(sql: str, dialect: str = "trino") -> bool:
        try:
            parse(sql, dialect=dialect)
            return True
        except ParseError:
            return False

    @staticmethod
    def extract_tables(sql: str, dialect: str = "trino") -> List[str]:
        try:
            parsed = parse(sql, dialect=dialect)[0]
            return list(parsed.find_all(Table))
        except ParseError:
            return []
```

### 4. 공통 유틸리티 (utils/)

#### 로깅 유틸리티
```python
# Python 예시
import structlog

def setup_logging(level: str = "INFO", format: str = "json"):
    structlog.configure(
        processors=[
            structlog.stdlib.filter_by_level,
            structlog.stdlib.add_logger_name,
            structlog.stdlib.add_log_level,
            structlog.processors.CallsiteParameterAdder(
                parameters=[structlog.processors.CallsiteParameter.FUNC_NAME]
            ),
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.JSONRenderer() if format == "json" else structlog.dev.ConsoleRenderer()
        ],
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )
```

## 서비스 통합 계획

### 1. project-basecamp-server
- JVM 라이브러리를 의존성으로 추가
- 공통 모델 및 유틸리티 활용
- 내부 API 클라이언트 사용

### 2. project-basecamp-parser
- Python 패키지를 의존성으로 추가
- SQL 검증 및 파싱 유틸리티 활용
- 공통 예외 및 로깅 사용

### 3. project-basecamp-ui
- Node.js 모듈을 의존성으로 추가
- TypeScript 타입 정의 활용
- API 클라이언트 사용

### 4. project-interface-cli
- Python 패키지를 의존성으로 추가
- 공통 설정 및 API 클라이언트 활용

## 패키지 배포 계획

### Python 패키지 (PyPI)
```bash
# 로컬 개발
pip install -e ./project-interface-library/python

# 배포된 패키지
pip install dataops-common
```

### JVM 라이브러리 (Maven Central 또는 사내 Repository)
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.lambda:dataops-common:1.0.0")
}
```

### Node.js 모듈 (npm)
```bash
# 로컬 개발
npm link ./project-interface-library/node

# 배포된 패키지
npm install @dataops/common
```

## 버전 관리 계획

- **Semantic Versioning**: MAJOR.MINOR.PATCH
- **Breaking Changes**: Major 버전 업
- **New Features**: Minor 버전 업
- **Bug Fixes**: Patch 버전 업
- **Multi-language Sync**: 모든 언어 패키지의 동기화된 버전 관리

## 개발 로드맵

### Phase 1: 기본 구조 (계획)
- [ ] 프로젝트 구조 설정
- [ ] 기본 데이터 모델 정의
- [ ] Python 패키지 초기 구현
- [ ] 기본 테스트 프레임워크 설정

### Phase 2: 핵심 컴포넌트 (계획)
- [ ] JVM 라이브러리 구현
- [ ] Node.js 모듈 구현
- [ ] API 클라이언트 구현
- [ ] 공통 유틸리티 구현

### Phase 3: 고급 기능 (계획)
- [ ] 검증 유틸리티 구현
- [ ] 로깅 및 모니터링 통합
- [ ] 문서화 자동화
- [ ] CI/CD 파이프라인 구축

### Phase 4: 통합 및 배포 (계획)
- [ ] 각 서비스에 통합
- [ ] 패키지 배포 자동화
- [ ] 성능 최적화
- [ ] 프로덕션 검증

## 기여 가이드

이 프로젝트가 구현되면 다음 가이드라인을 따를 예정입니다:

1. **새 기능 브랜치**: `feature/라이브러리명-기능명`
2. **버그 수정 브랜치**: `fix/라이브러리명-버그명`
3. **코딩 스타일**: 각 언어별 표준 컨벤션 준수
4. **테스트 커버리지**: 최소 80% 이상 유지
5. **문서화**: 모든 공개 API 문서화 필수
6. **버전 동기화**: 모든 언어 패키지의 기능 동기화

## 라이선스

이 프로젝트는 DataOps 플랫폼의 일부로, 동일한 라이선스 조건을 따를 예정입니다.

---

**📋 참고**: 이 문서는 프로젝트 계획을 위한 것으로, 실제 구현과 다를 수 있습니다. 구현 진행에 따라 문서도 함께 업데이트될 예정입니다.

**❓ 문의사항이나 제안사항은 GitHub Issues를 통해 제보해주세요.**