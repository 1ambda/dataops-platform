# GitHub Repository 연동 시스템 기능 명세

## 📋 개요

데이터플랫폼에서 팀별 GitHub Repository, Branch, PR 관리를 위한 시스템 구현

### 핵심 요구사항

- 팀마다 1개의 GitHub Repository 할당 (1:1 관계)
- 한 사용자는 여러 팀에 소속 가능
- GitHub Repository 등록/조회 API 제공
- **Branch 목록 및 상세 정보 조회**
- **Pull Request 목록 및 상세 정보 조회**
- 현재 수준에서 권한/보안 제외

### 워크플로우

```
DA/DAE/DS/DE → dli CLI → GitHub Feature 브랜치
                              ↓
                         PR 생성 (feature → develop)
                              ↓
                         코드 리뷰 & 병합
                              ↓
                         S3 Push → Airflow DAG 생성
```

**목표**: Team 기반 Repository 관리 + Branch/PR 추적으로 워크플로우 가시성 확보

### 관련 문서

> **Airflow 연동 관련 내용은 [`AIRFLOW_FEATURE.md`](./AIRFLOW_FEATURE.md)를 참조하세요.**
> - `AirflowClusterEntity`, Repository, DDL
> - Airflow API 연동 (Airflow 3 기준)
> - S3 Spec Sync 서비스

---

## 🏗️ 시스템 아키텍처

### Data Source 정의

| 데이터 소스 | 타입 | 설명 |
|-------------|------|------|
| **GitHub Repository** | Self-managed JPA | 팀별 Repository 정보 (URL, 브랜치, S3 경로) |
| **GitHub API** | External API (Mock) | Branch/PR 정보 조회 |

### 아키텍처 패턴

- **Pure Hexagonal Architecture** 적용
- **Repository Pattern**: JPA + DSL 분리 (기존 PATTERNS.md 따름)
- **External Client Pattern**: GitHubClient 인터페이스 + Mock 구현
- **Service Layer**: 구체 클래스, Domain 인터페이스 주입
- **API Layer**: REST 기반 리소스 중심 설계

---

## 📊 Domain Model

### GitHubRepositoryEntity (Self-Managed)

```kotlin
@Entity
@Table(name = "github_repositories")
class GitHubRepositoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 255)
    val team: String,

    @Column(nullable = false, length = 255)
    val owner: String,  // GitHub owner (org or user)

    @Column(nullable = false, length = 255)
    val repoName: String,  // Repository name

    @Column(nullable = false, length = 500)
    val repositoryUrl: String,

    @Column(nullable = false, length = 100)
    val defaultBranch: String = "main",

    @Column(nullable = false, length = 100)
    val developBranch: String = "develop",

    @Column(nullable = false, length = 500)
    val s3DevPath: String,

    @Column(nullable = false, length = 500)
    val s3ProdPath: String,

    @Column(nullable = false)
    val isActive: Boolean = true,

    @Column(length = 1000)
    val description: String? = null
) : BaseEntity()
```

### Branch/PR Domain Models (External API - No Entity)

```kotlin
// Branch 정보 (GitHub API로부터 조회)
data class GitHubBranch(
    val name: String,
    val sha: String,
    val isProtected: Boolean,
    val lastCommitDate: LocalDateTime?,
    val lastCommitAuthor: String?,
    val lastCommitMessage: String?
)

// Pull Request 정보 (GitHub API로부터 조회)
data class GitHubPullRequest(
    val number: Long,
    val title: String,
    val state: PullRequestState,  // OPEN, CLOSED, MERGED
    val sourceBranch: String,
    val targetBranch: String,
    val author: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val mergedAt: LocalDateTime?,
    val mergedBy: String?,
    val reviewers: List<String>,
    val labels: List<String>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val url: String
)

enum class PullRequestState {
    OPEN, CLOSED, MERGED
}

// PR 필터 조건
data class PullRequestFilter(
    val state: PullRequestState? = null,  // null = all
    val author: String? = null,
    val targetBranch: String? = null,
    val limit: Int = 30
)
```

### 관계 설계

- **Team → GitHub Repository**: 1:1 (Foreign Key: team)
- **Repository → Branches**: 1:N (External API, 저장하지 않음)
- **Repository → PRs**: 1:N (External API, 저장하지 않음)
- WorkflowEntity와 동일한 `team: String` 패턴 사용으로 일관성 확보

---

