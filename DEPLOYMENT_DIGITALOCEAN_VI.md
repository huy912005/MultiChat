# Hướng Dẫn Deploy Chat App Lên Digital Ocean

## PHẦN 1: CHUẨN BỊ PROJECT (Máy Local)

### Bước 1: Build JAR Server
```bash
# Di vào thư mục project
cd f:\2025-2026\2026\LapTrinhMang\Multicast

# Build project
build.bat
```

JAR sẽ được tạo tại: `dist/ChatApp-TCP.jar` (hoặc tương tự)

### Bước 2: Push Code lên GitHub
```bash
# Khởi tạo git repo (nếu chưa có)
git init
git add .
git commit -m "Initial commit: Chat App for Digital Ocean deployment"

# Tạo repo trên GitHub.com trước, sau đó:
git remote add origin https://github.com/[YOUR_USERNAME]/[REPO_NAME].git
git branch -M main
git push -u origin main
```

---

## PHẦN 2: TẠO DROPLET TRÊN DIGITAL OCEAN

### Bước 1: Đăng ký Digital Ocean
1. Truy cập: https://www.digitalocean.com
2. Click "Sign up" → Dùng email hoặc GitHub account
3. Nhập thông tin thanh toán (yêu cầu có thẻ tín dụng)
4. Nhận $200 credit trong 60 ngày (nếu là user mới)

**Giá:** $5-6/tháng cho Droplet cơ bản (1GB RAM, 1 vCPU, 25GB SSD)

### Bước 2: Tạo Droplet
1. Vào **Create** → **Droplets**
2. Chọn **Image:** Ubuntu 22.04 LTS x64
3. Chọn **Plan:** Basic ($5/mo)
4. Chọn **CPU:** Regular (1 vCPU)
5. Chọn **Datacenter:** Singapore (để gần Việt Nam) hoặc Tokyo
6. **Add SSH key** (hoặc dùng password):
   ```bash
   # Tạo SSH key (nếu chưa có)
   ssh-keygen -t rsa -b 4096 -f ~/.ssh/do_rsa
   
   # Copy public key
   cat ~/.ssh/do_rsa.pub
   
   # Paste vào Digital Ocean
   ```
7. **Hostname:** chat-server
8. Click **Create Droplet**

Chờ ~30 giây để server khởi động. Copy **IP Address** (ví dụ: `192.168.1.100`)

### Bước 3: SSH vào Droplet
```bash
# Nếu dùng SSH key
ssh -i ~/.ssh/do_rsa root@[IP_ADDRESS]

# Hoặc dùng password (do gửi email)
ssh root@[IP_ADDRESS]
```

---

## PHẦN 3: SETUP SERVER TRÊN DIGITAL OCEAN

### Bước 1: Cập nhật hệ thống
```bash
apt update && apt upgrade -y
apt install -y curl wget git
```

### Bước 2: Cài đặt Java 17
```bash
apt install -y openjdk-17-jdk
java -version
```

### Bước 3: Cài đặt MySQL Server
```bash
apt install -y mysql-server

# Bắt đầu MySQL service
systemctl start mysql
systemctl enable mysql

# Secure MySQL (tùy chọn)
mysql_secure_installation
```

### Bước 4: Tạo Database cho Chat App
```bash
mysql -u root -p
# (Nhập password nếu có)

# Trong MySQL console:
CREATE DATABASE ChatAppDB;
CREATE USER 'chatuser'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### Bước 5: Clone Project từ GitHub
```bash
cd /opt
git clone https://github.com/[YOUR_USERNAME]/[REPO_NAME].git chat-app
cd chat-app
```

### Bước 6: Build JAR trên Server
```bash
# Nếu dùng Maven
mvn clean package

# Hoặc copy JAR đã build từ máy local
# scp -i ~/.ssh/do_rsa dist/ChatApp-TCP.jar root@[IP_ADDRESS]:/opt/chat-app/
```

---

## PHẦN 4: RUN SERVER CHAT APP

### Bước 1: Set biến môi trường
```bash
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="your_secure_password"
export CHAT_SERVER_PORT="5000"
export CHAT_SERVER_HEADLESS="true"  # Chạy không cần GUI
```

### Bước 2: Kiểm tra Firewall
```bash
# Digital Ocean Firewall (qua giao diện web)
# hoặc dùng ufw:
ufw allow 5000/tcp
ufw allow 22/tcp  # SSH
ufw enable
```

### Bước 3: Chạy Server (dùng nohup hoặc systemd)

**Cách 1: Chạy nền với nohup**
```bash
cd /opt/chat-app
nohup java -jar dist/ChatApp-TCP.jar > server.log 2>&1 &

