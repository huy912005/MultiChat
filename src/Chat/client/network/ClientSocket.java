package Chat.client.network;

import Chat.server.model.Message;
import java.io.*;
import java.net.*;

/**
 * ClientSocket: Quản lý kết nối TCP với server
 * Xử lý gửi/nhận tin nhắn và callback khi nhận tin nhắn mới
 */
public class ClientSocket {
    // IP Digital Ocean Server: 127.0.0.1 (Fixed)
    private static String SERVER_HOST = "159.65.134.130"; // ✅ Server IP Digital Ocean
    private static final int SERVER_PORT = 5000; // Cổng server (phải khớp với Server.java)

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean connected = false;
    private String lastErrorMessage = "";

    // Interface callback để thông báo cho Controller khi nhận tin nhắn
    public interface MessageListener {
        void onMessageReceived(Message message);

        void onConnectionLost();
    }

    private MessageListener listener;

    public ClientSocket(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Cấu hình địa chỉ IP server
     * @param host IP hoặc hostname của server
     */
    public static void setServerHost(String host) {
        SERVER_HOST = host;
        System.out.println("[CLIENT] Cấu hình server host: " + host);
    }

    /**
     * Kết nối tới server với username
     * 
     * @param username Tên người dùng
     * @return true nếu kết nối thành công
     */
    public boolean connect(String username, String password, boolean registerMode) {
        this.username = username;

        try {
            System.out.println("[CLIENT] Đang kết nối tới " + SERVER_HOST + ":" + SERVER_PORT);
            socket = new Socket(SERVER_HOST, SERVER_PORT);

            // Khởi tạo luồng văn bản (UTF-8 dể hỗ trợ Tiếng Việt)
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            // Bắt tay xác thực trước khi bắt đầu lắng nghe
            String action = registerMode ? "REGISTER" : "LOGIN";
            Message authMsg = new Message(username, action + "|" + password, Message.Type.AUTH);
            out.println(authMsg.toNetworkString());

            String authResponseLine = in.readLine();
            Message authResponse = Message.fromNetworkString(authResponseLine);
            if (authResponse == null || authResponse.getType() != Message.Type.AUTH_RESULT) {
                lastErrorMessage = "Phản hồi xác thực từ server không hợp lệ.";
                disconnect();
                return false;
            }
            if (!authResponse.getContent().startsWith("OK|")) {
                String[] parts = authResponse.getContent().split("\\|", 2);
                lastErrorMessage = (parts.length == 2) ? parts[1] : "Đăng nhập thất bại.";
                disconnect();
                return false;
            }

            connected = true;
            lastErrorMessage = "";
            System.out.println("[CLIENT] ✓ Đã xác thực thành công!");

            // Bắt đầu thread lắng nghe tin nhắn từ server
            startListening();

            return true;

        } catch (ConnectException e) {
            System.err.println("[CLIENT] Không thể kết nối tới server! Server có đang chạy không?");
            lastErrorMessage = "Không thể kết nối tới server.";
            return false;
        } catch (IOException e) {
            System.err.println("[CLIENT] Lỗi kết nối: " + e.getMessage());
            lastErrorMessage = "Lỗi kết nối: " + e.getMessage();
            return false;
        }
    }

    /**
     * Bắt đầu thread lắng nghe tin nhắn từ server
     */
    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                while (connected) {
                    // Đọc từ server từng dòng văn bản và giải mã
                    String line = in.readLine();
                    if (line == null)
                        throw new EOFException();

                    Message msg = Message.fromNetworkString(line);

                    if (msg != null && listener != null) {
                        // Thông báo cho listener (Controller) trên Event Dispatch Thread
                        listener.onMessageReceived(msg);
                    }
                }
            } catch (EOFException | SocketException e) {
                // Kết nối bị đóng
                System.out.println("[CLIENT] Kết nối với server đã đóng");
                if (connected) {
                    connected = false;
                    if (listener != null) {
                        listener.onConnectionLost();
                    }
                }
            } catch (IOException e) {
                System.err.println("[CLIENT] Lỗi đọc tin nhắn: " + e.getMessage());
                if (connected) {
                    connected = false;
                    if (listener != null) {
                        listener.onConnectionLost();
                    }
                }
            }
        });

        // Đánh dấu là daemon thread để tự tắt khi ứng dụng đóng
        listenerThread.setDaemon(true);
        listenerThread.setName("MessageListener-" + username);
        listenerThread.start();
    }

    /**
     * Gửi tin nhắn tới server
     * 
     * @param message Tin nhắn cần gửi
     */
    public synchronized void sendMessage(Message message) {
        if (!connected || out == null) {
            System.err.println("[CLIENT] Chưa kết nối! Không thể gửi tin nhắn.");
            return;
        }

        try {
            out.println(message.toNetworkString());
        } catch (Exception e) {
            System.err.println("[CLIENT] Lỗi gửi tin nhắn: " + e.getMessage());
        }
    }

    /**
     * Ngắt kết nối khỏi server
     */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[CLIENT] Lỗi ngắt kết nối: " + e.getMessage());
        }
        System.out.println("[CLIENT] Đã ngắt kết nối.");
    }

    public boolean isConnected() {
        return connected;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public String getUsername() {
        return username;
    }
}
