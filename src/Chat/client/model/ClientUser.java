package Chat.client.model;

import Chat.server.model.User;

/**
 * Client-side User model (extends server User)
 * Có thể thêm các thuộc tính riêng cho client nếu cần
 */
public class ClientUser extends User {
    
    public ClientUser(String username) {
        super(username);
    }
}
