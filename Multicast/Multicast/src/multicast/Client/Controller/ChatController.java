/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package multicast.Client.Controller;

import multicast.Client.Model.UserList;
import multicast.Client.View.ChatFrame;
import multicast.Network.MulticastService;

/**
 *
 * @author ADMIN
 */
public class ChatController {
    private ChatFrame view;
    private MulticastService service;
    private UserList userList;
    private String username;

    public ChatController(ChatFrame view, String username) throws Exception {
        this.view = view;
        this.username = username;
        this.service = new MulticastService();
        this.userList = new UserList();

        Init();
    }
    
    private void Init(){
        // gửi JOIN
        service.sendMessage("JOIN:" +username);
        //lấy request
        service.sendMessage("REQUEST_USERS");
        // nút gửi
        view.btnSend.addActionListener(e -> sendMessage());
        // enter để gửi
        view.inputField.addActionListener(e -> sendMessage());
        // thread nhận message
        new Thread(() -> receive()).start();
    }
    private void sendMessage() {
        String msg = view.inputField.getText();
        if (!msg.isEmpty()) {
            service.sendMessage("MSG:" + username + ":" + msg);
            view.inputField.setText("");
        }
    }
    private void receive() {
        while (true) {
            String msg = service.receiveMessage();
            if (msg != null) {
                handleMessage(msg);
            }
        }
    }
    private void handleMessage(String msg) {
        if (msg.startsWith("JOIN:")) {
            String user = msg.split(":")[1];
            userList.addUser(user);
            updateUserUI();
            view.chatArea.append(user + " joined\n");
        }
        else if (msg.startsWith("LEAVE:")) {
            String user = msg.split(":")[1];
            userList.removeUser(user);
            updateUserUI();
            view.chatArea.append(user + " left\n");
        }
        else if (msg.startsWith("MSG:")) {
            String[] parts = msg.split(":", 3);
            view.chatArea.append(parts[1] + ": " + parts[2] + "\n");
        }
        else if (msg.equals("REQUEST_USERS")) {
            StringBuilder users = new StringBuilder("USER_LIST:");

            for (String u : userList.getUser()) {
                users.append(u).append(",");
            }

            service.sendMessage(users.toString());
        }
        else if (msg.startsWith("USER_LIST:")) {
            String[] users = msg.substring(10).split(",");

            for (String u : users) {
                if (!u.isEmpty()) {
                    userList.addUser(u);
                }
            }

            updateUserUI();
        }
    }

    private void updateUserUI() {
        view.userModel.clear();
        for (String u : userList.getUser()) {
            view.userModel.addElement(u);
        }
        view.userCount.setText("Users: " + userList.getSize());
    }
}
