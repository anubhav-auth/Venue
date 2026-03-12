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

echo "▶ Building and restarting backend (Docker)..."
cd venue
docker compose up --build -d
cd ..

echo "✅ Deploy complete!"
docker compose -f venue/docker-compose.yml ps