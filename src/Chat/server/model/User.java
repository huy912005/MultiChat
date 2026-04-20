package Chat.server.model;

import java.io.Serializable;

/**
 * Model: Đại diện cho một người dùng trong hệ thống
 */
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String username;   // Tên người dùng
    private String avatarText; // Chữ cái đầu (dùng cho avatar)
    
    public User(String username) {
        this.username = username;
        // Lấy chữ cái đầu tiên, viết hoa
        this.avatarText = username.isEmpty() ? "?" : 
                String.valueOf(username.charAt(0)).toUpperCase();
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = username;
        this.avatarText = username.isEmpty() ? "?" :
                String.valueOf(username.charAt(0)).toUpperCase();
    }
    
    public String getAvatarText() { return avatarText; }
    
    @Override
    public String toString() { return username; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof User)) return false;
        User user = (User) obj;
        return username.equals(user.username);
    }
    
    @Override
    public int hashCode() { return username.hashCode(); }
}
