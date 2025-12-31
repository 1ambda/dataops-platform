# Dependency Coordination Skill

크로스 Agent 의존성을 추적하고 요청을 관리하는 프로세스.

## 문제 배경

Agent가 외부 의존성이 필요한 기능 구현 시:
- 다른 컴포넌트(Basecamp Server, Connect 등) 작업이 필요한 항목이 추적되지 않음
- 의존성 요청이 Agent 간에 전달되지 않음
- "Blocked" 상태 항목이 무기한 대기
- 의존성 해결 후에도 알림 없음

## 적용 시점

이 skill은 다음 상황에서 적용:
- `gap-analysis`에서 EXTERNAL dependency 발견 시
- `phase-tracking`에서 Blocked 항목 발견 시
- Agent가 다른 컴포넌트 작업 필요 시
- 의존성 요청 상태 확인 시

---

## 의존성 유형

### Dependency Types

| Type | 설명 | 예시 |
|------|------|------|
| `API` | Server API endpoint 필요 | POST /api/quality/run |
| `INTEGRATION` | 외부 서비스 연동 필요 | Slack webhook, Airflow |
| `SCHEMA` | DB 스키마/모델 변경 필요 | Quality 테이블 추가 |
| `CONFIG` | 설정 변경 필요 | 환경 변수, secrets |
| `UI` | UI 컴포넌트 필요 | Quality 대시보드 |

### Owner Agents

| Component | Owner Agent | 담당 |
|-----------|-------------|------|
| Basecamp Server | `feature-basecamp-server` | REST API |
| Basecamp Connect | `feature-basecamp-connect` | 외부 연동 |
| Basecamp UI | `feature-basecamp-ui` | 웹 대시보드 |
| Basecamp Parser | `feature-basecamp-parser` | SQL 파싱 |
| Airflow | `expert-devops-cicd` | DAG 생성/배포 |
| Infrastructure | `expert-devops-cicd` | 인프라 |

---

## 의존성 요청 프로세스

### Step 1: 의존성 식별

```markdown
## Dependency Identification

### From gap-analysis

| Item | Dependency Type | Required From |
|------|-----------------|---------------|
| SERVER mode run | API | feature-basecamp-server |
| Slack notification | INTEGRATION | feature-basecamp-connect |
| Quality dashboard | UI | feature-basecamp-ui |

### From phase-tracking

| Phase Item | Blocked By | Owner |
|------------|------------|-------|
| SERVER mode | Basecamp Server API | feature-basecamp-server |
```

### Step 2: 요청 생성

```markdown
## Dependency Request: DEP-2026-001

### Request Header

| Field | Value |
|-------|-------|
| **Request ID** | DEP-2026-001 |
| **Created** | 2026-01-01 |
| **From** | feature-interface-cli |
| **To** | feature-basecamp-server |
| **Feature** | Quality SERVER Mode |
| **Priority** | P0 |
| **Status** | Pending |

### Required Capability

**Type:** API Endpoint

**Specification:**
```
POST /api/quality/run

Request Body:
{
  "spec_path": "quality.iceberg.analytics.daily_clicks.yaml",
  "mode": "server",
  "tests": ["pk_unique"],  // optional
  "parameters": {}          // optional
}

Response:
{
  "execution_id": "exec-2026-01-01-001",
  "status": "success" | "failed",
  "results": [...],
  "started_at": "...",
  "finished_at": "..."
}
```

### Reference Documentation

- FEATURE_QUALITY.md Section 2.3 (Architecture)
- FEATURE_QUALITY.md Section 4 (API Design)

### Impact if Not Resolved

- QualityAPI.run(mode=SERVER) will remain stub
- SERVER mode execution not available
- Phase 1.5 blocked

### Acceptance Criteria

1. API endpoint accessible at /api/quality/run
2. Request/Response matches specification
3. Results stored in database
4. Integration test passing
```

### Step 3: 요청 추적

```markdown
## Dependency Request Tracker

### Active Requests

| ID | From | To | Feature | Priority | Status | Created | Updated |
|----|------|----|---------|---------:|--------|---------|---------|
| DEP-2026-001 | CLI | Server | Quality SERVER | P0 | Pending | 2026-01-01 | - |
| DEP-2026-002 | CLI | Connect | Slack notification | P1 | Pending | 2026-01-01 | - |
| DEP-2026-003 | CLI | UI | Quality dashboard | P2 | Pending | 2026-01-01 | - |

### Status Legend

| Status | 의미 |
|--------|------|
| `Pending` | 요청 생성됨, 응답 대기 |
| `Accepted` | 요청 수락됨, 구현 예정 |
| `In Progress` | 구현 진행 중 |
| `Blocked` | 담당 Agent도 blocked |
| `Completed` | 구현 완료, 통합 가능 |
| `Rejected` | 요청 거절됨, 대안 필요 |
| `Deferred` | 연기됨, 추후 재검토 |
```

---

## 요청/응답 프로토콜

### 요청 형식 (Request)

```markdown
## Dependency Request

**Request ID:** DEP-{YEAR}-{SEQ}

### From (Requester)
- Agent: {requester_agent}
- Feature: {feature_name}
- FEATURE Doc: {feature_doc_path}

### To (Provider)
- Agent: {provider_agent}
- Component: {component_name}

### Requirement
- Type: API | INTEGRATION | SCHEMA | CONFIG | UI
- Description: {brief_description}
- Specification: {detailed_spec}
- Priority: P0 | P1 | P2 | P3

### Timeline
- Requested: {date}
- Desired By: {date} (optional)

### Notes
{additional_context}
```

