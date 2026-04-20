# Các Nền Tảng Server MIỄN PHÍ VĨNH VIỄN Cho Java App

## 🏆 TỐTẤT NHẤT: Oracle Cloud Always Free Tier

**Tính chất:** Hoàn toàn miễn phí, vĩnh viễn, không cần credit card

### Các tài nguyên miễn phí:
- **2 x Compute VM** (e3.flex, 1-4 vCPU, 6GB RAM)
- **2 x Autonomous Database** (MySQL hoặc PostgreSQL, 20GB)
- **10GB Object Storage**
- **Bandwidth miễn phí**
- **Email/SMS notification**
- **24/7 uptime** (99.99% SLA)

### Hướng dẫn setup:

#### 1. Tạo tài khoản
```
Truy cập: https://www.oracle.com/cloud/free/
Nhấn: Sign Up
Chọn: Create account
(Không cần credit card!)
```

#### 2. Tạo Compute VM
```
1. Oracle Cloud Console → Compute → Instances
2. Create Instance:
   - Name: chat-server
   - Image: Ubuntu 22.04 LTS (free eligible)
   - Shape: Ampere (ARM-based, free)
   - vCPU: 4 cores (free)
   - RAM: 24GB (free)
3. Add SSH Public Key
4. Create
```

#### 3. SSH vào VM
```bash
# Từ cmd/PowerShell trên Windows
ssh ubuntu@YOUR_INSTANCE_PUBLIC_IP

# Hoặc dùng PuTTY/MobaXterm
```

#### 4. Cài đặt Java & MySQL
```bash
# Cập nhật
sudo apt update && sudo apt upgrade -y

# Cài JDK 17
sudo apt install openjdk-17-jdk -y

# Cài MySQL Server
sudo apt install mysql-server -y

# Khởi động MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# Tạo database
sudo mysql -u root -e "
  CREATE DATABASE ChatAppDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  CREATE USER 'chatuser'@'localhost' IDENTIFIED BY 'SecurePassword123!';
  GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';
  FLUSH PRIVILEGES;
"
```

#### 5. Upload JAR & chạy
```bash
# Từ máy local, upload JAR
scp dist\ChatApp-TCP.jar ubuntu@YOUR_IP:/home/ubuntu/app.jar

# SSH vào VM & chạy
ssh ubuntu@YOUR_IP
cd /home/ubuntu
java -jar app.jar
```

#### 6. Mở Firewall (Important!)
```bash
# Trong Oracle Cloud Console:
1. Networking → Virtual Cloud Networks
2. Chọn VCN mặc định
3. Security Lists → Default Security List
4. Add Ingress Rule:
   - Stateless: No
   - Source Type: CIDR
   - Source CIDR: 0.0.0.0/0
   - IP Protocol: TCP
   - Destination Port Range: 5000
   - Action: Allow
```

---

## 🥈 THAM KHẢO KHÁC

### Railway.app
**Ưu:** Dễ deploy, có CLI
**Nhược:** 
- Giới hạn 500 giờ/tháng (không đủ cho 24/7)
- Khi hết quota, app bị pause
- Có thể upgrade paid tí tí để extend

```bash
# Install CLI
npm install -g @railway/cli

# Login
railway login

# Deploy
railway link
railway up
```

**Chi phí thực tế:** Miễn phí 500h/tháng = ~16h/ngày, không phù hợp 24/7

---

### Render.com
**Ưu:** Free tier tạm được, có auto-deploy
**Nhược:** 
- App sleep sau 15 phút không hoạt động
- Slow startup (15-30 giây)
- Không lý tưởng cho chat real-time

**Setup:**
```
1. Tạo tài khoản: https://render.com
2. Push code lên GitHub
3. Connect Render → GitHub repo
4. Deploy
```

**Chi phí:** Miễn phí nhưng kém hiệu suất

---

### Replit
**Ưu:** Mã hóa trên web, support Java
**Nhược:** 
- Chậm, thiếu tài nguyên
- Không lý tưởng cho production
- Chỉ dùng để test/demo

---

## ❌ TRÁNH:

| Platform | Lý do |
|----------|-------|
| **Heroku** | Đóng free tier (11/2022) |
| **AWS Free Tier** | Chỉ free 1 năm, sau đó tính phí |
| **Google Cloud** | Free 1 năm, sau đó ~$15-30/tháng |
| **DigitalOcean** | Miễn phí lúc đầu, rồi tính phí $5-40/tháng |
| **Vultr** | Minimum $2.5/tháng |
| **VPS miễn phí random** | Không đáng tin, uptime tệ |

---

## 📊 BẢNG SO SÁNH

| Nền tảng | Vĩnh viễn | Giá | Uptime | RAM | Java Support | Ghi chú |
|----------|----------|-----|--------|-----|--------------|---------|
| **Oracle Cloud** ⭐ | ✅ Có | $0 | 99.99% | 24GB | ✅ Tốt | **BEST** |
| **Railway** | ❌ (500h/tháng) | $0 | Tùy | 1GB | ✅ | Có giới hạn |
| **Render** | ❌ (Sleep) | $0 | ~95% | 512MB | ✅ | Chậm startup |
| **Replit** | ❌ (Limited) | $0 | ~90% | 256MB | ✅ | Chỉ demo |
| **AWS** | ❌ (1 năm) | $0-15 | 99.9% | 1GB | ✅ | Sau đó tính phí |
| **GCP** | ❌ (1 năm) | $0-30 | 99.95% | 3.75GB | ✅ | Sau đó tính phí |

---

## 🎯 KHUYẾN CÁO

### Nếu muốn **100% miễn phí vĩnh viễn**:
👉 **Dùng Oracle Cloud Always Free Tier** - Tốt nhất, không bị hạn chế

### Nếu chỉ cần **test/demo ngắn hạn** (vài tuần/tháng):
👉 Railway hoặc Render (miễn phí tạm, rồi tính phí)

### Nếu chấp nhận **miễn phí 1-2 năm**:
👉 AWS Free Tier hoặc Google Cloud (after đó cần trả tiền)

---

## 🚀 QUICK START: Oracle Cloud

```bash
# 1. Tạo tài khoản (không cần card)
https://www.oracle.com/cloud/free/

# 2. Tạo VM Ubuntu 22.04 (free eligible)
# 3. SSH vào VM

# 4. Setup Java
sudo apt update && sudo apt install openjdk-17-jdk mysql-server -y

# 5. Upload & chạy
# Từ local: scp dist\ChatApp-TCP.jar ubuntu@ORACLE_IP:/home/ubuntu/app.jar
# Trên VM: java -jar app.jar

# 6. Mở firewall (port 5000)
# Console → VCN → Security List → Add Ingress Rule
```

**Kết quả:** Chat app chạy 24/7 hoàn toàn miễn phí, không giới hạn thời gian! 🎉

---

## Troubleshooting Oracle Cloud

### Không thể SSH
```bash
# Kiểm tra Security List
Console → VCN → Security Lists
Thêm Ingress Rule cho SSH (port 22):
- Source: 0.0.0.0/0
- Port: 22
```

### App không connect được từ client
```bash
# Thêm Ingress Rule cho port 5000
Console → VCN → Security Lists
- Destination Port: 5000
```

### Lỗi "Out of capacity"
- Lựa chọn VM type khác hoặc region khác

### Check resource usage
```bash
# Trên VM
df -h              # Disk usage
free -h            # Memory
top                # CPU usage
```

---

## Các lựa chọn khác (nếu Oracle Cloud không hoạt động)

### **Linode (DigitalOcean sibling)**
- Minimum $5/tháng (rẻ nhất có thể)
- Không hoàn toàn miễn phí

### **Hetzner Cloud**
- Giá rẻ: €4.15/tháng
- Bonus €20 credit ban đầu
- Tính phí sau

### **Pterodactyl/GameServers miễn phí**
- Một số hosting cho phép miễn phí
- Chất lượng tệ, không đáng tin

---

## Kết luận

**✅ SỬ DỤNG ORACLE CLOUD** = Miễn phí vĩnh viễn + Chất lượng tốt + Không giới hạn ✨
