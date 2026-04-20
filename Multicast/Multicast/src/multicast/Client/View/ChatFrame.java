/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package multicast.Client.View;

import java.awt.BorderLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 *
 * @author ADMIN
 */
public class ChatFrame extends JFrame{
    public JTextArea chatArea = new JTextArea();
    public JTextField inputField = new JTextField();
    public JButton btnSend = new JButton("Gửi");
    public DefaultListModel<String> userModel = new DefaultListModel<>();
    public JList<String> userList = new JList<>(userModel);
    public JLabel userCount = new JLabel("Số người : 0");

    public ChatFrame() {
        setTitle("Chat Multicast");
        setSize(600,400);
        setLayout(new BorderLayout());
        
        chatArea.setEditable(false);
        
        JPanel panelRight = new JPanel(new BorderLayout());
        panelRight.add(new JScrollPane(userList),BorderLayout.CENTER);
        panelRight.add(userCount,BorderLayout.SOUTH);
        
        JPanel panelBottom = new JPanel(new BorderLayout());
        panelBottom.add(inputField,BorderLayout.CENTER);
        panelBottom.add(btnSend,BorderLayout.EAST);
        
        add(new JScrollPane(chatArea),BorderLayout.CENTER);
        add(panelRight,BorderLayout.EAST);
        add(panelBottom,BorderLayout.SOUTH);
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }
    
}
