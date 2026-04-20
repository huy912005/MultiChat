#!/bin/bash
# QUICK FIX - One command to rule them all!
# Chạy: curl -sSL https://raw.githubusercontent.com/[YOUR_REPO]/fix-mysql-quick.sh | bash
# Hoặc chạy file này trực tiếp: bash fix-mysql-quick.sh

set -e  # Exit on error

echo "🚀 CHAT APP QUICK FIX - Starting..."
echo "=========================================="

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Start MySQL
echo -e "${YELLOW}[1/7] Starting MySQL...${NC}"
sudo systemctl start mysql 2>/dev/null || true
sudo systemctl enable mysql 2>/dev/null || true
sleep 2

# Step 2: Create database and user
echo -e "${YELLOW}[2/7] Creating database and user...${NC}"
sudo mysql -u root << 'MYSQL_COMMANDS' 2>/dev/null || true
CREATE DATABASE IF NOT EXISTS ChatAppDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'chatuser'@'localhost' IDENTIFIED BY 'ChatApp@123';
CREATE USER IF NOT EXISTS 'chatuser'@'%' IDENTIFIED BY 'ChatApp@123';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'%';
FLUSH PRIVILEGES;
MYSQL_COMMANDS

# Step 3: Test MySQL connection
echo -e "${YELLOW}[3/7] Testing MySQL connection...${NC}"
if mysql -u chatuser -p'ChatApp@123' -e "SELECT 1;" 2>/dev/null; then
    echo -e "${GREEN}✅ MySQL connection OK${NC}"
else
    echo -e "${RED}❌ MySQL connection failed${NC}"
    exit 1
fi

# Step 4: Set environment variables
echo -e "${YELLOW}[4/7] Setting environment variables...${NC}"
if ! grep -q "CHAT_DB_URL" ~/.bashrc; then
    cat >> ~/.bashrc << 'ENVFILE'

# Chat App Database
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="ChatApp@123"
export CHAT_SERVER_PORT="5000"
export CHAT_SERVER_HEADLESS="true"
ENVFILE
fi
source ~/.bashrc

# Step 5: Kill old process
echo -e "${YELLOW}[5/7] Stopping old Chat App processes...${NC}"
pkill -f "Chat.server.Main" 2>/dev/null || true
sleep 1

# Step 6: Restart Chat App
echo -e "${YELLOW}[6/7] Starting Chat App server...${NC}"
cd /root/MultiChat
nohup java -cp "build/classes:dist/lib/mysql-connector-j-9.6.0.jar" Chat.server.Main > server.log 2>&1 &
sleep 2

# Step 7: Verify
echo -e "${YELLOW}[7/7] Verifying server is running...${NC}"
if pgrep -f "Chat.server.Main" > /dev/null; then
    echo -e "${GREEN}✅ Chat App is running!${NC}"
    echo -e "${GREEN}✅ Listening on 0.0.0.0:5000${NC}"
    echo ""
    echo "📝 Recent logs:"
    tail -5 server.log
else
    echo -e "${RED}❌ Chat App failed to start${NC}"
    echo "📝 Error logs:"
    tail -20 server.log
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}✅ DONE! Chat App is ready for clients.${NC}"
echo "=========================================="
echo ""
echo "📊 Connection Info:"
echo "  - Server IP: $(hostname -I | awk '{print $1}')"
echo "  - Port: 5000"
echo "  - Database: ChatAppDB"
echo "  - DB User: chatuser"
echo ""
echo "🔗 Connect clients with:"
echo "  java -cp dist/ChatApp-TCP.jar Chat.client.Main $(hostname -I | awk '{print $1}') 5000"
echo ""
echo "📋 View logs: tail -f server.log"
echo "❌ Stop server: pkill -f Chat.server.Main"
