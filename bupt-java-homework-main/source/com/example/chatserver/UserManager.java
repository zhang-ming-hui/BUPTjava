package com.example.chatserver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private static final String USER_FILE = "users.txt";
    private Map<String, User> users;
    private Map<String, String> sessionToUser; // sessionId -> username
    private Map<String, String> userToSession; // username -> sessionId

    public UserManager() {
        this.users = new ConcurrentHashMap<>();
        this.sessionToUser = new ConcurrentHashMap<>();
        this.userToSession = new ConcurrentHashMap<>();
        System.out.println("当前工作目录: " + System.getProperty("user.dir"));

        loadUsers();
        System.out.println("当前工作目录: " + System.getProperty("user.dir"));
    }

    // 用户认证
    public boolean authenticateUser(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }

    // 用户登录
    public String loginUser(String username, String clientIP) {
        User user = users.get(username);
        if (user == null) {
            return null;
        }

        // 测试模式：允许同一用户多地登录，注释掉用户漫游限制
        /*
         * // 检查用户是否已在其他地方登录 String existingSession =
         * userToSession.get(username); if (existingSession != null) { // 踢出之前的会话
         * sessionToUser.remove(existingSession); userToSession.remove(username); }
         */

        // 创建新会话
        String sessionId = generateSessionId();
        user.setOnline(true);
        user.setCurrentSessionId(sessionId);
        user.setLastLoginTime(System.currentTimeMillis());
        user.setLastLoginIP(clientIP);

        sessionToUser.put(sessionId, username);
        // 测试模式：允许多个会话，注释掉这行以避免覆盖
        // userToSession.put(username, sessionId);

        // 更新用户在线状态到数据库
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE users SET online_status = true, last_login = NOW() WHERE username = ?")) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("更新用户在线状态时出错: " + e.getMessage());
        }

        return sessionId;
    }

    // 用户登出
    public void logoutUser(String sessionId) {
        String username = sessionToUser.get(sessionId);
        if (username != null) {
            User user = users.get(username);
            if (user != null) {
                user.setOnline(false);
                user.setCurrentSessionId(null);
            }
            sessionToUser.remove(sessionId);
            userToSession.remove(username);

            // 更新用户在线状态到数据库
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE users SET online_status = false WHERE username = ?")) {
                stmt.setString(1, username);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("更新用户离线状态时出错: " + e.getMessage());
            }
        }
    }

    // 根据会话ID获取用户
    public User getUserBySession(String sessionId) {
        String username = sessionToUser.get(sessionId);
        return username != null ? users.get(username) : null;
    }

    // 根据用户名获取用户
    public User getUserByUsername(String username) {
        return users.get(username);
    }

    // 获取所有在线用户
    public List<String> getOnlineUsers() {
        List<String> onlineUsers = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT username FROM users WHERE online_status = true")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                onlineUsers.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("获取在线用户时出错: " + e.getMessage());
        }
        return onlineUsers;
    }

    // 获取所有用户
    public List<String> getAllUsers() {
        List<String> allUsers = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT username FROM users")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                allUsers.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("获取所有用户时出错: " + e.getMessage());
        }
        return allUsers;
    }

    // 创建新用户
    public boolean createUser(String username, String password) {
        if (users.containsKey(username)) {
            return false; // 用户已存在
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();

            User newUser = new User(username, password);
            users.put(username, newUser);
            saveUsers();
            return true;
        } catch (SQLException e) {
            System.err.println("创建用户时出错: " + e.getMessage());
            return false;
        }
    }

    // 检查用户是否在线
    public boolean isUserOnline(String username) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT online_status FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getBoolean("online_status");
        } catch (SQLException e) {
            System.err.println("检查用户在线状态时出错: " + e.getMessage());
            return false;
        }
    }

    // 检查会话是否有效
    public boolean isSessionValid(String sessionId) {
        return sessionToUser.containsKey(sessionId);
    }

    // 获取用户的未读消息计数
    public Map<String, Integer> getUserUnreadCounts(String username) {
        User user = users.get(username);
        return user != null ? user.getAllUnreadCounts() : new HashMap<>();
    }

    // 增加用户的未读消息计数
    public void incrementUnreadCount(String username, String chatId) {
        User user = users.get(username);
        if (user != null) {
            user.incrementUnreadCount(chatId);
        }
    }

    // 清除用户的未读消息计数
    public void clearUnreadCount(String username, String chatId) {
        User user = users.get(username);
        if (user != null) {
            user.clearUnreadCount(chatId);
        }
    }

    // 私有辅助方法
    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    // 加载用户数据
    private void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    users.put(username, new User(username, password));
                }
            }
            System.out.println("已加载 " + users.size() + " 个用户");
        } catch (IOException e) {
            System.out.println("用户文件不存在，将创建新文件");
        }
    }

    // 保存用户数据
    private void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_FILE))) {
            for (User user : users.values()) {
                writer.println(user.getUsername() + ":" + user.getPassword());
            }
        } catch (IOException e) {
            System.err.println("保存用户数据失败: " + e.getMessage());
        }
    }
}