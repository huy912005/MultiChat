package Chat.server.network;

import Chat.Dao.DBContext;
import Chat.server.model.Message;
import Chat.server.view.ServerLogger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server: Điểm vào chính của máy chủ chat.
 * Quản lý kết nối client, phòng chat, và các hành động admin.
 */
public class Server {

    private static final int PORT        = resolvePort();
    private static final int MAX_THREADS = 50;

    // Map: username → ClientHandler (các client đang kết nối)
    private final ConcurrentHashMap<String, ClientHandler> clients;

    // Map tĩnh: roomId → danh sách ClientHandler trong phòng đó
    private static final ConcurrentHashMap<Integer, List<ClientHandler>> roomGroups =
            new ConcurrentHashMap<>();

    // Danh sách các admin remote đang kết nối
    private static final java.util.concurrent.CopyOnWriteArrayList<ClientHandler> adminClients =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private final ExecutorService threadPool;
    private final ServerLogger gui;

    public Server(ServerLogger gui) {
        this.gui        = gui;
        this.clients    = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
    }

    private static int resolvePort() {
        String envPort = System.getenv("CHAT_SERVER_PORT");
        if (envPort == null || envPort.isBlank()) {
            return 5000;
        }
        try {
            return Integer.parseInt(envPort.trim());
        } catch (NumberFormatException ex) {
            return 5000;
        }
    }

    public static ConcurrentHashMap<Integer, List<ClientHandler>> getRoomGroups() {
        return roomGroups;
    }

    public static void registerAdmin(ClientHandler admin) {
        adminClients.add(admin);
    }

    public static void removeAdmin(ClientHandler admin) {
        adminClients.remove(admin);
    }

