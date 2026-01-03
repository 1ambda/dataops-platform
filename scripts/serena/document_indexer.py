#!/usr/bin/env python3
"""
Serena Document Indexer

Creates searchable indexes from parsed markdown documents
for token-efficient content retrieval and search optimization.

Features:
- Tag-based indexing
- Section-level search
- Cross-document references
- Quick lookup by keywords
- Content similarity detection
"""

import json
import re
import os
from pathlib import Path
from typing import Dict, List, Set, Optional, Any, Tuple
from collections import defaultdict, Counter
from dataclasses import dataclass, asdict
from datetime import datetime
import hashlib
import logging

logger = logging.getLogger('document-indexer')

@dataclass
class DocumentReference:
    """Reference to a specific document section"""
    file_path: str
    relative_path: str
    section_title: str
    section_anchor: str
    line_start: int
    line_end: int
    project: Optional[str]
    doc_type: str
    relevance_score: float = 0.0

@dataclass
class SearchIndex:
    """Complete search index structure"""
    tag_index: Dict[str, List[DocumentReference]]  # tag -> document refs
    title_index: Dict[str, List[DocumentReference]]  # section title -> refs
    project_index: Dict[str, List[DocumentReference]]  # project -> refs
    doc_type_index: Dict[str, List[DocumentReference]]  # doc_type -> refs
    content_index: Dict[str, List[DocumentReference]]  # keywords -> refs
    all_documents: Dict[str, Dict[str, Any]]  # file_path -> parsed doc
    metadata: Dict[str, Any]  # index metadata