## 🔧 Repository Layer

### Domain Interfaces (module-core-domain)

```kotlin
interface GitHubRepositoryJpa {
    fun save(repository: GitHubRepositoryEntity): GitHubRepositoryEntity
    fun findById(id: Long): GitHubRepositoryEntity?
    fun findByTeam(team: String): GitHubRepositoryEntity?
    fun findAllActive(): List<GitHubRepositoryEntity>
    fun deleteById(id: Long)
    fun existsByTeam(team: String): Boolean
    fun existsByRepositoryUrl(url: String): Boolean
}

interface GitHubRepositoryDsl {
    fun findByRepositoryUrl(url: String): GitHubRepositoryEntity?
    fun findByOwnerAndRepoName(owner: String, repoName: String): GitHubRepositoryEntity?
    fun searchByKeyword(keyword: String): List<GitHubRepositoryEntity>
}
```

---

## 🔗 External Client Layer (module-core-domain)

### GitHubClient Interface (Port)

```kotlin
// Domain Port - External GitHub API 접근
interface GitHubClient {
    // Repository 검증
    fun validateRepository(owner: String, repoName: String): Boolean
    fun getRepositoryInfo(owner: String, repoName: String): GitHubRepositoryInfo?

    // Branch 조회
    fun listBranches(owner: String, repoName: String): List<GitHubBranch>
    fun getBranch(owner: String, repoName: String, branchName: String): GitHubBranch?
    fun compareBranches(
        owner: String,
        repoName: String,
        baseBranch: String,
        headBranch: String
    ): BranchComparison?

    // Pull Request 조회
    fun listPullRequests(
        owner: String,
        repoName: String,
        filter: PullRequestFilter = PullRequestFilter()
    ): List<GitHubPullRequest>
    fun getPullRequest(owner: String, repoName: String, prNumber: Long): GitHubPullRequest?

    // Connection 확인
    fun isAvailable(): Boolean
}

data class GitHubRepositoryInfo(
    val fullName: String,
    val description: String?,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val language: String?,
    val starCount: Int,
    val forkCount: Int
)

data class BranchComparison(
    val aheadBy: Int,
    val behindBy: Int,
    val status: ComparisonStatus,  // AHEAD, BEHIND, DIVERGED, IDENTICAL
    val commits: List<CommitSummary>
)

enum class ComparisonStatus {
    AHEAD, BEHIND, DIVERGED, IDENTICAL
}

data class CommitSummary(
    val sha: String,
    val message: String,
    val author: String,
    val date: LocalDateTime
)
```

### Mock Implementation (module-core-infra)

