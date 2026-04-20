# Hướng Dẫn Đóng Gói & Deploy Chat App Lên Google Cloud

## PHẦN 1: ĐÓNG GÓI APP TRONG NETBEANS

### Bước 1: Chuẩn bị project

1. Mở NetBeans → Open Project → Chọn thư mục gốc (ChatApp-TCP)
2. Đảm bảo MySQL Connector JAR đã được thêm:
   - Chuột phải Project → Properties → Libraries
   - Kiểm tra `mysql-connector-j-9.6.0.jar` đã được add vào Classpath

### Bước 2: Build JAR trong NetBeans

**Cách 1: Dùng menu (dễ nhất)**
1. Chuột phải vào Project → Clean and Build
2. Chờ process hoàn thành
3. JAR sẽ được tạo tại: `dist/ChatApp-TCP.jar`

**Cách 2: Dùng Ant Command**
1. Mở Terminal trong NetBeans (Tools → Open in Terminal)
2. Gõ lệnh: `ant clean build`
3. Lệnh này sẽ compile code và tạo JAR

### Bước 3: Tạo JAR riêng cho Server và Client (tùy chọn)

Nếu muốn tách Server và Client thành 2 JAR riêng:

**Chỉnh sửa build.xml để tạo 2 JAR:**
```xml
<!-- Thêm target này vào cuối build.xml -->
<target name="package-server" depends="compile">
    <jar jarfile="${dist.dir}/ChatServer.jar">
        <fileset dir="${build.classes.dir}" includes="server/**" />
        <zipfileset src="${file.reference.mysql-connector-j-9.6.0.jar}"/>
        <manifest>
            <attribute name="Main-Class" value="server.Server"/>
            <attribute name="Encoding" value="UTF-8"/>
        </manifest>
    </jar>
</target>

<target name="package-client" depends="compile">
    <jar jarfile="${dist.dir}/ChatClient.jar">
        <fileset dir="${build.classes.dir}" includes="client/**" />
        <manifest>
            <attribute name="Main-Class" value="client.Main"/>
        </manifest>
    </jar>
</target>
```

Chạy: `ant package-server` và `ant package-client`

---

## PHẦN 2: DEPLOY LÊN GOOGLE CLOUD (MIỄN PHÍ)

