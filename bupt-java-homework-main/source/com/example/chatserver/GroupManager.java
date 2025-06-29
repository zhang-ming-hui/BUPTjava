package com.example.chatserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

public class GroupManager {
    public GroupManager() {
        createTablesIfNotExist();
    }

    private void createTablesIfNotExist() {
        try (Connection conn = DBConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            // 表已存在，不需要创建
        } catch (SQLException e) {
            System.err.println("检查表时出错: " + e.getMessage());
        }
    }

    public Group getGroup(String groupName) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT g.group_name, u.username AS creator FROM user_groups g " +
                             "JOIN users u ON g.creator_id = u.user_id WHERE g.group_name = ?")) {

            stmt.setString(1, groupName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Group group = new Group(rs.getString("group_name"), rs.getString("creator"));
                // 添加成员
                List<String> members = getGroupMembers(groupName);
                for (String member : members) {
                    group.addMember(member);
                }
                return group;
            }
        } catch (SQLException e) {
            System.err.println("获取群组信息时出错: " + e.getMessage());
        }
        return null;
    }

    public boolean isUserInAnySubgroup(String username) {
        try (Connection conn = DBConnection.getConnection()) {
            int userId = DBConnection.getUserId(username);
            if (userId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM subgroup_members WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                return stmt.executeQuery().next();
            }
        } catch (SQLException e) {
            System.err.println("检查用户是否在任何小组中时出错: " + e.getMessage());
            return false;
        }
    }

    public boolean leaveGroup(String groupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            int groupId = DBConnection.getGroupId(groupName);
            int userId = DBConnection.getUserId(username);
            if (groupId == -1 || userId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM group_members WHERE group_id = ? AND user_id = ?")) {
                stmt.setInt(1, groupId);
                stmt.setInt(2, userId);
                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    updateGroupMemberCount(groupId);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("离开群组时出错: " + e.getMessage());
        }
        return false;
    }

    public boolean createGroup(String groupName, String creator) {
        try (Connection conn = DBConnection.getConnection()) {
            int creatorId = DBConnection.getUserId(creator);
            if (creatorId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO user_groups (group_name, creator_id) VALUES (?, ?)")) {

                stmt.setString(1, groupName);
                stmt.setInt(2, creatorId);
                stmt.executeUpdate();

                // 创建者自动加入群组
                addGroupMember(groupName, creator);
                return true;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("群组已存在: " + groupName);
            return false;
        } catch (SQLException e) {
            System.err.println("创建群组时出错: " + e.getMessage());
            return false;
        }
    }

    public boolean joinSubgroup(String subgroupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            // 获取小组ID
            int subgroupId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT subgroup_id FROM subgroups WHERE subgroup_name = ?")) {
                stmt.setString(1, subgroupName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    subgroupId = rs.getInt("subgroup_id");
                }
            }

            if (subgroupId == -1) return false;

            int userId = DBConnection.getUserId(username);
            if (userId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO subgroup_members (subgroup_id, user_id) VALUES (?, ?)")) {
                stmt.setInt(1, subgroupId);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
                updateSubgroupMemberCount(subgroupId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("加入小组时出错: " + e.getMessage());
            return false;
        }
    }

    public boolean acceptSubgroupInvite(String subgroupName, String username) {
        // 实现接受小组邀请的逻辑
        return joinSubgroup(subgroupName, username);
    }

    public boolean declineSubgroupInvite(String subgroupName, String username) {
        // 实现拒绝小组邀请的逻辑
        // 在实际应用中，可能需要从邀请表中删除记录
        return true;
    }

    public List<SubGroup> getAllSubgroups() {
        List<SubGroup> subgroups = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT s.subgroup_name, g.group_name, u.username AS creator " +
                             "FROM subgroups s JOIN user_groups g ON s.group_id = g.group_id " +
                             "JOIN users u ON s.creator_id = u.user_id")) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                SubGroup subgroup = new SubGroup(
                        rs.getString("subgroup_name"),
                        rs.getString("group_name"),
                        rs.getString("creator")
                );

                // 添加成员
                List<String> members = getSubgroupMembers(subgroup.getName());
                for (String member : members) {
                    subgroup.addMember(member);
                }

                subgroups.add(subgroup);
            }
        } catch (SQLException e) {
            System.err.println("获取所有小组时出错: " + e.getMessage());
        }
        return subgroups;
    }

    private boolean addGroupMember(String groupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            int groupId = DBConnection.getGroupId(groupName);
            int userId = DBConnection.getUserId(username);
            if (groupId == -1 || userId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)")) {

                stmt.setInt(1, groupId);
                stmt.setInt(2, userId);
                stmt.executeUpdate();

                // 更新成员计数
                updateGroupMemberCount(groupId);
                return true;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("用户已在群组中: " + username + " -> " + groupName);
            return false;
        } catch (SQLException e) {
            System.err.println("添加群组成员时出错: " + e.getMessage());
            return false;
        }
    }

    private void updateGroupMemberCount(int groupId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE user_groups SET member_count = (SELECT COUNT(*) FROM group_members WHERE group_id = ?) WHERE group_id = ?")) {

            stmt.setInt(1, groupId);
            stmt.setInt(2, groupId);
            stmt.executeUpdate();
        }
    }

    public boolean joinGroup(String groupName, String username) {
        return addGroupMember(groupName, username);
    }

    public boolean isUserInGroup(String groupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            int groupId = DBConnection.getGroupId(groupName);
            int userId = DBConnection.getUserId(username);
            if (groupId == -1 || userId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?")) {

                stmt.setInt(1, groupId);
                stmt.setInt(2, userId);
                return stmt.executeQuery().next();
            }
        } catch (SQLException e) {
            System.err.println("检查用户是否在群组中时出错: " + e.getMessage());
            return false;
        }
    }

    public List<String> getGroupMembers(String groupName) {
        List<String> members = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            int groupId = DBConnection.getGroupId(groupName);
            if (groupId == -1) return members;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT u.username FROM users u JOIN group_members gm ON u.user_id = gm.user_id WHERE gm.group_id = ?")) {

                stmt.setInt(1, groupId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    members.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("获取群组成员时出错: " + e.getMessage());
        }
        return members;
    }

    public List<String> getAllGroups() {
        List<String> groups = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT group_name FROM user_groups")) {

            while (rs.next()) {
                groups.add(rs.getString("group_name"));
            }
        } catch (SQLException e) {
            System.err.println("获取所有群组时出错: " + e.getMessage());
        }
        return groups;
    }

    public boolean createSubgroup(String parentGroup, String subgroupName, String creator, List<String> members) {
        try (Connection conn = DBConnection.getConnection()) {
            int groupId = DBConnection.getGroupId(parentGroup);
            int creatorId = DBConnection.getUserId(creator);
            if (groupId == -1 || creatorId == -1) return false;

            // 创建小组
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO subgroups (subgroup_name, group_id, creator_id) VALUES (?, ?, ?)")) {

                stmt.setString(1, subgroupName);
                stmt.setInt(2, groupId);
                stmt.setInt(3, creatorId);
                stmt.executeUpdate();

                // 获取新创建的小组ID
                int subgroupId = DBConnection.getSubgroupId(subgroupName, groupId);
                if (subgroupId == -1) return false;

                // 添加创建者到小组
                addSubgroupMember(subgroupId, creatorId);

                // 添加其他成员到邀请列表（这里简化处理，实际应该有一个邀请表）
                for (String member : members) {
                    int memberId = DBConnection.getUserId(member);
                    if (memberId != -1 && !member.equals(creator)) {
                        addSubgroupMember(subgroupId, memberId);
                    }
                }

                return true;
            }
        } catch (SQLException e) {
            System.err.println("创建小组时出错: " + e.getMessage());
            return false;
        }
    }

    private boolean addSubgroupMember(int subgroupId, int userId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO subgroup_members (subgroup_id, user_id) VALUES (?, ?)")) {

            stmt.setInt(1, subgroupId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();

            // 更新成员计数
            updateSubgroupMemberCount(subgroupId);
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("用户已在小组成员中");
            return false;
        } catch (SQLException e) {
            System.err.println("添加小组成员时出错: " + e.getMessage());
            return false;
        }
    }

    private void updateSubgroupMemberCount(int subgroupId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE subgroups SET member_count = (SELECT COUNT(*) FROM subgroup_members WHERE subgroup_id = ?) WHERE subgroup_id = ?")) {

            stmt.setInt(1, subgroupId);
            stmt.setInt(2, subgroupId);
            stmt.executeUpdate();
        }
    }

    public boolean isUserInSubgroup(String subgroupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            int userId = DBConnection.getUserId(username);
            if (userId == -1) return false;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM subgroup_members sm JOIN subgroups s ON sm.subgroup_id = s.subgroup_id " +
                            "WHERE s.subgroup_name = ? AND sm.user_id = ?")) {

                stmt.setString(1, subgroupName);
                stmt.setInt(2, userId);
                return stmt.executeQuery().next();
            }
        } catch (SQLException e) {
            System.err.println("检查用户是否在小组中时出错: " + e.getMessage());
            return false;
        }
    }

    public List<String> getSubgroupMembers(String subgroupName) {
        List<String> members = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.username FROM users u JOIN subgroup_members sm ON u.user_id = sm.user_id " +
                             "JOIN subgroups s ON sm.subgroup_id = s.subgroup_id WHERE s.subgroup_name = ?")) {

            stmt.setString(1, subgroupName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                members.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("获取小组成员时出错: " + e.getMessage());
        }
        return members;
    }

    /**
     * 退出小组
     * @param subgroupName 小组名称
     * @param username 用户名
     * @return 是否成功退出
     */
    public boolean leaveSubgroup(String subgroupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            // 检查用户是否是小组创建者
            String creator = getSubgroupCreator(subgroupName);
            if (creator != null && creator.equalsIgnoreCase(username)) {
                System.out.println("小组创建者不能退出小组: " + username + " -> " + subgroupName);
                return false;
            }

            // 获取小组ID和用户ID
            int subgroupId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT subgroup_id FROM subgroups WHERE subgroup_name = ?")) {
                stmt.setString(1, subgroupName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    subgroupId = rs.getInt("subgroup_id");
                }
            }

            int userId = DBConnection.getUserId(username);
            if (subgroupId == -1 || userId == -1) return false;

            // 从小组成员表中删除
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM subgroup_members WHERE subgroup_id = ? AND user_id = ?")) {
                stmt.setInt(1, subgroupId);
                stmt.setInt(2, userId);
                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    updateSubgroupMemberCount(subgroupId);
                    System.out.println("用户 " + username + " 成功退出小组 " + subgroupName);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("退出小组时出错: " + e.getMessage());
        }
        return false;
    }

    /**
     * 删除小组（仅创建者可以删除）
     * @param subgroupName 小组名称
     * @param username 用户名
     * @return 是否成功删除
     */
    public boolean deleteSubgroup(String subgroupName, String username) {
        try (Connection conn = DBConnection.getConnection()) {
            // 检查用户是否是小组创建者
            String creator = getSubgroupCreator(subgroupName);
            if (creator == null || !creator.equalsIgnoreCase(username)) {
                System.out.println("只有小组创建者可以删除小组: " + username + " -> " + subgroupName);
                System.out.println("[调试] 创建者: '" + creator + "', 当前用户: '" + username + "'");
                return false;
            }

            // 获取小组ID
            int subgroupId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT subgroup_id FROM subgroups WHERE subgroup_name = ?")) {
                stmt.setString(1, subgroupName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    subgroupId = rs.getInt("subgroup_id");
                }
            }

            if (subgroupId == -1) return false;

            // 开始事务
            conn.setAutoCommit(false);
            try {
                // 1. 删除小组成员
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM subgroup_members WHERE subgroup_id = ?")) {
                    stmt.setInt(1, subgroupId);
                    stmt.executeUpdate();
                }

                // 2. 删除小组聊天消息（如果有的话）
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM messages WHERE receiver_id = ? AND chat_type = 'subgroup'")) {
                    stmt.setInt(1, subgroupId);
                    stmt.executeUpdate();
                }

                // 3. 删除小组
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM subgroups WHERE subgroup_id = ?")) {
                    stmt.setInt(1, subgroupId);
                    int affectedRows = stmt.executeUpdate();
                    
                    if (affectedRows > 0) {
                        conn.commit();
                        System.out.println("小组 " + subgroupName + " 已被创建者 " + username + " 删除");
                        return true;
                    }
                }
                
                conn.rollback();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("删除小组时出错: " + e.getMessage());
        }
        return false;
    }

    /**
     * 获取小组创建者
     * @param subgroupName 小组名称
     * @return 创建者用户名，如果不存在返回null
     */
    public String getSubgroupCreator(String subgroupName) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.username FROM users u JOIN subgroups s ON u.user_id = s.creator_id WHERE s.subgroup_name = ?")) {
            stmt.setString(1, subgroupName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            System.err.println("获取小组创建者时出错: " + e.getMessage());
        }
        return null;
    }
}