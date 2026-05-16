#!/bin/bash

# Health Check Script for Render Deployment
# Validates that all services are running and responding

set -e

# Configuration
TIMEOUT=30
MAX_RETRIES=5

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Parse arguments
API_GATEWAY_URL="${1:-https://api-gateway.onrender.com}"
FRONTEND_URL="${2:-https://frontend.onrender.com}"

print_header() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

check_service() {
    local name=$1
    local url=$2
    local expected_code=${3:-200}
    
    echo -n "Checking $name... "
    
    local attempt=1
    while [ $attempt -le $MAX_RETRIES ]; do
        local http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "$url" 2>/dev/null || echo "000")
        
        if [ "$http_code" = "$expected_code" ] || ([ "$expected_code" = "200" ] && [[ "$http_code" =~ ^2[0-9]{2}$ ]]); then
            print_success "$name is healthy (HTTP $http_code)"
            return 0
        fi
        
        if [ $attempt -lt $MAX_RETRIES ]; then
            echo -n "."
            sleep 2
        fi
        
        attempt=$((attempt + 1))
    done
    
    print_error "$name is not responding (HTTP $http_code after $MAX_RETRIES attempts)"
    return 1
}

check_endpoint() {
    local name=$1
    local url=$2
    local method=${3:-GET}
    
    echo -n "Checking $name... "
    
    local http_code=$(curl -s -X $method -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "$url" 2>/dev/null || echo "000")
    
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
        print_success "$name responded with HTTP $http_code"
        return 0
    elif [[ "$http_code" =~ ^4[0-9]{2}$ ]]; then
        print_warning "$name responded with HTTP $http_code (expected for protected endpoints)"
        return 0
    else
        print_error "$name failed with HTTP $http_code"
        return 1
    fi
}

# Main health checks
print_header "Render Deployment Health Check"

echo "API Gateway URL: $API_GATEWAY_URL"
echo "Frontend URL: $FRONTEND_URL"
echo ""

failed=0

# Check Frontend
if ! check_service "Frontend" "$FRONTEND_URL" "200"; then
    failed=$((failed + 1))
fi

# Check API Gateway
if ! check_service "API Gateway" "$API_GATEWAY_URL/health" "200"; then
    failed=$((failed + 1))
fi

# Check Auth Endpoint (should be 400 without credentials, but responsive)
if ! check_endpoint "Auth Service" "$API_GATEWAY_URL/api/auth/me" "GET"; then
    failed=$((failed + 1))
fi

# Check Candidates Endpoint
if ! check_endpoint "Candidates Service" "$API_GATEWAY_URL/api/candidates" "GET"; then
    failed=$((failed + 1))
fi

echo ""
print_header "Health Check Summary"

if [ $failed -eq 0 ]; then
    print_success "All services are healthy!"
    echo ""
    echo "Your Render deployment is ready:"
    echo "  • Frontend: $FRONTEND_URL"
    echo "  • API: $API_GATEWAY_URL"
    exit 0
else
    print_error "$failed service(s) are not healthy"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check Render Dashboard for deployment status"
    echo "  2. View service logs: https://dashboard.render.com"
    echo "  3. Verify environment variables are set correctly"
    echo "  4. Check service URLs match your actual Render services"
    exit 1
fi
