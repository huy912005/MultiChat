/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package multicast;

/**
 *
 * @author ADMIN
 */
import javax.swing.*;
import multicast.Client.Controller.ChatController;
import multicast.Client.View.ChatFrame;

public class Main {
    public static void main(String[] args) {
        String username = JOptionPane.showInputDialog("Nhập tên:");

        if (username != null && !username.isEmpty()) {
            ChatFrame frame = new ChatFrame();

            try {
                new ChatController(frame, username);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
