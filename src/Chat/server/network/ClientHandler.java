package Chat.server.network;

import Chat.Dao.DBContext;
import Chat.server.model.Message;
import Chat.server.view.ServerLogger;
import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ServerLogger gui;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private int currentRoomId = -1; // -1 nghĩa là chưa ở phòng nào
    private Map<String, ClientHandler> clients;

    public ClientHandler(Socket socket, ServerLogger gui, Map<String, ClientHandler> clients) {
        this.socket = socket;
        this.gui = gui;
        this.clients = clients;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // 1. Xử lý tin nhắn AUTH ban đầu (Đăng nhập / Đăng ký)
            String firstLine = in.readLine();
            if (firstLine != null) {
                Message authMsg = Message.fromNetworkString(firstLine);
                if (authMsg == null || authMsg.getType() != Message.Type.AUTH) {
                    sendMessage(new Message("Hệ thống", "FAIL|Yêu cầu xác thực không hợp lệ.", Message.Type.AUTH_RESULT));
                    return;
                }

                String authUser = authMsg.getSender() != null ? authMsg.getSender().trim() : "";
                String authContent = authMsg.getContent() != null ? authMsg.getContent() : "";
                String[] authParts = authContent.split("\\|", 2);
                if (authUser.isEmpty() || authParts.length < 2) {
                    sendMessage(new Message("Hệ thống", "FAIL|Thiếu thông tin tài khoản.", Message.Type.AUTH_RESULT));
                    return;
                }

                String action = authParts[0].trim().toUpperCase();
                String password = authParts[1];

                String authError = authenticate(action, authUser, password);
                if (authError != null) {
                    sendMessage(new Message("Hệ thống", "FAIL|" + authError, Message.Type.AUTH_RESULT));
                    return;
                }

                this.username = authUser;
                sendMessage(new Message("Hệ thống", "OK|Xác thực thành công.", Message.Type.AUTH_RESULT));

                if ("ADMIN_LOGIN".equals(action)) {
                    Server.registerAdmin(this);
                    gui.logSystem("Admin GUI da ket noi tu " + socket.getInetAddress().getHostAddress());
                    sendMessage(new Message("SYS", "Da ket noi luong Log Cloud.", Message.Type.ADMIN_LOG));

                    // Admin process loop
                    String line;
                    while ((line = in.readLine()) != null) {
                        try {
                            Message msg = Message.fromNetworkString(line);
                            if (msg == null) continue;
                            if (msg.getType() == Message.Type.ADMIN_KICK) {
                                String targetUser = msg.getContent();
                                ClientHandler ch = clients.get(targetUser);
                                if (ch != null) {
                                    ch.kickByAdmin();
                                    Server.broadcastToAdmins(new Message("SYS", "Da kick: " + targetUser, Message.Type.ADMIN_LOG));
                                } else {
                                    sendMessage(new Message("ERROR", "User " + targetUser + " khong online", Message.Type.ADMIN_LOG));
                                }
                            } else if (msg.getType() == Message.Type.ADMIN_CREATE_ROOM) {
                                // content = "tenRoom|gioiHan"
                                String[] p = msg.getContent().split("\\|", 2);
                                if (p.length == 2) {
                                    adminCreateRoom(p[0].trim(), Integer.parseInt(p[1].trim()));
                                }
                            } else if (msg.getType() == Message.Type.ADMIN_EDIT_ROOM) {
                                // content = "roomId|tenRoom|gioiHan"
                                String[] p = msg.getContent().split("\\|", 3);
                                if (p.length == 3) {
                                    adminEditRoom(Integer.parseInt(p[0].trim()), p[1].trim(), Integer.parseInt(p[2].trim()));
                                }
                            } else if (msg.getType() == Message.Type.DELETE_ROOM) {
                                int dRoomId = Integer.parseInt(msg.getContent().trim());
                                // Kick tat ca roi xoa
                                java.util.List<ClientHandler> mems = Server.getRoomGroups().get(dRoomId);
                                if (mems != null) {
                                    java.util.List<ClientHandler> copy = new java.util.ArrayList<>(mems);
                                    for (ClientHandler ch : copy) { try { ch.joinRoom(1); } catch (Exception ignored) {} }
                                }
                                String[] sqls = {"DELETE FROM Message WHERE maRoom = ?",
                                    "DELETE FROM UserRoom WHERE maRoom = ?", "DELETE FROM Room WHERE maRoom = ?"};
                                try (Connection conn = DBContext.getConnection()) {
                                    for (String sq : sqls) {
                                        try (PreparedStatement ps = conn.prepareStatement(sq)) {
                                            ps.setInt(1, dRoomId); ps.executeUpdate();
                                        }
                                    }
                                } catch (Exception ex) { gui.logError("Loi xoa phong: " + ex.getMessage()); }
                                Server.getRoomGroups().remove(dRoomId);
                                gui.logSystem("[ADMIN] Da xoa phong ID=" + dRoomId);
                                Server.broadcastToAdmins(new Message("SYS", "[ADMIN] Da xoa phong ID=" + dRoomId, Message.Type.ADMIN_LOG));
                                broadcastRoomListToAll();
                            }
                        } catch (Exception e) { gui.logError("Admin cmd error: " + e.getMessage()); }
                    }
                    Server.removeAdmin(this);
                    return;
                }

                clients.put(username, this);
                gui.logSystem("User '" + username + "' đã đăng nhập.");
                gui.logJoin(username, socket.getInetAddress().getHostAddress());
                gui.updateOnlineUserCount();  // ✅ CẬP NHẬT SỐ USER ONLINE
                Server.broadcastToAdmins(new Message("JOIN", username + " đã kết nối", Message.Type.ADMIN_LOG));

                // Gửi danh sách phòng cho client trước
                sendRoomListToClient();

                // Vào phòng mặc định (ID = 1)
                joinRoom(1);
            }

            // 2. Vòng lặp nhận tin nhắn
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    Message msg = Message.fromNetworkString(line);
                    if (msg != null) {
                        handleProtocol(msg);
                    }
                } catch (Exception e) {
                    gui.logError("Lỗi vòng lặp client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            String who = (username == null) ? "client chưa xác thực" : username;
            gui.logError("Kết nối với " + who + " bị ngắt.");
        } finally {
            if (username != null) {
                leaveCurrentRoom();
                clients.remove(username);
                updateUserStatus(username, "OFFLINE"); // Cập nhật DB khi disconnect
                gui.logLeave(username, clients.size());
                Server.broadcastToAdmins(new Message("LEAVE", username + " đã rời đi", Message.Type.ADMIN_LOG));
            } else {
                Server.removeAdmin(this);
            }
            try { socket.close(); } catch (IOException ex) {}
        }
    }

    private void handleProtocol(Message msg) {
        switch (msg.getType()) {
            case CHAT:
                saveMessageToDB(msg, currentRoomId);
                broadcastToRoom(currentRoomId, msg);
                gui.logMessageDetail(msg.getSender(), String.valueOf(currentRoomId), msg.getContent());
                gui.logChat(msg.getSender(), "(" + currentRoomId + "): " + msg.getContent());
                Server.broadcastToAdmins(new Message("CHAT", "[" + msg.getSender() + "] (" + currentRoomId + "): " + msg.getContent(), Message.Type.ADMIN_LOG));
                break;

            case JOIN_ROOM:
                try {
                    int targetRoomId = Integer.parseInt(msg.getContent());
                    joinRoom(targetRoomId);
                } catch (NumberFormatException e) {
                    // Nen la room code 5 ky tu
                    joinRoomByCode(msg.getContent().trim());
                }
                break;

            case CREATE_ROOM:
                // content = "roomName" or "roomName|limit"
                handleCreateRoom(msg.getContent());
                break;

            case DELETE_ROOM:
                handleDeleteRoom(msg.getContent());
                break;

            case ROOM_CODE_SEARCH:
                handleRoomCodeSearch(msg.getContent());
                break;

            case KICK:
                handleKickRequest(msg.getContent());
                break;

            case LEAVE_ROOM:
                joinRoom(1);
                break;
        }
    }

    // --- XỬ LÝ DATABASE ---

    private String authenticate(String action, String user, String pass) {
        if (user.isBlank() || pass == null || pass.isBlank()) {
            return "Tên đăng nhập và mật khẩu không được để trống.";
        }
        if (clients.containsKey(user)) {
            return "Tài khoản đang online ở thiết bị khác.";
        }
        if ("REGISTER".equals(action)) {
            return registerUser(user, pass);
        }
        if ("LOGIN".equals(action)) {
            return loginUser(user, pass);
        }
        if ("ADMIN_LOGIN".equals(action)) {
            if ("admin123".equals(pass)) return null;
            return "Sai mật khẩu admin.";
        }
        return "Hành động xác thực không hợp lệ.";
    }

    private String registerUser(String user, String pass) {
        String checkSql = "SELECT 1 FROM User WHERE tenUser = ?";
        String insertSql = "INSERT INTO User (tenUser, password, trangThai) VALUES (?, ?, 'ONLINE')";
        try (Connection conn = DBContext.getConnection()) {
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, user);
                ResultSet rs = checkPs.executeQuery();
                if (rs.next()) {
                    return "Tên đăng nhập đã tồn tại.";
                }
            }
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setString(1, user);
                insertPs.setString(2, pass);
                insertPs.executeUpdate();
            }
            return null;
        } catch (Exception e) {
            gui.logError("DB Error (registerUser): " + e.getMessage());
            return "Lỗi hệ thống khi đăng ký.";
        }
    }

    private String loginUser(String user, String pass) {
        String sql = "SELECT password FROM User WHERE tenUser = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return "Tài khoản không tồn tại.";
            }
            String dbPassword = rs.getString("password");
            if (dbPassword == null || !dbPassword.equals(pass)) {
                return "Sai mật khẩu.";
            }
            updateUserStatus(user, "ONLINE");
            return null;
        } catch (Exception e) {
            gui.logError("DB Error (loginUser): " + e.getMessage());
            return "Lỗi hệ thống khi đăng nhập.";
        }
    }

    /** Cập nhật trạng thái online/offline cho user */
    private void updateUserStatus(String user, String status) {
        String sql = "UPDATE User SET trangThai = ? WHERE tenUser = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (Exception e) {
            gui.logError("DB Error (updateStatus): " + e.getMessage());
        }
    }

    private void saveMessageToDB(Message msg, int roomId) {
        String sql = "INSERT INTO Message (noiDung, maUser, maRoom, thoiGian, trangThai) " +
                     "VALUES (?, (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1), ?, NOW(), 'SENT')";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getContent());
            ps.setString(2, msg.getSender());
            ps.setInt(3, roomId);
            ps.executeUpdate();
        } catch (Exception e) {
            // FALLBACK: Thử bỏ cột trangThai nếu DB cũ không chấp nhận 'SENT'
            try {
                String fallback = "INSERT INTO Message (noiDung, maUser, maRoom, thoiGian) VALUES (?, (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1), ?, NOW())";
                try (Connection conn2 = DBContext.getConnection();
                     PreparedStatement ps2 = conn2.prepareStatement(fallback)) {
                    ps2.setString(1, msg.getContent());
                    ps2.setString(2, msg.getSender());
                    ps2.setInt(3, roomId);
                    ps2.executeUpdate();
                }
            } catch (Exception ex) {
                gui.logError("DB Error (saveMsg Fallback): " + ex.getMessage());
            }
        }
    }

    private void loadRoomHistory(int roomId) {
        String sql = "SELECT * FROM ( " +
                     "  SELECT u.tenUser, m.noiDung, DATE_FORMAT(m.thoiGian, '%d/%m %H:%i') as thoiGianStr, m.thoiGian " +
                     "  FROM Message m JOIN User u ON m.maUser = u.maUser " +
                     "  WHERE m.maRoom = ? ORDER BY m.thoiGian DESC LIMIT 50 " +
                     ") AS sub ORDER BY thoiGian ASC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Message oldMsg = new Message(rs.getString("tenUser"), rs.getString("noiDung"));
                oldMsg.setTimestamp(rs.getString("thoiGianStr"));
                sendMessage(oldMsg);
            }
        } catch (Exception e) {
            gui.logError("DB Error (loadHistory): " + e.getMessage());
        }
    }

    // --- XỬ LÝ LOGIC PHÒNG ---

    void joinRoom(int roomId) {
        if (roomId <= 0) {
            sendMessage(new Message("Hệ thống", "Phòng không hợp lệ.", Message.Type.SYSTEM));
            return;
        }

        if (roomId == currentRoomId) {
            String roomName = getRoomNameFromDB(roomId);
            sendMessage(new Message("Hệ thống", roomId + ":" + roomName, Message.Type.JOIN_ROOM));
            sendMessage(new Message("Hệ thống", "Bạn đang ở phòng " + roomName + ".", Message.Type.SYSTEM));
            return;
        }

        String enterError = tryEnterRoom(roomId);
        if (enterError != null) {
            sendMessage(new Message("Hệ thống", enterError, Message.Type.SYSTEM));
            return;
        }

        // Khong gui thong bao LEAVE khi chuyen phong (chi gui khi ngat ket noi han)
        if (currentRoomId > 0) {
            List<ClientHandler> oldMembers = Server.getRoomGroups().get(currentRoomId);
            if (oldMembers != null) {
                synchronized (oldMembers) {
                    oldMembers.remove(this);
                }
                broadcastUserList(currentRoomId);
            }
        }
        
        this.currentRoomId = roomId;
        List<ClientHandler> roomMembers = Server.getRoomGroups().computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        synchronized (roomMembers) {
            roomMembers.add(this);
        }

        String roomName = getRoomNameFromDB(roomId);
        sendMessage(new Message("Hệ thống", roomId + ":" + roomName, Message.Type.JOIN_ROOM));
        loadRoomHistory(roomId);

        broadcastUserList(roomId);
        
        // Chi thong bao cho NHUNG NGUOI KHAC trong phong
        Message joinNotification = new Message("He thong", username, Message.Type.JOIN);
        List<ClientHandler> members = Server.getRoomGroups().get(roomId);
        if (members != null) {
            for (ClientHandler ch : members) {
                if (ch != this) {
                    ch.sendMessage(joinNotification);
                }
            }
        }

        broadcastRoomListToAll();
        gui.updateOnlineUserCount();
        gui.logSystem(username + " đã vào phòng " + roomId + " (" + roomName + ")");
    }

    private void leaveCurrentRoom() {
        if (currentRoomId <= 0) return;

        int tempRoomId = currentRoomId;
        currentRoomId = -1;
        
        List<ClientHandler> members = Server.getRoomGroups().get(tempRoomId);
        if (members != null) {
            synchronized (members) {
                members.remove(this);
            }
            broadcastUserList(tempRoomId);
            Message leaveNotification = new Message("Hệ thống", username, Message.Type.LEAVE);
            broadcastToRoom(tempRoomId, leaveNotification);
        }
        broadcastRoomListToAll();
        markUserRoomStatus(tempRoomId, "LEFT");
        gui.updateOnlineUserCount();
    }

    private void handleCreateRoom(String content) {
        // content co the la "roomName" hoac "roomName|limit"
        String roomName;
        int limit = 50;
        if (content.contains("|")) {
            String[] p = content.split("\\|", 2);
            roomName = p[0].trim();
            try { limit = Integer.parseInt(p[1].trim()); } catch (Exception ignored) {}
        } else {
            roomName = content.trim();
        }
        // Tao ma phong 5 ky tu duy nhat
        String roomCode = generateRoomCode();
        String sql = "INSERT INTO Room (tenRoom, gioiHan, maUser, roomCode) VALUES (?, ?, (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1), ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, roomName);
            ps.setInt(2, limit);
            ps.setString(3, username);
            ps.setString(4, roomCode);
            ps.executeUpdate();
            gui.logSystem("User '" + username + "' da tao phong: " + roomName + " (Ma: " + roomCode + ")");
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int newId = rs.getInt(1);
                // Join phong TRUOC - joinRoom se clear chat cu, broadcast room list
                joinRoom(newId);
                broadcastRoomListToAll();
                // Sau do moi gui ROOM_CODE - se hien thi trong phong moi, khong bi clear
                sendMessage(new Message("System", "ROOM_CODE:" + roomCode, Message.Type.SYSTEM));
            }
        } catch (Exception e) {
            gui.logError("DB Error (createRoom): Ban chua chay lenh them cot maUser va roomCode vao bang Room tren Cloud. Loi chi tiet: " + e.getMessage());
            sendMessage(new Message("System", "Loi server: Database thieu cot maUser/roomCode. Yeu cau Admin chay migrate_db.sql tren Cloud!", Message.Type.SYSTEM));
        }
    }

    /** Admin tao phong tu xa */
    private void adminCreateRoom(String name, int limit) {
        String sql = "INSERT INTO Room (tenRoom, gioiHan) VALUES (?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, limit);
            ps.executeUpdate();
            gui.logSystem("[ADMIN] Da tao phong: " + name);
            Server.broadcastToAdmins(new Message("SYS", "[ADMIN] Da tao phong: " + name, Message.Type.ADMIN_LOG));
            broadcastRoomListToAll();
        } catch (Exception e) {
            gui.logError("DB Error (adminCreateRoom): " + e.getMessage());
            sendMessage(new Message("ERROR", "Loi tao phong: " + e.getMessage(), Message.Type.ADMIN_LOG));
        }
    }

    /** Admin sua phong tu xa */
    private void adminEditRoom(int roomId, String name, int limit) {
        String sql = "UPDATE Room SET tenRoom = ?, gioiHan = ? WHERE maRoom = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, limit);
            ps.setInt(3, roomId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                gui.logSystem("[ADMIN] Da sua phong ID=" + roomId + " -> " + name);
                Server.broadcastToAdmins(new Message("SYS", "[ADMIN] Da sua phong ID=" + roomId, Message.Type.ADMIN_LOG));
                broadcastRoomListToAll();
            } else {
                sendMessage(new Message("ERROR", "Khong tim thay phong ID=" + roomId, Message.Type.ADMIN_LOG));
            }
        } catch (Exception e) {
            gui.logError("DB Error (adminEditRoom): " + e.getMessage());
        }
    }

    /** Xoa phong - chi chu phong moi duoc xoa */
    private void handleDeleteRoom(String content) {
        int roomId;
        try { roomId = Integer.parseInt(content.trim()); }
        catch (Exception e) { return; }
        if (roomId == 1) {
            sendMessage(new Message("System", "Khong the xoa phong mac dinh!", Message.Type.SYSTEM));
            return;
        }
        // Kiem tra chu phong
        String checkSql = "SELECT maUser FROM Room WHERE maRoom = ?";
        String getUserSql = "SELECT maUser FROM User WHERE tenUser = ?";
        try (Connection conn = DBContext.getConnection()) {
            int ownerUserId = -1;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, roomId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) ownerUserId = rs.getInt(1);
            }
            int myUserId = -1;
            try (PreparedStatement ps = conn.prepareStatement(getUserSql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) myUserId = rs.getInt(1);
            }
            // Chỉ chủ phòng thực sự mới được xoá (loại trừ luôn phòng public không chủ nếu ai đó cố xoá)
            if (ownerUserId == -1 || ownerUserId != myUserId) {
                sendMessage(new Message("System", "Ban khong co quyen xoa phong nay (chi chu phong moi duoc xoa)!", Message.Type.SYSTEM));
                return;
            }
            // Kick moi nguoi ra phong 1 truoc
            List<ClientHandler> members = Server.getRoomGroups().get(roomId);
            if (members != null) {
                List<ClientHandler> copy;
                synchronized (members) { copy = new ArrayList<>(members); }
                for (ClientHandler ch : copy) { ch.joinRoom(1); }
            }
            // Xoa du lieu
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Message WHERE maRoom = ?")) {
                ps.setInt(1, roomId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM UserRoom WHERE maRoom = ?")) {
                ps.setInt(1, roomId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Room WHERE maRoom = ?")) {
                ps.setInt(1, roomId); ps.executeUpdate();
            }
            gui.logSystem("Da xoa phong ID=" + roomId + " boi " + username);
            broadcastRoomListToAll();
        } catch (Exception e) {
            gui.logError("DB Error (deleteRoom): " + e.getMessage());
        }
    }

    /** Tim kiem phong theo ma 5 ky tu */
    private void handleRoomCodeSearch(String code) {
        String sql = "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan,999) FROM Room WHERE roomCode = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String info = rs.getInt(1) + ":" + rs.getString(2) + ":" + rs.getInt(3) + ":" + rs.getInt(4);
                sendMessage(new Message("System", info, Message.Type.ROOM_LIST));
            } else {
                sendMessage(new Message("System", "NOT_FOUND:Khong tim thay phong voi ma: " + code, Message.Type.SYSTEM));
            }
        } catch (Exception e) {
            // DB chua co cot roomCode - tra ve danh sach phong binh thuong
            sendRoomListToClient();
        }
    }

    /** Vao phong bang ma 5 ky tu */
    private void joinRoomByCode(String code) {
        String sql = "SELECT maRoom FROM Room WHERE roomCode = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                joinRoom(rs.getInt(1));
            } else {
                sendMessage(new Message("System", "Khong tim thay phong voi ma: " + code, Message.Type.SYSTEM));
            }
        } catch (Exception e) {
            sendMessage(new Message("System", "Loi tim phong: " + e.getMessage(), Message.Type.SYSTEM));
        }
    }

    /** Tao ma phong 5 so duy nhat (0-9) */
    private String generateRoomCode() {
        String chars = "0123456789";
        java.util.Random rnd = new java.util.Random();
        String checkSql = "SELECT 1 FROM Room WHERE roomCode = ?";
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
            String code = sb.toString();
            try (Connection conn = DBContext.getConnection();
                 PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, code);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return code; // Chua ton tai
            } catch (Exception e) { return code; }
        }
        return "XXXXX";
    }

    private void handleKickRequest(String targetUser) {
        // Kiểm tra quyền (Ví dụ: Chỉ chủ phòng hoặc Admin mới được kick)
        List<ClientHandler> members = Server.getRoomGroups().get(currentRoomId);
        if (members == null) return; // null check để tránh NullPointerException
        
        // Sử dụng CopyOnWriteArrayList hoặc copy danh sách để tránh ConcurrentModificationException
        List<ClientHandler> membersCopy;
        synchronized (members) {
            membersCopy = new ArrayList<>(members);
        }
        
        for (ClientHandler ch : membersCopy) {
            if (ch.username != null && ch.username.equals(targetUser)) {
                ch.sendMessage(new Message("Hệ thống", "Bạn bị kick khỏi phòng bởi " + this.username, Message.Type.SYSTEM));
                ch.joinRoom(1); // Trả về phòng mặc định
                break;
            }
        }
    }

    private void broadcastToRoom(int roomId, Message msg) {
        List<ClientHandler> members = Server.getRoomGroups().get(roomId);
        if (members != null) {
            // Copy danh sách để tránh ConcurrentModificationException
            List<ClientHandler> membersCopy;
            synchronized (members) {
                membersCopy = new ArrayList<>(members);
            }
            
            // Gửi tin nhắn cho từng client - đảm bảo mỗi client đều nhận được
            for (ClientHandler ch : membersCopy) {
                try {
                    if (ch != null) {
                        ch.sendMessage(msg);
                    }
                } catch (Exception e) {
                    // Nếu 1 client lỗi, không làm crash server - tiếp tục gửi cho client khác
                    gui.logError("Lỗi gửi tin nhắn cho " + (ch != null ? ch.getUsername() : "client") + ": " + e.getMessage());
                }
            }
        }
    }

    private void broadcastUserList(int roomId) {
        List<ClientHandler> members = Server.getRoomGroups().get(roomId);
        if (members != null) {
            List<ClientHandler> membersCopy;
            synchronized (members) {
                membersCopy = new ArrayList<>(members);
            }
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < membersCopy.size(); i++) {
                ClientHandler ch = membersCopy.get(i);
                if (ch != null && ch.username != null) {
                    sb.append(ch.username);
                    if (i < membersCopy.size() - 1) sb.append(",");
                }
            }
            
            gui.logSystem("[DEBUG] Broadcast user list for room " + roomId + ": " + sb.toString() + " (" + membersCopy.size() + " users)");
            Message listMsg = new Message("Hệ thống", sb.toString(), Message.Type.USER_LIST);
            
            for (ClientHandler ch : membersCopy) {
                try {
                    if (ch != null) {
                        ch.sendMessage(listMsg);
                    }
                } catch (Exception e) {
                    gui.logError("Loi gui danh sach user cho " + (ch != null ? ch.getUsername() : "client") + ": " + e.getMessage());
                }
            }
            gui.updateOnlineUserCount();
        }
    }

    public void sendMessage(Message msg) {
        try {
            out.println(msg.toNetworkString());
            out.flush();
        } catch (Exception e) {
            gui.logError("Loi gui tin nhan cho " + username + ": " + e.getMessage());
        }
    }

    public String getUsername() { return username; }

    /**
     * Gui danh sach phong cho client nay:
     *  - Phong CONG KHAI: Admin tao (maUser IS NULL) -> hien voi tat ca
     *  - Phong RIENG TU: Client tao (maUser IS NOT NULL) -> chi hien voi:
     *      + Chinh nguoi tao phong do
     *      + Nguoi da tung join phong do qua ma
     */
    private void sendRoomListToClient() {
        StringBuilder sb = new StringBuilder();
        // SQL chinh: dung ca maUser va roomCode cot
        String sql =
            "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) FROM Room " +
            "WHERE (maUser IS NULL) " +
            "   OR (maUser = (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1)) " +
            "   OR (maRoom IN (SELECT maRoom FROM UserRoom " +
            "                  WHERE maUser = (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1))) " +
            "ORDER BY maRoom";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(rs.getInt(1)).append(":").append(rs.getString(2)).append(":")
                      .append(rs.getInt(3)).append(":").append(rs.getInt(4));
                }
            }
            gui.logSystem("[RoomList] Gui cho " + username + ": " + sb.toString());
        } catch (Exception e) {
            // FALLBACK: neu DB chua co cot maUser trong Room, dung roomCode
            gui.logSystem("[DEBUG] sendRoomList primary failed, fallback: " + e.getMessage());
            String fallbackSql =
                "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) FROM Room " +
                "WHERE (roomCode IS NULL OR roomCode = '') " +
                "   OR (maRoom IN (SELECT maRoom FROM UserRoom " +
                "                  WHERE maUser = (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1))) " +
                "ORDER BY maRoom";
            try (Connection conn = DBContext.getConnection();
                 PreparedStatement ps = conn.prepareStatement(fallbackSql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(rs.getInt(1)).append(":").append(rs.getString(2)).append(":")
                          .append(rs.getInt(3)).append(":").append(rs.getInt(4));
                    }
                }
            } catch (Exception ex) {
                gui.logError("DB Error (sendRoomList Fallback): " + ex.getMessage());
                // Last resort: tra ve phong mac dinh
                sb.setLength(0);
                sb.append("1:Sanh Chung:0:999");
            }
        }
        if (sb.length() > 0) {
            sendMessage(new Message("He thong", sb.toString(), Message.Type.ROOM_LIST));
        }
    }

    /** Broadcast lại danh sách phòng cho mọi client đang online */
    private void broadcastRoomListToAll() {
        // Copy danh sách clients để tránh ConcurrentModificationException
        List<ClientHandler> clientsList = new ArrayList<>(clients.values());
        
        for (ClientHandler ch : clientsList) {
            try {
                if (ch != null) {
                    ch.sendRoomListToClient();
                }
            } catch (Exception e) {
                gui.logError("Lỗi gửi danh sách phòng cho " + (ch != null ? ch.getUsername() : "client") + ": " + e.getMessage());
            }
        }
    }

    /** Lấy tên phòng từ DB theo ID */
    private String getRoomNameFromDB(int roomId) {
        String sql = "SELECT tenRoom FROM Room WHERE maRoom = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("tenRoom");
        } catch (Exception e) {
            gui.logError("DB Error (getRoomName): " + e.getMessage());
        }
        return "Phòng " + roomId;
    }

    /**
     * Kiểm tra giới hạn phòng theo số thành viên đã từng tham gia.
     * - Nếu user chưa từng tham gia phòng này: thêm vào UserRoom và tăng soLuongHienTai.
     * - Nếu user đã từng tham gia: cho vào lại, không tăng số lượng.
     * @return null nếu thành công, ngược lại là message lỗi.
     */
    private String tryEnterRoom(int roomId) {
        String selectSql = "SELECT tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) AS gioiHan FROM Room WHERE maRoom = ?";
        String userSql = "SELECT maUser FROM User WHERE tenUser = ?";
        String checkMemberSql = "SELECT 1 FROM UserRoom WHERE maUser = ? AND maRoom = ?";
        String insertMemberSql = "INSERT INTO UserRoom (maUser, maRoom, vaiTro, trangThai) VALUES (?, ?, 'MEMBER', 'ACTIVE')";
        String activateMemberSql = "UPDATE UserRoom SET trangThai = 'ACTIVE' WHERE maUser = ? AND maRoom = ?";
        String updateRoomCountSql = "UPDATE Room SET soLuongHienTai = soLuongHienTai + 1 WHERE maRoom = ?";

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement selectPs = conn.prepareStatement(selectSql);
                 PreparedStatement userPs = conn.prepareStatement(userSql);
                 PreparedStatement checkMemberPs = conn.prepareStatement(checkMemberSql);
                 PreparedStatement insertMemberPs = conn.prepareStatement(insertMemberSql);
                 PreparedStatement activateMemberPs = conn.prepareStatement(activateMemberSql);
                 PreparedStatement updateRoomCountPs = conn.prepareStatement(updateRoomCountSql)) {

                selectPs.setInt(1, roomId);
                ResultSet roomRs = selectPs.executeQuery();
                if (!roomRs.next()) {
                    conn.rollback();
                    return "Phòng không tồn tại.";
                }

                String roomName = roomRs.getString("tenRoom");
                int current = roomRs.getInt("soLuongHienTai");
                int limit = roomRs.getInt("gioiHan");

                userPs.setString(1, username);
                ResultSet userRs = userPs.executeQuery();
                if (!userRs.next()) {
                    conn.rollback();
                    return "Không tìm thấy thông tin người dùng.";
                }
                int userId = userRs.getInt("maUser");

                checkMemberPs.setInt(1, userId);
                checkMemberPs.setInt(2, roomId);
                ResultSet memberRs = checkMemberPs.executeQuery();
                boolean existed = memberRs.next();

                if (!existed) {
                    if (current >= limit) {
                        conn.rollback();
                        return "Phòng \"" + roomName + "\" đã đạt giới hạn thành viên (" + current + "/" + limit + ").";
                    }

                    insertMemberPs.setInt(1, userId);
                    insertMemberPs.setInt(2, roomId);
                    insertMemberPs.executeUpdate();

                    updateRoomCountPs.setInt(1, roomId);
                    updateRoomCountPs.executeUpdate();
                } else {
                    activateMemberPs.setInt(1, userId);
                    activateMemberPs.setInt(2, roomId);
                    activateMemberPs.executeUpdate();
                }

                conn.commit();
                return null;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            gui.logError("DB Error (tryEnterRoom): " + e.getMessage());
            return "Không thể vào phòng do lỗi hệ thống.";
        }
    }

    private void markUserRoomStatus(int roomId, String status) {
        String sql = "UPDATE UserRoom ur " +
                "JOIN User u ON ur.maUser = u.maUser " +
                "SET ur.trangThai = ? " +
                "WHERE ur.maRoom = ? AND u.tenUser = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, roomId);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (Exception e) {
            gui.logError("DB Error (markUserRoomStatus): " + e.getMessage());
        }
    }

    /**
     * Được gọi bởi Admin qua GUI để kick user này ra khỏi server.
     * Gửi thông báo cho client, sau đó đóng socket.
     * finally block trong run() sẽ tự dọn dẹp (clients map, DB status...).
     */
    public void kickByAdmin() {
        try {
            sendMessage(new Message("Hệ thống",
                    "⚠️ Bạn đã bị Admin kick khỏi server!", Message.Type.SYSTEM));
            Thread.sleep(250); // cho tin nhắn kịp truyền đi
            socket.close();    // đóng socket → IOException → finally dọn dẹp
        } catch (Exception e) {
            gui.logError("Lỗi khi kick " + username + ": " + e.getMessage());
        }
    }
}