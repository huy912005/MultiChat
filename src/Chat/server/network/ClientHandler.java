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

                clients.put(username, this);
                gui.logSystem("User '" + username + "' đã đăng nhập.");
                gui.logJoin(username, socket.getInetAddress().getHostAddress());
                gui.updateOnlineUserCount();  // ✅ CẬP NHẬT SỐ USER ONLINE

                // Gửi danh sách phòng cho client trước
                sendRoomListToClient();

                // Vào phòng mặc định (ID = 1)
                joinRoom(1);
            }

            // 2. Vòng lặp nhận tin nhắn
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = Message.fromNetworkString(line);
                if (msg != null) {
                    handleProtocol(msg);
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
            }
            try { socket.close(); } catch (IOException ex) {}
        }
    }

    private void handleProtocol(Message msg) {
        switch (msg.getType()) {
            case CHAT:
                // Lưu vào DB trước khi gửi đi
                saveMessageToDB(msg, currentRoomId);
                broadcastToRoom(currentRoomId, msg);
                
                // ✅ LOG CHI TIẾT VỀ TIN NHẮN
                gui.logMessageDetail(msg.getSender(), String.valueOf(currentRoomId), msg.getContent());
                gui.logChat(msg.getSender(), "(" + currentRoomId + "): " + msg.getContent());
                break;

            case JOIN_ROOM:
                int targetRoomId = Integer.parseInt(msg.getContent());
                joinRoom(targetRoomId);
                break;

            case CREATE_ROOM:
                handleCreateRoom(msg.getContent());
                break;

            case KICK:
                handleKickRequest(msg.getContent());
                break;

            case LEAVE_ROOM:
                joinRoom(1); // Quay về phòng chờ chung
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
        String sql = "INSERT INTO Message (noiDung, maUser, maRoom, thoiGian) " +
                     "VALUES (?, (SELECT maUser FROM User WHERE tenUser = ? LIMIT 1), ?, NOW())";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getContent());
            ps.setString(2, msg.getSender());
            ps.setInt(3, roomId);
            ps.executeUpdate();
        } catch (Exception e) {
            gui.logError("DB Error (saveMsg): " + e.getMessage());
        }
    }

    private void loadRoomHistory(int roomId) {
        String sql = "SELECT u.tenUser, m.noiDung FROM Message m " +
                     "JOIN User u ON m.maUser = u.maUser " +
                     "WHERE m.maRoom = ? ORDER BY m.thoiGian ASC LIMIT 20";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Message oldMsg = new Message(rs.getString("tenUser"), rs.getString("noiDung"));
                sendMessage(oldMsg);
            }
        } catch (Exception e) {
            gui.logError("DB Error (loadHistory): " + e.getMessage());
        }
    }

    // --- XỬ LÝ LOGIC PHÒNG ---

    private void joinRoom(int roomId) {
        if (roomId <= 0) {
            sendMessage(new Message("Hệ thống", "Phòng không hợp lệ.", Message.Type.SYSTEM));
            return;
        }

        if (roomId == currentRoomId) {
            // Đã ở sẵn phòng này, chỉ cần đồng bộ lại UI client
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

        // Rời phòng cũ sau khi chắc chắn đã giữ được chỗ ở phòng mới
        leaveCurrentRoom();
        this.currentRoomId = roomId;

        // Thêm vào Map room trong Server (sử dụng CopyOnWriteArrayList để tránh ConcurrentModificationException)
        List<ClientHandler> roomMembers = Server.getRoomGroups().computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        synchronized (roomMembers) {
            roomMembers.add(this);
        }

        // Thông báo cho client phòng vừa vào (format: "roomId:roomName")
        String roomName = getRoomNameFromDB(roomId);
        sendMessage(new Message("Hệ thống", roomId + ":" + roomName, Message.Type.JOIN_ROOM));

        // Load lịch sử cho người mới vào
        sendMessage(new Message("Hệ thống", "Đang tải lịch sử phòng " + roomName + "...", Message.Type.SYSTEM));
        loadRoomHistory(roomId);

        // Thôi báo danh sách user mới cho mọi người trong phòng
        gui.logSystem("[DEBUG-JOIN] roomId=" + roomId + ", username=" + username);
        broadcastUserList(roomId);
        
        // Gửi thông báo cho tất cả client trong phòng biết user mới vừa join
        Message joinNotification = new Message("Hệ thống", username + " đã vào phòng", Message.Type.SYSTEM);
        broadcastToRoom(roomId, joinNotification);
        
        broadcastRoomListToAll();
        gui.logSystem(username + " đã vào phòng " + roomId + " (" + roomName + ")");
    }

    private void leaveCurrentRoom() {
        if (currentRoomId <= 0) return;

        List<ClientHandler> members = Server.getRoomGroups().get(currentRoomId);
        if (members != null) {
            synchronized (members) {  // Lock khi xóa để tránh race condition
                members.remove(this);
            }
            broadcastUserList(currentRoomId);
        }
        broadcastRoomListToAll();
        markUserRoomStatus(currentRoomId, "LEFT");
        gui.updateOnlineUserCount();  // ✅ CẬP NHẬT SỐ USER ONLINE
        currentRoomId = -1;
    }

    private void handleCreateRoom(String roomName) {
        // Chỉ dùng tenRoom và gioiHan (mặc định 50) — đúng schema bảng Room
        String sql = "INSERT INTO Room (tenRoom, gioiHan) VALUES (?, 50)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, roomName);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int newId = rs.getInt(1);
                sendMessage(new Message("Hệ thống", "Đã tạo phòng " + roomName + " thành công!", Message.Type.SYSTEM));
                joinRoom(newId);
            }
        } catch (Exception e) {
            gui.logError("DB Error (createRoom): " + e.getMessage());
        }
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
            // Copy danh sách để tránh ConcurrentModificationException
            List<ClientHandler> membersCopy;
            synchronized (members) {
                membersCopy = new ArrayList<>(members);
            }
            
            // Tạo danh sách usernames
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
            
            // Gửi danh sách user cập nhật cho từng client trong phòng
            for (ClientHandler ch : membersCopy) {
                try {
                    if (ch != null) {
                        ch.sendMessage(listMsg);
                    }
                } catch (Exception e) {
                    gui.logError("Lỗi gửi danh sách user cho " + (ch != null ? ch.getUsername() : "client") + ": " + e.getMessage());
                }
            }
        }
    }

    public void sendMessage(Message msg) {
        try {
            out.println(msg.toNetworkString());
            out.flush();  // Đảm bảo tin nhắn được gửi ngay lập tức
        } catch (Exception e) {
            gui.logError("Lỗi gửi tin nhắn cho " + username + ": " + e.getMessage());
        }
    }

    public String getUsername() { return username; }

    /** Gửi danh sách tất cả phòng cho client này */
    private void sendRoomListToClient() {
        String sql = "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) FROM Room ORDER BY maRoom";
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getInt(1)).append(":").append(rs.getString(2)).append(":")
                  .append(rs.getInt(3)).append(":").append(rs.getInt(4));
            }
        } catch (Exception e) {
            gui.logError("DB Error (sendRoomList): " + e.getMessage());
        }
        if (sb.length() > 0) {
            sendMessage(new Message("Hệ thống", sb.toString(), Message.Type.ROOM_LIST));
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