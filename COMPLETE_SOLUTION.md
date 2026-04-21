═══════════════════════════════════════════════════════════════════════════════
🔧 GIẢI PHÁP HOÀN CHỈNH - CHAT SERVER REALTIME ĐẦY ĐỦ
═══════════════════════════════════════════════════════════════════════════════

❌ VẤN ĐỀ HIỆN TẠI:
1. GUI Admin không update số user online
2. Log không ghi tin nhắn từ nhiều client
3. Kick user không hoạt động
4. Client list không cập nhật realtime

✅ GIẢI PHÁP:

═══════════════════════════════════════════════════════════════════════════════
PHẦN 1: SỬA SERVER GUI (Hiển thị số user online)
═══════════════════════════════════════════════════════════════════════════════

File: src/Chat/server/view/ServerGUI.java

THÊM hàm này vào class ServerGUI:

```java
// Cập nhật số user online từ Server
public void updateOnlineUserCount() {
    // Lấy từ Server.getRoomGroups()
    int totalOnline = 0;
    Map<Integer, List<ClientHandler>> rooms = Server.getRoomGroups();
    for (List<ClientHandler> members : rooms.values()) {
        if (members != null) {
            totalOnline += members.size();
        }
    }
    lblOnlineCount.setText(totalOnline + " người");
    lblOnlineCount.setForeground(ACCENT_GREEN);
}
```

THÊM vào logJoin():
```java
updateOnlineUserCount();  // ← THÊM DÒNG NÀY
```

THÊM vào logLeave():
```java
updateOnlineUserCount();  // ← THÊM DÒNG NÀY
```

═══════════════════════════════════════════════════════════════════════════════
PHẦN 2: GHI LOG TIN NHẮN TỪNG CLIENT (In ra giao diện)
═══════════════════════════════════════════════════════════════════════════════

File: src/Chat/server/view/ServerGUI.java

THÊM hàm này:
```java
public void logMessageDetail(String sender, String room, String content) {
    SwingUtilities.invokeLater(() -> {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String message = String.format("[%s] 💬 %s (Phòng %s): %s\n", 
                timestamp, sender, room, content);
            
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, ACCENT_BLUE);
            logDoc.insertString(logDoc.getLength(), message, attrs);
            
            logPane.setCaretPosition(logDoc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
}
```

═══════════════════════════════════════════════════════════════════════════════
PHẦN 3: FIX CLIENTHANDLER.JAVA
═══════════════════════════════════════════════════════════════════════════════

File: src/Chat/server/network/ClientHandler.java

THAY ĐỔI handleProtocol() - CHAT case:

TỪ:
```java
case CHAT:
    // Lưu vào DB trước khi gửi đi
    saveMessageToDB(msg, currentRoomId);
    broadcastToRoom(currentRoomId, msg);
    gui.logChat(msg.getSender(), "(" + currentRoomId + "): " + msg.getContent());
    break;
```

THÀNH:
```java
case CHAT:
    // Lưu vào DB trước khi gửi đi
    saveMessageToDB(msg, currentRoomId);
    broadcastToRoom(currentRoomId, msg);
    
    // ← THÊM DÒNG NÀY
    if (gui instanceof Chat.server.view.ServerGUI) {
        ((Chat.server.view.ServerGUI) gui).logMessageDetail(
            msg.getSender(), 
            String.valueOf(currentRoomId), 
            msg.getContent()
        );
    }
    
    gui.logChat(msg.getSender(), "(" + currentRoomId + "): " + msg.getContent());
    break;
```

═══════════════════════════════════════════════════════════════════════════════
PHẦN 4: KICK USER FUNCTIONALITY
═══════════════════════════════════════════════════════════════════════════════

File: src/Chat/server/network/ClientHandler.java

Verify handleKickRequest() đã được gọi:

```java
case KICK:
    gui.logSystem("[ADMIN] Kick request: " + msg.getContent());
    handleKickRequest(msg.getContent());
    break;
```

Verify handleKickRequest() code:
```java
private void handleKickRequest(String targetUser) {
    List<ClientHandler> members = Server.getRoomGroups().get(currentRoomId);
    if (members == null) {
        gui.logError("Kick failed: phòng không tồn tại");
        return;
    }
    
    List<ClientHandler> membersCopy;
    synchronized (members) {
        membersCopy = new ArrayList<>(members);
    }
    
    for (ClientHandler ch : membersCopy) {
        if (ch != null && ch.username != null && ch.username.equals(targetUser)) {
            ch.sendMessage(new Message("Hệ thống", 
                "Bạn bị kick khỏi phòng bởi " + this.username, 
                Message.Type.SYSTEM));
            ch.joinRoom(1); // Quay về phòng mặc định
            gui.logSystem("[KICK] " + targetUser + " bị kick bởi " + this.username);
            break;
        }
    }
}
```

