#!/bin/bash

# validate-python-projects.sh
# Comprehensive validation script for Python projects in dataops-platform
# Validates type annotations, linting, formatting, and tests

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECTS=("project-basecamp-parser" "project-interface-cli")
FAILED_PROJECTS=()
SUCCESS_PROJECTS=()

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo -e "\n${BLUE}============================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}============================================${NC}"
}

# Function to validate a single project
validate_project() {
    local project_dir=$1
    local project_name=$(basename "$project_dir")
    
    log_section "Validating $project_name"
    
    if [[ ! -d "$project_dir" ]]; then
        log_error "Project directory $project_dir does not exist"
        FAILED_PROJECTS+=("$project_name: directory not found")
        return 1
    fi
    
    cd "$project_dir"
    
    # Check if pyproject.toml exists
    if [[ ! -f "pyproject.toml" ]]; then
        log_error "pyproject.toml not found in $project_dir"
        FAILED_PROJECTS+=("$project_name: missing pyproject.toml")
        cd - > /dev/null
        return 1
    fi
    
    # Install dependencies
    log_info "Installing dependencies for $project_name..."
    if ! uv sync; then
        log_error "Failed to install dependencies for $project_name"
        FAILED_PROJECTS+=("$project_name: dependency installation failed")
        cd - > /dev/null
        return 1
    fi
    
    local validation_failed=false
    
    # Type checking with mypy
    log_info "Running type checking with mypy..."
    if ! uv run mypy src tests 2>/dev/null; then
        log_warning "Type checking failed for $project_name"
        validation_failed=true
    else
        log_success "Type checking passed for $project_name"
    fi
    
    # Linting with ruff
    log_info "Running linting with ruff..."
    if ! uv run ruff check src tests; then
        log_warning "Linting failed for $project_name"
        validation_failed=true
    else
        log_success "Linting passed for $project_name"
    fi
    
    # Format checking with ruff
    log_info "Checking code formatting..."
    if ! uv run ruff format --check src tests; then
        log_warning "Format checking failed for $project_name"
        validation_failed=true
    else
        log_success "Format checking passed for $project_name"
    fi
    
    # Format checking with black (if available)
    if uv run black --check src tests 2>/dev/null; then
        log_success "Black format checking passed for $project_name"
    fi
    
    # Running tests
    log_info "Running tests..."
    if ! uv run pytest --tb=short; then
        log_warning "Tests failed for $project_name"
        validation_failed=true
    else
        log_success "Tests passed for $project_name"
    fi
    
    # Project-specific validations
    case "$project_name" in
        "project-interface-cli")
            log_info "Running CLI-specific validations..."
            if ! uv run dli version &>/dev/null; then
                log_warning "CLI version command failed"
                validation_failed=true
            fi
            
            if ! uv run dli sql-parse "SELECT * FROM test" &>/dev/null; then
                log_warning "CLI SQL parse command failed"
                validation_failed=true
            fi
            
            if ! validation_failed; then
                log_success "CLI-specific validations passed"
            fi
            ;;
        "project-basecamp-parser")
            log_info "Running parser-specific validations..."
            if ! uv run python -c "from src.parser.sql_parser import TrinoSQLParser; parser = TrinoSQLParser(); print('Import successful')" &>/dev/null; then
                log_warning "Parser import test failed"
                validation_failed=true
            else
                log_success "Parser-specific validations passed"
            fi
            ;;
    esac
    
    cd - > /dev/null
    
    if $validation_failed; then
        FAILED_PROJECTS+=("$project_name")
        return 1
    else
        SUCCESS_PROJECTS+=("$project_name")
        log_success "All validations passed for $project_name"
        return 0
    fi
}

# Function to generate summary report
generate_report() {
    log_section "VALIDATION SUMMARY"
    
    echo "Total projects validated: ${#PROJECTS[@]}"
    echo "Successful: ${#SUCCESS_PROJECTS[@]}"
    echo "Failed: ${#FAILED_PROJECTS[@]}"
    
    if [[ ${#SUCCESS_PROJECTS[@]} -gt 0 ]]; then
        echo -e "\n${GREEN}Successful projects:${NC}"
        for project in "${SUCCESS_PROJECTS[@]}"; do
            echo -e "  ✓ $project"
        done
    fi
    
    if [[ ${#FAILED_PROJECTS[@]} -gt 0 ]]; then
        echo -e "\n${RED}Failed projects:${NC}"
        for project in "${FAILED_PROJECTS[@]}"; do
            echo -e "  ✗ $project"
        done
        echo -e "\n${RED}Some validations failed. Please check the output above for details.${NC}"
        return 1
    else
        echo -e "\n${GREEN}🎉 All Python projects passed validation!${NC}"
        return 0
    fi
}

# Main execution
main() {
    log_section "Python Projects Type Safety Validation"
    log_info "Validating projects: ${PROJECTS[*]}"
    
    # Store original directory
    ORIGINAL_DIR=$(pwd)
    
    # Validate each project
    for project in "${PROJECTS[@]}"; do
        validate_project "$project" || true
    done
    
    # Return to original directory
    cd "$ORIGINAL_DIR"
    
    # Generate final report
    generate_report
}

# Handle script arguments
case "${1:-validate}" in
    "validate"|"")
        main
        ;;
    "install")
        log_info "Installing dependencies for all Python projects..."
        for project in "${PROJECTS[@]}"; do
            if [[ -d "$project" ]]; then
                log_info "Installing dependencies for $project..."
                cd "$project" && uv sync && cd - > /dev/null
            fi
        done
        ;;
    "clean")
        log_info "Cleaning cache files for all Python projects..."
        for project in "${PROJECTS[@]}"; do
            if [[ -d "$project" ]]; then
                log_info "Cleaning $project..."
                cd "$project" && make clean 2>/dev/null || {
                    rm -rf .pytest_cache/ __pycache__/ .mypy_cache/ .ruff_cache/ htmlcov/ .coverage
                }
                cd - > /dev/null
            fi
        done
        ;;
    "help")
        echo "Usage: $0 [validate|install|clean|help]"
        echo "  validate (default): Run all validations"
        echo "  install: Install dependencies for all projects" 
        echo "  clean: Clean cache files for all projects"
        echo "  help: Show this help message"
        ;;
    *)
        log_error "Unknown command: $1"
        log_info "Use '$0 help' for usage information"
        exit 1
        ;;
esac