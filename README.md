# Chat App TCP/IP (Java Swing)

## Cau truc chinh

```
Multicast/
├── src/Chat/
│   ├── server/
│   │   ├── Main.java
│   │   ├── network/
│   │   └── view/
│   ├── client/
│   └── Dao/DBContext.java
├── build.bat
├── run_server.bat
└── run_client.bat
```

## Chay nhanh tren may local (Windows)

1. Dat MySQL Connector vao `lib/mysql-connector-j-9.6.0.jar` (hoac set `CHAT_MYSQL_JAR`).
2. Chay `build.bat`.
3. Chay `run_server.bat`.
4. Chay `run_client.bat` (mo nhieu cua so neu can test nhieu user).

## Bien moi truong ho tro deploy

- `CHAT_MYSQL_JAR`: duong dan toi file `mysql-connector-j-*.jar`.
- `CHAT_DB_URL`: vi du `jdbc:mysql://127.0.0.1:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC`.
- `CHAT_DB_USER`: user DB.
- `CHAT_DB_PASS`: password DB.
- `CHAT_SERVER_PORT`: port TCP cua server (mac dinh `5000`).
- `CHAT_SERVER_HEADLESS`: dat `true` de chay server khong can GUI (phu hop VPS/Linux server).

## Deploy len server

### 1) Chuan bi server

- Cai JDK 17+.
- Cai MySQL, tao database `ChatAppDB`.
- Mo firewall cho TCP port ban dung (mac dinh `5000`).

### 2) Copy source + mysql connector

- Copy project len server.
- Dat file connector vao `lib/mysql-connector-j-9.6.0.jar` hoac dat bien `CHAT_MYSQL_JAR`.

### 3) Set bien moi truong (Linux)

```bash
export CHAT_DB_URL='jdbc:mysql://127.0.0.1:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
export CHAT_DB_USER='root'
export CHAT_DB_PASS='12345'
export CHAT_SERVER_PORT='5000'
export CHAT_SERVER_HEADLESS='true'
```

### 4) Build + run

```bash
cmd /c build.bat
cmd /c run_server.bat
```

Neu chay native shell Linux, ban co the build/run bang `javac` + `java` tuong duong script tren.

### 5) Client ket noi

- Chay client tren may khac.
- Nhap IP public/LAN cua server khi app hoi dia chi server.

## Ghi chu

- `Chat.server.Main` tu dong chay che do headless khi `CHAT_SERVER_HEADLESS=true` hoac moi truong khong co GUI.
- De chay ben vung tren Linux, nen tao service `systemd` de auto restart sau reboot/crash.
