#!/bin/bash
# Automated madonctl compatibility test script
# Prerequisites:
#   - madonctl installed and configured with a test account
#   - moth server running with test data
#
# Usage: ./bin/test-madonctl-compatibility.sh [server_url]
#
# Environment variables:
#   MADONCTL_CONFIG - path to madonctl config (optional)
#   TEST_ACCOUNT_ID - account ID to use for relationship tests
#   TEST_STATUS_ID  - status ID to use for status tests (will create one if not set)

set -e

SERVER_URL="${1:-http://localhost:3333}"
PASSED=0
FAILED=0
SKIPPED=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED++))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED++))
}

log_skip() {
    echo -e "${YELLOW}[SKIP]${NC} $1"
    ((SKIPPED++))
}

log_info() {
    echo -e "[INFO] $1"
}

# Check if madonctl is available
check_madonctl() {
    if ! command -v madonctl &> /dev/null; then
        echo "madonctl not found. Please install it first."
        echo "See: https://github.com/McKael/madonctl"
        exit 1
    fi
}

# Test helper - runs command and checks for success
test_command() {
    local description="$1"
    local cmd="$2"

    if output=$(eval "$cmd" 2>&1); then
        log_pass "$description"
        echo "$output"
        return 0
    else
        log_fail "$description"
        echo "$output"
        return 1
    fi
}

# Test helper - runs command expecting it might fail (for cleanup operations)
test_command_optional() {
    local description="$1"
    local cmd="$2"

    if output=$(eval "$cmd" 2>&1); then
        log_pass "$description"
    else
        log_skip "$description (expected or optional)"
    fi
}

echo "========================================"
echo "Madonctl Compatibility Test Suite"
echo "Server: $SERVER_URL"
echo "========================================"
echo ""

check_madonctl

# ========================================
# Phase 1: Critical Status Operations
# ========================================
echo ""
echo "--- Phase 1: Critical Status Operations ---"

# Create a test status first
log_info "Creating test status..."
if TEST_STATUS_OUTPUT=$(madonctl toot "Test status for madonctl compatibility $(date +%s)" 2>&1); then
    # Extract status ID from output (format varies by madonctl version)
    TEST_STATUS_ID=$(echo "$TEST_STATUS_OUTPUT" | grep -oE '[0-9a-f]{24}' | head -1)
    if [ -n "$TEST_STATUS_ID" ]; then
        log_pass "Created test status: $TEST_STATUS_ID"
    else
        log_info "Could not extract status ID, using output as reference"
        echo "$TEST_STATUS_OUTPUT"
    fi
else
    log_fail "Failed to create test status"
    echo "$TEST_STATUS_OUTPUT"
fi

if [ -n "$TEST_STATUS_ID" ]; then
    # GET /api/v1/statuses/{id}
    test_command "GET status by ID" "madonctl status show $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/favourite
    test_command "Favourite status" "madonctl status favourite $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/unfavourite
    test_command "Unfavourite status" "madonctl status unfavourite $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/reblog
    test_command "Reblog (boost) status" "madonctl status boost $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/unreblog
    test_command "Unreblog (unboost) status" "madonctl status unboost $TEST_STATUS_ID"

    # GET /api/v1/statuses/{id}/context
    test_command "Get status context" "madonctl status context $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/bookmark
    test_command "Bookmark status" "madonctl status bookmark $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/unbookmark
    test_command "Unbookmark status" "madonctl status unbookmark $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/pin
    test_command "Pin status" "madonctl status pin $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/unpin
    test_command "Unpin status" "madonctl status unpin $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/mute
    test_command "Mute conversation" "madonctl status mute $TEST_STATUS_ID"

    # POST /api/v1/statuses/{id}/unmute
    test_command "Unmute conversation" "madonctl status unmute $TEST_STATUS_ID"
else
    log_skip "Status tests (no test status ID)"
fi

# GET /api/v1/favourites
test_command "Get favourites list" "madonctl accounts favourites"

