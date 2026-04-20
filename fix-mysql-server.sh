#!/bin/bash
# Fix MySQL connection error - Chạy trên Digital Ocean server

echo "=========================================="
echo "FIXING MYSQL CONNECTION FOR CHAT APP"
echo "=========================================="

# 1. Check MySQL status
echo -e "\n[1] Checking MySQL status..."
sudo systemctl status mysql

# 2. Start MySQL if not running
echo -e "\n[2] Starting MySQL if needed..."
sudo systemctl start mysql
sudo systemctl enable mysql

# 3. Wait for MySQL to start
echo "Waiting for MySQL to start..."
sleep 3

# 4. Check if MySQL is listening on 3306
echo -e "\n[3] Checking MySQL port..."
netstat -tuln | grep 3306 || echo "MySQL not listening yet!"

# 5. Create database and user
echo -e "\n[4] Creating database and user..."
sudo mysql -u root << EOF
-- Create database
CREATE DATABASE IF NOT EXISTS ChatAppDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user with password
CREATE USER IF NOT EXISTS 'chatuser'@'localhost' IDENTIFIED BY 'ChatApp@123';

-- Grant permissions
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';

-- Also allow remote connections if needed
CREATE USER IF NOT EXISTS 'chatuser'@'%' IDENTIFIED BY 'ChatApp@123';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'%';

FLUSH PRIVILEGES;

-- Verify
SELECT user, host FROM mysql.user WHERE user='chatuser';
SHOW GRANTS FOR 'chatuser'@'localhost';
EOF

# 6. Test connection
echo -e "\n[5] Testing MySQL connection..."
mysql -u chatuser -p'ChatApp@123' -e "SELECT 1;" 2>&1 && echo "✅ Connection successful!" || echo "❌ Connection failed!"

# 7. Set environment variables for Chat App
echo -e "\n[6] Setting environment variables..."
cat >> ~/.bashrc << 'ENVFILE'

# Chat App Database Environment Variables
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="ChatApp@123"
export CHAT_SERVER_PORT="5000"
export CHAT_SERVER_HEADLESS="true"
ENVFILE

source ~/.bashrc

echo -e "\n[7] Verifying environment variables..."
echo "CHAT_DB_URL=$CHAT_DB_URL"
echo "CHAT_DB_USER=$CHAT_DB_USER"
echo "CHAT_SERVER_PORT=$CHAT_SERVER_PORT"

# 8. Kill any existing Java process
echo -e "\n[8] Killing old Java processes..."
pkill -f "java.*Chat.server.Main" || echo "No old process found"
sleep 2

# 9. Restart Chat App
echo -e "\n[9] Restarting Chat App..."
cd /root/MultiChat
java -cp "build/classes:dist/lib/mysql-connector-j-9.6.0.jar" Chat.server.Main &
SERVER_PID=$!
echo "Chat App started with PID: $SERVER_PID"

# 10. Monitor logs
echo -e "\n[10] Monitoring for errors (15 seconds)..."
sleep 15

# Check if process still running
if ps -p $SERVER_PID > /dev/null; then
    echo "✅ Chat Server is running successfully!"
    echo "Listening on: 0.0.0.0:5000"
else
    echo "❌ Chat Server failed to start. Check errors below:"
    tail -50 nohup.out
fi

echo -e "\n=========================================="
echo "DONE! Chat App should be running now."
echo "=========================================="
