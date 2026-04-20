# FIX LỖI MYSQL CONNECTION ERROR TRÊN SERVER

## ❌ LỖI BẠN GẶP

```
[ERROR] DB Error (ensureDefaultRoom): Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago. 
The driver has not received any packets from the server.
```

**Nguyên nhân:** Server Chat App không kết nối được MySQL → Database không khởi tạo được

---

## ✅ CÁCH FIX (Từng Bước)

### **Bước 1: SSH vào Server (máy local chạy)**
```bash
ssh root@[YOUR_SERVER_IP]
# Hoặc nếu dùng SSH key:
ssh -i ~/.ssh/do_rsa root@[YOUR_SERVER_IP]
```

### **Bước 2: Check MySQL đã chạy chưa**
```bash
# Xem status MySQL
sudo systemctl status mysql

# Nếu không chạy, khởi động:
sudo systemctl start mysql
sudo systemctl enable mysql

# Chờ 3 giây
sleep 3

# Kiểm tra MySQL lắng nghe trên port 3306
netstat -tuln | grep 3306
# Sẽ thấy: tcp 0 0 127.0.0.1:3306 0.0.0.0:* LISTEN
```

### **Bước 3: Tạo Database & User MySQL**

```bash
# Kết nối MySQL với root (mặc định không có password)
sudo mysql -u root

# Hoặc có password:
sudo mysql -u root -p
# (Nhập password khi được hỏi)
```

Trong MySQL console, chạy:
```sql
-- Tạo database
CREATE DATABASE IF NOT EXISTS ChatAppDB 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo user
CREATE USER IF NOT EXISTS 'chatuser'@'localhost' IDENTIFIED BY 'ChatApp@123';

-- Cấp quyền (FULL ACCESS)
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';

-- Nếu cần kết nối từ máy khác (ví dụ client đây):
CREATE USER IF NOT EXISTS 'chatuser'@'%' IDENTIFIED BY 'ChatApp@123';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'%';

-- Áp dụng thay đổi
FLUSH PRIVILEGES;

-- Kiểm tra user đã được tạo chưa
SELECT user, host FROM mysql.user WHERE user='chatuser';

-- Thoát
EXIT;
```

### **Bước 4: Test kết nối MySQL**

```bash
# Test với user vừa tạo
mysql -u chatuser -p'ChatApp@123' -e "SELECT 1;" 

# Nếu thấy "1" → Kết nối thành công! ✅
```

### **Bước 5: Set Environment Variables cho Java App**

**Cách A: Tạm thời (chỉ session hiện tại)**
```bash
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="ChatApp@123"
export CHAT_SERVER_PORT="5000"
export CHAT_SERVER_HEADLESS="true"
```

**Cách B: Vĩnh viễn (recommended)**
```bash
# Edit ~/.bashrc
sudo nano ~/.bashrc

# Thêm vào cuối file:
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="ChatApp@123"
export CHAT_SERVER_PORT="5000"
export CHAT_SERVER_HEADLESS="true"

# Save: Ctrl + O → Enter → Ctrl + X

# Apply changes
source ~/.bashrc
```

### **Bước 6: Kill Process Cũ & Restart Server**

```bash
# Kill Java process cũ
pkill -f "Chat.server.Main"

# Di vào thư mục project
cd /root/MultiChat

# Chạy server (dùng nohup để chạy nền)
nohup java -cp "build/classes:dist/lib/mysql-connector-j-9.6.0.jar" Chat.server.Main > server.log 2>&1 &

# Kiểm tra logs
tail -f server.log
```

**Nếu thấy:**
```
[2026-04-20 13:50:21] [SYSTEM] Server đã sẵn sàng! Địa chỉ: [0:0:0:0:0:0:0:0]:5000
```
→ **✅ Server chạy thành công!**

### **Bước 7: Test Client kết nối từ máy khác**

