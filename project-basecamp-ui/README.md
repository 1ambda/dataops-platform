# project-basecamp-ui

**Modern Web Dashboard - React 19 + TypeScript DataOps Platform Interface**

## Overview

`project-basecamp-ui` is the frontend web dashboard for the DataOps Platform, providing a modern, responsive interface for managing pipelines, monitoring jobs, and administering the system.

**Technology:** React 19.2.3 + TypeScript + Vite 7.3.0 + TanStack Router

**Port:** 3000 (development), nginx in production

---

## Quick Start

### Prerequisites

- Node.js 20+ (recommended: Node.js 20 LTS)
- pnpm (recommended) or npm

### Local Development

```bash
# Install dependencies
pnpm install
# OR
npm install

# Run development server (port 3000)
pnpm run dev
# OR
npm run dev

# Open in browser
open http://localhost:3000
```

### Production Build

```bash
# Build for production
pnpm run build
# OR
npm run build

# Preview production build
pnpm run preview
# OR
npm run preview
```

---

## Key Features

### Dashboard & Analytics
- **Analytics Dashboard**: System metrics and performance indicators
- **Sales Dashboard**: Business metrics and charts
- **Real-time Monitoring**: Pipeline and job status tracking

### User Management
- **User CRUD**: Create, read, update, delete users
- **Role Management**: Role-based access control
- **Profile Management**: Personal settings and preferences

### Task Management
- **Task Dashboard**: Track all task execution status
- **Task CRUD**: Create, update, delete tasks
- **Progress Monitoring**: Task completion tracking

### Settings
- **Profile Settings**: Personal information and preferences
- **Account Settings**: Security and account management
- **Notification Settings**: Alert preferences and subscriptions
- **Appearance Settings**: Theme, font, and layout customization

### Authentication & Security
- **Clerk Integration**: Modern authentication service
- **OTP Support**: Two-factor authentication
- **Session Management**: Secure session handling

### Internationalization & Accessibility
- **Dark/Light Mode**: User-preferred theme switching
- **RTL Support**: Right-to-Left language support
- **Responsive Design**: Mobile to desktop support
- **Accessibility**: WCAG guidelines compliance

---

## Technology Stack

### Core Framework
- **React** 19.2.3 - Modern React framework
- **TypeScript** 5.9.3 - Type safety
- **Vite** 7.1.9 - Fast build tool
- **TanStack Router** 1.132.47 - Type-safe routing

### UI & Styling
- **ShadcnUI** - Modern UI component library (Radix UI + TailwindCSS)
- **TailwindCSS** 4.1.14 - Utility-first CSS framework
- **Lucide React** 0.545.0 - Icon library

### State Management & Data
- **Zustand** 5.0.8 - Lightweight state management
- **TanStack React Query** 5.90.2 - Server state management
- **React Hook Form** 7.64.0 - Form state management
- **Zod** 4.1.12 - Schema validation

### HTTP & API
- **Axios** 1.12.2 - HTTP client
- **Ky** 1.12.0 - Modern HTTP client

### Charts & Visualization
- **Recharts** 3.2.1 - React charting library

### Authentication & Notifications
- **Clerk** - Authentication service (partial implementation)
- **Sonner** 2.0.7 - Toast notifications

### User Experience
- **react-top-loading-bar** 3.0.2 - Page loading indicator

### Development Tools
- **ESLint** 9.37.0 - Code linting
- **Prettier** 3.6.2 - Code formatting

---

## Project Structure

