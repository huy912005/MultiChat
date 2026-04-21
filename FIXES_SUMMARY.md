# 🔧 Sửa Lỗi Real-Time Chat Updates

## Vấn đề đã giải quyết

### ❌ Vấn đề cũ:
1. **ServerGUI không cập nhật danh sách user** - chỉ đếm số lượng, không hiển thị tên
2. **Client không thấy user khác online** - USER_LIST không được gửi đều đặn
3. **Tin nhắn không sync real-time** - phải disconnect/reconnect mới thấy
4. **JOIN/LEAVE notification không broadcast** - mọi user không biết ai vào/ra

### ✅ Sửa chữa thực hiện:

#### 1. **ServerGUI.refreshUserList()** (src/Chat/server/view/ServerGUI.java)
```java
// CŨ: Chỉ đếm số lượng
int totalUsers = members.size();

// MỚI: Lấy danh sách từng user từ tất cả phòng
Set<String> uniqueUsers = new LinkedHashSet<>();
for (List<ClientHandler> members : rooms.values()) {
    for (ClientHandler ch : members) {
        uniqueUsers.add(ch.getUsername());
    }
}
for (String username : uniqueUsers) {
    userListModel.addElement(username);  // ✅ Thêm vào UI
}
```

#### 2. **ClientHandler.joinRoom()** (src/Chat/server/network/ClientHandler.java)
```java
// ✅ Khi vào phòng mới:
// 1. Broadcast LEAVE notification cho phòng cũ
// 2. Xóa khỏi danh sách phòng cũ
// 3. Thêm vào danh sách phòng mới
// 4. Broadcast USER_LIST cập nhật
// 5. Broadcast JOIN notification
```

#### 3. **ClientHandler.leaveCurrentRoom()** (src/Chat/server/network/ClientHandler.java)
```java
// ✅ Khi rời phòng:
// 1. Broadcast USER_LIST cập nhật (danh sách mới không có user này)
// 2. Broadcast LEAVE notification với tên user
// 3. Update database trạng thái
```

#### 4. **ClientHandler.broadcastUserList()** (src/Chat/server/network/ClientHandler.java)
```java
// ✅ Thêm dòng này cuối function
gui.updateOnlineUserCount();  // Cập nhật ServerGUI
```

#### 5. **ChatController.onMessageReceived()** (src/Chat/client/controller/ChatController.java)
```java
// ✅ Xử lý MESSAGE type JOIN
case JOIN:
    view.addSystemMessage("👋 " + message.getSender() + " đã vào phòng");
    break;

// ✅ Xử lý MESSAGE type LEAVE
case LEAVE:
    view.addSystemMessage("👋 " + message.getSender() + " rời phòng");
    break;
```

## 🎯 Kết quả sau sửa:

✓ **User mới vào phòng được hiển thị ngay trên ServerGUI**
✓ **Mọi user thấy danh sách user khác online khi vào phòng**
✓ **Tin nhắn được gửi/nhận thực thời không phải disconnect-reconnect**
✓ **Thông báo JOIN/LEAVE được broadcast cho tất cả user trong phòng**
✓ **Danh sách user update động khi ai vào/ra**

## 🚀 Cách test trên cloud:

1. **Compile code:**
   ```bash
   cd f:\2025-2026\2026\LapTrinhMang\Multicast
   .\build.bat
   ```

2. **Khởi động server trên cloud:**
   ```bash
   java -cp "build/classes:lib/*" Chat.server.Main
   ```

3. **Khởi động client từ máy khác:**
   ```bash
   java -cp "build/classes:lib/*" Chat.client.Main
   ```

4. **Test scenario:**
   - Mở 2-3 clients từ máy khác nhau
   - User A vào phòng, xem ServerGUI có hiển thị không
   - Gửi tin nhắn, kiểm tra các user khác thấy không
   - Kick user từ ServerGUI, kiểm tra user bị kick có nhận notification không
   - Mọi user khác thấy user vừa bị kick rời phòng chưa

## 📝 Commit info:
- **Commit hash:** fc59bbf
- **Files sửa:** 
  - src/Chat/server/view/ServerGUI.java
  - src/Chat/server/network/ClientHandler.java
  - src/Chat/client/controller/ChatController.java

## 🔐 Để push lên VPS:

**Cách 1: Dùng script (Windows):**
```bash
.\push-to-vps.bat
```

**Cách 2: Dùng script (Linux/Mac):**
```bash
chmod +x push-to-vps.sh
./push-to-vps.sh
```

**Cách 3: Manual (cần setup SSH key trước):**
```bash
git remote add vps ssh://root@159.65.134.130/repo/path
git push vps main
```

**⚠️ Lưu ý:** 
- Để push không cần nhập password, cần setup SSH key
- Hiện tại sử dụng sshpass với password trong script
- Không commit file push-to-vps.bat/sh nếu chứa password
