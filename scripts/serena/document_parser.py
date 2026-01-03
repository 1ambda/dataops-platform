#!/usr/bin/env python3
"""
Serena Document Parser

Parses Markdown documentation files into structured JSON format
for token-efficient access and search optimization.

Features:
- Header-based section extraction
- Code block extraction and language tagging
- Automatic keyword tagging
- Cross-reference detection
- File metadata tracking
"""

import re
import os
import json
import hashlib
from pathlib import Path
from typing import Dict, List, Set, Optional, Any
from dataclasses import dataclass, asdict
from datetime import datetime
import logging

# Setup logging
logger = logging.getLogger('document-parser')

@dataclass
class CodeBlock:
    """Represents a code block in markdown"""
    language: Optional[str]
    content: str
    line_start: int
    line_end: int

@dataclass
class DocumentSection:
    """Represents a section in a markdown document"""
    title: str
    level: int
    content: str
    line_start: int
    line_end: int
    code_blocks: List[CodeBlock]
    tags: Set[str]
    anchor: str  # URL anchor (#section-name)

@dataclass
class DocumentMetadata:
    """Document metadata"""
    file_path: str
    relative_path: str
    last_modified: str
    file_size: int
    project: Optional[str]  # project-basecamp-server, etc.
    doc_type: str  # README, guide, specification, etc.
    content_hash: str

@dataclass
class ParsedDocument:
    """Complete parsed document structure"""
    metadata: DocumentMetadata
    sections: List[DocumentSection]
    all_tags: Set[str]
    total_lines: int
    summary: str  # First paragraph or description

