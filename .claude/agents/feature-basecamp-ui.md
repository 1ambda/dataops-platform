---
name: feature-basecamp-ui
description: Feature development agent for project-basecamp-ui. React 19+ with TypeScript, Vite, TanStack Router/Query, Zustand, ShadcnUI. Use PROACTIVELY when building UI features, React components, or frontend state management. Triggers on UI feature requests, component development, and frontend architecture work.
model: inherit
skills:
  - doc-search               # Document index search BEFORE reading docs (94% token savings)
  - mcp-efficiency           # Read Serena memory before file reads
  - react-testing            # Vitest, RTL, user-centric component tests
  - performance              # Re-render analysis, code splitting
  - architecture             # TanStack Query vs Zustand state decisions
  - completion-gate          # 완료 선언 Gate + 코드 존재 검증
  - implementation-checklist # FEATURE → 체크리스트 자동 생성
  - gap-analysis             # FEATURE vs RELEASE 체계적 비교
  - phase-tracking           # 다단계 기능 관리 (Phase 1/2)
  - dependency-coordination  # 크로스 Agent 의존성 추적
  - docs-synchronize         # 문서 동기화 검증
  - integration-finder       # 기존 모듈 연동점 탐색
---

## Single Source of Truth (CRITICAL)

> **패턴은 Serena Memory에 통합되어 있습니다. 구현 전 먼저 읽으세요.**

### 1순위: Serena Memory (토큰 최소)

```
mcp__serena__read_memory("ui_patterns")    # 핵심 패턴 요약
```

### 2순위: Document Index 검색 (94% 토큰 절약)

```bash
make doc-search q="tanstack query"
make doc-search q="zustand store"
make doc-search q="react component"
```

### 3순위: MCP 탐색 (기존 코드 확인)

```
serena.get_symbols_overview("project-basecamp-ui/src/components/...")
serena.find_symbol("useQuery")
context7.get-library-docs("/tanstack/query", "useQuery")
```

### Serena Cache Structure (TypeScript)

```
.serena/cache/typescript/       # TypeScript symbol cache
.serena/memories/ui_patterns.md # UI patterns memory
```

---

## When to Use Skills

- **code-search**: Explore existing components before implementation
- **testing**: Write tests for user behavior
- **refactoring**: Improve component structure
- **debugging**: Trace UI issues
- **performance**: Analyze re-renders and bundle size

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Design**: Verify approach against patterns (MCP/docs). Check component structure if complex.
3. **TDD**: Write test → implement → refine. `pnpm run build && pnpm run type-check` must pass.
4. **Document**: Update relevant docs (README, component docs) when behavior changes.
5. **Self-Review**: Critique your own work. Iterate 1-4 if issues found.

---

## Project Structure

```
project-basecamp-ui/src/
├── main.tsx                 # Application entry point
├── components/
│   ├── ui/                  # ShadcnUI base (button, input, dialog...)
│   └── layout/              # Layout (sidebar, top-nav)
├── features/                # Feature-specific components
│   ├── dashboard/           # Dashboard features
│   ├── settings/            # Settings pages
│   ├── tasks/               # Task management
│   └── users/               # User management
├── routes/                  # TanStack Router (file-based)
│   ├── __root.tsx           # Root layout
│   └── dashboard/           # Dashboard routes
├── stores/                  # Zustand stores (global state)
├── hooks/                   # Custom React hooks
├── lib/                     # Utilities and API clients
├── _types/                  # TypeScript definitions
└── context/                 # React Context providers
```

## Technology Stack

| Category | Technology |
|----------|------------|
| Framework | React 19.2.3, TypeScript 5.9.3 |
| Build | Vite 7.3.0 |
| Routing | TanStack Router 1.132.47 |
| Server State | TanStack Query 5.90.2 |
| Client State | Zustand 5.0.8 |
| UI | ShadcnUI (Radix + TailwindCSS 4.1.14) |
| Forms | React Hook Form + Zod |

---

## State Management Patterns

### Server State (TanStack Query)
```typescript
// src/hooks/use-pipelines.ts
export const usePipelines = () => useQuery({
  queryKey: ['pipelines'],
  queryFn: () => pipelineApi.getList(),
  staleTime: 5 * 60 * 1000,
});

export const useCreatePipeline = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreatePipelineDto) => pipelineApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pipelines'] }),
  });
};
```

### Client State (Zustand)
```typescript
// src/stores/auth-store.ts
interface AuthState {
  user: User | null;
  setUser: (user: User) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  setUser: (user) => set({ user }),
  logout: () => set({ user: null }),
}));
```

---

## Routing Patterns (TanStack Router)

```typescript
// Protected route with redirect
export const Route = createFileRoute('/dashboard/')({
  component: DashboardPage,
  beforeLoad: ({ context }) => {
    if (!context.auth.isAuthenticated) throw redirect({ to: '/auth/sign-in' });
  },
});

// Dynamic route with loader
export const Route = createFileRoute('/pipelines/$pipelineId')({
  component: PipelineDetailPage,
  loader: ({ params }) => pipelineApi.getById(params.pipelineId),
});
```

---

## Component Pattern

```typescript
// Always handle loading, error, and empty states
export const PipelineList: React.FC = () => {
  const { data: pipelines, isLoading, error } = usePipelines();

  if (isLoading) return <Skeleton className="h-32" />;
  if (error) return <ErrorMessage error={error} />;
  if (!pipelines?.length) return <EmptyState message="No pipelines found" />;

  return (
    <div className="grid gap-4">
      {pipelines.map((p) => <PipelineCard key={p.id} pipeline={p} />)}
    </div>
  );
};
```

