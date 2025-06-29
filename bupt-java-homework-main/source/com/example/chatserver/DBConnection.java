package com.example.chatserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:mysql://10.29.253.184:3306/chatserver";
    private static final String USER = "java";
    private static final String PASSWORD = "123456";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // 获取用户ID
    public static int getUserId(String username) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("user_id") : -1;
        }
    }

    // 获取群组ID
    public static int getGroupId(String groupName) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT group_id FROM user_groups WHERE group_name = ?")) {
            stmt.setString(1, groupName);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("group_id") : -1;
        }
    }

    // 获取小组ID
    public static int getSubgroupId(String subgroupName, int groupId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT subgroup_id FROM subgroups WHERE subgroup_name = ? AND group_id = ?")) {
            stmt.setString(1, subgroupName);
            stmt.setInt(2, groupId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("subgroup_id") : -1;
        }
    }

    // 通过小组名查所属群组ID
    public static int getGroupIdBySubgroupName(String subgroupName) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT group_id FROM subgroups WHERE subgroup_name = ?")) {
            stmt.setString(1, subgroupName);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("group_id") : -1;
        }
    }
}