class MarkdownParser:
    """Parses markdown files into structured JSON format"""

    # Document type patterns
    DOC_TYPE_PATTERNS = {
        'README': r'README\.md$',
        'guide': r'(development|deployment|troubleshooting|contributing)\.md$',
        'specification': r'(FEATURE|RELEASE|PATTERNS|IMPLEMENTATION)\.md$',
        'architecture': r'architecture\.md$',
        'changelog': r'(CHANGELOG|HISTORY)\.md$',
        'license': r'LICENSE\.md$'
    }

    # Common technical keywords for auto-tagging
    TECH_KEYWORDS = {
        'setup', 'installation', 'config', 'configuration', 'environment',
        'docker', 'kubernetes', 'deployment', 'build', 'test', 'testing',
        'api', 'endpoint', 'rest', 'graphql', 'database', 'sql', 'mysql',
        'redis', 'postgres', 'keycloak', 'oauth', 'authentication', 'auth',
        'frontend', 'backend', 'microservice', 'service', 'server', 'client',
        'typescript', 'javascript', 'kotlin', 'python', 'java', 'spring',
        'react', 'flask', 'gradle', 'npm', 'maven', 'poetry', 'pip',
        'cli', 'command', 'terminal', 'bash', 'shell', 'script',
        'logging', 'monitoring', 'metrics', 'health', 'debug', 'troubleshoot',
        'security', 'ssl', 'tls', 'https', 'certificate', 'encryption',
        'performance', 'optimization', 'cache', 'redis', 'memory',
        'workflow', 'pipeline', 'ci', 'cd', 'github', 'git', 'version',
        'serena', 'symbol', 'mcp', 'claude', 'ai', 'agent', 'automation'
    }

    def __init__(self, project_root: Path):
        self.project_root = project_root

    def detect_project_name(self, file_path: str) -> Optional[str]:
        """Detect which project a file belongs to"""
        if 'project-basecamp-server' in file_path:
            return 'project-basecamp-server'
        elif 'project-basecamp-ui' in file_path:
            return 'project-basecamp-ui'
        elif 'project-basecamp-parser' in file_path:
            return 'project-basecamp-parser'
        elif 'project-basecamp-connect' in file_path:
            return 'project-basecamp-connect'
        elif 'project-interface-cli' in file_path:
            return 'project-interface-cli'
        else:
            return None  # Root level document

    def detect_doc_type(self, file_path: str) -> str:
        """Detect document type based on filename"""
        file_name = os.path.basename(file_path)

        for doc_type, pattern in self.DOC_TYPE_PATTERNS.items():
            if re.search(pattern, file_name, re.IGNORECASE):
                return doc_type

        return 'documentation'

    def create_anchor(self, title: str) -> str:
        """Create URL anchor from section title"""
        # Convert to lowercase, replace spaces with hyphens, remove special chars
        anchor = title.lower()
        anchor = re.sub(r'[^\w\s-]', '', anchor)
        anchor = re.sub(r'[-\s]+', '-', anchor)
        return anchor.strip('-')

    def extract_tags(self, text: str) -> Set[str]:
        """Extract relevant tags from text content"""
        tags = set()
        text_lower = text.lower()

        # Extract technical keywords
        for keyword in self.TECH_KEYWORDS:
            if keyword in text_lower:
                tags.add(keyword)

        # Extract code language references
        code_langs = re.findall(r'```(\w+)', text)
        for lang in code_langs:
            tags.add(lang.lower())

        # Extract common command patterns
        commands = re.findall(r'`([a-z]+(?:-[a-z]+)*)`', text_lower)
        for cmd in commands:
            if len(cmd) <= 15:  # Reasonable command length
                tags.add(cmd)

        return tags

    def extract_code_blocks(self, content: str, start_line: int) -> List[CodeBlock]:
        """Extract code blocks from section content"""
        blocks = []
        lines = content.split('\n')

        i = 0
        while i < len(lines):
            line = lines[i].strip()

            # Check for fenced code block
            if line.startswith('```'):
                # Extract language
                lang = line[3:].strip() or None
                code_lines = []
                block_start = start_line + i
                i += 1

                # Collect code content until closing ```
                while i < len(lines) and not lines[i].strip().startswith('```'):
                    code_lines.append(lines[i])
                    i += 1

                if i < len(lines):  # Found closing ```
                    block_end = start_line + i
                    code_content = '\n'.join(code_lines)

                    blocks.append(CodeBlock(
                        language=lang,
                        content=code_content,
                        line_start=block_start,
                        line_end=block_end
                    ))
            i += 1

        return blocks

    def parse_markdown_content(self, content: str) -> List[DocumentSection]:
        """Parse markdown content into sections"""
        lines = content.split('\n')
        sections = []
        current_section = None
        line_num = 0

        for i, line in enumerate(lines):
            line_num = i + 1
            stripped = line.strip()

            # Check for headers
            header_match = re.match(r'^(#{1,6})\s+(.+)$', stripped)

            if header_match:
                # Save previous section if exists
                if current_section:
                    current_section['line_end'] = line_num - 1
                    current_section['content'] = '\n'.join(current_section['content_lines'])

                    # Extract code blocks
                    code_blocks = self.extract_code_blocks(
                        current_section['content'],
                        current_section['line_start']
                    )

                    # Extract tags
                    section_text = current_section['title'] + ' ' + current_section['content']
                    tags = self.extract_tags(section_text)

                    sections.append(DocumentSection(
                        title=current_section['title'],
                        level=current_section['level'],
                        content=current_section['content'],
                        line_start=current_section['line_start'],
                        line_end=current_section['line_end'],
                        code_blocks=code_blocks,
                        tags=tags,
                        anchor=self.create_anchor(current_section['title'])
                    ))

                # Start new section
                level = len(header_match.group(1))
                title = header_match.group(2)

                current_section = {
                    'title': title,
                    'level': level,
                    'line_start': line_num,
                    'content_lines': []
                }

            elif current_section:
                # Add line to current section
                current_section['content_lines'].append(line)

        # Handle last section
        if current_section:
            current_section['line_end'] = len(lines)
            current_section['content'] = '\n'.join(current_section['content_lines'])

            code_blocks = self.extract_code_blocks(
                current_section['content'],
                current_section['line_start']
            )

            section_text = current_section['title'] + ' ' + current_section['content']
            tags = self.extract_tags(section_text)

            sections.append(DocumentSection(
                title=current_section['title'],
                level=current_section['level'],
                content=current_section['content'],
                line_start=current_section['line_start'],
                line_end=current_section['line_end'],
                code_blocks=code_blocks,
                tags=tags,
                anchor=self.create_anchor(current_section['title'])
            ))

        return sections

    def extract_summary(self, content: str) -> str:
        """Extract document summary (first meaningful paragraph)"""
        lines = content.split('\n')
        summary_lines = []

        for line in lines:
            stripped = line.strip()

            # Skip headers, empty lines
            if not stripped or stripped.startswith('#'):
                continue

            # Stop at first code block or list
            if stripped.startswith('```') or stripped.startswith('-') or stripped.startswith('*'):
                break

            summary_lines.append(stripped)

            # Stop after first paragraph (double line break)
            if len(summary_lines) > 0 and not stripped:
                break

        summary = ' '.join(summary_lines).strip()

        # Limit length
        if len(summary) > 200:
            summary = summary[:197] + '...'

        return summary or "No summary available"

    def parse_file(self, file_path: str) -> ParsedDocument:
        """Parse a single markdown file"""
        path_obj = Path(file_path)

        if not path_obj.exists():
            raise FileNotFoundError(f"File not found: {file_path}")

        # Read file content
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
        except UnicodeDecodeError:
            # Try with different encoding
            with open(file_path, 'r', encoding='latin-1') as f:
                content = f.read()

        # Create content hash
        content_hash = hashlib.sha256(content.encode('utf-8')).hexdigest()[:16]

        # Get file metadata
        stat = path_obj.stat()
        relative_path = str(path_obj.relative_to(self.project_root))

        metadata = DocumentMetadata(
            file_path=str(path_obj.absolute()),
            relative_path=relative_path,
            last_modified=datetime.fromtimestamp(stat.st_mtime).isoformat(),
            file_size=stat.st_size,
            project=self.detect_project_name(relative_path),
            doc_type=self.detect_doc_type(relative_path),
            content_hash=content_hash
        )

        # Parse sections
        sections = self.parse_markdown_content(content)

        # Collect all tags
        all_tags = set()
        for section in sections:
            all_tags.update(section.tags)

        # Add document-level tags
        doc_tags = self.extract_tags(content)
        all_tags.update(doc_tags)

        # Extract summary
        summary = self.extract_summary(content)

        return ParsedDocument(
            metadata=metadata,
            sections=sections,
            all_tags=all_tags,
            total_lines=len(content.split('\n')),
            summary=summary
        )

    def to_json(self, parsed_doc: ParsedDocument) -> str:
        """Convert parsed document to JSON string"""
        # Convert to dictionary with proper serialization
        doc_dict = {
            'metadata': asdict(parsed_doc.metadata),
            'sections': [
                {
                    'title': section.title,
                    'level': section.level,
                    'content': section.content,
                    'line_start': section.line_start,
                    'line_end': section.line_end,
                    'code_blocks': [asdict(cb) for cb in section.code_blocks],
                    'tags': sorted(list(section.tags)),
                    'anchor': section.anchor
                }
                for section in parsed_doc.sections
            ],
            'all_tags': sorted(list(parsed_doc.all_tags)),
            'total_lines': parsed_doc.total_lines,
            'summary': parsed_doc.summary
        }

        return json.dumps(doc_dict, indent=2, ensure_ascii=False)


def main():
    """CLI interface for document parser"""
    import argparse

    parser = argparse.ArgumentParser(description='Parse markdown documents to JSON')
    parser.add_argument('file_path', help='Path to markdown file')
    parser.add_argument('--project-root', default='.', help='Project root directory')
    parser.add_argument('--output', '-o', help='Output JSON file')

    args = parser.parse_args()

    # Setup logging
    logging.basicConfig(level=logging.INFO)

    # Parse file
    project_root = Path(args.project_root)
    md_parser = MarkdownParser(project_root)

    try:
        parsed_doc = md_parser.parse_file(args.file_path)
        json_output = md_parser.to_json(parsed_doc)

        if args.output:
            with open(args.output, 'w', encoding='utf-8') as f:
                f.write(json_output)
            logger.info(f"Output written to {args.output}")
        else:
            print(json_output)

    except Exception as e:
        logger.error(f"Failed to parse {args.file_path}: {e}")
        exit(1)


if __name__ == '__main__':
    main()