CHAT APP - 3 FILES TO USE

1. START_SERVER.bat
   - Runs Chat Server
   - Use this on the SERVER machine

2. START_CLIENT.bat
   - Runs Chat Client
   - Use this on CLIENT machines
   - You'll be asked for server IP

3. START_BOTH.bat
   - Runs Server + Client on SAME machine
   - Best for testing on 1 computer

=== THAT'S IT ===
Choose 1 file, double-click, and chat!
# Chat App - Deployment Guide

## Cac file:
- ChatApp-TCP.jar: Main application
- mysql-connector-j-9.6.0.jar: Database driver (Bat buoc)
- run_server.bat: Start server (Chay tren may chu)
- run_client.bat: Start client (Chay tren may client)

================================
## BUOC 1: CAI DAT MYSQL (SAU BUOC NAY NHAY DEN BUOC 2)
================================
1. Tai MySQL Community Server: https://dev.mysql.com/downloads/mysql/
2. Chay installer va chon:
   - MySQL Server
   - Chon port: 3306 (mac dinh)
3. Sap xep tai Root user: root / root (hoac tuy chon)

================================
## BUOC 2: CONFIG DATABASE
================================

Mo MySQL Command Line Client va chay:

  CREATE DATABASE IF NOT EXISTS ChatAppDB CHARACTER SET utf8mb4;
  CREATE USER 'chatuser'@'localhost' IDENTIFIED BY 'ChatApp@123';
  GRANT ALL PRIVILEGES ON ChatAppDB.* TO 'chatuser'@'localhost';
  FLUSH PRIVILEGES;

Luu y: Neu bang cach khac, co the dung MySQL Workbench hoac HeidiSQL

================================
## BUOC 3: SET ENVIRONMENT VARIABLES (Windows)
================================

Nhan Windows + R, ghi: sysdm.cpl
Trong Advanced tab, click "Environment Variables"
Them New User Variables:

  CHAT_DB_URL = jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
  CHAT_DB_USER = chatuser
  CHAT_DB_PASS = ChatApp@123

Click OK va dong het cac terminal cu, mo terminal moi

================================
## BUOC 4: CHAY SERVER
================================

1. Trong folder nay, double-click: run_server.bat
2. Kiem tra output:
   - "RUNNING on port 5000" = SUCCESS
   - Neu co loi, xem loi va fix database setup (BUOC 2)

================================
## BUOC 5: CHAY CLIENT (Tren may khac hoac cung may)
================================

1. Trong folder nay, double-click: run_client.bat
2. Nhap thong tin server:
   - IP: localhost (neu cung may) hoac [external IP cua server]
   - Port: 5000
3. Click OK va vao chat

================================
## TRUONG HOP KHONG CHAY DUOC:
================================

MySQL Connection Error?
  -> Kiem tra MySQL da start: services.msc, tim MySQL80
  -> Kiem tra credentials (user/pass) trong BUOC 2
  -> Kiem tra environment variables da set

Port 5000 bi chiem?
  -> Chay: netstat -ano | findstr :5000
  -> Kill process hoac doi port trong code

Client khong ket noi server?
  -> Kiem tra IP server (ping)
  -> Kiem tra firewall cho phep port 5000
  -> Neu cloud, kiem tra security group rules

Java khong tim thay class?
  -> Kiem tra ChatApp-TCP.jar co trong folder nay
  -> Kiem tra mysql-connector-j-9.6.0.jar trong folder nay