```kotlin
@Service
@Profile("!github-api")  // 실제 GitHub API 연동 시 비활성화
class MockGitHubClient : GitHubClient {

    private val mockBranches = mapOf(
        "main" to GitHubBranch(
            name = "main",
            sha = "abc123def456",
            isProtected = true,
            lastCommitDate = LocalDateTime.now().minusDays(1),
            lastCommitAuthor = "developer@example.com",
            lastCommitMessage = "Merge pull request #42"
        ),
        "develop" to GitHubBranch(
            name = "develop",
            sha = "def789ghi012",
            isProtected = true,
            lastCommitDate = LocalDateTime.now().minusHours(6),
            lastCommitAuthor = "analyst@example.com",
            lastCommitMessage = "feat: add user metrics dataset"
        ),
        "feature/user-metrics" to GitHubBranch(
            name = "feature/user-metrics",
            sha = "ghi345jkl678",
            isProtected = false,
            lastCommitDate = LocalDateTime.now().minusHours(2),
            lastCommitAuthor = "analyst@example.com",
            lastCommitMessage = "WIP: user metrics implementation"
        )
    )

    private val mockPullRequests = listOf(
        GitHubPullRequest(
            number = 42,
            title = "feat: Add user activity metrics",
            state = PullRequestState.OPEN,
            sourceBranch = "feature/user-metrics",
            targetBranch = "develop",
            author = "analyst@example.com",
            createdAt = LocalDateTime.now().minusDays(2),
            updatedAt = LocalDateTime.now().minusHours(1),
            mergedAt = null,
            mergedBy = null,
            reviewers = listOf("lead@example.com", "senior@example.com"),
            labels = listOf("enhancement", "metrics"),
            additions = 150,
            deletions = 20,
            changedFiles = 5,
            url = "https://github.com/example/data-specs/pull/42"
        ),
        GitHubPullRequest(
            number = 41,
            title = "fix: Correct sales aggregation logic",
            state = PullRequestState.MERGED,
            sourceBranch = "fix/sales-agg",
            targetBranch = "develop",
            author = "developer@example.com",
            createdAt = LocalDateTime.now().minusDays(5),
            updatedAt = LocalDateTime.now().minusDays(3),
            mergedAt = LocalDateTime.now().minusDays(3),
            mergedBy = "lead@example.com",
            reviewers = listOf("lead@example.com"),
            labels = listOf("bugfix"),
            additions = 25,
            deletions = 10,
            changedFiles = 2,
            url = "https://github.com/example/data-specs/pull/41"
        )
    )

    override fun validateRepository(owner: String, repoName: String): Boolean = true

    override fun getRepositoryInfo(owner: String, repoName: String): GitHubRepositoryInfo =
        GitHubRepositoryInfo(
            fullName = "$owner/$repoName",
            description = "Data platform specifications repository",
            defaultBranch = "main",
            isPrivate = true,
            language = "SQL",
            starCount = 0,
            forkCount = 0
        )

    override fun listBranches(owner: String, repoName: String): List<GitHubBranch> =
        mockBranches.values.toList()

    override fun getBranch(owner: String, repoName: String, branchName: String): GitHubBranch? =
        mockBranches[branchName]

    override fun compareBranches(
        owner: String,
        repoName: String,
        baseBranch: String,
        headBranch: String
    ): BranchComparison = BranchComparison(
        aheadBy = 3,
        behindBy = 0,
        status = ComparisonStatus.AHEAD,
        commits = listOf(
            CommitSummary(
                sha = "abc123",
                message = "feat: add metrics",
                author = "analyst@example.com",
                date = LocalDateTime.now().minusHours(2)
            )
        )
    )

    override fun listPullRequests(
        owner: String,
        repoName: String,
        filter: PullRequestFilter
    ): List<GitHubPullRequest> {
        var result = mockPullRequests
        filter.state?.let { state -> result = result.filter { it.state == state } }
        filter.author?.let { author -> result = result.filter { it.author == author } }
        filter.targetBranch?.let { branch -> result = result.filter { it.targetBranch == branch } }
        return result.take(filter.limit)
    }

    override fun getPullRequest(owner: String, repoName: String, prNumber: Long): GitHubPullRequest? =
        mockPullRequests.find { it.number == prNumber }

    override fun isAvailable(): Boolean = true
}
```

---

## ⚙️ Service Layer

### GitHubService

```kotlin
@Service
@Transactional(readOnly = true)
class GitHubService(
    private val gitHubRepositoryJpa: GitHubRepositoryJpa,
    private val gitHubRepositoryDsl: GitHubRepositoryDsl,
    private val gitHubClient: GitHubClient
) {
    // === Repository CRUD ===

    @Transactional
    fun registerRepository(command: RegisterGitHubRepositoryCommand): GitHubRepositoryDto

    fun getRepository(id: Long): GitHubRepositoryDto?

    fun getRepositoryByTeam(team: String): GitHubRepositoryDto?

    fun getAllRepositories(): List<GitHubRepositoryDto>

    @Transactional
    fun updateRepository(id: Long, command: UpdateGitHubRepositoryCommand): GitHubRepositoryDto

    @Transactional
    fun deleteRepository(id: Long)

    // === Branch Operations (via GitHubClient) ===

    fun listBranches(repositoryId: Long): List<GitHubBranchDto>

    fun getBranch(repositoryId: Long, branchName: String): GitHubBranchDto?

    fun compareBranches(
        repositoryId: Long,
        baseBranch: String,
        headBranch: String
    ): BranchComparisonDto?

    // === Pull Request Operations (via GitHubClient) ===

    fun listPullRequests(
        repositoryId: Long,
        state: PullRequestState? = null,
        targetBranch: String? = null,
        limit: Int = 30
    ): List<GitHubPullRequestDto>

    fun getPullRequest(repositoryId: Long, prNumber: Long): GitHubPullRequestDto?
}
```

---

## 🌐 API Layer

### REST Endpoints

