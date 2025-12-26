# Contributing to DataOps Platform

Thank you for your interest in contributing to the DataOps Platform! This document provides guidelines and best practices for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [CI/CD Pipeline](#cicd-pipeline)
- [Security Guidelines](#security-guidelines)

---

## Code of Conduct

Please be respectful and professional in all interactions. We expect contributors to:

- Use welcoming and inclusive language
- Be respectful of differing viewpoints
- Accept constructive criticism gracefully
- Focus on what is best for the community and project

---

## Getting Started

### Prerequisites

Before contributing, ensure you have the following installed:

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | Latest | Container builds |
| Java | 21+ | Basecamp Server |
| Python | 3.12+ | Parser & CLI |
| Node.js | 22+ | Basecamp UI |
| uv | Latest | Python package management |

### Local Setup

1. **Fork and Clone**
   ```bash
   git clone https://github.com/YOUR_USERNAME/dataops-platform.git
   cd dataops-platform
   ```

2. **Set Up Infrastructure**
   ```bash
   make setup && make dev
   ```

3. **Start Development**
   ```bash
   # Start all services
   make dev-all

   # Or start individual services
   cd project-basecamp-server && ./gradlew bootRun
   cd project-basecamp-parser && uv run python main.py
   cd project-basecamp-ui && npm run dev
   ```

4. **Verify Setup**
   ```bash
   make health
   ```

---

## Development Workflow

### Branch Naming

Use descriptive branch names with prefixes:

| Prefix | Purpose | Example |
|--------|---------|---------|
| `feature/` | New features | `feature/add-pipeline-api` |
| `fix/` | Bug fixes | `fix/parser-timeout-issue` |
| `docs/` | Documentation | `docs/update-api-guide` |
| `refactor/` | Code refactoring | `refactor/cleanup-services` |
| `chore/` | Maintenance | `chore/update-dependencies` |

### Commit Messages

Follow conventional commit format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting (no code change)
- `refactor`: Code restructuring
- `test`: Adding/updating tests
- `chore`: Maintenance tasks

**Examples:**
```bash
feat(server): add pipeline status endpoint
fix(parser): handle null values in SQL parsing
docs(readme): update installation instructions
```

### Testing Requirements

All contributions must include appropriate tests:

| Component | Test Command | Requirements |
|-----------|--------------|--------------|
| basecamp-server | `./gradlew test` | Unit + integration tests |
| basecamp-parser | `uv run pytest` | Unit + API tests |
| basecamp-ui | `npm test` | Component + E2E tests |
| interface-cli | `uv run pytest` | Unit + CLI tests |

---

## Coding Standards

### Kotlin (basecamp-server)

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use ktlint for formatting: `./gradlew ktlintFormat`
- Prefer immutable data structures
- Use meaningful variable and function names

### Python (parser & CLI)

- Follow PEP 8 guidelines
- Use Black for formatting: `uv run black .`
- Use Ruff for linting: `uv run ruff check .`
- Add type hints to all functions
- Document public APIs with docstrings

### TypeScript (basecamp-ui)

- Follow ESLint configuration
- Use Prettier for formatting
- Prefer functional components
- Use TypeScript strict mode

### General Guidelines

1. **Keep PRs focused** - One feature/fix per PR
2. **Write self-documenting code** - Use clear names
3. **Add comments for complex logic**
4. **Update tests for any code changes**
5. **Keep dependencies up to date**

---

## Pull Request Process

### Before Creating a PR

- [ ] Code compiles without errors
- [ ] All tests pass locally
- [ ] No linting errors
- [ ] Documentation is updated
- [ ] Commit messages follow conventions

### Creating the PR

1. Push your branch to your fork
2. Create a PR against `main` or `develop`
3. Fill out the PR template completely
4. Link related issues
5. Request appropriate reviewers

### Review Process

1. **Automated Checks** - CI must pass
2. **Code Review** - At least one approval required
3. **Address Feedback** - Respond to all comments
4. **Merge** - Squash and merge preferred

### After Merge

- Delete your feature branch
- Verify CI/CD completes successfully
- Monitor for any issues

---

## CI/CD Pipeline

### Automated Checks

All PRs trigger the following checks:

| Check | Purpose | Blocking |
|-------|---------|----------|
| Build | Compile and build | Yes |
| Tests | Unit/integration tests | Yes |
| Linting | Code style | No (warning) |
| Type Check | Type safety | No (warning) |
| Dockerfile | Validate syntax | Yes |
| Security Scan | Vulnerability check | No (warning) |

### Workflow Files

Workflow files are located in `.github/workflows/`:

| File | Purpose |
|------|---------|
| `basecamp-server-ci.yml` | Server CI |
| `basecamp-parser-ci.yml` | Parser CI |
| `basecamp-ui-ci.yml` | UI CI |
| `interface-cli-ci.yml` | CLI CI |
| `*-docker.yml` | Docker builds |
| `security-scan.yml` | Security scanning |

### Skip CI

For documentation-only changes:

```bash
git commit -m "docs: update README [skip ci]"
```

---

## Security Guidelines

### Do NOT Commit

- Credentials or API keys
- Private keys or certificates
- Environment files (`.env`)
- Database connection strings
- Any sensitive configuration

### Security Best Practices

1. **Use environment variables** for secrets
2. **Review dependencies** before adding
3. **Keep dependencies updated** regularly
4. **Report vulnerabilities** responsibly
5. **Use HTTPS** for all external calls

### Reporting Security Issues

For security vulnerabilities, please:

1. **Do NOT** create a public issue
2. Email security concerns directly to maintainers
3. Allow time for fix before disclosure

---

## Project Structure

```
dataops-platform/
├── project-basecamp-server/   # Spring Boot + Kotlin
├── project-basecamp-parser/   # Python + Flask
├── project-basecamp-ui/       # React + TypeScript
├── project-interface-cli/     # Python + Typer
├── docs/                      # Documentation
├── .github/                   # GitHub config
│   ├── workflows/             # CI/CD workflows
│   ├── ISSUE_TEMPLATE/        # Issue templates
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── CONTRIBUTING.md        # This file
│   └── DEVELOPMENT.md         # Development & CI/CD guide
├── Makefile                   # Development commands
└── docker-compose.yaml        # Infrastructure
```

---

## Getting Help

- **Documentation:** Check `docs/` directory
- **Development Guide:** See `.github/DEVELOPMENT.md`
- **Discussions:** GitHub Discussions
- **Issues:** GitHub Issues (use templates)

---

## Recognition

Contributors will be recognized in:

- GitHub contribution graph
- Release notes for significant contributions
- Repository acknowledgments

Thank you for contributing to the DataOps Platform!

---

**Last Updated:** 2025-12-28
