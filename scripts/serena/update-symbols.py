#!/usr/bin/env python3
"""
Serena Symbol Auto-Update System

This script automatically updates Serena's symbol cache and memory patterns
for the dataops-platform monorepo project.

Usage:
    python update-symbols.py --all                     # Update all projects
    python update-symbols.py --project PROJECT_NAME    # Update specific project
    python update-symbols.py --language LANGUAGE       # Update specific language
    python update-symbols.py --with-deps              # Include dependency analysis
    python update-symbols.py --with-memories          # Update memory patterns
    python update-symbols.py --changed-only           # Git-based: only changed files
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path
from typing import List, Set, Dict, Optional
import json
import logging
from datetime import datetime

# Import document processing modules
try:
    from document_parser import MarkdownParser
    from document_indexer import DocumentIndexer
    DOCS_AVAILABLE = True
except ImportError:
    DOCS_AVAILABLE = False

# Project root
PROJECT_ROOT = Path(__file__).parent.parent.parent
SERENA_DIR = PROJECT_ROOT / ".serena"
CACHE_DIR = SERENA_DIR / "cache"

# Language mapping for each project
LANGUAGE_MAPPING = {
    'project-basecamp-server': 'kotlin',
    'project-basecamp-ui': 'typescript',
    'project-basecamp-parser': 'python',
    'project-basecamp-connect': 'python',
    'project-interface-cli': 'python'
}

# Reverse mapping
PROJECT_MAPPING = {v: [] for v in LANGUAGE_MAPPING.values()}
for project, lang in LANGUAGE_MAPPING.items():
    PROJECT_MAPPING[lang].append(project)

class SerenaUpdater:
    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.logger = self._setup_logging()
        self.changed_files: Set[str] = set()

    def _setup_logging(self) -> logging.Logger:
        """Setup logging configuration"""
        logger = logging.getLogger('serena-updater')
        logger.setLevel(logging.INFO)

        handler = logging.StreamHandler()
        formatter = logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )
        handler.setFormatter(formatter)
        logger.addHandler(handler)

        return logger

    def get_changed_files_since_commit(self, commit_hash: str = "HEAD~1") -> Set[str]:
        """Get list of changed files since specific commit"""
        try:
            cmd = ["git", "diff", "--name-only", commit_hash, "HEAD"]
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=PROJECT_ROOT)

            if result.returncode != 0:
                self.logger.warning(f"Git command failed: {result.stderr}")
                return set()

            files = set(result.stdout.strip().split('\n')) if result.stdout.strip() else set()
            self.logger.info(f"Found {len(files)} changed files since {commit_hash}")
            return files

        except Exception as e:
            self.logger.error(f"Failed to get changed files: {e}")
            return set()

    def get_projects_for_files(self, files: Set[str]) -> Set[str]:
        """Determine which projects are affected by changed files"""
        affected_projects = set()

        for file_path in files:
            for project in LANGUAGE_MAPPING.keys():
                if file_path.startswith(project + "/"):
                    affected_projects.add(project)
                    break

        return affected_projects

    def restart_language_server(self, language: str) -> bool:
        """Restart language server for specific language"""
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would restart {language} language server")
            return True

        try:
            # Using MCP Serena tool to restart language server
            cmd = [
                "python", "-c",
                f"""
import subprocess
import sys
try:
    # Call Serena MCP restart tool
    result = subprocess.run([
        'mcp-client', 'serena', 'restart_language_server'
    ], capture_output=True, text=True)
    print(f"Language server restart result: {{result.returncode}}")
    sys.exit(result.returncode)
except Exception as e:
    print(f"Error restarting language server: {{e}}")
    sys.exit(1)
