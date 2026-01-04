#!/bin/bash

# Script to fix remaining query enum imports

echo "Fixing query enum imports..."

cd /Users/kun/github/1ambda/dataops-platform/project-basecamp-server

# Fix Query enum imports that are still pointing to domain.entity.query
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.entity\.query\.QueryScope|import com.github.lambda.common.enums.QueryScope|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.entity\.query\.QueryStatus|import com.github.lambda.common.enums.QueryStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.entity\.query\.QueryEngine|import com.github.lambda.common.enums.QueryEngine|g' {} \;

echo "Query enum import fixes completed!"