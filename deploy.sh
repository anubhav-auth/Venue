#!/bin/bash
set -e

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

echo "▶ Pulling latest changes..."
git pull origin main

# ── Frontend ────────────────────────────────────────────────────────────────
echo "▶ Building frontend..."
cd venue-client
pnpm install --frozen-lockfile
pnpm run build
cd ..

# ── Backend ─────────────────────────────────────────────────────────────────
echo "▶ Building backend..."
cd venue
./mvnw clean package -DskipTests -q
cd ..

echo "▶ Restarting service..."
sudo systemctl restart venue

echo "✅ Deploy complete!"
sudo systemctl status venue --no-pager