"""
            ]

            result = subprocess.run(cmd, cwd=PROJECT_ROOT)
            success = result.returncode == 0

            if success:
                self.logger.info(f"Successfully restarted {language} language server")
            else:
                self.logger.warning(f"Failed to restart {language} language server")

            return success

        except Exception as e:
            self.logger.error(f"Error restarting {language} language server: {e}")
            return False

    def update_symbol_cache(self, language: str) -> bool:
        """Update symbol cache for specific language"""
        cache_file = CACHE_DIR / language / "document_symbols.pkl"

        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would update symbol cache: {cache_file}")
            return True

        try:
            # First restart the language server to pick up changes
            if not self.restart_language_server(language):
                self.logger.warning(f"Language server restart failed for {language}, continuing anyway")

            # Remove existing cache to force regeneration
            if cache_file.exists():
                cache_file.unlink()
                self.logger.info(f"Removed existing cache: {cache_file}")

            raw_cache_file = CACHE_DIR / language / "raw_document_symbols.pkl"
            if raw_cache_file.exists():
                raw_cache_file.unlink()
                self.logger.info(f"Removed existing raw cache: {raw_cache_file}")

            # Cache will be regenerated automatically on next Serena tool use
            self.logger.info(f"Symbol cache marked for regeneration: {language}")
            return True

        except Exception as e:
            self.logger.error(f"Failed to update symbol cache for {language}: {e}")
            return False

    def analyze_dependencies(self, projects: Set[str]) -> Dict[str, List[str]]:
        """Analyze dependencies between projects (simplified version)"""
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would analyze dependencies for: {projects}")
            return {}

        dependencies = {}

        # Simplified dependency analysis
        # In a real implementation, this would parse import statements,
        # API calls, etc. across projects

        for project in projects:
            deps = []

            if project == "project-interface-cli":
                # CLI likely depends on server APIs
                deps.extend(["project-basecamp-server", "project-basecamp-parser"])
            elif project == "project-basecamp-ui":
                # UI likely depends on server APIs
                deps.append("project-basecamp-server")
            elif project == "project-basecamp-connect":
                # Connect service likely integrates with server
                deps.append("project-basecamp-server")

            dependencies[project] = deps

        return dependencies

    def update_memories(self, affected_projects: Set[str]) -> bool:
        """Update memory patterns based on affected projects"""
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would update memories for: {affected_projects}")
            return True

        try:
            # This would analyze code patterns and update memory files
            # For now, just log what would be updated

            memory_updates = {
                'project-basecamp-server': ['server_patterns'],
                'project-basecamp-ui': ['ui_patterns'],
                'project-basecamp-parser': ['parser_patterns'],
                'project-basecamp-connect': ['connect_patterns'],
                'project-interface-cli': ['cli_patterns', 'cli_test_patterns']
            }

            for project in affected_projects:
                if project in memory_updates:
                    for memory in memory_updates[project]:
                        self.logger.info(f"Would update memory: {memory}")

            return True

        except Exception as e:
            self.logger.error(f"Failed to update memories: {e}")
            return False

    def get_changed_documents(self, commit_hash: str = "HEAD~1") -> Set[str]:
        """Get list of changed markdown files since specific commit"""
        try:
            cmd = ["git", "diff", "--name-only", commit_hash, "HEAD"]
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=PROJECT_ROOT)

            if result.returncode != 0:
                self.logger.warning(f"Git command failed: {result.stderr}")
                return set()

            changed_files = set()
            if result.stdout.strip():
                for file_path in result.stdout.strip().split('\n'):
                    if file_path.endswith('.md'):
                        changed_files.add(file_path)

            self.logger.info(f"Found {len(changed_files)} changed markdown files since {commit_hash}")
            return changed_files

        except Exception as e:
            self.logger.error(f"Failed to get changed documents: {e}")
            return set()

    def needs_document_update(self, changed_files: Set[str] = None) -> bool:
        """Check if document index needs updating"""
        if not DOCS_AVAILABLE:
            return False

        # If we have specific changed files, check if any are markdown
        if changed_files:
            md_files = {f for f in changed_files if f.endswith('.md')}
            return len(md_files) > 0

        # Otherwise, check if index exists and is recent
        cache_dir = SERENA_DIR / "cache" / "documents"
        index_file = cache_dir / "document_index.json"

        if not index_file.exists():
            return True

        # Check if any markdown files are newer than index
        try:
            index_time = index_file.stat().st_mtime

            for md_file in PROJECT_ROOT.rglob("*.md"):
                # Skip hidden directories and common exclusions
                if any(part.startswith('.') for part in md_file.parts):
                    continue
                if any(exclude in str(md_file) for exclude in ['node_modules', '__pycache__']):
                    continue

                if md_file.stat().st_mtime > index_time:
                    return True

        except Exception as e:
            self.logger.warning(f"Error checking document timestamps: {e}")
            return True

        return False

    def get_project_documents(self, project_name: str) -> List[str]:
        """Get list of markdown documents for a specific project"""
        project_path = PROJECT_ROOT / project_name
        documents = []

        if project_path.exists() and project_path.is_dir():
            # Find all .md files in the project directory
            for md_file in project_path.rglob("*.md"):
                # Skip hidden directories and common exclusions
                if any(part.startswith('.') for part in md_file.parts):
                    continue
                if any(exclude in str(md_file) for exclude in ['node_modules', '__pycache__', '.venv']):
                    continue
                documents.append(str(md_file))

        self.logger.info(f"Found {len(documents)} documents for project {project_name}")
        return documents

    def update_document_index(self, changed_files: Optional[Set[str]] = None, target_projects: Optional[Set[str]] = None) -> bool:
        """Update document search index"""
        if not DOCS_AVAILABLE:
            self.logger.warning("Document parsing modules not available")
            return True  # Don't fail the entire update

        if self.dry_run:
            self.logger.info("[DRY-RUN] Would update document index")
            return True

        try:
            # Create indexer
            indexer = DocumentIndexer(PROJECT_ROOT)

            # If specific projects are targeted, focus on those
            if target_projects:
                self.logger.info(f"Updating document index for projects: {target_projects}")

                # Get project-specific documents
                project_documents = set()
                for project in target_projects:
                    project_docs = self.get_project_documents(project)
                    project_documents.update(project_docs)

                if not project_documents:
                    self.logger.info("No documents found for target projects")
                    return True

                # Add project documents to changed files for processing
                if changed_files is None:
                    changed_files = set()

                # Convert absolute paths to relative for consistency
                for doc_path in project_documents:
                    try:
                        rel_path = str(Path(doc_path).relative_to(PROJECT_ROOT))
                        changed_files.add(rel_path)
                    except ValueError:
                        # Path is not relative to project root
                        continue

                self.logger.info(f"Processing {len(changed_files)} documents from target projects")

            else:
                self.logger.info("Updating document search index...")

                # Check if we need to update (only for full updates)
                if not self.needs_document_update(changed_files):
                    self.logger.info("Document index is up to date")
                    return True

            # Try to load existing index first
            existing_index = indexer.load_index()

            if existing_index and changed_files:
                # Incremental update: only reprocess changed files
                self.logger.info(f"Performing incremental update for {len(changed_files)} changed files")

                # Get existing documents
                all_documents = existing_index.all_documents.copy()

                # Reparse changed documents
                parser = MarkdownParser(PROJECT_ROOT)

                for changed_file in changed_files:
                    if changed_file.endswith('.md'):
                        full_path = PROJECT_ROOT / changed_file
                        if full_path.exists():
                            try:
                                parsed_doc = parser.parse_file(str(full_path))
                                all_documents[str(full_path)] = json.loads(parser.to_json(parsed_doc))
                                self.logger.info(f"Updated: {changed_file}")
                            except Exception as e:
                                self.logger.error(f"Failed to reparse {changed_file}: {e}")
                        else:
                            # File was deleted, remove from index
                            all_documents.pop(str(full_path), None)
                            self.logger.info(f"Removed: {changed_file}")

                # Rebuild index with updated documents
                new_index = indexer.build_index(all_documents)

            else:
                # Full rebuild
                self.logger.info("Performing full document index rebuild...")

                # Discover and parse all documents
                documents = indexer.discover_documents()
                parser = MarkdownParser(PROJECT_ROOT)
                parsed_docs = {}

                for doc_path in documents:
                    try:
                        parsed_doc = parser.parse_file(doc_path)
                        parsed_docs[doc_path] = json.loads(parser.to_json(parsed_doc))

                        rel_path = str(Path(doc_path).relative_to(PROJECT_ROOT))
                        self.logger.debug(f"Parsed: {rel_path}")
                    except Exception as e:
                        self.logger.error(f"Failed to parse {doc_path}: {e}")

                # Build index
                new_index = indexer.build_index(parsed_docs)

            # Save updated index
            indexer.save_index(new_index)

            self.logger.info(
                f"Document index updated: {new_index.metadata['total_sections']} sections "
                f"from {new_index.metadata['total_documents']} documents"
            )

            return True

        except Exception as e:
            self.logger.error(f"Failed to update document index: {e}")
            return False

    def run_update(self,
                   projects: Optional[Set[str]] = None,
                   languages: Optional[Set[str]] = None,
                   with_deps: bool = False,
                   with_memories: bool = False,
                   with_docs: bool = False,
                   changed_only: bool = False) -> bool:
        """Run the symbol update process"""

        self.logger.info("Starting Serena Symbol Update...")

        # Determine what to update
        target_projects = set()
        target_languages = set()

        # Document changes tracking
        changed_documents = set()

        if changed_only:
            # Git-based: only update changed files
            self.changed_files = self.get_changed_files_since_commit()
            target_projects = self.get_projects_for_files(self.changed_files)

            # Also track document changes
            changed_documents = self.get_changed_documents()

            self.logger.info(f"Changed-only mode: updating projects {target_projects}")
            if changed_documents:
                self.logger.info(f"Found {len(changed_documents)} changed documents")

        if projects:
            target_projects.update(projects)

        if languages:
            target_languages.update(languages)
            # Add projects for these languages
            for lang in languages:
                target_projects.update(PROJECT_MAPPING.get(lang, []))

        # Default: update all if nothing specified
        if not target_projects and not target_languages:
            target_projects = set(LANGUAGE_MAPPING.keys())

        # Get languages to update
        if not target_languages:
            for project in target_projects:
                if project in LANGUAGE_MAPPING:
                    target_languages.add(LANGUAGE_MAPPING[project])

        self.logger.info(f"Target projects: {target_projects}")
        self.logger.info(f"Target languages: {target_languages}")

        # Analyze dependencies if requested
        dependencies = {}
        if with_deps and target_projects:
            dependencies = self.analyze_dependencies(target_projects)
            # Add dependent projects to target set
            for deps in dependencies.values():
                for dep in deps:
                    if dep in LANGUAGE_MAPPING:
                        target_projects.add(dep)
                        target_languages.add(LANGUAGE_MAPPING[dep])

        # Update symbol caches
        success = True
        for language in target_languages:
            if not self.update_symbol_cache(language):
                success = False

        # Update memories if requested
        if with_memories and target_projects:
            if not self.update_memories(target_projects):
                success = False

        # Update document index if requested or for specific projects
        if with_docs or changed_documents or target_projects:
            update_files = changed_documents if changed_documents else None
            if not self.update_document_index(update_files, target_projects):
                success = False

        status = "SUCCESS" if success else "PARTIAL_FAILURE"
        self.logger.info(f"Serena Symbol Update completed: {status}")

        return success


def main():
    parser = argparse.ArgumentParser(description='Serena Symbol Auto-Update System')

    # Update scope options
    parser.add_argument('--all', action='store_true',
                       help='Update all projects and languages')
    parser.add_argument('--project', action='append',
                       choices=list(LANGUAGE_MAPPING.keys()),
                       help='Update specific project(s)')
    parser.add_argument('--language', action='append',
                       choices=list(set(LANGUAGE_MAPPING.values())),
                       help='Update specific language(s)')

    # Update options
    parser.add_argument('--with-deps', action='store_true',
                       help='Include dependency analysis')
    parser.add_argument('--with-memories', action='store_true',
                       help='Update memory patterns')
    parser.add_argument('--with-docs', action='store_true',
                       help='Update document search index')
    parser.add_argument('--changed-only', action='store_true',
                       help='Only update files changed since last commit')

    # Execution options
    parser.add_argument('--dry-run', action='store_true',
                       help='Show what would be done without executing')
    parser.add_argument('--verbose', '-v', action='store_true',
                       help='Enable verbose output')

    args = parser.parse_args()

    # Validate arguments
    if args.all and (args.project or args.language):
        parser.error("Cannot use --all with --project or --language")

    if args.changed_only and (args.project or args.language):
        parser.error("Cannot use --changed-only with --project or --language")

    # Setup updater
    updater = SerenaUpdater(dry_run=args.dry_run)

    if args.verbose:
        updater.logger.setLevel(logging.DEBUG)

    # Determine update parameters
    projects = set(args.project) if args.project else None
    languages = set(args.language) if args.language else None

    if args.all:
        projects = None
        languages = None

    # Run update
    try:
        success = updater.run_update(
            projects=projects,
            languages=languages,
            with_deps=args.with_deps,
            with_memories=args.with_memories,
            with_docs=args.with_docs,
            changed_only=args.changed_only
        )

        sys.exit(0 if success else 1)

    except KeyboardInterrupt:
        updater.logger.info("Update cancelled by user")
        sys.exit(130)
    except Exception as e:
        updater.logger.error(f"Unexpected error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()