```
project-basecamp-ui/
├── public/                      # Static assets
│   └── images/                  # Static images
│
├── src/
│   ├── main.tsx                 # React application entry point
│   │
│   ├── components/              # Reusable components
│   │   ├── ui/                  # ShadcnUI base components
│   │   │   ├── button.tsx
│   │   │   ├── input.tsx
│   │   │   ├── dialog.tsx
│   │   │   ├── table.tsx
│   │   │   └── ...
│   │   ├── layout/              # Layout components
│   │   │   ├── top-nav.tsx
│   │   │   └── sidebar.tsx
│   │   └── ...
│   │
│   ├── features/                # Feature-specific components
│   │   ├── dashboard/           # Dashboard features
│   │   ├── settings/            # Settings pages
│   │   ├── tasks/               # Task management
│   │   ├── users/               # User management
│   │   ├── auth/                # Authentication
│   │   └── errors/              # Error pages
│   │
│   ├── context/                 # React Context providers
│   │   ├── auth-provider.tsx
│   │   ├── theme-provider.tsx
│   │   └── ...
│   │
│   ├── routes/                  # TanStack Router routes
│   │   ├── __root.tsx
│   │   ├── index.tsx
│   │   ├── dashboard/
│   │   ├── settings/
│   │   └── ...
│   │
│   ├── stores/                  # Zustand stores
│   │   └── auth-store.ts
│   │
│   ├── hooks/                   # Custom React hooks
│   │   ├── use-auth.ts
│   │   ├── use-theme.ts
│   │   └── ...
│   │
│   ├── lib/                     # Utility libraries
│   │   ├── utils.ts
│   │   ├── api.ts
│   │   └── ...
│   │
│   ├── _types/                  # TypeScript type definitions
│   │   ├── api.ts
│   │   ├── auth.ts
│   │   └── common.ts
│   │
│   ├── config/                  # Application configuration
│   │   └── env.ts
│   │
│   ├── styles/                  # Style sheets
│   │   └── globals.css
│   │
│   └── routeTree.gen.ts         # Auto-generated TanStack Router file
│
├── vite.config.ts               # Vite build configuration
├── tsconfig.json                # TypeScript configuration
├── tailwind.config.js           # TailwindCSS configuration
├── components.json              # ShadcnUI configuration
└── package.json                 # Project dependencies
```

---

## API Integration

### Development Proxy

The Vite dev server proxies API calls to the backend:

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8070',  // basecamp-server
        changeOrigin: true,
      }
    }
  }
})
```

> **Note**: Update target URL if your backend runs on a different port. The default configuration expects basecamp-server on port 8070, but it runs on 8080 in local mode and 8081 in Docker mode.

### API Client

```typescript
// src/lib/api.ts
import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// Pipeline API
export const pipelineApi = {
  getList: () => apiClient.get('/pipelines'),
  getById: (id: string) => apiClient.get(`/pipelines/${id}`),
  create: (data: Pipeline) => apiClient.post('/pipelines', data),
  update: (id: string, data: Partial<Pipeline>) =>
    apiClient.put(`/pipelines/${id}`, data),
  delete: (id: string) => apiClient.delete(`/pipelines/${id}`),
  execute: (id: string) => apiClient.post(`/pipelines/${id}/execute`),
};
```

---

## Configuration

### Environment Variables

Create `.env.local` for development:

```bash
# API configuration
VITE_API_BASE_URL=http://localhost:8070
VITE_APP_TITLE=DataOps Platform

# Authentication (Clerk)
VITE_CLERK_PUBLISHABLE_KEY=pk_test_xxx

# Feature flags
VITE_ENABLE_ANALYTICS=true
```

Create `.env.production` for production:

```bash
VITE_API_BASE_URL=https://api.dataops.example.com
VITE_APP_TITLE=DataOps Platform
VITE_CLERK_PUBLISHABLE_KEY=pk_live_xxx
```

---

## Development

### Available Scripts

```bash
# Development server
pnpm run dev

# Build for production
pnpm run build

# Preview production build
pnpm run preview

# Type checking
pnpm run type-check

# Linting
pnpm run lint

# Code formatting
pnpm run format
```

### Code Quality

```bash
# ESLint
pnpm run lint

# Fix ESLint issues
pnpm run lint --fix

# Format with Prettier
pnpm run format

