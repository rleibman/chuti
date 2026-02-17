#!/bin/bash
# Chuti Deployment Script
# Builds and deploys the Chuti application to an Ubuntu server
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/.deploy-config"
DRY_RUN=false
SKIP_TESTS=false
BACKUP_OLD=true

# Parse command line arguments (before loading config so --help works)
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --no-backup)
            BACKUP_OLD=false
            shift
            ;;
        --version)
            SPECIFIC_VERSION="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --dry-run        Build but don't deploy"
            echo "  --skip-tests     Skip running tests before build"
            echo "  --no-backup      Don't backup old package on server"
            echo "  --version VER    Deploy specific version (overrides git version)"
            echo "  --help           Show this help message"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Load configuration
if [ -f "$CONFIG_FILE" ]; then
    echo -e "${BLUE}Loading configuration from ${CONFIG_FILE}${NC}"
    source "$CONFIG_FILE"
else
    echo -e "${RED}Error: Configuration file not found: ${CONFIG_FILE}${NC}"
    echo -e "${YELLOW}Please create .deploy-config from .deploy-config.example${NC}"
    echo "cp .deploy-config.example .deploy-config"
    echo "# Edit .deploy-config with your server details"
    exit 1
fi

# Validate required configuration
if [ -z "$DEPLOY_SERVER_HOST" ] || [ -z "$DEPLOY_SERVER_USER" ]; then
    echo -e "${RED}Error: Required configuration missing${NC}"
    echo "Please set DEPLOY_SERVER_HOST and DEPLOY_SERVER_USER in .deploy-config"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Chuti Deployment${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "${BLUE}Target: ${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST}${NC}"
echo -e "${BLUE}Mode: $([ "$DRY_RUN" = true ] && echo "DRY RUN" || echo "DEPLOY")${NC}"
echo ""

# Pre-flight checks
echo -e "${BLUE}Running pre-flight checks...${NC}"

# Check SSH connectivity
if ! ssh -o ConnectTimeout=5 -o BatchMode=yes "${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST}" "echo 2>&1" >/dev/null 2>&1; then
    echo -e "${RED}Error: Cannot connect to ${DEPLOY_SERVER_HOST}${NC}"
    echo "Please ensure:"
    echo "  1. Server is reachable"
    echo "  2. SSH key is configured (ssh-copy-id ${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST})"
    echo "  3. User has passwordless sudo configured"
    exit 1
fi
echo -e "${GREEN}  SSH connectivity OK${NC}"

# Check passwordless sudo
echo -e "${BLUE}Checking passwordless sudo access...${NC}"
if ! ssh -o BatchMode=yes "${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST}" "sudo -n true" 2>/dev/null; then
    echo -e "${RED}Error: User ${DEPLOY_SERVER_USER} does not have passwordless sudo access${NC}"
    echo ""
    echo "To fix this, run the following on the remote server:"
    echo ""
    echo "  sudo visudo"
    echo ""
    echo "Add this line at the end:"
    echo "  ${DEPLOY_SERVER_USER} ALL=(ALL) NOPASSWD: \\"
    echo "      /usr/bin/dpkg, \\"
    echo "      /usr/bin/systemctl, \\"
    echo "      /usr/bin/apt-get, \\"
    echo "      /usr/bin/journalctl, \\"
    echo "      /usr/bin/mkdir, \\"
    echo "      /usr/bin/chown, \\"
    echo "      /usr/bin/cp, \\"
    echo "      /usr/bin/rm, \\"
    echo "      /usr/bin/tar, \\"
    echo "      /usr/bin/true"
    echo ""
    echo "Or for full passwordless sudo (less secure, simpler):"
    echo "  ${DEPLOY_SERVER_USER} ALL=(ALL) NOPASSWD: ALL"
    echo ""
    echo "Then test with: ssh ${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST} 'sudo -n true'"
    exit 1
fi
echo -e "${GREEN}  Passwordless sudo OK${NC}"

# Check git status (warning only)
if [ -n "$(git status --porcelain)" ]; then
    echo -e "${YELLOW}Warning: You have uncommitted changes${NC}"
    echo -e "${YELLOW}Consider committing before deploying${NC}"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check if current commit is tagged
CURRENT_COMMIT=$(git rev-parse HEAD)
CURRENT_TAG=$(git describe --exact-match --tags "$CURRENT_COMMIT" 2>/dev/null || true)

if [ -z "$CURRENT_TAG" ]; then
    echo -e "${RED}Error: Current commit is not tagged${NC}"
    echo "Please tag the current commit before deploying:"
    echo "  git tag -a vX.Y.Z -m \"Version X.Y.Z\""
    echo "  git push origin vX.Y.Z"
    echo ""
    echo "Current commit: ${CURRENT_COMMIT:0:8}"
    echo "Recent tags:"
    git tag --sort=-creatordate | head -5
    exit 1
fi

echo -e "${GREEN}  Current commit tagged: ${CURRENT_TAG}${NC}"

# Run tests unless skipped
if [ "$SKIP_TESTS" = false ]; then
    echo -e "${BLUE}Running tests...${NC}"
    if ! sbt --error server/test; then
        echo -e "${RED}Tests failed! Aborting deployment.${NC}"
        exit 1
    fi
    echo -e "${GREEN}  All tests passed${NC}"
else
    echo -e "${YELLOW}  Skipping tests (--skip-tests)${NC}"
fi

# Build process
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Building Application${NC}"
echo -e "${BLUE}========================================${NC}"

# Build frontend (dist)
echo -e "${BLUE}Building frontend (optimized)...${NC}"
if ! sbt --error web/dist; then
    echo -e "${RED}Failed to build frontend${NC}"
    exit 1
fi
echo -e "${GREEN}  Frontend built${NC}"

# Create Debian package (includes both server and dist/)
echo -e "${BLUE}Creating Debian package (server + web content)...${NC}"
if ! sbt --error server/debian:packageBin; then
    echo -e "${RED}Failed to create Debian package${NC}"
    exit 1
fi

# Find the package
PACKAGE=$(ls -t "${SCRIPT_DIR}/server/target/"*.deb 2>/dev/null | head -1)
if [ -z "$PACKAGE" ]; then
    echo -e "${RED}Error: No .deb package found in server/target/${NC}"
    exit 1
fi

PACKAGE_BASENAME=$(basename "$PACKAGE")
PACKAGE_SIZE=$(du -h "$PACKAGE" | cut -f1)

echo -e "${GREEN}  Package created: ${PACKAGE_BASENAME} (${PACKAGE_SIZE})${NC}"

# Extract version from package name
VERSION=$(echo "$PACKAGE_BASENAME" | grep -oP 'chuti_\K[^_]+')
echo -e "${BLUE}Version: ${VERSION}${NC}"

# Dry run ends here
if [ "$DRY_RUN" = true ]; then
    echo ""
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}  DRY RUN - No deployment performed${NC}"
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${GREEN}Package ready: ${PACKAGE}${NC}"
    echo "To deploy, run: $0 (without --dry-run)"
    exit 0