### Yêu cầu:
- Tài khoản Google Cloud (đăng ký tại https://cloud.google.com)
- Google Cloud SDK (tải tại https://cloud.google.com/sdk/docs/install)
- Cài đặt gcloud CLI

### 3 Tùy Chọn Miễn Phí:

---

## TÙYỌN 1: Google Compute Engine (VM miễn phí 1 năm)

**Ưu điểm:** Toàn quyền kiểm soát, có thể chạy MySQL + Server cùng lúc
**Nhược điểm:** Sau 1 năm sẽ tính phí

### Bước 1: Tạo VM Instance
```bash
gcloud compute instances create chat-server ^
  --zone=asia-southeast1-a ^
  --machine-type=e2-micro ^
  --image-family=ubuntu-2204-lts ^
  --image-project=ubuntu-os-cloud
```

### Bước 2: SSH vào VM
```bash
gcloud compute ssh chat-server --zone=asia-southeast1-a
```

### Bước 3: Cài đặt Java & MySQL
```bash
# Cập nhật package
sudo apt update && sudo apt upgrade -y

# Cài JDK 17
sudo apt install openjdk-17-jdk -y

# Cài MySQL Server
sudo apt install mysql-server -y

# Khởi động MySQL
sudo systemctl start mysql
sudo mysql -u root -e "CREATE DATABASE ChatAppDB;"
```

### Bước 4: Upload và chạy app
```bash
# Upload JAR file từ máy local
gcloud compute scp dist/ChatApp-TCP.jar chat-server:/home/[USERNAME]/app.jar ^
  --zone=asia-southeast1-a

# SSH vào VM
gcloud compute ssh chat-server --zone=asia-southeast1-a

# Trong VM, chạy server
java -cp app.jar:mysql-connector-j-9.6.0.jar server.Server
```

---

## TÙYỌN 2: Google Cloud Run (Miễn phí vĩnh viễn, hạn chế)

**Ưu điểm:** Miễn phí vĩnh viễn, auto-scale, dễ deploy
**Nhược điểm:** Stateless, khó chạy database + socket server cùng lúc, phù hợp API hơn

⚠️ **Lưu ý:** Vì app là Swing GUI + Socket Server, Cloud Run không lý tưởng. 
Chuyên cho REST API hoặc stateless services.

### Nếu muốn, chuyển Server thành REST API:
- Dùng Spring Boot (lightweight)
- Expose endpoints: `/api/messages`, `/api/users`
- Deploy lên Cloud Run

---

## TÙYỌN 3: Google Cloud SQL + Compute Engine (Hiệu quả nhất)

**Best Practice cho Chat App:**

### Cấu hình recommended:
- **Database:** Google Cloud SQL for MySQL (free tier: 1 instance 1 vCPU, 3.75GB)
- **Server App:** Compute Engine e2-micro (free: 1 instance, 1 vCPU)
- **Client:** Chạy cLocal hoặc cài trên các máy khác

### Bước 1: Tạo Cloud SQL Instance
```bash
gcloud sql instances create chat-db ^
  --database-version=MYSQL_8_0 ^
  --tier=db-f1-micro ^
  --region=asia-southeast1 ^
  --database-flags=cloudsql_iam_authentication=off

# Tạo database
gcloud sql databases create ChatAppDB --instance=chat-db

# Tạo user
gcloud sql users create chatuser ^
  --instance=chat-db ^
  --password=YourSecurePassword123!
```

### Bước 2: Tạo Compute Engine
```bash
gcloud compute instances create chat-server ^
  --zone=asia-southeast1-a ^
  --machine-type=e2-micro ^
  --image-family=ubuntu-2204-lts
```

### Bước 3: Kết nối Server tới Cloud SQL
```bash
# Cài Cloud SQL Proxy trên VM
curl https://dl.google.com/cloudsql/cloud_sql_proxy.linux.amd64 \
  -o cloud_sql_proxy
chmod +x cloud_sql_proxy

# Chạy proxy
./cloud_sql_proxy -instances=YOUR-PROJECT-ID:asia-southeast1:chat-db=tcp:3306
```

### Bước 4: Chỉnh sửa connection string
Trong code Java, thay đổi:
```java
// Từ cũ (local):
String url = "jdbc:mysql://localhost:3306/ChatAppDB";

// Thành (cloud):
String url = "jdbc:mysql://127.0.0.1:3306/ChatAppDB";
```

---

## PHẦN 3: TẠO FIREWALL RULE

### Cho phép Client kết nối từ internet:
```bash
gcloud compute firewall-rules create allow-chat-port ^
  --allow=tcp:5000 ^
  --source-ranges=0.0.0.0/0
```

### Lấy External IP của VM:
```bash
gcloud compute instances list
```

### Client sửa IP kết nối:
```java
String serverIP = "YOUR-EXTERNAL-IP"; // Thay bằng IP thực
int serverPort = 5000;
```

---

## PHẦN 4: GIÁM SÁT & LOG

### Xem log trên Google Cloud Console:
1. Vào https://console.cloud.google.com
2. Logging → Logs Explorer
3. Tìm logs từ VM instance

### SSH vào VM và xem log trực tiếp:
```bash
gcloud compute ssh chat-server --zone=asia-southeast1-a

# Trong VM, xem process
ps aux | grep java

# Kill process nếu cần
kill -9 [PID]
```

---

## BẢNG SO SÁNH MIỄN PHÍ

| Tùy chọn | Giới hạn | Thích hợp cho |
|----------|---------|--------------|
| **Compute Engine** | 1 năm free e2-micro | Chat Server + Local DB |
| **Cloud SQL** | ~$13/tháng sau free tier | Database chung cho nhiều server |
| **Cloud Run** | 2 triệu requests/tháng | REST API, stateless services |
| **Cloud Storage** | 5GB miễn phí | Backup, file logs |

---

## HƯỚNG DẪN NHANH (TÓM TẮT)

### NetBeans:
1. Chuột phải Project → **Clean and Build**
2. JAR sẽ ở `dist/ChatApp-TCP.jar`

### Google Cloud:
1. `gcloud init` → Đăng nhập Google Account
2. Tạo VM: `gcloud compute instances create ...`
3. SSH: `gcloud compute ssh ...`
4. Cài Java: `sudo apt install openjdk-17-jdk`
5. Upload JAR: `gcloud compute scp dist/ChatApp-TCP.jar ...`
6. Chạy: `java -jar ChatApp-TCP.jar`

---

## Troubleshooting

### Lỗi "Connection refused"
- Kiểm tra firewall rule
- Kiểm tra port (5000) có opening không: `sudo netstat -tlnp | grep 5000`

### Lỗi MySQL not found
- Cài MySQL Driver: `apt install libmysql-java`
- Thêm vào CLASSPATH

### Lỗi "Permission denied" khi SSH
- Kiểm tra SSH key: `gcloud compute config-properties set core/account [email]`
- Hoặc dùng Web Console trong Google Cloud

---

## Chi phí (Tính đến tháng 12/2026)

- **Compute Engine e2-micro:** Miễn phí (năm đầu)
- **Cloud SQL db-f1-micro:** Miễn phí (trong free tier)
- **Tổng:** ~$0 (năm đầu), sau đó ~$15-30/tháng

---

## Tài liệu tham khảo

- Google Cloud Compute Engine: https://cloud.google.com/compute/docs
- Google Cloud SQL: https://cloud.google.com/sql/docs
- Google Cloud Run: https://cloud.google.com/run/docs
- gcloud CLI: https://cloud.google.com/sdk/gcloud