class DocumentIndexer:
    """Creates and manages document search indexes"""

    def __init__(self, project_root: Path, cache_dir: Optional[Path] = None):
        self.project_root = project_root
        self.cache_dir = cache_dir or (project_root / ".serena" / "cache" / "documents")
        self.cache_dir.mkdir(parents=True, exist_ok=True)

        # Document discovery patterns
        self.document_patterns = [
            "README.md",
            "docs/*.md",
            "*/README.md",
            "features/*.md",
            "*/features/*.md",
            "*/docs/*.md",
            "*/*.md"
        ]

        # Stop words for content indexing
        self.stop_words = {
            'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for',
            'of', 'with', 'by', 'as', 'is', 'are', 'was', 'were', 'be', 'been',
            'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could',
            'should', 'may', 'might', 'can', 'must', 'shall', 'this', 'that',
            'these', 'those', 'it', 'its', 'you', 'your', 'we', 'our', 'they',
            'their', 'them', 'he', 'his', 'him', 'she', 'her', 'hers'
        }

    def discover_documents(self) -> List[str]:
        """Discover all markdown documents in the project"""
        documents = []

        # Search patterns
        patterns = [
            self.project_root / "README.md",
            self.project_root / "docs",
            self.project_root / "features",
            self.project_root / "project-basecamp-server",
            self.project_root / "project-basecamp-ui",
            self.project_root / "project-basecamp-parser",
            self.project_root / "project-basecamp-connect",
            self.project_root / "project-interface-cli",
        ]

        for pattern_path in patterns:
            if pattern_path.is_file() and pattern_path.name.endswith('.md'):
                documents.append(str(pattern_path))
            elif pattern_path.is_dir():
                # Recursively find .md files
                for md_file in pattern_path.rglob("*.md"):
                    documents.append(str(md_file))

        # Filter out hidden files and common exclusions
        filtered = []
        exclusions = {'.git', 'node_modules', '__pycache__', '.idea', '.vscode', '.venv', 'venv',
                      'test_install_env', '.pytest_cache', 'dist-info', 'site-packages'}

        for doc in documents:
            path_parts = set(Path(doc).parts)
            if not path_parts.intersection(exclusions):
                filtered.append(doc)

        logger.info(f"Discovered {len(filtered)} markdown documents")
        return sorted(filtered)

    def create_document_reference(self, parsed_doc: Dict[str, Any], section: Dict[str, Any]) -> DocumentReference:
        """Create a document reference from parsed data"""
        metadata = parsed_doc['metadata']

        return DocumentReference(
            file_path=metadata['file_path'],
            relative_path=metadata['relative_path'],
            section_title=section['title'],
            section_anchor=section['anchor'],
            line_start=section['line_start'],
            line_end=section['line_end'],
            project=metadata.get('project'),
            doc_type=metadata['doc_type'],
            relevance_score=0.0
        )

    def extract_content_keywords(self, content: str) -> Set[str]:
        """Extract meaningful keywords from content"""
        keywords = set()

        # Extract words (alphanumeric + hyphens)
        words = re.findall(r'\b[a-zA-Z][\w-]*\b', content.lower())

        for word in words:
            # Filter stop words and very short words
            if word not in self.stop_words and len(word) >= 3:
                keywords.add(word)

        return keywords

    def calculate_relevance_score(self, doc_ref: DocumentReference, query_terms: Set[str]) -> float:
        """Calculate relevance score for a document reference"""
        score = 0.0

        # Title match bonus
        title_words = set(doc_ref.section_title.lower().split())
        title_matches = len(query_terms.intersection(title_words))
        score += title_matches * 3.0

        # Project match bonus
        if doc_ref.project and doc_ref.project.lower() in query_terms:
            score += 2.0

        # Doc type match bonus
        if doc_ref.doc_type.lower() in query_terms:
            score += 1.5

        return score

    def build_index(self, parsed_documents: Dict[str, Dict[str, Any]]) -> SearchIndex:
        """Build comprehensive search index from parsed documents"""
        tag_index = defaultdict(list)
        title_index = defaultdict(list)
        project_index = defaultdict(list)
        doc_type_index = defaultdict(list)
        content_index = defaultdict(list)

        # Process each document
        for file_path, parsed_doc in parsed_documents.items():
            metadata = parsed_doc['metadata']
            project = metadata.get('project', 'root')
            doc_type = metadata['doc_type']

            # Process each section
            for section in parsed_doc['sections']:
                doc_ref = self.create_document_reference(parsed_doc, section)

                # Index by tags
                for tag in section['tags']:
                    tag_index[tag.lower()].append(doc_ref)

                # Index by section title words
                title_words = re.findall(r'\b\w+\b', section['title'].lower())
                for word in title_words:
                    if word not in self.stop_words:
                        title_index[word].append(doc_ref)

                # Index by project
                if project:
                    project_index[project].append(doc_ref)

                # Index by document type
                doc_type_index[doc_type].append(doc_ref)

                # Index by content keywords
                content_keywords = self.extract_content_keywords(section['content'])
                for keyword in content_keywords:
                    content_index[keyword].append(doc_ref)

        # Convert defaultdicts to regular dicts and sort by relevance
        index = SearchIndex(
            tag_index=dict(tag_index),
            title_index=dict(title_index),
            project_index=dict(project_index),
            doc_type_index=dict(doc_type_index),
            content_index=dict(content_index),
            all_documents=parsed_documents,
            metadata={
                'total_documents': len(parsed_documents),
                'total_sections': sum(len(doc['sections']) for doc in parsed_documents.values()),
                'total_tags': len(tag_index),
                'total_keywords': len(content_index),
                'created_at': str(datetime.now()),
                'project_root': str(self.project_root)
            }
        )

        return index

    def search(self, index: SearchIndex, query: str, max_results: int = 10) -> List[DocumentReference]:
        """Search the index for relevant document sections"""
        query_terms = set(query.lower().split())
        all_matches = []

        # Search in different indexes with different weights
        search_sources = [
            (index.tag_index, 5.0),      # Highest priority for tags
            (index.title_index, 3.0),   # High priority for titles
            (index.content_index, 1.0), # Normal priority for content
            (index.project_index, 2.0), # Medium priority for projects
            (index.doc_type_index, 1.5) # Medium priority for doc types
        ]

        # Collect matches from all sources
        seen_refs = set()

        for search_dict, weight in search_sources:
            for term in query_terms:
                if term in search_dict:
                    for doc_ref in search_dict[term]:
                        ref_key = (doc_ref.relative_path, doc_ref.section_title)

                        if ref_key not in seen_refs:
                            # Calculate relevance score
                            base_score = self.calculate_relevance_score(doc_ref, query_terms)
                            doc_ref.relevance_score = base_score * weight

                            all_matches.append(doc_ref)
                            seen_refs.add(ref_key)

        # Sort by relevance score (descending)
        all_matches.sort(key=lambda x: x.relevance_score, reverse=True)

        return all_matches[:max_results]

    def get_section_content(self, index: SearchIndex, doc_ref: DocumentReference) -> Optional[str]:
        """Get the actual content of a document section"""
        if doc_ref.file_path not in index.all_documents:
            return None

        parsed_doc = index.all_documents[doc_ref.file_path]

        # Find matching section
        for section in parsed_doc['sections']:
            if (section['title'] == doc_ref.section_title and
                section['anchor'] == doc_ref.section_anchor):
                return section['content']

        return None

    def save_index(self, index: SearchIndex, index_file: str = "document_index.json") -> None:
        """Save index to disk"""
        index_path = self.cache_dir / index_file

        # Convert to serializable format
        serializable_index = {
            'tag_index': {tag: [asdict(ref) for ref in refs]
                         for tag, refs in index.tag_index.items()},
            'title_index': {title: [asdict(ref) for ref in refs]
                           for title, refs in index.title_index.items()},
            'project_index': {project: [asdict(ref) for ref in refs]
                             for project, refs in index.project_index.items()},
            'doc_type_index': {doc_type: [asdict(ref) for ref in refs]
                              for doc_type, refs in index.doc_type_index.items()},
            'content_index': {keyword: [asdict(ref) for ref in refs]
                             for keyword, refs in index.content_index.items()},
            'all_documents': index.all_documents,
            'metadata': index.metadata
        }

        with open(index_path, 'w', encoding='utf-8') as f:
            json.dump(serializable_index, f, indent=2, ensure_ascii=False)

        logger.info(f"Index saved to {index_path}")

    def load_index(self, index_file: str = "document_index.json") -> Optional[SearchIndex]:
        """Load index from disk"""
        index_path = self.cache_dir / index_file

        if not index_path.exists():
            return None

        try:
            with open(index_path, 'r', encoding='utf-8') as f:
                data = json.load(f)

            # Convert back to SearchIndex format
            def refs_from_dicts(ref_dicts):
                return [DocumentReference(**ref_dict) for ref_dict in ref_dicts]

            index = SearchIndex(
                tag_index={tag: refs_from_dicts(refs) for tag, refs in data['tag_index'].items()},
                title_index={title: refs_from_dicts(refs) for title, refs in data['title_index'].items()},
                project_index={project: refs_from_dicts(refs) for project, refs in data['project_index'].items()},
                doc_type_index={doc_type: refs_from_dicts(refs) for doc_type, refs in data['doc_type_index'].items()},
                content_index={keyword: refs_from_dicts(refs) for keyword, refs in data['content_index'].items()},
                all_documents=data['all_documents'],
                metadata=data['metadata']
            )

            logger.info(f"Index loaded from {index_path}")
            return index

        except Exception as e:
            logger.error(f"Failed to load index: {e}")
            return None


