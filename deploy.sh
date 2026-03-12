#!/bin/bash
set -e

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

echo "▶ Pulling latest changes..."
git pull origin main

echo "▶ Building frontend..."
cd venue-client
npm ci
npm run build
cd ..

echo "▶ Building backend..."
./mvnw clean package -DskipTests -q

echo "▶ Restarting service..."
sudo systemctl restart venue

echo "✅ Deploy complete!"
sudo systemctl status venue --no-pager
