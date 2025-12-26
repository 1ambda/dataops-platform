# Development Guide

**Complete guide to development workflow, CI/CD, and release management for the DataOps Platform.**

---

## Table of Contents

- [Git Flow & Branch Strategy](#git-flow--branch-strategy)
- [Branch Naming Convention](#branch-naming-convention)
- [Commit Message Convention](#commit-message-convention)
- [Pull Request Guidelines](#pull-request-guidelines)
- [CI/CD Workflows](#cicd-workflows)
- [Local Development](#local-development)
- [Docker Images](#docker-images)
- [Security & Best Practices](#security--best-practices)
- [Troubleshooting](#troubleshooting)

---

## Git Flow & Branch Strategy

This project follows **Git Flow** branching model:

```
feature/* ──PR──► develop ──PR──► main
                     │              │
                     │              └── Production releases
                     └── Integration/Staging
```

### Branch Types

| Branch | Purpose | Merges To | Protected |
|--------|---------|-----------|-----------|
| `main` | Production releases | - | Yes |
| `develop` | Integration branch | `main` | Yes |
| `feature/*` | New features | `develop` | No |
| `bugfix/*` | Bug fixes | `develop` | No |
| `hotfix/*` | Production fixes | `main`, `develop` | No |

### Workflow

1. Create `feature/*` branch from `develop`
2. Develop and commit following conventions
3. Create PR to `develop` with `[DEV]` prefix
4. After testing in develop, create PR to `main` with `[RELEASE]` prefix

---

## Branch Naming Convention

### Feature Branches

**Format:** `feature/{system}.{TICKET}-{number}/{description}`

```bash
# Examples
feature/basecamp.TICKET-4500/improve-dockerfile
feature/interface.TICKET-1234/add-login-command
feature/platform.TICKET-9999/update-dependencies
```

### Allowed Values

| Variable | Allowed Values |
|----------|---------------|
| `{system}` | `basecamp`, `interface`, `platform` |
| `{TICKET}` | `TICKET` (Jira project prefix) |
| `{number}` | Numeric ticket ID |
| `{description}` | Alphanumeric with hyphens/underscores |

### Validation Rules

1. Must be under `feature/` directory
2. System name followed by dot (`.`)
3. Must include `TICKET-XXXX` reference
4. Ticket followed by slash (`/`)
5. Description using alphanumeric, underscore, or hyphen

---

## Commit Message Convention

### Format

**Pattern:** `{system}.{TICKET}-{number}: {description}`

```bash
# Examples
basecamp.TICKET-4500: improve dockerfile caching
interface.TICKET-1234: add user authentication command
platform.TICKET-9999: update spring boot to 4.0.1
```

### Allowed Values

| Variable | Allowed Values |
|----------|---------------|
| `{system}` | `basecamp`, `interface`, `platform` |
| `{TICKET}` | `TICKET` (Jira project prefix) |
| `{number}` | Numeric ticket ID |
| `{description}` | Clear, concise description |

### Validation Rules

1. Must start with system name
2. System followed by dot (`.`)
3. Must include `TICKET-XXXX` reference
4. Ticket followed by colon and space (`: `)
5. Must have a description

### Fixing Commit Messages

```bash
# Fix last commit
git commit --amend -m "basecamp.TICKET-4500: corrected message"

# Fix older commits
git rebase -i HEAD~3
```

---

## Pull Request Guidelines

### PR Title Format

#### Feature to Develop (`feature/*` → `develop`)

**Format:** `[DEV][TICKET-{number}] {description}`

```
[DEV][TICKET-4500] Add dockerfile optimization
[DEV][TICKET-1234] Implement user authentication
```

#### Develop to Main (`develop` → `main`)

**Format:** `[RELEASE][TICKET-{number}] {description}`

```
[RELEASE][TICKET-4500] Deploy dockerfile improvements
[RELEASE][TICKET-1234] Release user authentication feature
```

### Validation Rules

| Target Branch | Prefix | Example |
|---------------|--------|---------|
| `develop` | `[DEV]` | `[DEV][TICKET-4500] Feature description` |
| `main` | `[RELEASE]` | `[RELEASE][TICKET-4500] Release description` |

### PR Checklist

- [ ] Branch name follows convention
- [ ] All commits follow message convention
- [ ] PR title follows format for target branch
- [ ] CI checks pass
- [ ] Code review completed
- [ ] Documentation updated (if needed)

---

## CI/CD Workflows

### Workflow Overview

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `github-validation-ci.yml` | PR, Push to feature/* | Validate branch, commit, PR title |
| `basecamp-server-ci.yml` | Changes to project-basecamp-server/ | Build, test Spring Boot API |
| `basecamp-parser-ci.yml` | Changes to project-basecamp-parser/ | Build, test Python Flask parser |
| `basecamp-ui-ci.yml` | Changes to project-basecamp-ui/ | Build, test React UI |
| `interface-cli-ci.yml` | Changes to project-interface-cli/ | Build, test CLI (multi-OS) |
| `*-docker.yml` | Push to main/develop | Build and push Docker images |
| `security-scan.yml` | Push, Schedule | Security scanning |

### Path-Based Triggers

Each project has isolated CI/CD triggered by path changes:

```yaml
on:
  push:
    paths:
      - 'project-basecamp-server/**'
```

### Build Times

| Project | Cold Cache | Warm Cache |
|---------|-----------|-----------|
| basecamp-server | 3 min | 2 min |
| basecamp-parser | 2 min | 1 min |
| basecamp-ui | 3 min | 2 min |
| interface-cli | 5 min | 3 min |

### Skip CI

```bash
git commit -m "docs: update readme [skip ci]"
```

---

## Local Development

### Quick Start

```bash
# Setup infrastructure
make setup && make dev

# Or full stack in Docker
make dev-all
```

### Per-Project Commands

#### basecamp-server (Kotlin/Spring Boot)

```bash
cd project-basecamp-server
./gradlew clean build          # Build
./gradlew test                 # Test
./gradlew bootRun              # Run (port 8080)
./gradlew ktlintCheck          # Lint
docker build -t server:local . # Docker
```

#### basecamp-parser (Python/Flask)

```bash
cd project-basecamp-parser
uv sync                           # Install deps
uv run flask run --port=5000      # Run
uv run pytest                     # Test
uv run ruff check .               # Lint
docker build -t parser:local .    # Docker
```

#### basecamp-ui (React/TypeScript)

```bash
cd project-basecamp-ui
npm ci                         # Install deps
npm run dev                    # Run (port 3000)
npm run build                  # Build
npm run lint                   # Lint
docker build -t ui:local .     # Docker
```

#### interface-cli (Python/Typer)

```bash
cd project-interface-cli
uv sync                            # Install deps
uv run dli --help                  # Run
uv run pytest                      # Test
uv run pyinstaller dli.spec        # Build binary
docker build -t cli:local .        # Docker
```

---

## Docker Images

### Registry

**GitHub Container Registry (GHCR):** `ghcr.io/1ambda/{project}`

### Available Images

```bash
docker pull ghcr.io/1ambda/basecamp-server:latest
docker pull ghcr.io/1ambda/basecamp-parser:latest
docker pull ghcr.io/1ambda/basecamp-ui:latest
docker pull ghcr.io/1ambda/interface-cli:latest
```

### Version Management

Each project maintains its version in a `.VERSION` file located in the project root:

```
project-basecamp-server/.VERSION   # e.g., 0.0.1
project-basecamp-parser/.VERSION   # e.g., 0.0.1
project-basecamp-ui/.VERSION       # e.g., 0.0.1
project-interface-cli/.VERSION     # e.g., 0.0.1
```

**Updating Version:**

```bash
# Update version for a specific project
echo "1.0.0" > project-basecamp-server/.VERSION

# Verify version
cat project-basecamp-server/.VERSION
```

### Image Tagging Strategy

Docker images are tagged based on the Git branch and event type:

| Branch/Event | Tag Format | Example |
|--------------|------------|---------|
| **PR (Feature)** | `feature-pr-{PR_NUMBER}` | `feature-pr-123` |
| **Develop** | `develop-{VERSION}-{BUILDDATE}` | `develop-0.0.1-202512281430` |
| **Main (Release)** | `release-{VERSION}-{BUILDDATE}` | `release-0.0.1-202512281430` |

**Additional Tags:**

| Tag | When Applied | Description |
|-----|--------------|-------------|
| `sha-{hash}` | All builds | 7-character commit SHA for traceability |
| `develop` | Develop branch | Floating tag for latest develop build |
| `main` | Main branch | Floating tag for latest main build |
| `latest` | Main branch | Standard "latest" tag for production |

**Build Date Format:**

- Format: `YYYYMMDDhhmm` (e.g., `202512281430`)
- Timezone: Asia/Seoul (KST)
- Generated at build time using: `TZ=Asia/Seoul date +%Y%m%d%H%M`

### Tagging Examples

```bash
# PR #42 to develop branch
ghcr.io/1ambda/basecamp-server:feature-pr-42
ghcr.io/1ambda/basecamp-server:sha-abc1234

# Merge to develop branch (version 0.0.1)
ghcr.io/1ambda/basecamp-server:develop-0.0.1-202512281430
ghcr.io/1ambda/basecamp-server:develop
ghcr.io/1ambda/basecamp-server:sha-def5678

# Merge to main branch (version 1.0.0)
ghcr.io/1ambda/basecamp-server:release-1.0.0-202512281500
ghcr.io/1ambda/basecamp-server:latest
ghcr.io/1ambda/basecamp-server:main
ghcr.io/1ambda/basecamp-server:sha-ghi9012
```

### Version Workflow

1. **Development:** Create feature branch, PR builds get `feature-pr-{number}` tag
2. **Integration:** Merge to develop, builds get `develop-{version}-{builddate}` tag
3. **Release:** Update `.VERSION` file, merge to main, builds get `release-{version}-{builddate}` tag

```bash
# Before release, update version in feature branch
echo "1.0.0" > project-basecamp-server/.VERSION
git add project-basecamp-server/.VERSION
git commit -m "basecamp.TICKET-XXX: bump version to 1.0.0"
```

### Image Details

| Image | Base | Size | Port |
|-------|------|------|------|
| basecamp-server | eclipse-temurin:21-jre-alpine | ~300MB | 8080 |
| basecamp-parser | python:3.12-slim | ~180MB | 5000 |
| basecamp-ui | nginx:1.27-alpine | ~100MB | 80 |
| interface-cli | debian:bookworm-slim | ~150MB | - |

---

## Security & Best Practices

### Action Pinning

All GitHub Actions are pinned to SHA commits:

```yaml
uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2
```

### Docker Security

- **Non-root users** - All containers run as unprivileged user
- **SBOM** - Software Bill of Materials included
- **Provenance** - Cryptographically signed build attestations
- **Tini** - Proper init system for signal handling

### Minimal Permissions

```yaml
permissions:
  contents: read
  packages: write
  checks: write
  pull-requests: write
```

### Security Scanning

- **CodeQL** - Java/Kotlin, JavaScript/TypeScript, Python
- **Trivy** - Container vulnerability scanning
- **OWASP** - Dependency check for Gradle
- **pip-audit** - Python dependency scanning

---

## Troubleshooting

### Validation Failures

#### Branch Name Invalid

```
ERROR: Branch name format is invalid!
Current: feature/my-feature
Expected: feature/{system}.TICKET-XXXX/{description}
Example: feature/basecamp.TICKET-4500/improve-dockerfile
```

**Fix:** Rename branch with correct format

```bash
git branch -m feature/basecamp.TICKET-4500/my-feature
```

#### Commit Message Invalid

```
ERROR: Commit message format is invalid!
Current: fix something
Expected: {system}.TICKET-XXXX: description
Example: basecamp.TICKET-4500: fix something
```

**Fix:** Amend commit message

```bash
git commit --amend -m "basecamp.TICKET-4500: fix something"
```

#### PR Title Invalid

```
ERROR: PR title format is invalid!
Current: Add new feature
Expected: [DEV][TICKET-XXXX] description
Example: [DEV][TICKET-4500] Add new feature
```

**Fix:** Edit PR title in GitHub UI

### Build Failures

| Issue | Solution |
|-------|----------|
| Gradle build fails | Check Java 21 version |
| Python tests fail | Run `uv sync` locally |
| npm build fails | Delete node_modules, run `npm ci` |
| Docker push fails | Only main/develop push images |

### Quick Diagnostics

```bash
# Check changed files
git diff HEAD~1 --name-only

# View workflow runs
gh run list --limit 10

# View specific run logs
gh run view {run-id} --log

# Re-run failed workflow
gh run rerun {run-id}
```

---

## Resources

- **Main Documentation:** [README.md](../README.md)
- **Architecture:** [docs/architecture.md](../docs/architecture.md)
- **Contribution Guide:** [CONTRIBUTING.md](./CONTRIBUTING.md)
- **PR Template:** [PULL_REQUEST_TEMPLATE.md](./PULL_REQUEST_TEMPLATE.md)

---

**Last Updated:** 2025-12-28
**Maintained by:** Platform/DevOps Team