    public static void broadcastToAdmins(Message msg) {
        for (ClientHandler admin : adminClients) {
            try {
                admin.sendMessage(msg);
            } catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Khởi động Server
    // ══════════════════════════════════════════════════════════════
    public void start() {
        gui.logSystem("Đang khởi động server trên cổng " + PORT + "...");
        ensureDefaultRoom(); // Phòng 1 luôn phải tồn tại

        // Cấp quyền admin cho GUI (cho phép kick/CRUD phòng)
        gui.setServer(this);

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
                gui.setServerStatus(true, PORT);
                String localIP = InetAddress.getLocalHost().getHostAddress();
                gui.logSystem("Server đã sẵn sàng! Địa chỉ: " + localIP + ":" + PORT);

                // Thread kiểm tra user bị kick từ DB (Do ServerGUI thực hiện từ xa)
                new Thread(() -> {
                    while (!serverSocket.isClosed()) {
                        try {
                            Thread.sleep(3000); // Check mỗi 3 giây
                            String sql = "SELECT tenUser FROM User WHERE trangThai = 'KICKED'";
                            List<String> kickedUsers = new ArrayList<>();
                            try (Connection conn = DBContext.getConnection();
                                 PreparedStatement ps = conn.prepareStatement(sql);
                                 ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    kickedUsers.add(rs.getString("tenUser"));
                                }
                            }
                            // Process kicks
                            for (String u : kickedUsers) {
                                kickUser(u);
                                // Update status to offline
                                try (Connection conn = DBContext.getConnection();
                                     PreparedStatement ps = conn.prepareStatement("UPDATE User SET trangThai = 'OFFLINE' WHERE tenUser = ?")) {
                                    ps.setString(1, u);
                                    ps.executeUpdate();
                                }
                            }
                            
                            // Broadcast sync state to Admins
                            String userListStr = String.join(",", clients.keySet());
                            Message syncMsg = new Message("ADMIN_SYNC", clients.size() + "|" + userListStr, Message.Type.ADMIN_LOG);
                            broadcastToAdmins(syncMsg);

                        } catch (Exception e) {}
                    }
                }).start();

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    String clientIp = clientSocket.getInetAddress().getHostAddress();
                    gui.logSystem("Kết nối mới từ: " + clientIp);

                    ClientHandler handler = new ClientHandler(clientSocket, gui, clients);
                    threadPool.execute(handler);
                }
            } catch (IOException e) {
                gui.logError("Lỗi server: " + e.getMessage());
                gui.setServerStatus(false, PORT);
            } finally {
                threadPool.shutdown();
                gui.logSystem("Server đã dừng.");
            }
        }, "server-accept-thread").start();
    }

    // ══════════════════════════════════════════════════════════════
    //  ADMIN: Kick user
    // ══════════════════════════════════════════════════════════════
    public void kickUser(String username) {
        ClientHandler handler = clients.get(username);
        if (handler != null) {
            handler.kickByAdmin();
            gui.logSystem("🔨 Admin đã kick user: " + username);
            broadcastToAdmins(new Message("SYS", "🔨 Admin đã kick user: " + username, Message.Type.ADMIN_LOG));
        } else {
            gui.logError("Không tìm thấy user đang online: " + username);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ADMIN: Quản lý phòng
    // ══════════════════════════════════════════════════════════════

    /** Lấy danh sách tất cả phòng từ DB (cho bảng quản lý) */
    public List<Object[]> getAllRooms() {
        List<Object[]> rooms = new ArrayList<>();
        String sql = "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) " +
                     "FROM Room ORDER BY maRoom";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(new Object[]{
                    rs.getInt(1),    // ID
                    rs.getString(2), // Tên
                    rs.getInt(3),    // Người hiện tại
                    rs.getInt(4)     // Giới hạn
                });
            }
        } catch (Exception e) {
            gui.logError("DB Error (getAllRooms): " + e.getMessage());
        }
        return rooms;
    }

    /** Tạo phòng mới */
    public boolean createRoom(String name, int limit) {
        String sql = "INSERT INTO Room (tenRoom, gioiHan) VALUES (?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, limit);
            ps.executeUpdate();
            gui.logSystem("✅ Đã tạo phòng mới: \"" + name + "\" (giới hạn: " + limit + " người)");
            broadcastToAdmins(new Message("SYS", "✅ Đã tạo phòng mới: " + name, Message.Type.ADMIN_LOG));
            broadcastRoomListToAll(); // Thông báo cho tất cả client
            return true;
        } catch (Exception e) {
            gui.logError("DB Error (createRoom): " + e.getMessage());
            broadcastToAdmins(new Message("ERROR", "DB Lỗi tạo phòng: " + e.getMessage(), Message.Type.ADMIN_LOG));
            return false;
        }
    }

    /** Sửa tên và giới hạn của phòng */
    public boolean editRoom(int roomId, String name, int limit) {
        String sql = "UPDATE Room SET tenRoom = ?, gioiHan = ? WHERE maRoom = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, limit);
            ps.setInt(3, roomId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                gui.logSystem("✏️ Đã sửa phòng ID=" + roomId + " → \"" + name + "\" (giới hạn: " + limit + ")");
                broadcastRoomListToAll(); // Thông báo cho tất cả client
                return true;
            }
            gui.logError("Không tìm thấy phòng ID=" + roomId);
            return false;
        } catch (Exception e) {
            gui.logError("DB Error (editRoom): " + e.getMessage());
            return false;
        }
    }

    // ――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――
    //  Broadcast room list đến toàn bộ client (sau create/edit)
    // ――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――
    private void broadcastRoomListToAll() {
        if (clients.isEmpty()) return;
        String content = buildRoomListContent();
        if (content.isEmpty()) return;
        Message msg = new Message("Hệ thống", content, Message.Type.ROOM_LIST);
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(msg);
        }
    }

    private String buildRoomListContent() {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) FROM Room ORDER BY maRoom";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getInt(1)).append(":").append(rs.getString(2)).append(":")
                  .append(rs.getInt(3)).append(":").append(rs.getInt(4));
            }
        } catch (Exception e) {
            gui.logError("DB Error (buildRoomList): " + e.getMessage());
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  Phòng mặc định (ID=1) luôn tồn tại
    // ══════════════════════════════════════════════════════════════
    private void ensureDefaultRoom() {
        // INSERT IGNORE: nếu maRoom=1 đã tồn tại thì bỏ qua, không báo lỗi
        String sql = "INSERT IGNORE INTO Room (maRoom, tenRoom, gioiHan) VALUES (1, 'Sảnh Chung', 100)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            gui.logSystem("✅ Phòng mặc định \"Sảnh Chung\" (ID=1) đã sẵn sàng.");
        } catch (Exception e) {
            gui.logError("DB Error (ensureDefaultRoom): " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Lấy danh sách online users cho GUI admin
    // ══════════════════════════════════════════════════════════════
    public List<String> getOnlineUsers() {
        return new ArrayList<>(clients.keySet());
    }
}
