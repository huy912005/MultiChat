/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package multicast.Client.Model;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author ADMIN
 */
public class UserList {
    private Set<String> userList = new HashSet<>();
    public void addUser(String user){
        userList.add(user);
    }
    public void removeUser(String user){
        if(userList.contains(user))
            userList.remove(user);
    }
    public Set<String> getUser(){
        return userList;
    }
    public int getSize(){
        return userList.size();
    }
}
