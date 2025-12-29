# Basecamp UI Development Patterns (Quick Reference)

> React 19+ with TypeScript, Vite, TanStack Router/Query, Zustand, ShadcnUI

## 1. Server State (TanStack Query)

```typescript
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

## 2. Client State (Zustand)

```typescript
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

## 3. Component Pattern

```typescript
export const PipelineList: React.FC = () => {
  const { data, isLoading, error } = usePipelines();

  if (isLoading) return <Skeleton className="h-32" />;
  if (error) return <ErrorMessage error={error} />;
  if (!data?.length) return <EmptyState message="No data" />;

  return <div>{data.map(p => <PipelineCard key={p.id} pipeline={p} />)}</div>;
};
```

## 4. Project Structure

```
src/
├── components/ui/     # ShadcnUI base
├── features/          # Feature components
├── routes/            # TanStack Router
├── stores/            # Zustand stores
├── hooks/             # Custom hooks
└── lib/               # Utilities
```

## 5. Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Components | PascalCase | `UserProfile` |
| Props | `*Props` | `UserProfileProps` |
| Hooks | `use*` | `useAuth` |
| Stores | `use*Store` | `useAuthStore` |
| Files | kebab-case | `user-profile.tsx` |

## 6. Essential Commands

```bash
pnpm install           # Install dependencies
pnpm run dev           # Dev server (port 3000)
pnpm run type-check    # TypeScript check
pnpm run build         # Production build
```

## 7. Anti-Patterns

- ❌ Using `any` type
- ❌ Global state for local component state
- ❌ Missing loading/error/empty states
- ❌ Direct API calls in components (use hooks)
