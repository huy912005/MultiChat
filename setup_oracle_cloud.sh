#!/bin/bash
# Oracle Cloud Deployment Script
# Chạy trên Oracle Cloud VM (Ubuntu 22.04)

set -e

echo "=========================================="
echo "  Chat App Setup trên Oracle Cloud VM"
echo "=========================================="

# Màu sắc
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ==================== FUNCTIONS ====================
log_info() {
    echo -e "${GREEN}✓${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

# ==================== BƯỚC 1: CẬP NHẬT HỆ THỐNG ====================
log_info "Cập nhật hệ thống..."
sudo apt update
sudo apt upgrade -y
log_info "Update xong!"

# ==================== BƯỚC 2: CÀI JAVA ====================
log_info "Cài JDK 17..."
sudo apt install -y openjdk-17-jdk
java -version
log_info "Java cài đặt thành công!"

# ==================== BƯỚC 3: CÀI MYSQL ====================
log_info "Cài MySQL Server..."
sudo apt install -y mysql-server

log_info "Khởi động MySQL..."
sudo systemctl start mysql
sudo systemctl enable mysql
log_info "MySQL chạy!"

# ==================== BƯỚC 4: TẠO DATABASE ====================
log_info "Tạo database ChatAppDB..."
sudo mysql -u root << EOF
CREATE DATABASE IF NOT EXISTS ChatAppDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'chatuser'@'localhost' IDENTIFIED BY 'SecurePassword123!';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';
FLUSH PRIVILEGES;
SHOW DATABASES;
EOF
log_info "Database tạo xong!"

# ==================== BƯỚC 5: CÀI NỘI DUNG ====================
log_info "Tạo thư mục app..."
mkdir -p ~/ChatApp
cd ~/ChatApp

log_info "Tạo startup script..."
cat > run_server.sh << 'SCRIPT'
#!/bin/bash
# Server startup script

# Logs directory
mkdir -p logs

# Run server with logs
java -jar ChatApp-TCP.jar > logs/server.log 2>&1 &

echo "Chat Server started (PID: $!)"
echo "Log: logs/server.log"

# Keep process running
wait
SCRIPT

chmod +x run_server.sh
log_info "Startup script tạo xong!"

# ==================== BƯỚC 6: SYSTEMD SERVICE (Optional) ====================
log_warn "Tạo systemd service (optional)..."
cat > chatapp-server.service << 'SERVICE'
[Unit]
Description=Chat App Server
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/ChatApp
ExecStart=/usr/bin/java -jar ChatApp-TCP.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

sudo mv chatapp-server.service /etc/systemd/system/
log_info "Service file tạo xong!"

# ==================== HƯỚNG DẪN TIẾP THEO ====================
cat << 'EOF'

========================================
  ✅ SETUP HOÀN TẤT!
========================================

📝 CÁC BƯỚC TIẾP THEO:

1. UPLOAD JAR FILE (từ máy local):
   scp dist\ChatApp-TCP.jar ubuntu@YOUR_ORACLE_IP:/home/ubuntu/ChatApp/

2. UPLOAD MYSQL CONNECTOR:
   scp mysql-connector-j-9.6.0.jar ubuntu@YOUR_ORACLE_IP:/home/ubuntu/ChatApp/

3. CHẠY SERVER (tuỳ chọn):
   
   A) Manual (kiểm tra log dễ):
      cd ~/ChatApp
      java -jar ChatApp-TCP.jar
   
   B) Systemd Service (auto-restart):
      sudo systemctl start chatapp-server
      sudo systemctl enable chatapp-server
      sudo systemctl status chatapp-server
   
   C) Daemon mode (chạy background):
      nohup java -jar ChatApp-TCP.jar > logs/server.log 2>&1 &

4. CHECK LOG:
   tail -f logs/server.log

5. ÔN LẠI FIREWALL (trong Oracle Cloud Console):
   - VCN → Security Lists → Add Ingress Rules
   - Port 5000 (Chat Server)
   - Port 3306 (MySQL - nếu access từ ngoài)
   - Port 22 (SSH)

6. CLIENT CONNECT:
   Thay IP address bên ChatController.java:
   String serverIP = "YOUR_ORACLE_PUBLIC_IP";
   int serverPort = 5000;

========================================
  📊 DATABASE INFO
========================================
Host: localhost
User: chatuser
Pass: SecurePassword123!
DB: ChatAppDB

========================================
  📚 USEFUL COMMANDS
========================================

# View logs
journalctl -u chatapp-server -f

# Kill running Java process
pkill -f "java -jar"

# Check open ports
sudo ss -tlnp | grep 5000

# MySQL CLI
mysql -u chatuser -p ChatAppDB

# MySQL command line
mysql -u root -e "SHOW DATABASES;"

# Reboot VM
sudo reboot

========================================
EOF

log_info "Setup script hoàn tất!"
log_warn "Vui lòng làm theo các bước trên để deploy app"