═══════════════════════════════════════════════════════════════════════════════
PHẦN 5: DEPLOY LÊN CLOUD
═══════════════════════════════════════════════════════════════════════════════

TỪ WINDOWS (CMD):

1. Copy file mới lên cloud:
```bash
scp f:\2025-2026\2026\LapTrinhMang\Multicast\src\Chat\server\view\ServerGUI.java ^
    root@159.65.134.130:~/MultiChat/src/Chat/server/view/

scp f:\2025-2026\2026\LapTrinhMang\Multicast\src\Chat\server\network\ClientHandler.java ^
    root@159.65.134.130:~/MultiChat/src/Chat/server/network/
```

TỪ CLOUD (SSH):

2. Recompile:
```bash
ssh root@159.65.134.130

cd ~/MultiChat

# Kill
pkill -9 -f java

# Clean
rm -rf build dist

# Compile
ant compile

# Run
export CHAT_DB_URL="jdbc:mysql://localhost:3306/ChatAppDB"
export CHAT_DB_USER="chatuser"
export CHAT_DB_PASS="ChatApp@123"
export CHAT_SERVER_PORT="5000"

java -cp "build/classes:lib/*" Chat.server.Main
```

═══════════════════════════════════════════════════════════════════════════════
PHẦN 6: TEST CHECKLIST
═══════════════════════════════════════════════════════════════════════════════

✅ TEST 1 - GUI Admin cập nhật số user online:
- Mở GUI Admin (phía server)
- Mở Client 1 vào Room 1
- Kiểm tra: GUI phải show "1 người" ở phần "Người Dùng Online"
- Mở Client 2 vào Room 1
- Kiểm tra: GUI phải show "2 người"

✅ TEST 2 - Log tin nhắn từ nhiều client:
- Client 1 gửi: "Hello"
- Kiểm tra: Server log phải show "[HH:mm:ss] 💬 Client1 (Phòng 1): Hello"
- Client 2 gửi: "Hi"
- Kiểm tra: Server log phải show "[HH:mm:ss] 💬 Client2 (Phòng 1): Hi"

✅ TEST 3 - Realtime user list update:
- Client 1 và Client 2 ở Room 1
- Client 1 thấy "Client1, Client2" online
- Client 2 thấy "Client1, Client2" online
- Client 1 gửi tin
- Client 2 nhận ngay (realtime)

✅ TEST 4 - Kick user:
- Admin click "Kick User" button trên GUI
- Chọn user cần kick
- Kiểm tra: User bị kick sẽ nhận "Bạn bị kick..."
- Kiểm tra: Server log hiển thị "[KICK] ..."

═══════════════════════════════════════════════════════════════════════════════
PHẦN 7: CÁCH LÀM CHI TIẾT - BƯỚC TỪ BƯỚC
═══════════════════════════════════════════════════════════════════════════════

BƯ BƯỚC 1: Sửa ServerGUI.java
────────────────────────────
1. Mở file: f:\2025-2026\2026\LapTrinhMang\Multicast\src\Chat\server\view\ServerGUI.java
2. Tìm class ServerGUI
3. Thêm method updateOnlineUserCount() (xem PHẦN 1)
4. Tìm method logJoin() → thêm updateOnlineUserCount()
5. Tìm method logLeave() → thêm updateOnlineUserCount()
6. Thêm method logMessageDetail() (xem PHẦN 2)

BƯỚC 2: Sửa ClientHandler.java
──────────────────────────────
1. Mở file: f:\2025-2026\2026\LapTrinhMang\Multicast\src\Chat\server\network\ClientHandler.java
2. Tìm method handleProtocol() → case CHAT
3. Sửa như ở PHẦN 3
4. Verify handleKickRequest() (xem PHẦN 4)

BƯỚC 3: Upload lên Cloud
─────────────────────────
1. Mở CMD
2. Copy file mới (xem PHẦN 5)

BƯỚC 4: Recompile trên Cloud
─────────────────────────────
1. SSH vào cloud
2. Chạy commands (xem PHẦN 5)

BƯỚC 5: Test (PHẦN 6)
──────────────────────

═══════════════════════════════════════════════════════════════════════════════
⚠️ LƯỚI: 
- Phải sync file java từ Windows lên Cloud trước khi compile
- Phải kill process java cũ trước khi compile
- Phải xóa build cũ (rm -rf build dist)
- Phải set biến môi trường CHAT_DB_URL, CHAT_DB_USER, CHAT_DB_PASS

═══════════════════════════════════════════════════════════════════════════════

Hãy làm theo từng bước, báo cáo progress!
