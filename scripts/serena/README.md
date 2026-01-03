# Serena Symbol Auto-Update System

Claude Code와 Serena MCP를 위한 토큰 효율적인 Symbol 자동 업데이트 시스템입니다.

## 🎯 기능

- **Git Hook 기반 자동 업데이트**: 커밋/머지 시 변경된 파일만 선택적 업데이트
- **수동 업데이트 스크립트**: 필요에 따라 실행하는 유연한 업데이트
- **언어별 선택적 갱신**: Kotlin, TypeScript, Python 언어별 독립 업데이트
- **의존성 분석**: 프로젝트 간 의존성 고려한 스마트 업데이트
- **메모리 패턴 갱신**: Serena 메모리 파일 자동 동기화

## 📁 구조

```
scripts/serena/
├── update-symbols.py    # 메인 업데이트 스크립트
├── README.md           # 이 파일
└── test-update.sh      # 테스트 스크립트

.git/hooks/
├── post-commit         # 커밋 후 자동 업데이트
└── post-merge          # 머지/풀 후 자동 업데이트
```

## 🚀 사용법

### 1. 자동 업데이트 (Git Hooks)

Git Hook이 설치되어 있어 다음 상황에서 자동 실행됩니다:

```bash
# 커밋 후 자동 실행
git commit -m "Update TypeScript types"
# 🔄 Serena: Updating symbol cache for changed files...
# ✅ Serena: Symbol cache updated successfully

# 머지/풀 후 자동 실행
git pull origin main
# 🔄 Serena: Updating symbol cache after merge...
# ✅ Serena: Symbol cache updated successfully after merge
```

### 2. 수동 업데이트

#### 기본 사용법

```bash
# 전체 프로젝트 업데이트
python3 scripts/serena/update-symbols.py --all

# 특정 프로젝트만
python3 scripts/serena/update-symbols.py --project project-basecamp-server

# 특정 언어만
python3 scripts/serena/update-symbols.py --language python

# 여러 언어
python3 scripts/serena/update-symbols.py --language python --language typescript
```

#### 고급 옵션

```bash
# 의존성 분석 포함
python3 scripts/serena/update-symbols.py --all --with-deps

# 메모리 패턴도 함께 갱신
python3 scripts/serena/update-symbols.py --all --with-memories

# 변경된 파일만 (Git 기반)
python3 scripts/serena/update-symbols.py --changed-only

# 실행 전 미리보기
python3 scripts/serena/update-symbols.py --all --dry-run

# 상세 로그 출력
python3 scripts/serena/update-symbols.py --all --verbose
```

#### 복합 사용 예시

```bash
# 완전 업데이트 (의존성 + 메모리 포함)
python3 scripts/serena/update-symbols.py --all --with-deps --with-memories

# Python 프로젝트만 의존성 분석하며 업데이트
python3 scripts/serena/update-symbols.py --language python --with-deps

# CLI 프로젝트 수정 후 관련 프로젝트도 함께 업데이트
python3 scripts/serena/update-symbols.py --project project-interface-cli --with-deps
```

## 🏗️ 프로젝트별 언어 매핑

| 프로젝트 | 언어 | 설명 |
|----------|------|------|
| `project-basecamp-server` | `kotlin` | Spring Boot + Kotlin (멀티모듈) |
| `project-basecamp-ui` | `typescript` | React 19 + TypeScript |
| `project-basecamp-parser` | `python` | Flask + SQLglot |
| `project-basecamp-connect` | `python` | Flask + 통합 서비스 |
| `project-interface-cli` | `python` | Typer + CLI 도구 |

## 🔧 설정

### Git Hook 활성화/비활성화

Git Hook을 일시적으로 비활성화하려면:

```bash
# Hook 비활성화
mv .git/hooks/post-commit .git/hooks/post-commit.disabled
mv .git/hooks/post-merge .git/hooks/post-merge.disabled

# Hook 재활성화
mv .git/hooks/post-commit.disabled .git/hooks/post-commit
mv .git/hooks/post-merge.disabled .git/hooks/post-merge
```

### 환경 변수

```bash
# 로그 레벨 설정
export SERENA_UPDATE_LOG_LEVEL=DEBUG

# Dry-run 모드 기본값
export SERENA_UPDATE_DRY_RUN=1
```

## 🔍 문제 해결

### 자주 발생하는 문제

1. **Language Server 재시작 실패**
   ```bash
   # 수동으로 Serena 재시작
   python3 -c "import subprocess; subprocess.run(['mcp-client', 'serena', 'restart_language_server'])"
   ```

2. **캐시 파일 권한 오류**
   ```bash
   # 캐시 디렉터리 권한 확인
   ls -la .serena/cache/
   # 권한 수정
   chmod -R 755 .serena/cache/
   ```

3. **Git Hook 실행 안됨**
   ```bash
   # Hook 파일 실행 권한 확인
   ls -la .git/hooks/post-*
   # 실행 권한 부여
   chmod +x .git/hooks/post-commit .git/hooks/post-merge
   ```

### 디버깅

```bash
# Verbose 모드로 상세 로그 확인
python3 scripts/serena/update-symbols.py --all --verbose

# Dry-run으로 실행 계획 확인
python3 scripts/serena/update-symbols.py --all --dry-run --verbose

# 특정 언어만 테스트
python3 scripts/serena/update-symbols.py --language python --dry-run --verbose
```

## 🧪 테스트

테스트 스크립트로 시스템이 올바르게 작동하는지 확인할 수 있습니다:

```bash
# 테스트 실행
bash scripts/serena/test-update.sh

# 특정 케이스만 테스트
bash scripts/serena/test-update.sh --test-case dry-run
```

## 📋 로그 예시

정상적으로 실행되면 다음과 같은 로그가 출력됩니다:

```
2026-01-04 15:30:25 - serena-updater - INFO - Starting Serena Symbol Update...
2026-01-04 15:30:25 - serena-updater - INFO - Found 3 changed files since HEAD~1
2026-01-04 15:30:25 - serena-updater - INFO - Changed-only mode: updating projects {'project-interface-cli'}
2026-01-04 15:30:25 - serena-updater - INFO - Target projects: {'project-interface-cli'}
2026-01-04 15:30:25 - serena-updater - INFO - Target languages: {'python'}
2026-01-04 15:30:26 - serena-updater - INFO - Successfully restarted python language server
2026-01-04 15:30:26 - serena-updater - INFO - Removed existing cache: .serena/cache/python/document_symbols.pkl
2026-01-04 15:30:26 - serena-updater - INFO - Symbol cache marked for regeneration: python
2026-01-04 15:30:26 - serena-updater - INFO - Serena Symbol Update completed: SUCCESS
```

## 🤝 기여하기

시스템 개선 사항이나 버그 리포트는 언제든 환영합니다:

1. Issue 생성 또는 직접 수정
2. 새로운 언어 지원 추가
3. 의존성 분석 알고리즘 개선
4. 메모리 패턴 업데이트 로직 향상

---

**마지막 업데이트:** 2026-01-04
**버전:** 1.0.0
**호환성:** Serena MCP, Claude Code CLI