package com.example.chatserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import com.example.chatserver.FileManager.FileInfo;

public class MessageManager {
    public void saveMessage(Message message) {
        try (Connection conn = DBConnection.getConnection()) {
            int senderId = DBConnection.getUserId(message.getSender());
            if (senderId == -1) return;

            int receiverId = -1;
            String chatType = message.getType().toString().toLowerCase();

            // 根据消息类型设置接收者ID
            if (message.getType() == Message.MessageType.PRIVATE) {
                receiverId = DBConnection.getUserId(message.getTarget());
            } else if (message.getType() == Message.MessageType.GROUP) {
                receiverId = DBConnection.getGroupId(message.getTarget());
            } else if (message.getType() == Message.MessageType.SUBGROUP) {
                int groupId = DBConnection.getGroupIdBySubgroupName(message.getTarget());
                if (groupId != -1) {
                    receiverId = DBConnection.getSubgroupId(message.getTarget(), groupId);
                }
            }

            String messageType = "text";
            String filePath = null;
            String fileName = null;

            // 处理图片消息
            if (message.isImageMessage()) {
                messageType = "image";
                fileName = message.getFileName();
                // 直接用 content 里的 fileUrl
                if (message.getContent() != null && message.getContent().startsWith("IMAGE:")) {
                    filePath = message.getContent().substring(6);
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO messages (sender_id, receiver_id, chat_type, content, message_type, file_path, file_name) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {

                stmt.setInt(1, senderId);
                if (receiverId != -1) {
                    stmt.setInt(2, receiverId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setString(3, chatType);
                stmt.setString(4, message.getContent());
                stmt.setString(5, messageType);
                stmt.setString(6, filePath);
                stmt.setString(7, fileName);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("保存消息时出错: " + e.getMessage());
        }
    }

    public List<Message> getMessages(String chatId, String chatType, String username) {
        List<Message> messages = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            int userId = DBConnection.getUserId(username);
            if (userId == -1) return messages;

            String query = "SELECT m.*, u.username AS sender_name FROM messages m " +
                    "JOIN users u ON m.sender_id = u.user_id WHERE ";

            if ("private".equalsIgnoreCase(chatType)) {
                int targetId = DBConnection.getUserId(chatId);
                query += "((m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?)) " +
                        "AND m.chat_type = 'private'";
            } else if ("group".equalsIgnoreCase(chatType)) {
                int groupId = DBConnection.getGroupId(chatId);
                query += "m.receiver_id = ? AND m.chat_type = 'group'";
            } else if ("subgroup".equalsIgnoreCase(chatType)) {
                int groupId = DBConnection.getGroupIdBySubgroupName(chatId);
                int subgroupId = DBConnection.getSubgroupId(chatId, groupId);
                query += "m.receiver_id = ? AND m.chat_type = 'subgroup'";
            } else {
                return messages;
            }

            query += " ORDER BY m.message_id ASC";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                if ("private".equalsIgnoreCase(chatType)) {
                    int targetId = DBConnection.getUserId(chatId);
                    stmt.setInt(1, userId);
                    stmt.setInt(2, targetId);
                    stmt.setInt(3, targetId);
                    stmt.setInt(4, userId);
                } else if ("group".equalsIgnoreCase(chatType)) {
                    int groupId = DBConnection.getGroupId(chatId);
                    stmt.setInt(1, groupId);
                } else if ("subgroup".equalsIgnoreCase(chatType)) {
                    int groupId = DBConnection.getGroupIdBySubgroupName(chatId);
                    int subgroupId = DBConnection.getSubgroupId(chatId, groupId);
                    stmt.setInt(1, subgroupId);
                }

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Message message = new Message();
                    message.setSender(rs.getString("sender_name"));
                    message.setContent(rs.getString("content"));
                    message.setType(Message.MessageType.valueOf(rs.getString("chat_type").toUpperCase()));

                    String messageType = rs.getString("message_type");
                    if ("file".equals(messageType)) {
                        message.setFileType("file");
                        message.setFileName(rs.getString("file_name"));
                    } else if ("image".equals(messageType)) {
                        message.setFileType("image");
                        message.setFileName(rs.getString("file_name"));
                    }

                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            System.err.println("获取消息时出错: " + e.getMessage());
        }
        return messages;
    }

    public void markAsRead(String chatId, String chatType, String username) {
        // 在数据库中标记消息为已读
        // 这里需要根据你的具体需求实现
    }
}