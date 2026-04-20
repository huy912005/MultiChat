# ⚡ QUICK START - FIX & DEPLOY (5 Lệnh)

## 🔴 VẤNĐỀ: "Communications link failure"

Server không kết nối được MySQL → Database không khởi tạo

---

## ✅ SOLUTION (COPY-PASTE VÀO SERVER)

Chạy **các lệnh sau trên server** (SSH vào server trước):

```bash
# [1] Start MySQL
sudo systemctl start mysql && sudo systemctl enable mysql && sleep 3

# [2] Create database
sudo mysql -u root << EOF
CREATE DATABASE IF NOT EXISTS ChatAppDB CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'chatuser'@'localhost' IDENTIFIED BY 'ChatApp@123';
CREATE USER IF NOT EXISTS 'chatuser'@'%' IDENTIFIED BY 'ChatApp@123';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';
GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'%';
FLUSH PRIVILEGES;
EOF

# [3] Set environment
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="ChatApp@123"
export CHAT_SERVER_PORT="5000"
export CHAT_SERVER_HEADLESS="true"

# [4] Kill old + restart server
pkill -f "Chat.server.Main" 2>/dev/null || true
cd /root/MultiChat
nohup java -cp "build/classes:dist/lib/mysql-connector-j-9.6.0.jar" Chat.server.Main > server.log 2>&1 &

# [5] Verify
sleep 2 && tail -10 server.log
```

---

## 🎯 Expected Output (sau bước 5)

```
[2026-04-20 13:50:20] [SYSTEM] Đang khởi động server trên cổng 5000...
[2026-04-20 13:50:21] [STATUS] RUNNING on port 5000
[2026-04-20 13:50:21] [SYSTEM] Server đã sẵn sàng! Địa chỉ: [0:0:0:0:0:0:0:0]:5000
```

✅ = Thành công!
❌ = Xem lỗi trong server.log: `tail -f server.log`

---

## 🚀 CONNECT CLIENT (MÁY KHÁC)

```bash
# Thay IP server
java -cp dist/ChatApp-TCP.jar Chat.client.Main [SERVER_IP] 5000

# Ví dụ:
java -cp dist/ChatApp-TCP.jar Chat.client.Main 192.168.1.100 5000
```

---

## 🔧 COMMON FIX

| Lỗi | Giải pháp |
|-----|----------|
| "MySQL connection error" | Chạy bước [1-2] lại |
| Server vẫn không chạy | `tail -f server.log` xem chi tiết lỗi |
| Client không kết nối | `netstat -tuln \| grep 5000` (kiểm tra port mở) |
| Firewall blocks | `sudo ufw allow 5000` |

---

## 📌 TIPS

```bash
# View logs realtime
tail -f /root/MultiChat/server.log

# Stop server
pkill -f "Chat.server.Main"

# Check running status
ps aux | grep Chat.server.Main

# Test DB connection
mysql -u chatuser -p'ChatApp@123' ChatAppDB -e "SELECT 1;"
```

---

## 🎉 DONE!

- ✅ MySQL running
- ✅ Database created
- ✅ Chat server on port 5000
- ✅ Multiple clients can connect
- ✅ Messages saved to database

**Gọi tôi nếu có issue!** 💬
