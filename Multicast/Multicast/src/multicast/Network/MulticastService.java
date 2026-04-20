/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package multicast.Network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

/**
 *
 * @author ADMIN
 */
public class MulticastService {
    private MulticastSocket socket;
    private InetAddress group;
    private int port = 4446;

    public MulticastService() throws Exception {
        socket = new MulticastSocket(port);
        // ✅ Sử dụng multicast address hợp lệ (224.0.0.1 - thay vì 230.0.0.1)
        group = InetAddress.getByName("224.0.0.1");
        
        // ✅ Đặt TTL cho phép multicast trên LAN (TTL=1 chỉ local, TTL=32 cho LAN)
        socket.setTimeToLive(32);
        
        // ✅ Bind vào port cụ thể trước khi joinGroup
        socket.joinGroup(group);
        
        System.out.println("[MULTICAST] Đã kết nối multicast group: " + group + ":" + port);
    }
    
    public void sendMessage(String msg){
        try {
            byte[] buffer = msg.getBytes();
            DatagramPacket DatagramPacket =new DatagramPacket(buffer,buffer.length,group,port);
            socket.send(DatagramPacket);
        } catch (IOException ex) {
            System.getLogger(MulticastService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            ex.printStackTrace();
        }
    }
    
    public String receiveMessage(){
        byte[] buffer = new byte[1024];
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(datagramPacket);
            return new String(datagramPacket.getData(),0,datagramPacket.getLength());
        } catch (IOException ex) {
            return null;
        }
    }
}