#### Repository Management (6 endpoints)

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| POST | `/api/v1/github/repositories` | GitHub Repository 등록 |
| GET | `/api/v1/github/repositories` | 모든 Repository 목록 조회 |
| GET | `/api/v1/github/repositories/{id}` | Repository 상세 조회 |
| GET | `/api/v1/github/repositories?team={team}` | 팀별 Repository 조회 |
| PUT | `/api/v1/github/repositories/{id}` | Repository 정보 수정 |
| DELETE | `/api/v1/github/repositories/{id}` | Repository 삭제 |

#### Branch Operations (3 endpoints)

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| GET | `/api/v1/github/repositories/{id}/branches` | Branch 목록 조회 |
| GET | `/api/v1/github/repositories/{id}/branches/{branchName}` | Branch 상세 조회 |
| GET | `/api/v1/github/repositories/{id}/branches/compare?base={base}&head={head}` | Branch 비교 |

#### Pull Request Operations (2 endpoints)

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| GET | `/api/v1/github/repositories/{id}/pulls` | PR 목록 조회 |
| GET | `/api/v1/github/repositories/{id}/pulls/{prNumber}` | PR 상세 조회 |

**Total: 11 endpoints**

### Request/Response DTOs

```kotlin
// === Repository DTOs ===

data class RegisterGitHubRepositoryCommand(
    val team: String,
    val owner: String,
    val repoName: String,
    val repositoryUrl: String,
    val defaultBranch: String = "main",
    val developBranch: String = "develop",
    val s3DevPath: String,
    val s3ProdPath: String,
    val description: String? = null
)

data class UpdateGitHubRepositoryCommand(
    val defaultBranch: String? = null,
    val developBranch: String? = null,
    val s3DevPath: String? = null,
    val s3ProdPath: String? = null,
    val description: String? = null,
    val isActive: Boolean? = null
)

data class GitHubRepositoryDto(
    val id: Long,
    val team: String,
    val owner: String,
    val repoName: String,
    val repositoryUrl: String,
    val defaultBranch: String,
    val developBranch: String,
    val s3DevPath: String,
    val s3ProdPath: String,
    val isActive: Boolean,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

// === Branch DTOs ===

data class GitHubBranchDto(
    val name: String,
    val sha: String,
    val isProtected: Boolean,
    val lastCommitDate: LocalDateTime?,
    val lastCommitAuthor: String?,
    val lastCommitMessage: String?
)

data class BranchComparisonDto(
    val baseBranch: String,
    val headBranch: String,
    val aheadBy: Int,
    val behindBy: Int,
    val status: String,  // AHEAD, BEHIND, DIVERGED, IDENTICAL
    val commits: List<CommitSummaryDto>
)

data class CommitSummaryDto(
    val sha: String,
    val message: String,
    val author: String,
    val date: LocalDateTime
)

// === Pull Request DTOs ===

data class GitHubPullRequestDto(
    val number: Long,
    val title: String,
    val state: String,  // OPEN, CLOSED, MERGED
    val sourceBranch: String,
    val targetBranch: String,
    val author: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val mergedAt: LocalDateTime?,
    val mergedBy: String?,
    val reviewers: List<String>,
    val labels: List<String>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val url: String
)

// === List Response with Metadata ===

data class GitHubPullRequestListResponse(
    val data: List<GitHubPullRequestDto>,
    val metadata: ListMetadata
)

data class ListMetadata(
    val total: Int,
    val limit: Int,
    val hasMore: Boolean
)
```

---

## 🗄️ Database Schema

### DDL Scripts

```sql
-- GitHub Repositories Table
CREATE TABLE github_repositories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team VARCHAR(255) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    default_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    develop_branch VARCHAR(100) NOT NULL DEFAULT 'develop',
    s3_dev_path VARCHAR(500) NOT NULL,
    s3_prod_path VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,

    UNIQUE KEY uk_github_repo_team (team),
    UNIQUE KEY uk_github_repo_url (repository_url),
    UNIQUE KEY uk_github_repo_owner_name (owner, repo_name),
    INDEX idx_github_repo_active (is_active),
    INDEX idx_github_repo_owner (owner)
);

-- 샘플 데이터
INSERT INTO github_repositories (team, owner, repo_name, repository_url, default_branch, develop_branch, s3_dev_path, s3_prod_path, description) VALUES
('analytics', 'dataops-org', 'analytics-specs', 'https://github.com/dataops-org/analytics-specs', 'main', 'develop', 's3://bucket/dev/analytics', 's3://bucket/prod/analytics', 'Analytics team data specifications'),
('marketing', 'dataops-org', 'marketing-specs', 'https://github.com/dataops-org/marketing-specs', 'main', 'develop', 's3://bucket/dev/marketing', 's3://bucket/prod/marketing', 'Marketing team data specifications');
```

