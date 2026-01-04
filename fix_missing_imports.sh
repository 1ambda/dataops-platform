#!/bin/bash

# Fix the most common missing imports in test files

TEST_DIR="/Users/kun/github/1ambda/dataops-platform/project-basecamp-server/module-core-domain/src/test"

echo "Fixing common missing imports..."

# Find all test files and add missing imports
find "$TEST_DIR" -name "*.kt" | while read file; do
    echo "Processing: $file"

    # Flag to track if we added imports
    MODIFIED=false

    # Create temporary file
    TEMP_FILE="${file}.tmp"
    cp "$file" "$TEMP_FILE"

    # Check for SqlDialect usage
    if grep -q "SqlDialect" "$file" && ! grep -q "import com.github.lambda.common.enums.SqlDialect" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.common.enums.SqlDialect' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added SqlDialect import"
    fi

    # Check for WorkflowStatus usage
    if grep -q "WorkflowStatus" "$file" && ! grep -q "import com.github.lambda.common.enums.WorkflowStatus" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.common.enums.WorkflowStatus' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added WorkflowStatus import"
    fi

    # Check for TranspileRuleEntity usage
    if grep -q "TranspileRuleEntity" "$file" && ! grep -q "import com.github.lambda.domain.entity.transpile.TranspileRuleEntity" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.domain.entity.transpile.TranspileRuleEntity' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added TranspileRuleEntity import"
    fi

    # Check for AirflowEnvironment usage
    if grep -q "AirflowEnvironment" "$file" && ! grep -q "import com.github.lambda.common.enums.AirflowEnvironment" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.common.enums.AirflowEnvironment' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added AirflowEnvironment import"
    fi

    # Check for WorkflowSourceType usage
    if grep -q "WorkflowSourceType" "$file" && ! grep -q "import com.github.lambda.common.enums.WorkflowSourceType" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.common.enums.WorkflowSourceType' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added WorkflowSourceType import"
    fi

    # Check for UserEntity usage
    if grep -q "UserEntity" "$file" && ! grep -q "import com.github.lambda.domain.entity.user.UserEntity" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.domain.entity.user.UserEntity' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added UserEntity import"
    fi

    # Check for WorkflowEntity usage
    if grep -q "WorkflowEntity" "$file" && ! grep -q "import com.github.lambda.domain.entity.workflow.WorkflowEntity" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.domain.entity.workflow.WorkflowEntity' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added WorkflowEntity import"
    fi

    # Check for AirflowClusterEntity usage
    if grep -q "AirflowClusterEntity" "$file" && ! grep -q "import com.github.lambda.domain.entity.workflow.AirflowClusterEntity" "$file"; then
        sed -i '' '/package /a\
\
import com.github.lambda.domain.entity.workflow.AirflowClusterEntity' "$TEMP_FILE"
        MODIFIED=true
        echo "  Added AirflowClusterEntity import"
    fi

    # Only replace the file if we made changes
    if [ "$MODIFIED" = true ]; then
        mv "$TEMP_FILE" "$file"
        echo "  ✓ Updated: $file"
    else
        rm "$TEMP_FILE"
    fi
done

echo "Import fixing completed!"