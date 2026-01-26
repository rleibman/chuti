# Nginx Configuration for Chuti

This directory contains nginx configuration files:

- **www.chuti.fun** - Main application (frontend + backend API)

## Architecture

```
┌──────────────────────┐
│  www.chuti.fun       │
│  (Application)       │
└─────────┬────────────┘ 
          │                              │
                ▼
┌──────────────────────┐
│  Nginx (Port 443)    │
│  Static + Proxy      │
└──────────┬───────────┘
           │
                 ▼
┌──────────────────────┐
│  ZIO HTTP (Port 8079)│
│  Backend Server      │
└──────────────────────┘
```

## Files

- `www.chuti.fun.conf` - Application nginx configuration
- `setup-nginx.sh` - Helper script to install nginx configs on server
- `README.md` - This file

## Installation

### Prerequisites

1. **Ubuntu server** with nginx installed:
   ```bash
   sudo apt update
   sudo apt install nginx
   ```

2. **SSL Certificates** - Use Let's Encrypt (certbot):
   ```bash
   sudo apt install certbot python3-certbot-nginx

   # Get certificates for domains
   sudo certbot --nginx -d www.chuti.fun -d chuti.fun
   ```

3. **DNS Configuration** - Ensure both domains point to your server:
   ```
  www.chuti.fun  A     YOUR_SERVER_IP
   chuti.fun      A     YOUR_SERVER_IP
   ```

### Manual Installation

1. Copy configuration files to nginx:
   ```bash
   sudo cp www.chuti.fun.conf /etc/nginx/sites-available/
   ```

2. Enable the sites:
   ```bash
   sudo ln -s /etc/nginx/sites-available/www.chuti.fun.conf /etc/nginx/sites-enabled/
   ```

3. Test nginx configuration:
   ```bash
   sudo nginx -t
   ```

4. Reload nginx:
   ```bash
   sudo systemctl reload nginx
   ```

### Automated Installation

Use the provided setup script:

```bash
./setup-nginx.sh
```

Or run it remotely during deployment (see deploy.sh).

## Directory Structure on Server

```
/data/www/
├── www.chuti.fun/
    └── html/              # Application frontend (built by sbt dist)
        ├── index.html
        ├── js/
        ├── css/
        └── ...
```

## Backend Configuration

The backend server (ZIO HTTP) should be configured to:

1. Listen on `localhost:8079` (not publicly accessible)
2. Trust the X-Forwarded-* headers from nginx
3. Use the `CHUTI_HTTP_HOST` environment variable:
   ```bash
   CHUTI_HTTP_HOST=0.0.0.0  # or localhost
   CHUTI_HTTP_PORT=8079
   ```

## SSL Certificate Renewal

Certbot will automatically renew certificates. Test renewal with:

```bash
sudo certbot renew --dry-run
```

Certificates are renewed automatically via systemd timer.

## Troubleshooting

### Check nginx status
```bash
sudo systemctl status nginx
```

### View nginx logs
```bash
# Landing page
sudo tail -f /var/log/nginx/www.chuti.fun.access.log
sudo tail -f /var/log/nginx/www.chuti.fun.error.log

# Application
sudo tail -f /var/log/nginx/app.chuti.fun.access.log
sudo tail -f /var/log/nginx/app.chuti.fun.error.log
```

### Test configuration
```bash
sudo nginx -t
```

### Reload configuration
```bash
sudo systemctl reload nginx
```

## Security Considerations

- SSL/TLS enabled for both domains
- HTTP redirects to HTTPS
- Security headers configured (X-Frame-Options, CSP, etc.)
- Gzip compression enabled
- Client upload size limited to 50MB (app only)
- WebSocket support for real-time features

## Deployment Integration

The main deployment script (`deploy.sh`) will:
1. Build the landing page (`npm run build`)
2. Build the application (`sbt dist`)
3. Upload both to the server
4. Deploy landing page to `/data/www/www.chuti.fun/html`
5. Deploy application to `/data/www/app.chuti.fun/html` (via .deb package)
6. Restart the backend service

No nginx restart is needed as content is updated in place.
