#!/bin/bash

# Fix common import issues in test files

TEST_DIR="/Users/kun/github/1ambda/dataops-platform/project-basecamp-server/module-core-domain/src/test"

echo "Fixing AssertJ imports..."

# Find all test files that use isTrue, isFalse, isNull but don't import them
find "$TEST_DIR" -name "*.kt" -exec grep -l "isTrue\|isFalse\|isNull\|isNotNull\|isEqualTo\|hasSize\|isEmpty\|isNotEmpty\|containsExactly" {} \; | while read file; do
    # Check if AssertJ static imports are missing
    if ! grep -q "import org.assertj.core.api.Assertions\.\*" "$file"; then
        # Add static import after the regular assertThat import
        sed -i '' '/import org.assertj.core.api.Assertions.assertThat/a\
import org.assertj.core.api.Assertions.*' "$file"
        echo "Fixed AssertJ imports in: $file"
    fi
done

echo "AssertJ import fix completed!"