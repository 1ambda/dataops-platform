# Workflow Repository File Creation Issue

The previous insert_after_symbol created content in the wrong file. Need to create new files for:

1. WorkflowRepositoryJpa.kt
2. WorkflowRepositoryDsl.kt  
3. WorkflowRunRepositoryJpa.kt
4. WorkflowRunRepositoryDsl.kt

These should be created in:
- project-basecamp-server/module-core-domain/src/main/kotlin/com/github/lambda/domain/repository/

And infrastructure implementations in:
- project-basecamp-server/module-core-infra/src/main/kotlin/com/github/lambda/infra/repository/