**Trên máy local (Windows/Mac):**
```bash
# Di vào thư mục project
cd f:\2025-2026\2026\LapTrinhMang\Multicast

# Build (nếu chưa có dist/ChatApp-TCP.jar)
build.bat

# Chạy client
java -cp dist/ChatApp-TCP.jar Chat.client.Main [SERVER_IP] 5000

# Ví dụ:
java -cp dist/ChatApp-TCP.jar Chat.client.Main 192.168.1.100 5000
```

---

## 🚀 AUTOMATED FIX (Nếu muốn nhanh hơn)

Chạy script tôi tạo:

```bash
# Trên server:
cd /root/MultiChat
chmod +x fix-mysql-server.sh
./fix-mysql-server.sh
```

Script sẽ tự động:
1. Check MySQL status
2. Start MySQL
3. Tạo database + user
4. Set environment variables
5. Restart Chat App
6. Test connection

---

## 🔧 TROUBLESHOOTING

### Lỗi: "Access denied for user 'root'@'localhost'"
**Nguyên nhân:** Mật khẩu root MySQL không đúng
**Fix:**
```bash
# Reset MySQL root password
sudo mysqld_safe --skip-grant-tables &
mysql -u root
FLUSH PRIVILEGES;
ALTER USER 'root'@'localhost' IDENTIFIED BY 'new_password';
EXIT;
sudo systemctl restart mysql
```

### Lỗi: "Can't connect to MySQL server on 'localhost'"
**Nguyên nhân:** MySQL service không chạy
**Fix:**
```bash
sudo systemctl start mysql
sudo systemctl enable mysql
```

### Lỗi: "Unknown database 'ChatAppDB'"
**Fix:**
```bash
# Check database tồn tại không
sudo mysql -u root -e "SHOW DATABASES;"

# Nếu không có, tạo:
sudo mysql -u root -e "CREATE DATABASE ChatAppDB CHARACTER SET utf8mb4;"
```

### Lỗi: "Communications link failure" vẫn xảy ra
**Check:**
```bash
# 1. MySQL listening?
netstat -tuln | grep 3306

# 2. Firewall blocks?
sudo ufw status
sudo ufw allow 3306

# 3. Credentials đúng?
mysql -u chatuser -p'ChatApp@123' ChatAppDB -e "SELECT 1;"

# 4. Check app logs
tail -100 server.log
```

---

## 📊 CHO PHÉP MULTIPLE CLIENTS KẾT NỐI

### 1. Mở Port TCP 5000 trên Firewall

```bash
# Nếu dùng ufw
sudo ufw allow 5000/tcp

# Nếu dùng iptables
sudo iptables -A INPUT -p tcp --dport 5000 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 3306 -j ACCEPT
```

### 2. Kiểm tra Server Listening trên 0.0.0.0 (tất cả interface)

```bash
# Kiểm tra port
netstat -tuln | grep 5000

# Sẽ thấy:
# tcp6 0 0 :::5000 :::* LISTEN
# Hoặc:
# tcp 0 0 0.0.0.0:5000 0.0.0.0:* LISTEN
```

### 3. Client Connect từ Máy Khác

```bash
# Client chỉ cần biết IP của server
java -cp dist/ChatApp-TCP.jar Chat.client.Main 192.168.1.100 5000
```

**Tất cả client có thể:**
- ✅ Kết nối cùng lúc
- ✅ Chat với nhau qua server
- ✅ Database lưu tất cả messages

---

## 📝 VERIFY FINAL STATE

Sau khi fix, kiểm tra:

```bash
# 1. Server chạy?
ps aux | grep "Chat.server.Main"

# 2. Listening port 5000?
netstat -tuln | grep 5000

# 3. MySQL chạy?
sudo systemctl status mysql

# 4. Database tồn tại?
sudo mysql -u chatuser -p'ChatApp@123' -e "SHOW DATABASES;"

# 5. Check logs
tail -20 /root/MultiChat/server.log
```

**Nếu tất cả OK:**
```
✅ Server ready for multiple clients
✅ Database connected
✅ Port 5000 open
✅ Clients can connect from anywhere
```

---

**Hỏi nếu còn vấn đề!** 🚀
