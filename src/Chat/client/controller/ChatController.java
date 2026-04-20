package Chat.client.controller;

import Chat.client.network.ClientSocket;
import Chat.client.view.ChatFrame;
import Chat.server.model.Message;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ChatController: Kết nối View và Network
 * Xử lý logic: nhận tin nhắn → cập nhật UI, người dùng gửi → gửi lên server
 * 
 * Luồng hoạt động:
 * User gõ → ChatFrame → ChatController → ClientSocket → Server
 * Server phát → ClientSocket → ChatController → ChatFrame (update UI)
 */
public class ChatController implements ChatFrame.ChatFrameListener, ClientSocket.MessageListener {
    
    private ChatFrame view;          // View (giao diện)
    private ClientSocket network;    // Network layer
    private String username;         // Tên người dùng hiện tại
    
    public ChatController() {
        // Tạo view trước
        this.view = new ChatFrame();
        this.view.setFrameListener(this); // Đăng ký lắng nghe sự kiện từ view
        
        // Tạo network layer
        this.network = new ClientSocket(this); // Đăng ký lắng nghe tin nhắn từ server
        
        // Bắt đầu quá trình kết nối
        startConnection();
    }
    
    /**
     * Bắt đầu kết nối: hiển thị dialog nhập tên, rồi kết nối
     */
    private void startConnection() {
        // Hiển thị dialog nhập username TRÊN Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            // ✅ Server IP cố định: 159.65.134.130 (Digital Ocean) - không cần nhập nữa
            ClientSocket.setServerHost("159.65.134.130");
            
            boolean connected = false;
            
            while (!connected) {
                // Hiện dialog xác thực (đăng nhập/đăng ký)
                ChatFrame.AuthRequest auth = view.showLoginDialog();
                
                // Nếu người dùng bấm Cancel → thoát ứng dụng
                if (auth == null) {
                    System.out.println("[CLIENT] Người dùng hủy đăng nhập. Thoát...");
                    System.exit(0);
                    return;
                }
                
                username = auth.getUsername();
                view.setCurrentUsername(username);
                
                // Kết nối tới server (chạy trong background để không block UI)
                boolean success = network.connect(username, auth.getPassword(), auth.isRegisterMode());
                
                if (success) {
                    // Cập nhật trạng thái kết nối
                    view.setConnectionStatus(true, username);
                    view.addSystemMessage("Đã kết nối. Chào mừng " + username + "! 👋");
                    connected = true;
                } else {
                    // Báo lỗi và cho thử lại
                    String err = network.getLastErrorMessage();
                    if (err == null || err.isBlank()) {
                        err = "Không thể kết nối tới server " + serverIp + ".";
                    }
                    view.showError(err);
                }
            }
        });
    }
    
    // ─────────────────────────────────────────────────────────────
    // ChatFrame.ChatFrameListener - Xử lý sự kiện từ View
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Được gọi khi người dùng gửi tin nhắn qua UI
     */
    @Override
    public void onSendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!network.isConnected()) {
            view.showError("Không có kết nối tới server!");
            return;
        }
        
        // Tạo đối tượng Message và gửi
        Message msg = new Message(username, text.trim(), Message.Type.CHAT);
        network.sendMessage(msg);
    }
    
    /**
     * Được gọi khi người dùng bấm chọn một phòng trong sidebar
     */
    @Override
    public void onJoinRoom(int roomId) {
        if (!network.isConnected()) {
            view.showError("Không có kết nối tới server!");
            return;
        }
        Message msg = new Message(username, String.valueOf(roomId), Message.Type.JOIN_ROOM);
        network.sendMessage(msg);
    }

    /**
     * Được gọi khi người dùng yêu cầu kết nối (không dùng ở đây vì tự động)
     */
    @Override
    public void onConnect(String username) {
        // Được xử lý trong startConnection()
    }
    
    /**
     * Được gọi khi người dùng đóng cửa sổ
     */
    @Override
    public void onDisconnect() {
        if (network != null && network.isConnected()) {
            network.disconnect();
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // ClientSocket.MessageListener - Xử lý tin nhắn từ Server
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Được gọi khi nhận được tin nhắn MỚI từ server
     * Chạy trên background thread → cần dùng SwingUtilities.invokeLater cho UI
     */
    @Override
    public void onMessageReceived(Message message) {
        // Switch theo loại tin nhắn
        switch (message.getType()) {
            
            case CHAT:
                // Tin nhắn chat thông thường
                boolean isMyMessage = message.getSender().equals(username);
                view.addChatMessage(
                        message.getSender(),
                        message.getContent(),
                        message.getTimestamp(),
                        isMyMessage
                );
                break;
                
            case JOIN:
                // Thông báo người dùng vào
                view.addSystemMessage("👋 " + message.getContent());
                break;
                
            case LEAVE:
                // Thông báo người dùng rời
                view.addSystemMessage("👋 " + message.getContent());
                break;
                
            case USER_LIST:
                // Cập nhật danh sách người dùng online
                String userListStr = message.getContent();
                if (userListStr != null && !userListStr.isEmpty()) {
                    List<String> users = Arrays.asList(userListStr.split(","));
                    view.updateUserList(users);
                } else {
                    view.updateUserList(java.util.Collections.emptyList());
                }
                break;
                
            case SYSTEM:
                if (message.getContent().contains("bị kick")) {
                    view.showError(message.getContent());
                }
                view.addSystemMessage(message.getContent());
                break;

            case JOIN_ROOM:
                // Server phản hồi sau khi vào phòng: "roomId:roomName"
                String[] roomInfo = message.getContent().split(":", 2);
                if (roomInfo.length == 2) {
                    try {
                        int    rId   = Integer.parseInt(roomInfo[0].trim());
                        String rName = roomInfo[1].trim();
                        view.setCurrentRoom(rId, rName);
                        view.addSystemMessage("✅ Bạn đã vào phòng: " + rName);
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case ROOM_LIST:
                // Content: "1:Sảnh Chung:2:100,2:Phòng Học:1:50,..."
                String listContent = message.getContent();
                if (listContent != null && !listContent.isEmpty()) {
                    List<String[]> rooms = new ArrayList<>();
                    for (String entry : listContent.split(",")) {
                        String[] parts = entry.split(":", 4);
                        if (parts.length == 4) rooms.add(parts);
                    }
                    view.updateRoomList(rooms);
                }
                break;

            default:
                System.out.println("[CONTROLLER] Loại tin nhắn không xác định: " + message.getType());
        }
    }
    
    /**
     * Được gọi khi mất kết nối với server
     */
    @Override
    public void onConnectionLost() {
        SwingUtilities.invokeLater(() -> {
            view.setConnectionStatus(false, username);
            view.addSystemMessage("❌ Mất kết nối với server!");
            view.showError("Mất kết nối với server!\nVui lòng khởi động lại ứng dụng.");
        });
    }
}