# Kiểm tra logs
tail -f server.log
```

**Cách 2: Tạo Systemd Service (tốt hơn)**

Tạo file: `/etc/systemd/system/chat-app.service`
```bash
sudo nano /etc/systemd/system/chat-app.service
```

Thêm nội dung:
```ini
[Unit]
Description=Chat App Server
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/chat-app
Environment="CHAT_DB_URL=jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
Environment="CHAT_DB_USER=chatuser"
Environment="CHAT_DB_PASS=your_secure_password"
Environment="CHAT_SERVER_PORT=5000"
Environment="CHAT_SERVER_HEADLESS=true"
ExecStart=/usr/bin/java -jar /opt/chat-app/dist/ChatApp-TCP.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Kích hoạt service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable chat-app
sudo systemctl start chat-app

# Kiểm tra status
sudo systemctl status chat-app

# Xem logs
sudo journalctl -u chat-app -f
```

---

## PHẦN 5: CHẠY CLIENT TRÊN MÁY CÓ ĐỊA CHỈ SERVER

### Trên máy Windows/Mac/Linux:
```bash
# Chỉnh sửa ClientSocket.java để kết nối đến server
# hoặc set biến môi trường

# Run client
java -cp dist/ChatApp-TCP.jar client.Main [SERVER_IP] 5000

# Ví dụ:
java -cp dist/ChatApp-TCP.jar client.Main 192.168.1.100 5000
```

---

## PHẦN 6: UPDATE CODE SAU KHI DEPLOY

### Cách 1: Pull từ GitHub
```bash
cd /opt/chat-app
git pull origin main
mvn clean package  # Rebuild

# Restart service
sudo systemctl restart chat-app
```

### Cách 2: Copy JAR mới từ máy local
```bash
# Trên máy local:
scp -i ~/.ssh/do_rsa dist/ChatApp-TCP.jar root@[IP_ADDRESS]:/opt/chat-app/

# Trên server:
sudo systemctl restart chat-app
```

---

## TROUBLESHOOTING

### 1. Server không khởi động
```bash
sudo journalctl -u chat-app -n 50  # Xem 50 dòng log gần nhất
```

### 2. Không kết nối được MySQL
- Kiểm tra MySQL đã chạy: `sudo systemctl status mysql`
- Kiểm tra credentials: `mysql -u chatuser -p`

### 3. Client không kết nối được server
- Kiểm tra port: `netstat -tuln | grep 5000`
- Kiểm tra firewall: `sudo ufw status`
- Ping server: `ping [IP_ADDRESS]`

### 4. Xem logs server
```bash
# Nếu dùng nohup
tail -f /opt/chat-app/server.log

# Nếu dùng systemd
sudo journalctl -u chat-app -f
```

---

## TỔNG KHAI CHI PHÍ

| Dịch vụ | Giá |
|--------|-----|
| Droplet 1GB ($5/mo) | $60/năm |
| Tên miền (.com/.vn) | $12-15/năm |
| **Tổng** | **~$75/năm** |

💰 **Rẻ hơn:**
- **Oracle Cloud:** Miễn phí vĩnh viễn (nhưng hơi phức tạp)
- **Heroku:** Ngừng free tier rồi
- **Railway/Render:** $5-10/tháng cho Java app

---

## MỀO VÀ LƯU Ý

✅ **Backup:**
```bash
# Backup database hàng tuần
sudo mysqldump -u chatuser -p ChatAppDB > /backup/chatappdb_$(date +%Y%m%d).sql
```

✅ **Monitor:**
- Dùng Digital Ocean monitoring hoặc datadog
- Setup uptime alerts để nhận thông báo nếu server down

✅ **SSL Certificate (HTTPS):**
```bash
# Nếu sau này cần HTTPS (hiện tại dùng TCP trực tiếp)
sudo apt install certbot python3-certbot-nginx -y
```

✅ **Auto-update:**
```bash
# Enable auto-update security packages
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure unattended-upgrades
```

---

**Hỏi thêm nếu cần!** 🚀
