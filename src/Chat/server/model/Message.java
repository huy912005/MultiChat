package Chat.server.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model: Đại diện cho một tin nhắn trong hệ thống chat
 * Implements Serializable để có thể gửi qua ObjectOutputStream
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    // Các loại tin nhắn
    public enum Type {
        CHAT, JOIN, LEAVE, USER_LIST, SYSTEM,
        CREATE_ROOM,      // Tạo phòng mới (bởi user hoặc admin)
        JOIN_ROOM,        // Tham gia phòng cụ thể
        LEAVE_ROOM,       // Thoát phòng hiện tại
        KICK,             // Admin kick người dùng
        HISTORY,          // Gửi lịch sử tin nhắn
        ROOM_LIST,        // Server gửi danh sách tất cả phòng
        AUTH,             // Client gửi yêu cầu đăng nhập/đăng ký
        AUTH_RESULT,      // Server trả kết quả xác thực
        ADMIN_LOG,        // Gửi log từ server đến Admin GUI
        ADMIN_USER_LIST,  // Gửi danh sách user toàn server
        ADMIN_KICK,       // Admin gửi yêu cầu kick
        DELETE_ROOM,      // Xóa phòng (chủ phòng hoặc admin)
        ROOM_CODE_SEARCH, // Tìm kiếm phòng theo mã 5 ký tự
        ADMIN_CREATE_ROOM,// Admin tạo phòng qua TCP stream
        ADMIN_EDIT_ROOM   // Admin sửa phòng qua TCP stream
    }

    private String sender;    // Tên người gửi
    private String content;   // Nội dung tin nhắn
    private Type type;        // Loại tin nhắn
    private String timestamp; // Thoi gian gui

    // Constructor đầy đủ
    public Message(String sender, String content, Type type) {
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }

    // Constructor cho tin nhắn chat thông thường
    public Message(String sender, String content) {
        this(sender, content, Type.CHAT);
    }

    // Getters
    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Type getType() {
        return type;
    }

    public String getTimestamp() {
        return timestamp;
    }

    // Setters
    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Chuyển Message thành một chuỗi (String) duy nhất theo định dạng:
     * TYPE|SENDER|TIMESTAMP|URL_ENCODED_CONTENT
     */
    public String toNetworkString() {
        try {
            String safeContent = java.net.URLEncoder.encode(content != null ? content : "", "UTF-8");
            return type.name() + "|" + sender + "|" + timestamp + "|" + safeContent;
        } catch (Exception e) {
            return type.name() + "|" + sender + "|" + timestamp + "|ERROR";
        }
    }

    /**
     * Đọc chuỗi (String) nhận từ mạng và chuyển lại thành đối tượng Message
     */
    public static Message fromNetworkString(String data) {
        if (data == null || data.trim().isEmpty())
            return null;
        try {
            String[] parts = data.split("\\|", 4);
            if (parts.length < 4)
                return null;

            Type msgType = Type.valueOf(parts[0]);
            String msgSender = parts[1];
            String msgTimestamp = parts[2];
            String msgContent = java.net.URLDecoder.decode(parts[3], "UTF-8");

            Message msg = new Message(msgSender, msgContent, msgType);
            msg.timestamp = msgTimestamp;
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", timestamp, sender, content);
    }

}