---

## ✅ 구현 완료

> **Status:** ✅ 전체 구현 완료 (2026-01-04)
> **Tests:** 107개 (단위 70개 + 통합 37개)
> **Endpoints:** 11개 전체 구현

### Phase 1: Core Repository CRUD ✅
- [x] **Entity 생성**: `GitHubRepositoryEntity` (owner, repoName 필드 포함)
- [x] **Repository 인터페이스**: `GitHubRepositoryJpa` + `GitHubRepositoryDsl`
- [x] **Repository 구현체**: `GitHubRepositoryJpaImpl` + `GitHubRepositoryDslImpl`
- [x] **Service 구현**: `GitHubService` (Repository CRUD)
- [x] **Controller 구현**: 6개 Repository 엔드포인트
- [x] **DTO 구현**: Request/Response 모델

### Phase 2: External Client (Branch/PR) ✅
- [x] **Domain Port**: `GitHubClient` 인터페이스 정의
- [x] **Mock 구현**: `MockGitHubClient`
- [x] **Domain Models**: `GitHubBranch`, `GitHubPullRequest`, `BranchComparison`, etc.
- [x] **Service 확장**: Branch/PR 조회 메서드 추가
- [x] **Controller 확장**: 5개 Branch/PR 엔드포인트

### Phase 3: Testing ✅
- [x] **단위 테스트**: `GitHubServiceTest` (47개), `GitHubRepositoryEntityTest` (23개)
- [x] **통합 테스트**: `GitHubControllerTest` (37개)
- [x] **Mock 검증**: MockGitHubClient 동작 확인

### Phase 4: Documentation ✅
- [x] **API 문서**: Swagger/OpenAPI 어노테이션 완료
- [x] **GITHUB_FEATURE.md 완료**: 구현 완료 표시

---

## 🎯 핵심 결정 사항

### 아키텍처 결정

| 결정 사항 | 선택 | 근거 |
|-----------|------|------|
| **Entity 관계** | 1:1 (Team ↔ Repository) | 요구사항 명확, 데이터 무결성 보장 |
| **Branch/PR 저장** | 저장하지 않음 (API 조회) | 실시간 정보 필요, 동기화 복잡도 회피 |
| **외부 API 패턴** | Port-Adapter (GitHubClient) | Hexagonal Architecture 준수 |
| **Mock 전략** | @Profile 기반 전환 | 테스트/개발 유연성 |
| **API 구조** | 리소스 기반 중첩 | RESTful 설계 원칙 |

### 기술 결정

| 영역 | 기술 | 근거 |
|------|------|------|
| **GitHub API Client** | Mock (현재) → REST (향후) | 개발 속도 우선 |
| **인증** | 없음 (현재) | 요구사항 범위 |
| **캐싱** | 없음 (현재) | Branch/PR 실시간성 |

---

**문서 버전**: v3.1 (구현 완료)
**작성일**: 2026-01-04
**구현 완료일**: 2026-01-04
**검토**: ✅ 구현 완료 및 테스트 통과

### v3.1 구현 완료 (2026-01-04)

| 구현 항목 | 상태 | 테스트 |
|-----------|------|--------|
| **Repository CRUD** | ✅ 6개 엔드포인트 | 단위 47개 + 통합 12개 |
| **Branch API** | ✅ 3개 엔드포인트 | 단위 12개 + 통합 11개 |
| **PR API** | ✅ 2개 엔드포인트 | 단위 11개 + 통합 14개 |
| **Total** | **11개 엔드포인트** | **107개 테스트** |

### v3.0 주요 변경사항

| 변경 영역 | Before | After | 근거 |
|-----------|--------|-------|------|
| **Branch API** | 없음 | 3개 엔드포인트 추가 | 워크플로우 가시성 |
| **PR API** | 없음 | 2개 엔드포인트 추가 | 코드 리뷰 추적 |
| **GitHubClient** | 단순 Mock | Port-Adapter 패턴 | 확장성, 테스트 용이성 |
| **Entity 필드** | repositoryUrl만 | owner + repoName 분리 | API 호출 용이성 |
| **총 Endpoints** | 6개 | 11개 | Branch/PR 기능 추가 |
