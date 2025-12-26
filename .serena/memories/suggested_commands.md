# Essential Development Commands

## Setup and Infrastructure
```bash
# Setup and start infrastructure (MySQL, Redis, etc.)
make setup && make dev

# Start full stack in Docker
make dev-all

# Check service health  
make health

# View logs
make logs
```

## Running Individual Services
```bash
# API Server (basecamp-server)
cd project-basecamp-server && ./gradlew bootRun

# SQL Parser (basecamp-parser) 
cd project-basecamp-parser && uv run python main.py

# UI Dashboard (basecamp-ui)
cd project-basecamp-ui && pnpm run dev

# CLI Tool (interface-cli)
cd project-interface-cli && uv run python -m dli
```

## Build and Test
```bash
# Spring Boot server
cd project-basecamp-server
./gradlew clean build
./gradlew test
./gradlew ktlintFormat

# Python services
cd project-basecamp-parser && uv run pytest
cd project-interface-cli && uv run pytest

# React UI
cd project-basecamp-ui && pnpm test
```

## System Commands (macOS)
- Git: `git status`, `git add`, `git commit`, `git push`
- File operations: `ls`, `find`, `grep`, `cat`
- Directory operations: `cd`, `mkdir`, `rm -rf`
- Process management: `ps aux`, `kill -9`