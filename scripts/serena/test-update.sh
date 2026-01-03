#!/bin/bash
#
# Serena Symbol Update System Test Script
#
# This script tests various scenarios of the Serena symbol update system
# to ensure it works correctly before deploying to production use.
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
UPDATE_SCRIPT="$SCRIPT_DIR/update-symbols.py"

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Utility functions
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

run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_exit_code="${3:-0}"

    TESTS_RUN=$((TESTS_RUN + 1))

    log_info "Running test: $test_name"

    if eval "$test_command"; then
        local exit_code=$?
        if [ $exit_code -eq $expected_exit_code ]; then
            TESTS_PASSED=$((TESTS_PASSED + 1))
            log_success "Test passed: $test_name"
        else
            TESTS_FAILED=$((TESTS_FAILED + 1))
            log_error "Test failed: $test_name (exit code: $exit_code, expected: $expected_exit_code)"
        fi
    else
        TESTS_FAILED=$((TESTS_FAILED + 1))
        log_error "Test failed: $test_name (command execution failed)"
    fi

    echo ""
}

# Test cases
test_script_exists() {
    [ -f "$UPDATE_SCRIPT" ]
}

test_script_executable() {
    [ -x "$UPDATE_SCRIPT" ]
}

test_help_option() {
    python3 "$UPDATE_SCRIPT" --help >/dev/null 2>&1
}

test_dry_run_all() {
    python3 "$UPDATE_SCRIPT" --all --dry-run --verbose
}

test_dry_run_single_project() {
    python3 "$UPDATE_SCRIPT" --project project-interface-cli --dry-run
}

test_dry_run_single_language() {
    python3 "$UPDATE_SCRIPT" --language python --dry-run
}

test_dry_run_with_deps() {
    python3 "$UPDATE_SCRIPT" --project project-interface-cli --with-deps --dry-run
}

test_dry_run_with_memories() {
    python3 "$UPDATE_SCRIPT" --all --with-memories --dry-run
}

test_dry_run_changed_only() {
    python3 "$UPDATE_SCRIPT" --changed-only --dry-run
}

test_invalid_project() {
    python3 "$UPDATE_SCRIPT" --project invalid-project --dry-run
}

test_invalid_language() {
    python3 "$UPDATE_SCRIPT" --language invalid-language --dry-run
}

test_conflicting_options() {
    python3 "$UPDATE_SCRIPT" --all --project project-interface-cli --dry-run
}

test_git_hooks_exist() {
    [ -f "$PROJECT_ROOT/.git/hooks/post-commit" ] && [ -f "$PROJECT_ROOT/.git/hooks/post-merge" ]
}

test_git_hooks_executable() {
    [ -x "$PROJECT_ROOT/.git/hooks/post-commit" ] && [ -x "$PROJECT_ROOT/.git/hooks/post-merge" ]
}

test_serena_cache_structure() {
    [ -d "$PROJECT_ROOT/.serena/cache" ] && \
    [ -d "$PROJECT_ROOT/.serena/cache/python" ] && \
    [ -d "$PROJECT_ROOT/.serena/cache/typescript" ] && \
    [ -d "$PROJECT_ROOT/.serena/cache/kotlin" ]
}

# Main test runner
main() {
    echo "======================================"
    echo "  Serena Symbol Update System Tests"
    echo "======================================"
    echo ""

    log_info "Project root: $PROJECT_ROOT"
    log_info "Update script: $UPDATE_SCRIPT"
    echo ""

    # Parse command line arguments
    local test_case="${1:-all}"

    if [[ "$test_case" == "--help" ]]; then
        echo "Usage: $0 [TEST_CASE]"
        echo ""
        echo "Available test cases:"
        echo "  all           Run all tests (default)"
        echo "  basic         Run basic functionality tests"
        echo "  dry-run       Run dry-run tests only"
        echo "  git-hooks     Test Git hooks installation"
        echo "  structure     Test directory structure"
        echo "  --help        Show this help"
        echo ""
        exit 0
    fi

    # Run tests based on specified test case
    case "$test_case" in
        "all")
            log_info "Running all tests..."
            echo ""

            # Basic tests
            run_test "Update script exists" "test_script_exists"
            run_test "Update script is executable" "test_script_executable"
            run_test "Help option works" "test_help_option"

            # Dry-run tests
            run_test "Dry-run all projects" "test_dry_run_all"
            run_test "Dry-run single project" "test_dry_run_single_project"
            run_test "Dry-run single language" "test_dry_run_single_language"
            run_test "Dry-run with dependencies" "test_dry_run_with_deps"
            run_test "Dry-run with memories" "test_dry_run_with_memories"
            run_test "Dry-run changed only" "test_dry_run_changed_only"

            # Error handling tests
            run_test "Invalid project (should fail)" "test_invalid_project" 2
            run_test "Invalid language (should fail)" "test_invalid_language" 2
            run_test "Conflicting options (should fail)" "test_conflicting_options" 2

            # Infrastructure tests
            run_test "Git hooks exist" "test_git_hooks_exist"
            run_test "Git hooks are executable" "test_git_hooks_executable"
            run_test "Serena cache structure" "test_serena_cache_structure"
            ;;

        "basic")
            log_info "Running basic tests..."
            echo ""
            run_test "Update script exists" "test_script_exists"
            run_test "Update script is executable" "test_script_executable"
            run_test "Help option works" "test_help_option"
            ;;

        "dry-run")
            log_info "Running dry-run tests..."
            echo ""
            run_test "Dry-run all projects" "test_dry_run_all"
            run_test "Dry-run single project" "test_dry_run_single_project"
            run_test "Dry-run single language" "test_dry_run_single_language"
            run_test "Dry-run with dependencies" "test_dry_run_with_deps"
            run_test "Dry-run with memories" "test_dry_run_with_memories"
            ;;

        "git-hooks")
            log_info "Running Git hooks tests..."
            echo ""
            run_test "Git hooks exist" "test_git_hooks_exist"
            run_test "Git hooks are executable" "test_git_hooks_executable"
            ;;

        "structure")
            log_info "Running structure tests..."
            echo ""
            run_test "Serena cache structure" "test_serena_cache_structure"
            ;;

        *)
            log_error "Unknown test case: $test_case"
            log_info "Use '$0 --help' for usage information"
            exit 1
            ;;
    esac

    # Print test results
    echo "======================================"
    echo "              Test Results"
    echo "======================================"
    echo ""
    echo -e "Tests run:    ${BLUE}$TESTS_RUN${NC}"
    echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "Tests failed: ${RED}$TESTS_FAILED${NC}"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        log_success "All tests passed! ✅"
        echo ""
        echo "The Serena Symbol Update System is ready to use."
        echo ""
        echo "Quick start:"
        echo "  python3 $UPDATE_SCRIPT --all --dry-run    # Test update"
        echo "  python3 $UPDATE_SCRIPT --all              # Run update"
        echo ""
        exit 0
    else
        log_error "Some tests failed. ❌"
        echo ""
        echo "Please fix the issues before using the system."
        echo ""
        exit 1
    fi
}

# Handle script interruption
trap 'echo ""; log_warning "Test interrupted by user"; exit 130' INT

# Run main function
main "$@"