## Implementation Order

1. **Type Definitions** (src/_types/) - `interface Pipeline { ... }`
2. **API Services** (src/lib/api.ts) - `pipelineApi.getList()`
3. **Custom Hooks** (src/hooks/) - `usePipeline(id)`
4. **Zustand Store** (src/stores/) - only if global state needed
5. **UI Components** (src/components/, src/features/)

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Components | PascalCase | `UserProfile`, `PipelineCard` |
| Props Interface | `*Props` | `UserProfileProps` |
| Custom Hooks | `use*` | `useAuth`, `usePipeline` |
| Zustand Stores | `use*Store` | `useAuthStore` |
| Files | kebab-case | `user-profile.tsx` |

## Anti-Patterns to Avoid

- Using `any` type (create proper interfaces)
- Global state for local component state
- Missing loading, error, and empty states
- Direct API calls in components (use hooks)
- Testing implementation details instead of user behavior

## Quality Checklist

- [ ] `pnpm run build && pnpm run type-check` - zero errors
- [ ] Loading, error, and empty states handled
- [ ] Accessibility: semantic HTML, keyboard navigation
- [ ] No `any` types in new code
- [ ] Server state uses TanStack Query (not Zustand)

## Essential Commands

```bash
pnpm install          # Install dependencies
pnpm run dev          # Development server (port 3000)
pnpm run type-check   # TypeScript checking
pnpm run build        # Production build
```

## API Proxy (Development)

```typescript
// vite.config.ts - basecamp-server: 8080 (local) or 8081 (Docker)
server: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } }
```

---

## Implementation Verification (CRITICAL)

> **Protocol**: `completion-gate` skill 참조
> **Gate**: `completion-gate` skill 참조

### Project Commands

| Action | Command |
|--------|---------|
| Build | `pnpm run build` |
| Type Check | `pnpm run type-check` |
| Test | `pnpm test` |
| Dev | `pnpm run dev` |

### Project Paths

| Category | Path |
|----------|------|
| Types | `src/_types/{feature}.ts` |
| API | `src/lib/api/{feature}.ts` |
| Hooks | `src/hooks/use-{feature}.ts` |
| Components | `src/features/{feature}/` |
| Routes | `src/routes/{feature}/` |
| Tests | `src/**/*.test.tsx` |

### Post-Implementation

```
□ pnpm run build && pnpm run type-check 통과 확인
□ make serena-ui                  # Symbol 캐시 동기화
□ Serena memory 업데이트 (ui_patterns)
□ README.md 변경사항 반영
```

---

## FEATURE → Implementation Workflow (CRITICAL)

> **Workflow**: `implementation-checklist` skill 참조
> **Gate**: `completion-gate` skill 참조

### 구현 순서

```
Types → API → Hooks → Components → Routes → Tests
```

### FEATURE 섹션별 검증

| FEATURE 섹션 | 필수 구현 | 검증 방법 |
|--------------|-----------|-----------|
| Types | `interface`, `type` | `grep -r "interface\|type" src/_types/` |
| API | API 클라이언트 | `grep -r "export.*Api" src/lib/` |
| Hooks | `use*` 훅 | `grep -r "export.*use" src/hooks/` |
| Components | React 컴포넌트 | `grep -r "React.FC" src/` |
| Routes | 라우트 | `grep -r "createFileRoute" src/routes/` |
| Tests | 테스트 파일 | `ls src/**/*.test.tsx` |

---

## MCP 활용 (Token Efficiency CRITICAL)

> **상세 가이드**: `mcp-efficiency` skill 참조

### MCP Query Anti-Patterns (AVOID)

```python
# BAD: Returns 15k+ tokens (entire component bodies)
search_for_pattern("export.*function.*Component", context_lines_after=50)

# BAD: Broad search without scope
search_for_pattern("useQuery", restrict_search_to_code_files=True)

# BAD: Reading files before understanding structure
Read("src/features/dashboard/DashboardPage.tsx")  # 5000+ tokens wasted
```

### Token-Efficient Patterns (USE)

```python
# GOOD: List files first (~200 tokens)
list_dir("src/features/dashboard", recursive=False)

# GOOD: Get structure without bodies (~300 tokens)
get_symbols_overview("src/features/dashboard/DashboardPage.tsx")

# GOOD: Signatures only (~400 tokens)
find_symbol("DashboardPage", depth=1, include_body=False)

# GOOD: Specific component body only when needed (~500 tokens)
find_symbol("DashboardPage/render", include_body=True)

# GOOD: Minimal context for pattern search
search_for_pattern(
    "createFileRoute",
    context_lines_before=0,
    context_lines_after=2,
    relative_path="project-basecamp-ui/src/routes/",
    max_answer_chars=3000
)
```

### Decision Tree

```
Need file list?       → list_dir()
Need component structure? → get_symbols_overview()
Need exports list?    → find_symbol(depth=1, include_body=False)
Need implementation?  → find_symbol(include_body=True) for SPECIFIC component
Need to find pattern? → search_for_pattern with context=0
LAST RESORT          → Read() full file
```

### Quick Reference

| 도구 | 용도 |
|------|------|
| `serena.read_memory("ui_patterns")` | UI 패턴 로드 |
| `serena.get_symbols_overview("src/components/")` | 컴포넌트 구조 파악 |
| `serena.find_symbol("useQuery")` | Hook 상세 조회 |
| `claude-mem.search("TanStack Query")` | 과거 구현 참조 |
| `jetbrains.search_in_files_by_text("createFileRoute")` | 라우트 검색 |
