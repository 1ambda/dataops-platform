#!/bin/bash

# Fix enum imports in module-core-infra

echo "Fixing enum imports in module-core-infra..."

# Fix AirflowDagStatus imports
find project-basecamp-server/module-core-infra -name "*.kt" -exec sed -i '' \
  -e '/^package /a\
import com.github.lambda.common.enums.AirflowDagStatus
' {} \; 2>/dev/null || true

# Fix ExecutionStatus imports
find project-basecamp-server/module-core-infra -name "*.kt" -exec sed -i '' \
  -e '/^package /a\
import com.github.lambda.common.enums.ExecutionStatus
' {} \; 2>/dev/null || true

# Fix ComparisonStatus imports
find project-basecamp-server/module-core-infra -name "*.kt" -exec sed -i '' \
  -e '/^package /a\
import com.github.lambda.common.enums.ComparisonStatus
' {} \; 2>/dev/null || true

# Fix WorkflowRunStatus imports
find project-basecamp-server/module-core-infra -name "*.kt" -exec sed -i '' \
  -e '/^package /a\
import com.github.lambda.common.enums.WorkflowRunStatus
' {} \; 2>/dev/null || true

# Fix QueryStatus imports
find project-basecamp-server/module-core-infra -name "*.kt" -exec sed -i '' \
  -e '/^package /a\
import com.github.lambda.common.enums.QueryStatus
' {} \; 2>/dev/null || true

# Remove duplicate import statements
find project-basecamp-server/module-core-infra -name "*.kt" -exec awk '
/^import com\.github\.lambda\.common\.enums\./ {
    if (!seen[$0]) {
        seen[$0] = 1
        print
    }
    next
}
{ print }' {} \; > temp_file && mv temp_file {} 2>/dev/null || true

echo "Fixed enum imports in module-core-infra"