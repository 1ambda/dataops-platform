"""Dataset registry with caching and search capabilities.

This module provides the DatasetRegistry class which manages
dataset specs with caching, filtering, and search functionality.
"""

from __future__ import annotations

from collections.abc import Iterator

from dli.core.discovery import DatasetDiscovery, ProjectConfig
from dli.core.models import DatasetSpec


class DatasetRegistry:
    """Registry for managing dataset specs with caching and search.

    The registry loads all dataset specs from the project directory
    and provides filtering and search capabilities.

    Attributes:
        config: Project configuration
    """

    def __init__(self, project_config: ProjectConfig):
        """Initialize the registry with a project configuration.

        Args:
            project_config: Project configuration
        """
        self.config = project_config
        self._discovery = DatasetDiscovery(project_config)
        self._cache: dict[str, DatasetSpec] = {}
        self._load_all()

    def _load_all(self) -> None:
        """Load all dataset specs into the cache."""
        for spec in self._discovery.discover_all():
            self._cache[spec.name] = spec

    def get(self, name: str) -> DatasetSpec | None:
        """Get a dataset spec by name.

        Args:
            name: Fully qualified dataset name (catalog.schema.table)

        Returns:
            DatasetSpec if found, None otherwise
        """
        return self._cache.get(name)

    def list_all(self) -> list[DatasetSpec]:
        """Get all registered dataset specs.

        Returns:
            List of all dataset specs
        """
        return list(self._cache.values())

    def search(
        self,
        *,
        tag: str | None = None,
        domain: str | None = None,
        owner: str | None = None,
        team: str | None = None,
        catalog: str | None = None,
        schema: str | None = None,
        name_pattern: str | None = None,
    ) -> list[DatasetSpec]:
        """Search for datasets with optional filters.

        All filters are ANDed together.

        Args:
            tag: Filter by tag
            domain: Filter by domain
            owner: Filter by owner email
            team: Filter by team
            catalog: Filter by catalog name
            schema: Filter by schema name
            name_pattern: Filter by name pattern (substring match)

        Returns:
            List of matching dataset specs
        """
        results = self.list_all()

        if tag:
            results = [s for s in results if tag in s.tags]

        if domain:
            results = [s for s in results if domain in s.domains]

        if owner:
            results = [s for s in results if s.owner == owner]

        if team:
            results = [s for s in results if s.team == team]

        if catalog:
            results = [s for s in results if s.catalog == catalog]

        if schema:
            results = [s for s in results if s.schema_name == schema]

        if name_pattern:
            pattern_lower = name_pattern.lower()
            results = [s for s in results if pattern_lower in s.name.lower()]

        return results

    def get_by_catalog(self, catalog: str) -> list[DatasetSpec]:
        """Get all datasets in a catalog.

        Args:
            catalog: Catalog name

        Returns:
            List of datasets in the catalog
        """
        return self.search(catalog=catalog)

    def get_by_schema(self, catalog: str, schema: str) -> list[DatasetSpec]:
        """Get all datasets in a schema.

        Args:
            catalog: Catalog name
            schema: Schema name

        Returns:
            List of datasets in the schema
        """
        return self.search(catalog=catalog, schema=schema)

    def get_by_domain(self, domain: str) -> list[DatasetSpec]:
        """Get all datasets in a domain.

        Args:
            domain: Domain name

        Returns:
            List of datasets in the domain
        """
        return self.search(domain=domain)

    def get_by_tag(self, tag: str) -> list[DatasetSpec]:
        """Get all datasets with a tag.

        Args:
            tag: Tag name

        Returns:
            List of datasets with the tag
        """
        return self.search(tag=tag)

    def get_by_owner(self, owner: str) -> list[DatasetSpec]:
        """Get all datasets owned by someone.

        Args:
            owner: Owner email

        Returns:
            List of datasets owned by the owner
        """
        return self.search(owner=owner)

    def get_catalogs(self) -> list[str]:
        """Get all unique catalog names.

        Returns:
            Sorted list of catalog names
        """
        return sorted({s.catalog for s in self.list_all() if s.catalog})

    def get_schemas(self, catalog: str | None = None) -> list[str]:
        """Get all unique schema names.

        Args:
            catalog: Optional catalog to filter by

        Returns:
            Sorted list of schema names
        """
        specs = self.list_all()
        if catalog:
            specs = [s for s in specs if s.catalog == catalog]
        return sorted({s.schema_name for s in specs if s.schema_name})

    def get_domains(self) -> list[str]:
        """Get all unique domain names.

        Returns:
            Sorted list of domain names
        """
        domains: set[str] = set()
        for spec in self.list_all():
            domains.update(spec.domains)
        return sorted(domains)

    def get_tags(self) -> list[str]:
        """Get all unique tag names.

        Returns:
            Sorted list of tag names
        """
        tags: set[str] = set()
        for spec in self.list_all():
            tags.update(spec.tags)
        return sorted(tags)

    def get_owners(self) -> list[str]:
        """Get all unique owners.

        Returns:
            Sorted list of owner emails
        """
        return sorted({s.owner for s in self.list_all() if s.owner})

    def get_teams(self) -> list[str]:
        """Get all unique teams.

        Returns:
            Sorted list of team names
        """
        return sorted({s.team for s in self.list_all() if s.team})

    def reload(self) -> None:
        """Reload all dataset specs from disk."""
        self._cache.clear()
        self._load_all()

    def __len__(self) -> int:
        """Return the number of registered datasets."""
        return len(self._cache)

    def __contains__(self, name: str) -> bool:
        """Check if a dataset is registered."""
        return name in self._cache

    def __iter__(self) -> Iterator[DatasetSpec]:
        """Iterate over all dataset specs."""
        return iter(self._cache.values())
