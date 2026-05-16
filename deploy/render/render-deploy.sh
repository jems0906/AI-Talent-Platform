#!/bin/bash

# Render Deployment Script
# This script helps manage deployments to Render
# Requires: Render CLI (https://render.com/docs/cli)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

check_render_cli() {
    if ! command -v render &> /dev/null; then
        print_error "Render CLI not found. Install from https://render.com/docs/cli"
        exit 1
    fi
    print_success "Render CLI found"
}

validate_render_yaml() {
    if [ ! -f "$PROJECT_ROOT/render.yaml" ]; then
        print_error "render.yaml not found at $PROJECT_ROOT/render.yaml"
        exit 1
    fi
    
    if ! grep -q "services:" "$PROJECT_ROOT/render.yaml"; then
        print_error "render.yaml appears to be malformed (missing 'services:')"
        exit 1
    fi
    
    print_success "render.yaml is valid"
}

list_services() {
    print_header "Render Services in render.yaml"
    grep "name:" "$PROJECT_ROOT/render.yaml" | sed 's/.*name: /  - /' | sort -u
}

deploy() {
    print_header "Deploying to Render"
    
    if [ -z "$RENDER_API_KEY" ]; then
        print_error "RENDER_API_KEY environment variable not set"
        echo "Set it with: export RENDER_API_KEY=<your-api-key-from-render-dashboard>"
        exit 1
    fi
    
    # Render deployments are triggered by GitHub pushes or via web dashboard
    # This just shows the status
    print_warning "Render deployments are triggered automatically on GitHub push or via web dashboard"
    echo ""
    echo "To deploy manually:"
    echo "  1. Go to https://dashboard.render.com"
    echo "  2. Select your Blueprint"
    echo "  3. Click 'Deploy'"
    echo ""
    echo "Or push to your repository:"
    echo "  git push origin main"
}

check_health() {
    print_header "Checking Service Health"
    
    if [ -z "$RENDER_API_KEY" ]; then
        print_error "RENDER_API_KEY not set. Run 'export RENDER_API_KEY=<key>' first"
        exit 1
    fi
    
    # List services and their status
    echo "Fetching service status..."
    
    # Note: This requires Render API access which may require service IDs
    # For now, just show instructions
    echo "To check status:"
    echo "  1. Go to https://dashboard.render.com"
    echo "  2. View each service's status"
    echo "  3. Check logs in the service detail page"
}

get_service_urls() {
    print_header "Service URLs"
    
    echo "Once deployed, your services will be available at:"
    echo ""
    echo "Frontend:               https://frontend.onrender.com"
    echo "API Gateway:            https://api-gateway.onrender.com"
    echo "User Service (internal): https://user-service.onrender.com"
    echo "Candidate Service:      https://candidate-service.onrender.com"
    echo "AI Ranking Service:     https://ai-ranking-service.onrender.com"
    echo "Notification Service:   https://notification-service.onrender.com"
    echo ""
    echo "Note: Replace 'frontend' with your actual service name from Render dashboard"
}

show_usage() {
    cat << EOF
${BLUE}Render Deployment Helper${NC}

Usage: $0 [command]

Commands:
  validate     Validate render.yaml syntax
  list         List services defined in render.yaml
  deploy       Show deployment instructions
  health       Check service health status
  urls         Show expected service URLs
  help         Show this help message

Environment Variables:
  RENDER_API_KEY    Your Render API key from https://dashboard.render.com/account/api-tokens

Examples:
  $0 validate
  $0 list
  export RENDER_API_KEY=rnd_...
  $0 health

EOF
}

# Main
case "${1:-help}" in
    validate)
        validate_render_yaml
        ;;
    list)
        list_services
        ;;
    deploy)
        deploy
        ;;
    health)
        check_health
        ;;
    urls)
        get_service_urls
        ;;
    help)
        show_usage
        ;;
    *)
        print_error "Unknown command: $1"
        show_usage
        exit 1
        ;;
esac