# Type check
pnpm run type-check
```

---

## Key Pages & Features

### 1. Dashboard Pages
- **Analytics Dashboard**: System metrics, pipeline execution stats
- **Sales Dashboard**: Business KPIs, revenue charts
- **Real-time Monitoring**: Active jobs, system resources

### 2. Pipeline Management
- **Pipeline List**: View all pipelines
- **Pipeline Detail**: Job steps, configuration
- **Execution Control**: Start, stop, restart pipelines

### 3. Task Management
- **Task Dashboard**: All task execution status
- **Task Creation**: Define new tasks and scheduling
- **Log Viewer**: Task execution logs and error analysis

### 4. User Management
- **User List**: Manage all users
- **Permission Settings**: Role-based access control
- **Profile Management**: Personal information and settings

### 5. Settings Pages
- **System Settings**: Global system configuration
- **Notification Settings**: Email, Slack integration
- **Appearance Settings**: Theme, language, layout

---

## ShadcnUI Customization

The project uses ShadcnUI as the component foundation with RTL support and additional features.

### Modified Components
- **scroll-area**: Improved scrolling behavior
- **sonner**: Customized toast notifications
- **separator**: Enhanced styling

### RTL-Updated Components
- **alert-dialog**, **calendar**, **command**
- **dialog**, **dropdown-menu**, **select**
- **table**, **sheet**, **sidebar**, **switch**

---

## State Management

### Zustand Store Example

```typescript
// src/stores/auth-store.ts
import { create } from 'zustand';

interface AuthState {
  user: User | null;
  token: string | null;
  setUser: (user: User) => void;
  setToken: (token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: localStorage.getItem('token'),
  setUser: (user) => set({ user }),
  setToken: (token) => {
    localStorage.setItem('token', token);
    set({ token });
  },
  logout: () => {
    localStorage.removeItem('token');
    set({ user: null, token: null });
  },
}));
```

---

## Routing

### TanStack Router Example

```typescript
// src/routes/__root.tsx
export const Route = createRootRoute({
  component: RootLayout,
  notFoundComponent: NotFoundPage,
});

// src/routes/dashboard/index.tsx
export const Route = createFileRoute('/dashboard/')({
  component: DashboardPage,
  beforeLoad: ({ context }) => {
    if (!context.auth.isAuthenticated) {
      throw redirect({ to: '/auth/sign-in' });
    }
  },
});
```

---

## Docker Deployment

### Production Dockerfile

```dockerfile
FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Nginx Configuration

```nginx
server {
    listen 80;
    server_name dashboard.dataops.example.com;

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://basecamp-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## Troubleshooting

### API Connection Failures

```bash
# Verify proxy configuration in vite.config.ts
# Check basecamp-server is running
curl http://localhost:8070/api/health

# Update proxy target if needed
```

### Build Errors

```bash
# Clear cache and reinstall
rm -rf node_modules .vite
pnpm install

# Rebuild
pnpm run build
```

### TypeScript Errors

```bash
# Type checking
pnpm run type-check

# Regenerate route tree (auto-generated by Vite)
pnpm run dev
```

---

## Performance Optimization

### Code Splitting

```typescript
// Lazy load features
const LazyDashboard = lazy(() => import('./features/dashboard'));
```

### Image Optimization

```typescript
// Vite image optimization
import imageUrl from './image.png?format=webp&w=800';
```

### Bundle Analysis

```bash
# Analyze bundle size
pnpm run build --analyze
```

---

## Project-Specific Resources

- **[Main Platform README](../README.md)** - Platform overview and quick start
- **[Architecture Documentation](../docs/architecture.md)** - System architecture
- **[Development Guide](../docs/development.md)** - Development workflows
- **[Troubleshooting](../docs/troubleshooting.md)** - Common issues

---

## Contributing

Follow the guidelines in the main [Development Guide](../docs/development.md).

**Code Style:**
- Follow ESLint + Prettier rules
- Use TypeScript strict mode
- Prefer functional components
- Use hooks for state management

---

**For platform-wide documentation, see the main repository README and docs/ directory.**
