#!/bin/bash

# Script to fix all controller tests to use @SpringBootTest instead of @WebMvcTest

TESTS=(
    "AirflowSyncControllerTest"
    "CatalogControllerTest"
    "DatasetControllerTest"
    "GitHubControllerTest"
    "HealthControllerTest"
    "LineageControllerTest"
    "MetricControllerTest"
    "QualityControllerTest"
    "QueryControllerTest"
    "RunControllerTest"
    "TranspileControllerTest"
)

BASE_DIR="/Users/kun/github/1ambda/dataops-platform/project-basecamp-server/module-server-api/src/test/kotlin/com/github/lambda/controller"

for TEST in "${TESTS[@]}"; do
    FILE="${BASE_DIR}/${TEST}.kt"

    if [ ! -f "$FILE" ]; then
        echo "File not found: $FILE"
        continue
    fi

    echo "Processing $TEST..."

    # Create backup
    cp "$FILE" "${FILE}.bak"

    # 1. Replace import for WebMvcTest with SpringBootTest and AutoConfigureMockMvc
    sed -i '' 's/import org\.springframework\.boot\.webmvc\.test\.autoconfigure\.WebMvcTest/import org.springframework.boot.test.context.SpringBootTest\nimport org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc/' "$FILE"

    # 2. Replace @WebMvcTest annotation (handle multi-line)
    # This is tricky - we need to handle the pattern carefully
    # For now, let's use a simpler approach

done

echo "Done! Backups created with .bak extension"
echo "Please review changes and remove backups if satisfied"
