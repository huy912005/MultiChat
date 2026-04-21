#!/bin/bash
# Script to push code to VPS server via SSH with password authentication

VPS_USER="root"
VPS_HOST="159.65.134.130"
VPS_PORT="22"
VPS_REPO_PATH="/root/chat-repo"
VPS_PASSWORD="Pham9Minh1Huy"

echo "======================================"
echo "Pushing to VPS server..."
echo "======================================"

# Install sshpass if not exists (for macOS/Linux)
if ! command -v sshpass &> /dev/null; then
    echo "Installing sshpass..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install sshpass
    else
        sudo apt-get install -y sshpass
    fi
fi

# Remove old remote and add new
git remote rm vps 2>/dev/null
git remote add vps "ssh://${VPS_USER}@${VPS_HOST}:${VPS_PORT}${VPS_REPO_PATH}"

# Push using sshpass
sshpass -p "${VPS_PASSWORD}" git push -u vps main --force

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Successfully pushed to VPS!"
else
    echo ""
    echo "✗ Failed to push to VPS"
    exit 1
fi