def main():
    """CLI interface for document indexer"""
    import argparse
    from datetime import datetime

    parser = argparse.ArgumentParser(description='Index markdown documents for search')
    parser.add_argument('--project-root', default='.', help='Project root directory')
    parser.add_argument('--search', help='Search the index')
    parser.add_argument('--rebuild', action='store_true', help='Force rebuild index')
    parser.add_argument('--max-results', type=int, default=10, help='Max search results')

    args = parser.parse_args()

    # Setup logging
    logging.basicConfig(level=logging.INFO)

    # Create indexer
    project_root = Path(args.project_root)
    indexer = DocumentIndexer(project_root)

    # Load or build index
    if args.rebuild:
        index = None
    else:
        index = indexer.load_index()

    if index is None:
        logger.info("Building new document index...")

        # Discover and parse documents
        from document_parser import MarkdownParser

        documents = indexer.discover_documents()
        parser = MarkdownParser(project_root)
        parsed_docs = {}

        for doc_path in documents:
            try:
                parsed_doc = parser.parse_file(doc_path)
                parsed_docs[doc_path] = json.loads(parser.to_json(parsed_doc))
                logger.info(f"Parsed: {Path(doc_path).relative_to(project_root)}")
            except Exception as e:
                logger.error(f"Failed to parse {doc_path}: {e}")

        # Build index
        index = indexer.build_index(parsed_docs)
        indexer.save_index(index)

        logger.info(f"Index built: {index.metadata['total_sections']} sections from {index.metadata['total_documents']} documents")

    # Search if requested
    if args.search:
        results = indexer.search(index, args.search, args.max_results)

        if results:
            print(f"\nFound {len(results)} results for '{args.search}':\n")

            for i, result in enumerate(results, 1):
                print(f"{i}. {result.section_title}")
                print(f"   📄 {result.relative_path}")
                if result.project:
                    print(f"   📦 {result.project}")
                print(f"   🏷️  {result.doc_type}")
                print(f"   🔗 #{result.section_anchor}")
                print(f"   📊 Score: {result.relevance_score:.2f}")
                print()
        else:
            print(f"No results found for '{args.search}'")


if __name__ == '__main__':
    main()