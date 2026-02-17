#!/bin/bash
# Nginx Setup Script for Chuti
# Installs and configures nginx for both www.chuti.fun
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Chuti Nginx Setup${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if running as root or with sudo
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Please run with sudo${NC}"
    exit 1
fi

# Check if nginx is installed
if ! command -v nginx &> /dev/null; then
    echo -e "${YELLOW}Nginx not installed. Installing...${NC}"
    apt update
    apt install -y nginx
    echo -e "${GREEN}✓ Nginx installed${NC}"
else
    echo -e "${GREEN}✓ Nginx already installed${NC}"
fi

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Copy configuration files
echo -e "${BLUE}Installing nginx configuration files...${NC}"

cp "${SCRIPT_DIR}/www.chuti.fun.conf" /etc/nginx/sites-available/

echo -e "${GREEN}✓ Configuration files copied${NC}"

# Enable sites
echo -e "${BLUE}Enabling sites...${NC}"

ln -sf /etc/nginx/sites-available/www.chuti.fun.conf /etc/nginx/sites-enabled/

echo -e "${GREEN}✓ Sites enabled${NC}"

# Create web directories if they don't exist
echo -e "${BLUE}Creating web directories...${NC}"

mkdir -p /data/www/www.chuti.fun/html
chown -R www-data:www-data /data/www

echo -e "${GREEN}✓ Web directories created${NC}"

# Test nginx configuration
echo -e "${BLUE}Testing nginx configuration...${NC}"

if nginx -t; then
    echo -e "${GREEN}✓ Nginx configuration is valid${NC}"
else
    echo -e "${RED}✗ Nginx configuration has errors${NC}"
    echo -e "${YELLOW}Fix the errors and run: sudo nginx -t${NC}"
    exit 1
fi

# Check if SSL certificates exist
echo ""
echo -e "${BLUE}Checking SSL certificates...${NC}"

WWW_CERT="/etc/letsencrypt/live/www.chuti.fun/fullchain.pem"

if [ ! -f "$WWW_CERT" ]; then
    echo -e "${YELLOW}⚠ SSL certificates not found${NC}"
    echo ""
    echo "To obtain SSL certificates, run:"
    echo ""
    echo "  sudo apt install certbot python3-certbot-nginx"
    echo "  sudo certbot --nginx -d www.chuti.fun -d chuti.fun"
    echo ""
    echo -e "${YELLOW}Warning: Nginx will not start without valid SSL certificates${NC}"
    echo -e "${YELLOW}You can temporarily comment out SSL lines in the config files${NC}"
else
    echo -e "${GREEN}✓ SSL certificates found${NC}"

    # Reload nginx
    echo -e "${BLUE}Reloading nginx...${NC}"
    systemctl reload nginx
    echo -e "${GREEN}✓ Nginx reloaded${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Nginx Setup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Ensure DNS points to this server:"
echo "     www.chuti.fun  → $(hostname -I | awk '{print $1}')"
echo ""
echo "  2. Obtain SSL certificates (if not done):"
echo "     sudo certbot --nginx -d www.chuti.fun -d chuti.fun"
echo ""
echo "  3. Deploy your application:"
echo "     ./deploy.sh"
echo ""
echo "Useful commands:"
echo "  sudo systemctl status nginx"
echo "  sudo nginx -t"
echo "  sudo systemctl reload nginx"
echo "  sudo tail -f /var/log/nginx/*.log"
