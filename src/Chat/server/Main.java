/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Chat.server;

import Chat.server.network.Server;
import Chat.server.view.ConsoleServerLogger;
import Chat.server.view.ServerLogger;
import Chat.server.view.ServerGUI;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

/**
 *
 * @author ADMIN
 */
public class Main {
    public static void main(String[] args) {
        boolean forceHeadless = "true".equalsIgnoreCase(System.getenv("CHAT_SERVER_HEADLESS"));
        boolean useHeadless = forceHeadless || GraphicsEnvironment.isHeadless();

        if (useHeadless) {
            ServerLogger logger = new ConsoleServerLogger();
            new Server(logger).start();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            new Server(gui).start();
        });
    }
}
