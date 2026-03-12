#!/bin/bash
set -e

export PNPM_HOME="/home/anubhavjaiswal2002/.local/share/pnpm"
export PATH="$PNPM_HOME:$PATH"

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

echo "▶ Pulling latest changes..."
git pull origin main

echo "▶ Building frontend..."
cd venue-client
pnpm install --frozen-lockfile
pnpm run build
cd ..

echo "▶ Building backend..."
cd venue
./mvnw clean package -DskipTests -q
cd ..

echo "▶ Restarting service..."
sudo systemctl restart venue

echo "✅ Deploy complete!"
sudo systemctl status venue --no-pager
