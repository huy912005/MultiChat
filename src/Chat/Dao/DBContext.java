/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Chat.Dao;
import java.sql.*;

/**
 *
 * @author ADMIN
 */
public class DBContext {
    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    public static Connection getConnection() throws Exception {
        String url = envOrDefault(
                "CHAT_DB_URL",
                "jdbc:mysql://localhost:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
        );
        String user = envOrDefault("CHAT_DB_USER", "root");
        String pass = envOrDefault("CHAT_DB_PASS", "12345");

        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, user, pass);
    }
}