# GET /api/v1/bookmarks
test_command "Get bookmarks list" "madonctl accounts bookmarks"

# ========================================
# Phase 2: Account Relationship Management
# ========================================
echo ""
echo "--- Phase 2: Account Relationship Management ---"

# Get current user info to avoid self-operations
CURRENT_USER=$(madonctl accounts show --account-id "" 2>/dev/null | grep -oE '"id":"[^"]+' | head -1 | cut -d'"' -f4)

if [ -n "$TEST_ACCOUNT_ID" ] && [ "$TEST_ACCOUNT_ID" != "$CURRENT_USER" ]; then
    # POST /api/v1/accounts/{id}/block
    test_command "Block account" "madonctl accounts block --account-id $TEST_ACCOUNT_ID"

    # POST /api/v1/accounts/{id}/unblock
    test_command "Unblock account" "madonctl accounts unblock --account-id $TEST_ACCOUNT_ID"

    # POST /api/v1/accounts/{id}/mute
    test_command "Mute account" "madonctl accounts mute --account-id $TEST_ACCOUNT_ID"

    # POST /api/v1/accounts/{id}/unmute
    test_command "Unmute account" "madonctl accounts unmute --account-id $TEST_ACCOUNT_ID"
else
    log_skip "Account block/mute tests (set TEST_ACCOUNT_ID env var)"
fi

# GET /api/v1/blocks
test_command "Get blocked accounts" "madonctl accounts blocks"

# GET /api/v1/mutes
test_command "Get muted accounts" "madonctl accounts mutes"

# ========================================
# Phase 3: Timeline Extensions
# ========================================
echo ""
echo "--- Phase 3: Timeline Extensions ---"

# GET /api/v1/timelines/tag/{hashtag}
test_command "Get hashtag timeline" "madonctl timeline --hashtag test --limit 5"

# GET /api/v1/lists
test_command "Get lists" "madonctl lists"

# Create a test list
log_info "Creating test list..."
if LIST_OUTPUT=$(madonctl lists create "MadonctlTest$(date +%s)" 2>&1); then
    TEST_LIST_ID=$(echo "$LIST_OUTPUT" | grep -oE '[0-9a-f]{24}' | head -1)
    if [ -n "$TEST_LIST_ID" ]; then
        log_pass "Created test list: $TEST_LIST_ID"

        # GET /api/v1/lists/{id}
        test_command "Get list by ID" "madonctl lists show --list-id $TEST_LIST_ID"

        # PUT /api/v1/lists/{id}
        test_command "Update list" "madonctl lists update --list-id $TEST_LIST_ID --title 'UpdatedTestList'"

        # DELETE /api/v1/lists/{id}
        test_command "Delete list" "madonctl lists delete --list-id $TEST_LIST_ID"
    else
        log_info "Could not extract list ID"
    fi
else
    log_fail "Failed to create test list"
fi

# ========================================
# Phase 4: Notification Management
# ========================================
echo ""
echo "--- Phase 4: Notification Management ---"

# GET /api/v1/notifications
test_command "Get notifications" "madonctl notifications --limit 5"

# POST /api/v1/notifications/clear (be careful - this clears all!)
# Commenting out by default to avoid data loss
# test_command "Clear notifications" "madonctl notifications --clear"
log_skip "Clear notifications (destructive - run manually if needed)"

# ========================================
# Cleanup
# ========================================
echo ""
echo "--- Cleanup ---"

if [ -n "$TEST_STATUS_ID" ]; then
    test_command_optional "Delete test status" "madonctl status delete $TEST_STATUS_ID"
fi

# ========================================
# Summary
# ========================================
echo ""
echo "========================================"
echo "Test Summary"
echo "========================================"
echo -e "${GREEN}Passed:${NC}  $PASSED"
echo -e "${RED}Failed:${NC}  $FAILED"
echo -e "${YELLOW}Skipped:${NC} $SKIPPED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}All executed tests passed!${NC}"
    exit 0
fi