fi

# Deployment
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Deploying to Server${NC}"
echo -e "${BLUE}========================================${NC}"

# Upload package to server
echo -e "${BLUE}Uploading package to server...${NC}"
if ! scp "$PACKAGE" "${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST}:/tmp/"; then
    echo -e "${RED}Failed to upload package${NC}"
    exit 1
fi
echo -e "${GREEN}  Package uploaded${NC}"

# Deploy on server
echo -e "${BLUE}Installing on server...${NC}"
ssh "${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST}" bash <<EOF
set -e

# Backup old package if it exists
if [ "$BACKUP_OLD" = true ] && [ -f "/opt/chuti/current.deb" ]; then
    echo "Backing up current package..."
    sudo cp /opt/chuti/current.deb /opt/chuti/previous.deb
fi

# Ensure web content directory exists
echo "Ensuring web content directory exists..."
sudo mkdir -p /data/www/www.chuti.fun/html
sudo chown -R chuti:chuti /data/www/www.chuti.fun

# Ensure log directory exists
echo "Ensuring log directory exists..."
sudo mkdir -p /var/log/chuti-server
sudo chown chuti:chuti /var/log/chuti-server

# Install new package
echo "Installing chuti package..."
sudo dpkg -i /tmp/$PACKAGE_BASENAME || {
    echo "Attempting to fix dependencies..."
    sudo apt-get install -f -y
}

# Save current package for future rollback
sudo mkdir -p /opt/chuti
sudo cp /tmp/$PACKAGE_BASENAME /opt/chuti/current.deb

# Restart service
echo "Restarting chuti-server service..."
sudo systemctl restart chuti-server

# Wait a moment for service to start
sleep 3

# Check service status
if sudo systemctl is-active --quiet chuti-server; then
    echo "Service started successfully"
    sudo systemctl status chuti-server --no-pager --lines=5
else
    echo "Service failed to start!"
    echo "Recent logs:"
    sudo journalctl -u chuti-server --no-pager --lines=20
    exit 1
fi

# Cleanup
rm /tmp/$PACKAGE_BASENAME

echo ""
echo "Deployment completed successfully!"
echo "Version: $VERSION"
echo "App: www.chuti.fun"
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Deployment Successful!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo -e "${BLUE}Version deployed: ${VERSION}${NC}"
    echo -e "${BLUE}Server: ${DEPLOY_SERVER_HOST}${NC}"
    echo ""
    echo "To view live logs:"
    echo "  ssh ${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST} 'sudo journalctl -u chuti-server -f'"
    echo ""
    echo "To rollback to previous version:"
    echo "  ssh ${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST} 'sudo dpkg -i /opt/chuti/previous.deb && sudo systemctl restart chuti-server'"
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  Deployment Failed!${NC}"
    echo -e "${RED}========================================${NC}"
    echo "Check server logs:"
    echo "  ssh ${DEPLOY_SERVER_USER}@${DEPLOY_SERVER_HOST} 'sudo journalctl -u chuti-server -n 50'"
    exit 1
fi