### 응답 형식 (Response)

```markdown
## Dependency Response

**Request ID:** DEP-{YEAR}-{SEQ}

### Decision
- Status: Accepted | Rejected | Deferred
- Decided By: {provider_agent}
- Date: {date}

### If Accepted
- ETA: {estimated_date}
- Assignee: {assignee}
- Notes: {implementation_notes}

### If Rejected
- Reason: {rejection_reason}
- Alternative: {suggested_alternative}

### If Deferred
- Reason: {deferral_reason}
- Revisit Date: {date}
```

---

## Serena Memory 연동

### 의존성 추적 저장

```python
# 의존성 요청 저장
mcp__serena__write_memory(
    "dependency_requests",
    """
# Cross-Agent Dependency Requests

## Active Requests

### DEP-2026-001
- From: feature-interface-cli
- To: feature-basecamp-server
- Feature: Quality SERVER Mode
- Type: API
- Priority: P0
- Status: Pending
- Created: 2026-01-01

### DEP-2026-002
...

## Completed Requests
(none)

## Rejected/Deferred Requests
(none)
"""
)
```

### 의존성 상태 업데이트

```python
# 상태 변경 시 업데이트
mcp__serena__edit_memory(
    "dependency_requests",
    "Status: Pending",
    "Status: Accepted\n- ETA: 2026-01-15",
    "literal"
)
```

---

## 의존성 해결 알림

### 해결 시 알림 트리거

```markdown
## Dependency Resolved: DEP-2026-001

**Request ID:** DEP-2026-001
**Feature:** Quality SERVER Mode
**Status:** COMPLETED ✅

### Resolution Summary

| Field | Value |
|-------|-------|
| Requested | 2026-01-01 |
| Completed | 2026-01-15 |
| Duration | 14 days |
| Implemented By | feature-basecamp-server |

### What's Available

- API: POST /api/quality/run
- Endpoint tested and operational
- Integration documentation: {link}

### Next Steps for Requester

1. Update QualityAPI to use real server endpoint
2. Remove mock/stub implementation
3. Add integration tests
4. Update RELEASE documentation
```

---

## agent-cross-review 연동

의존성 요청을 `agent-cross-review` skill과 연동:

```markdown
## Integration with agent-cross-review

### 요청 생성 시
dependency-coordination에서 요청 생성
       ↓
agent-cross-review로 요청 전달
       ↓
담당 Agent에 리뷰 요청 형태로 알림

### 요청 완료 시
담당 Agent가 구현 완료
       ↓
agent-cross-review로 완료 알림
       ↓
dependency-coordination에서 상태 업데이트
       ↓
요청자 Agent에 알림
```

---

## 자동화 트리거

### 자동 요청 생성

다음 상황에서 자동으로 의존성 요청 생성 제안:

| 트리거 | 액션 |
|--------|------|
| gap-analysis에서 EXTERNAL 발견 | 요청 생성 제안 |
| phase-tracking에서 Blocked 발견 | 요청 생성 제안 |
| FEATURE에 외부 API 참조 | 요청 생성 제안 |

### 출력 예시

```markdown
## External Dependencies Detected

gap-analysis에서 다음 외부 의존성이 발견되었습니다:

| Item | Required From | Status |
|------|---------------|--------|
| POST /api/quality/run | Basecamp Server | Not Requested |
| Slack webhook | Basecamp Connect | Not Requested |

### Recommended Action

위 의존성에 대해 요청을 생성하시겠습니까?

1. **[Create All Requests]** - 모든 의존성 요청 생성
2. **[Create Selected]** - 선택한 의존성만 요청
3. **[Skip]** - 나중에 생성

→ "모든 요청 생성해줘" 또는 번호 선택
```

---

## SLA 가이드라인

의존성 요청에 대한 기대 응답/구현 시간:

| Priority | 응답 기한 | 구현 기한 | 비고 |
|----------|----------|----------|------|
| **P0** | 1 영업일 | 1주일 | 핵심 기능 블로커 |
| **P1** | 3 영업일 | 2주일 | 중요 기능 |
| **P2** | 1주일 | 1개월 | 일반 기능 |
| **P3** | 2주일 | TBD | 낮은 우선순위 |

> **Note**: SLA는 권장 가이드라인이며, 실제 일정은 담당 Agent와 협의하여 결정합니다.

---

## 관련 Skills

- `gap-analysis`: EXTERNAL dependency 식별 (선행)
- `phase-tracking`: Blocked 항목 관리
- `agent-cross-review`: 크로스 Agent 리뷰/알림
- `completion-gate`: 의존성 해결 시 재검증

---

## Agent Integration

### 워크플로우 예시

```
Feature 구현 시작
       ↓
gap-analysis 실행
       ↓
EXTERNAL dependency 발견
       ↓
[dependency-coordination skill]
       ↓
요청 생성 및 전송
       ↓
Phase 1 먼저 완료 (가능한 부분)
       ↓
의존성 해결 대기
       ↓
의존성 해결됨 알림
       ↓
나머지 구현 진행
       ↓
Feature 완료
```

---

## 사용 예시

```markdown
# 의존성 요청 생성
"Basecamp Server에 Quality API 요청 생성해줘"
→ DEP-2026-001 요청 생성

# 의존성 상태 확인
"현재 의존성 요청 상태 보여줘"
→ 활성 요청 목록 출력

# 의존성 해결 확인
"DEP-2026-001 상태 확인"
→ 해당 요청 상세 정보

# 전체 의존성 맵
"Quality 기능 의존성 맵 보여줘"
→ 모든 외부 의존성 및 상태 표시
```
