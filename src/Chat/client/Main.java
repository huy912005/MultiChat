/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Chat.client;

import Chat.client.controller.ChatController;
import javax.swing.SwingUtilities;

/**
 *
 * @author ADMIN
 */
public class Main {
    public static void main(String[] args) {
        // Khởi động trên Event Dispatch Thread (EDT) của Swing
        SwingUtilities.invokeLater(() -> {
            new ChatController();
        });
    }
}
