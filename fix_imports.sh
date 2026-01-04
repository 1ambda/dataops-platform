#!/bin/bash

# Script to fix enum imports from domain.model to common.enums

echo "Starting import fixes..."

cd /Users/kun/github/1ambda/dataops-platform/project-basecamp-server

# WorkflowEnums
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.workflow\.WorkflowSourceType|import com.github.lambda.common.enums.WorkflowSourceType|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.workflow\.WorkflowStatus|import com.github.lambda.common.enums.WorkflowStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.workflow\.WorkflowRunStatus|import com.github.lambda.common.enums.WorkflowRunStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.workflow\.WorkflowRunType|import com.github.lambda.common.enums.WorkflowRunType|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.workflow\.AirflowEnvironment|import com.github.lambda.common.enums.AirflowEnvironment|g' {} \;

# QualityEnums
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.quality\.ResourceType|import com.github.lambda.common.enums.ResourceType|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.quality\.TestType|import com.github.lambda.common.enums.TestType|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.quality\.Severity|import com.github.lambda.common.enums.Severity|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.quality\.TestStatus|import com.github.lambda.common.enums.TestStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.quality\.QualitySpecStatus|import com.github.lambda.common.enums.QualitySpecStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.quality\.RunStatus|import com.github.lambda.common.enums.RunStatus|g' {} \;

# LineageEnums
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.lineage\.LineageEdgeType|import com.github.lambda.common.enums.LineageEdgeType|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.lineage\.LineageNodeType|import com.github.lambda.common.enums.LineageNodeType|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.lineage\.LineageDirection|import com.github.lambda.common.enums.LineageDirection|g' {} \;

# QueryEnums
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.query\.QueryEngine|import com.github.lambda.common.enums.QueryEngine|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.query\.QueryScope|import com.github.lambda.common.enums.QueryScope|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.query\.QueryStatus|import com.github.lambda.common.enums.QueryStatus|g' {} \;

# Other enums
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.adhoc\.ExecutionStatus|import com.github.lambda.common.enums.ExecutionStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.health\.HealthStatus|import com.github.lambda.common.enums.HealthStatus|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.transpile\.SqlDialect|import com.github.lambda.common.enums.SqlDialect|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.user\.UserRole|import com.github.lambda.common.enums.UserRole|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.audit\.AuditResourceAction|import com.github.lambda.common.enums.AuditResourceAction|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.workflow\.SpecSyncErrorType|import com.github.lambda.common.enums.SpecSyncErrorType|g' {} \;

# GitHub enums
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.github\.PullRequestState|import com.github.lambda.common.enums.PullRequestState|g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's|import com\.github\.lambda\.domain\.model\.github\.ComparisonStatus|import com.github.lambda.common.enums.ComparisonStatus|g' {} \;

echo "Import fixes completed!"