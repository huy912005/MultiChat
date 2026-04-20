package Chat.server.view;

import Chat.server.network.Server;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleServerLogger implements ServerLogger {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Server server;

    private void println(String level, String msg) {
        String now = LocalDateTime.now().format(TIME_FMT);
        System.out.println("[" + now + "] [" + level + "] " + msg);
    }

    @Override
    public void setServer(Server server) {
        this.server = server;
    }

    @Override
    public void logJoin(String username, String ip) {
        println("JOIN", username + " connected from " + ip);
    }

    @Override
    public void logLeave(String username, int remaining) {
        println("LEAVE", username + " disconnected, remaining=" + remaining);
    }

    @Override
    public void logChat(String sender, String content) {
        println("CHAT", sender + ": " + content);
    }

    @Override
    public void logError(String message) {
        println("ERROR", message);
    }

    @Override
    public void logSystem(String message) {
        println("SYSTEM", message);
    }

    @Override
    public void setServerStatus(boolean running, int port) {
        String mode = (server == null) ? "unknown" : "attached";
        println("STATUS", (running ? "RUNNING" : "STOPPED") + " on port " + port + " (" + mode + ")");
    }
}
