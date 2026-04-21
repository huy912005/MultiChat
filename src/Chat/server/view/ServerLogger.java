package Chat.server.view;

import Chat.server.network.Server;

public interface ServerLogger {
    void setServer(Server server);

    void logJoin(String username, String ip);

    void logLeave(String username, int remaining);

    void logChat(String sender, String content);

    void logError(String message);

    void logSystem(String message);

    void setServerStatus(boolean running, int port);

    // ✅ NEW: Cập nhật số user online
    default void updateOnlineUserCount() {}

    // ✅ NEW: Log tin nhắn chi tiết
    default void logMessageDetail(String sender, String room, String content) {}
}
