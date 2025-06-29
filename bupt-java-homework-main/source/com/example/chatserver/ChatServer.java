package com.example.chatserver;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class ChatServer {
    private static final int PORT = 8000;
    private static final Gson gson = new Gson();
    
    // 管理器实例
    private static UserManager userManager;
    private static MessageManager messageManager;
    private static GroupManager groupManager;
    private static FileManager fileManager;

    // 客户端连接管理
    private static Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // 初始化所有管理器
        userManager = new UserManager();
        messageManager = new MessageManager();
        groupManager = new GroupManager();
        fileManager = new FileManager();
        
        System.out.println("智能聊天服务器启动中...");
        System.out.println("端口: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("服务器已启动，等待客户端连接...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("新客户端连接: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 客户端处理器
    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private OutputStream out;
        private InputStream in;
        private String sessionId;
        private User currentUser;
        private boolean isWebSocket = false;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = clientSocket.getOutputStream();
                in = clientSocket.getInputStream();

                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    String message;
                    if (isWebSocket) {
                        message = parseWebSocketFrame(Arrays.copyOf(buffer, bytesRead));
                    } else {
                        message = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        if (message.startsWith("GET / HTTP/1.1")) {
                            handleWebSocketHandshake(message);
                            continue;
                        }
                    }

                    if (message != null && !message.trim().isEmpty()) {
                        System.out.println("收到消息: " + message);
                        handleMessage(message);
                    }
                }
            } catch (IOException e) {
                System.err.println("处理客户端消息时出错: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String message) {
            try {
                // 尝试解析JSON消息（图片上传）
                if (message.startsWith("{") && message.endsWith("}")) {
                    JsonObject json = gson.fromJson(message, JsonObject.class);
                    if (json.has("type") && "IMAGE_UPLOAD".equals(json.get("type").getAsString())) {
                        handleImageUpload(json);
                        return;
                    }
                    if (json.has("type") && "FILE_UPLOAD".equals(json.get("type").getAsString())) {
                        handleFileUpload(json);
                        return;
                    }
                }

                String[] parts = message.split("\\|");
                String command = parts[0];
                
                switch (command) {
                    case "LOGIN":
                        handleLogin(parts[1], parts[2]);
                        break;
                    case "LOGOUT":
                        handleLogout();
                        break;
                    case "CREATE_GROUP":
                    {
                        String groupName = parts.length > 1 ? parts[1] : "";
                        String description = parts.length > 2 ? parts[2] : "";
                        handleCreateGroup(groupName, description);
                        break;
                    }
                    case "CREATE_SUBGROUP":
                        handleCreateSubgroup(
                            parts[1],
                            parts[2],
                            parts.length > 3 ? parts[3] : ""
                        );
                        break;
                    case "JOIN_GROUP":
                        handleJoinGroup(parts[1]);
                        break;
                    case "LEAVE_GROUP":
                        handleLeaveGroup(parts[1]);
                        break;
                    case "JOIN_SUBGROUP":
                        handleJoinSubgroup(parts[1]);
                        break;
                    case "ACCEPT_SUBGROUP_INVITE":
                        handleAcceptSubgroupInvite(parts[1]);
                        break;
                    case "DECLINE_SUBGROUP_INVITE":
                        handleDeclineSubgroupInvite(parts[1]);
                        break;
                    case "GET_GROUP_MEMBERS":
                        handleGetGroupMembers(parts[1]);
                        break;
                    case "GET_USER_GROUPS":
                        handleGetUserGroups();
                        break;
                    case "INVITE_TO_GROUP":
                        handleInviteToGroup(parts[1], parts[2]);
                        break;
                    case "GROUP_MESSAGE":
                        handleGroupMessage(parts[1], parts[2]);
                        break;
                    case "SUBGROUP_MESSAGE":
                        handleSubgroupMessage(parts[1], parts[2]);
                        break;
                    case "PRIVATE_MESSAGE":
                        handlePrivateMessage(parts[1], parts[2]);
                        break;
                    case "FILE_MESSAGE":
                        sendMessage("ERROR|请升级前端，文件发送请用新版上传接口");
                        break;
                    case "IMAGE_MESSAGE":
                        sendMessage("ERROR|请升级前端，图片发送请用新版上传接口");
                        break;
                    case "GET_MESSAGES":
                        handleGetMessages(parts[1], parts[2]);
                        break;
                    case "MARK_READ":
                        handleMarkRead(parts[1], parts[2]);
                        break;
                    case "INVITE_TO_SUBGROUP":
                        if (parts.length >= 3) {
                            handleInviteToSubgroup(parts[1], parts[2]);
                        }
                        break;
                    case "GET_SUBGROUP_INVITABLE_USERS":
                        if (parts.length >= 3) {
                            handleGetSubgroupInvitableUsers(parts[1], parts[2]);
                        }
                        break;
                    default:
                        System.out.println("未知命令: " + command);
                }
            } catch (Exception e) {
                System.err.println("处理消息时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 登录处理
        private void handleLogin(String username, String password) {
            if (userManager.authenticateUser(username, password)) {
                String clientIP = clientSocket.getInetAddress().getHostAddress();
                sessionId = userManager.loginUser(username, clientIP);
                currentUser = userManager.getUserBySession(sessionId);
                
                if (sessionId != null) {
                    onlineClients.put(sessionId, this);
                    sendMessage("LOGIN_SUCCESS|" + username);
                    
                    // 发送用户列表和群组列表给当前用户
                    broadcastUserList();
                    sendGroupListToCurrentUser();
                    broadcastSubgroupList();
                    
                    // 广播用户加入通知给其他用户
                    for (ClientHandler client : onlineClients.values()) {
                        if (client != this) {
                            client.sendMessage("USER_JOINED|" + username);
                        }
                    }
                    
                    System.out.println("用户 " + username + " 登录成功");
                } else {
                    sendMessage("LOGIN_FAILED|登录失败");
                }
            } else {
                sendMessage("LOGIN_FAILED|用户名或密码错误");
            }
        }
        
        // 登出处理
        private void handleLogout() {
            if (sessionId != null && currentUser != null) {
                String username = currentUser.getUsername();
                userManager.logoutUser(sessionId);
                onlineClients.remove(sessionId);
                broadcastGroupListToAll();
                broadcastToAll("USER_LEFT|" + username);
                System.out.println("用户 " + username + " 登出");
            }
        }
        
        // 创建群组
        private void handleCreateGroup(String groupName, String description) {
            if (currentUser == null) return;
            
            if (groupManager.createGroup(groupName, currentUser.getUsername())) {
                groupManager.joinGroup(groupName, currentUser.getUsername());
                broadcastGroupListToAll();
                sendMessage("GROUP_CREATED|" + groupName);
                System.out.println("群组 " + groupName + " 创建成功");
            } else {
                sendMessage("GROUP_CREATE_FAILED|群组已存在");
            }
        }
        
        // 创建小组
        private void handleCreateSubgroup(String parentGroup, String subgroupName, String membersStr) {
            if (currentUser == null) return;
            
            // 检查群组是否存在
            Group group = groupManager.getGroup(parentGroup);
            if (group == null) {
                sendMessage("SUBGROUP_CREATE_FAILED|群组不存在");
                return;
            }
            
            // 检查用户是否在群组中
            if (!groupManager.isUserInGroup(parentGroup, currentUser.getUsername())) {
                sendMessage("SUBGROUP_CREATE_FAILED|您不在该群组中");
                return;
            }
            
            List<String> members = Arrays.asList(membersStr.split(","));
                if (groupManager.createSubgroup(parentGroup, subgroupName, currentUser.getUsername(), members)) {
                    // 广播小组列表给所有在线用户
                    broadcastSubgroupListToAll();

                    sendMessage("SUBGROUP_CREATED|" + subgroupName);

                    // 发送邀请给选中的成员（如果有的话）
                    for (String member : members) {
                        if (!member.isEmpty() && !member.equals(currentUser.getUsername())) {
                            // 新增：过滤已在其他小组的成员
                            if (groupManager.isUserInAnySubgroup(member)) {
                                // 可选：通知创建者哪些成员未被邀请
                                sendMessage("SUBGROUP_INVITE_SKIPPED|" + member + "|已在其他小组，未发送邀请");
                                continue;
                            }
                            ClientHandler memberClient = getClientByUsername(member);
                            if (memberClient != null) {
                                memberClient.sendMessage("SUBGROUP_INVITE|" + subgroupName + "|" + parentGroup + "|" + currentUser.getUsername());
                                System.out.println("发送小组邀请: " + subgroupName + " -> " + member);
                            } else {
                                System.out.println("用户 " + member + " 不在线，无法发送邀请");
                            }
                        }
                    }

                    System.out.println("小组 " + subgroupName + " 创建成功");
                } else {
                    sendMessage("SUBGROUP_CREATE_FAILED|小组创建失败");
                }
        }
        
        // 加入群组
        private void handleJoinGroup(String groupName) {
            if (currentUser == null) return;
            
            if (groupManager.joinGroup(groupName, currentUser.getUsername())) {
                broadcastGroupListToAll();
                sendMessage("GROUP_JOINED|" + groupName);
            } else {
                sendMessage("GROUP_JOIN_FAILED|群组不存在或已加入");
            }
        }
        
        // 离开群组
        private void handleLeaveGroup(String groupName) {
            if (currentUser == null) return;
            
            if (groupManager.leaveGroup(groupName, currentUser.getUsername())) {
                broadcastGroupListToAll();
                sendMessage("GROUP_LEFT|" + groupName);
            } else {
                sendMessage("GROUP_LEAVE_FAILED|操作失败");
            }
        }
        
        // 加入小组
        private void handleJoinSubgroup(String subgroupName) {
            if (currentUser == null) return;
            
            if (groupManager.joinSubgroup(subgroupName, currentUser.getUsername())) {
                broadcastSubgroupListToAll();
                sendMessage("SUBGROUP_JOINED|" + subgroupName);
            } else {
                sendMessage("SUBGROUP_JOIN_FAILED|小组不存在或已加入");
            }
        }
        
        // 接受小组邀请
        private void handleAcceptSubgroupInvite(String subgroupName) {
            if (currentUser == null) return;
            
            if (groupManager.acceptSubgroupInvite(subgroupName, currentUser.getUsername())) {
                // 广播小组列表更新给所有在线用户
                broadcastSubgroupListToAll();
                sendMessage("SUBGROUP_INVITE_ACCEPTED|" + subgroupName);
                System.out.println("用户 " + currentUser.getUsername() + " 接受了小组 " + subgroupName + " 的邀请");
            } else {
                sendMessage("SUBGROUP_INVITE_FAILED|您已经在其他小组中或邀请无效");
            }
        }
        
        // 拒绝小组邀请
        private void handleDeclineSubgroupInvite(String subgroupName) {
            if (currentUser == null) return;
            
            if (groupManager.declineSubgroupInvite(subgroupName, currentUser.getUsername())) {
                sendMessage("SUBGROUP_INVITE_DECLINED|" + subgroupName);
                System.out.println("用户 " + currentUser.getUsername() + " 拒绝了小组 " + subgroupName + " 的邀请");
            } else {
                sendMessage("SUBGROUP_INVITE_FAILED|邀请无效");
            }
        }
        
        // 获取群组成员
        private void handleGetGroupMembers(String groupName) {
            if (currentUser == null) return;
            
            // 检查群组是否存在
            Group group = groupManager.getGroup(groupName);
            if (group == null) {
                sendMessage("GET_GROUP_MEMBERS_FAILED|群组不存在");
                return;
            }
            
            List<String> members = groupManager.getGroupMembers(groupName);
            String membersJson = gson.toJson(members);
            sendMessage("GROUP_MEMBERS|" + groupName + "|" + membersJson);
            System.out.println("发送群组成员列表: " + groupName + " -> " + membersJson);
        }
        
        // 获取用户的群组列表
        private void handleGetUserGroups() {
            if (currentUser == null) return;
            
            // 直接广播更新群组列表
            sendGroupListToCurrentUser();
        }
        
        // 邀请用户加入群组
        private void handleInviteToGroup(String groupName, String targetUser) {
            if (currentUser == null) return;
            
            // 检查群组是否存在
            Group group = groupManager.getGroup(groupName);
            if (group == null) {
                sendMessage("INVITE_TO_GROUP_FAILED|群组不存在");
                return;
            }
            
            // 检查当前用户是否在群组中
            if (!groupManager.isUserInGroup(groupName, currentUser.getUsername())) {
                sendMessage("INVITE_TO_GROUP_FAILED|您不在该群组中");
                return;
            }
            
            // 检查目标用户是否已经在群组中
            if (groupManager.isUserInGroup(groupName, targetUser)) {
                sendMessage("INVITE_TO_GROUP_FAILED|用户已在群组中");
                return;
            }
            
            // 发送邀请给目标用户
            ClientHandler targetClient = getClientByUsername(targetUser);
            if (targetClient != null) {
                targetClient.sendMessage("GROUP_INVITE|" + groupName + "|" + currentUser.getUsername());
                sendMessage("INVITE_TO_GROUP_SUCCESS|已向 " + targetUser + " 发送群组邀请");
                System.out.println("用户 " + currentUser.getUsername() + " 邀请 " + targetUser + " 加入群组 " + groupName);
            } else {
                sendMessage("INVITE_TO_GROUP_FAILED|用户不在线");
            }
        }
        
        // 群组消息处理
        private void handleGroupMessage(String groupName, String content) {
            if (currentUser == null) return;
            
            // 检查用户是否在群组中
            if (!groupManager.isUserInGroup(groupName, currentUser.getUsername())) {
                sendMessage("GROUP_MESSAGE_FAILED|您不在该群组中");
                return;
            }
            
            // 创建消息
            Message message = new Message();
            message.setSender(currentUser.getUsername());
            message.setContent(content);
            message.setType(Message.MessageType.GROUP);
            message.setTarget(groupName);
            message.setTimestamp(System.currentTimeMillis());
            // 自动识别图片/文件
            String messageStr;
            if (content.startsWith("IMAGE:")) {
                String fileUrl = content.substring(6);
                message.setFileType("image");
                message.setFileName(fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
                messageManager.saveMessage(message);
                messageStr = "IMAGE_MESSAGE|" + groupName + "|" + currentUser.getUsername() + "|" + fileUrl + "||" + message.getTimestamp();
            } else if (content.startsWith("FILE:")) {
                String fileUrl = content.substring(5);
                message.setFileType("file");
                message.setFileName(fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
                messageManager.saveMessage(message);
                messageStr = "FILE_MESSAGE|" + groupName + "|" + currentUser.getUsername() + "|" + fileUrl + "|" + message.getFileName() + "||" + message.getTimestamp();
            } else {
                messageManager.saveMessage(message);
                messageStr = "GROUP_MESSAGE|" + groupName + "|" + currentUser.getUsername() + "|" + content + "|" + message.getTimestamp();
            }
            // 广播给群组所有成员
            List<String> groupMembers = groupManager.getGroupMembers(groupName);
            for (String member : groupMembers) {
                if (!member.equals(currentUser.getUsername())) {
                    ClientHandler memberClient = getClientByUsername(member);
                    if (memberClient != null) {
                        memberClient.sendMessage(messageStr);
                    }
                }
            }
            System.out.println("群组消息: " + currentUser.getUsername() + " -> " + groupName + ": " + content);
        }
        
        // 小组消息处理
        private void handleSubgroupMessage(String subgroupName, String content) {
            if (currentUser == null) return;
            
            // 检查用户是否在小组中
            if (!groupManager.isUserInSubgroup(subgroupName, currentUser.getUsername())) {
                sendMessage("SUBGROUP_MESSAGE_FAILED|您不在该小组中");
                return;
            }
            // 创建消息
            Message message = new Message();
            message.setSender(currentUser.getUsername());
            message.setContent(content);
            message.setType(Message.MessageType.SUBGROUP);
            message.setTarget(subgroupName);
            message.setTimestamp(System.currentTimeMillis());
            String messageStr;
            if (content.startsWith("IMAGE:")) {
                String fileUrl = content.substring(6);
                message.setFileType("image");
                message.setFileName(fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
                messageManager.saveMessage(message);
                messageStr = "IMAGE_MESSAGE|" + subgroupName + "|" + currentUser.getUsername() + "|" + fileUrl + "||" + message.getTimestamp();
            } else if (content.startsWith("FILE:")) {
                String fileUrl = content.substring(5);
                message.setFileType("file");
                message.setFileName(fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
                messageManager.saveMessage(message);
                messageStr = "FILE_MESSAGE|" + subgroupName + "|" + currentUser.getUsername() + "|" + fileUrl + "|" + message.getFileName() + "||" + message.getTimestamp();
            } else {
                messageManager.saveMessage(message);
                messageStr = "SUBGROUP_MESSAGE|" + subgroupName + "|" + currentUser.getUsername() + "|" + content + "|" + message.getTimestamp();
            }
            // 广播给小组所有成员
            List<String> subgroupMembers = groupManager.getSubgroupMembers(subgroupName);
            for (String member : subgroupMembers) {
                if (!member.equals(currentUser.getUsername())) {
                    ClientHandler memberClient = getClientByUsername(member);
                    if (memberClient != null) {
                        memberClient.sendMessage(messageStr);
                    }
                }
            }
            System.out.println("小组消息: " + currentUser.getUsername() + " -> " + subgroupName + ": " + content);
        }
        
        // 私聊消息处理
        private void handlePrivateMessage(String target, String content) {
            if (currentUser == null) return;
            // 检查目标用户是否在线
            ClientHandler targetClient = getClientByUsername(target);
            if (targetClient == null) {
                sendMessage("PRIVATE_MESSAGE_FAILED|用户不在线");
                return;
            }
            // 创建消息
            Message message = new Message();
            message.setSender(currentUser.getUsername());
            message.setContent(content);
            message.setType(Message.MessageType.PRIVATE);
            message.setTarget(target);
            message.setTimestamp(System.currentTimeMillis());
            String messageStr;
            if (content.startsWith("IMAGE:")) {
                String fileUrl = content.substring(6);
                message.setFileType("image");
                message.setFileName(fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
                messageManager.saveMessage(message);
                messageStr = "IMAGE_MESSAGE|" + target + "|" + currentUser.getUsername() + "|" + fileUrl + "||" + message.getTimestamp();
            } else if (content.startsWith("FILE:")) {
                String fileUrl = content.substring(5);
                message.setFileType("file");
                message.setFileName(fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
                messageManager.saveMessage(message);
                messageStr = "FILE_MESSAGE|" + target + "|" + currentUser.getUsername() + "|" + fileUrl + "|" + message.getFileName() + "||" + message.getTimestamp();
            } else {
                messageManager.saveMessage(message);
                messageStr = "PRIVATE_MESSAGE|" + currentUser.getUsername() + "|" + content + "|" + message.getTimestamp();
            }
            // 发送给目标用户
            targetClient.sendMessage(messageStr);
            System.out.println("私聊消息: " + currentUser.getUsername() + " -> " + target + ": " + content);
        }
        
        // 获取消息历史
        private void handleGetMessages(String chatId, String chatType) {
            if (currentUser == null) return;
            
            List<Message> messages = messageManager.getMessages(chatId, chatType, currentUser.getUsername());
            JsonArray messageArray = new JsonArray();
            
            for (Message msg : messages) {
                JsonObject messageObj = new JsonObject();
                messageObj.addProperty("sender", msg.getSender());
                messageObj.addProperty("content", msg.getContent());
                messageObj.addProperty("timestamp", msg.getTimestamp());
                messageObj.addProperty("type", msg.getType().toString());
                if (msg.isFileMessage() || msg.isImageMessage()) {
                    messageObj.addProperty("fileType", msg.getFileType());
                    messageObj.addProperty("fileName", msg.getFileName());
                }
                messageArray.add(messageObj);
            }
            
            sendMessage("CHAT_HISTORY|" + chatId + "|" + chatType + "|" + messageArray.toString());
            System.out.println("发送历史消息: " + chatType + " " + chatId + " -> " + messages.size() + " 条消息");
        }
        
        // 标记消息为已读
        private void handleMarkRead(String chatId, String chatType) {
            if (currentUser == null) return;
            
            messageManager.markAsRead(chatId, chatType, currentUser.getUsername());
        }
        
        // 邀请用户加入小组
        private void handleInviteToSubgroup(String subgroupName, String targetUser) {
            if (currentUser == null) return;
            
            // 检查小组是否存在以及当前用户是否有权限邀请
            boolean canInvite = false;
            String parentGroup = null;
            
            for (SubGroup subgroup : groupManager.getAllSubgroups()) {
                if (subgroup.getName().equals(subgroupName)) {
                    parentGroup = subgroup.getGroupName();
                    // 检查当前用户是否在小组中或是创建者
                    if (subgroup.isMember(currentUser.getUsername()) || 
                        subgroup.getCreator().equals(currentUser.getUsername())) {
                        canInvite = true;
                    }
                    break;
                }
            }
            
            if (!canInvite) {
                sendMessage("INVITE_TO_SUBGROUP_FAILED|您没有权限邀请用户加入该小组");
                return;
            }
            
            // 检查目标用户是否在群组中
            if (!groupManager.isUserInGroup(parentGroup, targetUser)) {
                sendMessage("INVITE_TO_SUBGROUP_FAILED|目标用户不在该群组中");
                return;
            }
            
            // 检查目标用户是否已经在其他小组中
            if (groupManager.isUserInAnySubgroup(targetUser)) {
                sendMessage("INVITE_TO_SUBGROUP_FAILED|目标用户已在其他小组中");
                return;
            }
            
            // 发送邀请
            ClientHandler targetClient = getClientByUsername(targetUser);
            if (targetClient != null) {
                String inviteMessage = "SUBGROUP_INVITE|" + subgroupName + "|" + parentGroup + "|" + currentUser.getUsername();
                System.out.println("发送小组邀请消息给 " + targetUser + ": " + inviteMessage);
                targetClient.sendMessage(inviteMessage);
                sendMessage("INVITE_TO_SUBGROUP_SUCCESS|邀请已发送给 " + targetUser);
                System.out.println("用户 " + currentUser.getUsername() + " 邀请 " + targetUser + " 加入小组 " + subgroupName + " - 邀请已发送");
            } else {
                System.out.println("目标用户 " + targetUser + " 不在线，无法发送邀请");
                sendMessage("INVITE_TO_SUBGROUP_FAILED|目标用户不在线");
            }
        }
        
        // 获取可邀请到小组的用户列表
        private void handleGetSubgroupInvitableUsers(String parentGroup, String subgroupName) {
            if (currentUser == null) return;
            
            System.out.println("处理获取可邀请用户列表: " + parentGroup + " -> " + subgroupName + ", 请求者: " + currentUser.getUsername());
            
            // 检查小组是否存在以及当前用户是否有权限邀请
            boolean canInvite = false;
            for (SubGroup subgroup : groupManager.getAllSubgroups()) {
                if (subgroup.getName().equals(subgroupName) && subgroup.getGroupName().equals(parentGroup)) {
                    // 检查当前用户是否在小组中或是创建者
                    if (subgroup.isMember(currentUser.getUsername()) || 
                        subgroup.getCreator().equals(currentUser.getUsername())) {
                        canInvite = true;
                    }
                    break;
                }
            }
            
            if (!canInvite) {
                System.out.println("用户 " + currentUser.getUsername() + " 没有权限邀请用户到小组 " + subgroupName);
                sendMessage("SUBGROUP_INVITABLE_USERS|" + parentGroup + "|" + subgroupName + "|[]");
                return;
            }
            
            // 获取群组中的所有用户
            List<String> groupMembers = groupManager.getGroupMembers(parentGroup);
            List<String> invitableUsers = new ArrayList<>();
            
            System.out.println("群组 " + parentGroup + " 的成员: " + groupMembers);
            
            for (String member : groupMembers) {
                // 排除当前用户和已在小组中的用户
                boolean isCurrentUser = member.equals(currentUser.getUsername());
                boolean isInCurrentSubgroup = groupManager.isUserInSubgroup(subgroupName, member);
                boolean isInAnySubgroup = groupManager.isUserInAnySubgroup(member);
                
                System.out.println("检查用户 " + member + ": 是当前用户=" + isCurrentUser + 
                                 ", 在当前小组=" + isInCurrentSubgroup + ", 在任何小组=" + isInAnySubgroup);
                
                if (!isCurrentUser && !isInCurrentSubgroup && !isInAnySubgroup) {
                    invitableUsers.add(member);
                    System.out.println("用户 " + member + " 可被邀请");
                } else {
                    System.out.println("用户 " + member + " 不可邀请");
                }
            }
            
            String invitableUsersJson = gson.toJson(invitableUsers);
            sendMessage("SUBGROUP_INVITABLE_USERS|" + parentGroup + "|" + subgroupName + "|" + invitableUsersJson);
            
            System.out.println("发送可邀请用户列表: " + parentGroup + " -> " + subgroupName + ": " + invitableUsers.size() + " 位用户: " + invitableUsers);
        }
        
        // 广播用户列表
        private void broadcastUserList() {
            List<String> onlineUsers = new ArrayList<>();
            for (ClientHandler client : onlineClients.values()) {
                if (client.currentUser != null) {
                    onlineUsers.add(client.currentUser.getUsername());
                }
            }
            
            String userListJson = gson.toJson(onlineUsers);
            broadcastToAll("USER_LIST|" + userListJson);
        }
        
                // 只给当前用户推送群组列表
        private void sendGroupListToCurrentUser() {
            List<Map<String, Object>> groups = new ArrayList<>();
            for (String groupName : groupManager.getAllGroups()) {
                Group group = groupManager.getGroup(groupName);
                Map<String, Object> groupInfo = new HashMap<>();
                groupInfo.put("name", group.getName());
                boolean isJoined = false;
                if (currentUser != null) {
                    isJoined = groupManager.isUserInGroup(group.getName(), currentUser.getUsername());
                }
                groupInfo.put("joined", isJoined);
                groups.add(groupInfo);
            }
            String groupListJson = gson.toJson(groups);
            sendMessage("GROUP_LIST|" + groupListJson);
        }

        // 给所有在线用户推送各自的群组列表
        private void broadcastGroupListToAll() {
            for (ClientHandler client : onlineClients.values()) {
                client.sendGroupListToCurrentUser();
            }
        }

        // 广播小组列表给当前用户
        private void broadcastSubgroupList() {
            sendSubgroupListToCurrentUser();
        }
        
        // 给当前用户发送小组列表
        private void sendSubgroupListToCurrentUser() {
            List<Map<String, Object>> subgroups = new ArrayList<>();
            for (SubGroup subgroup : groupManager.getAllSubgroups()) {
                // 显示用户加入的小组或创建的小组
                if (groupManager.isUserInSubgroup(subgroup.getName(), currentUser.getUsername()) || 
                    subgroup.getCreator().equals(currentUser.getUsername())) {
                    Map<String, Object> subgroupInfo = new HashMap<>();
                    subgroupInfo.put("name", subgroup.getName());
                    subgroupInfo.put("parentGroup", subgroup.getGroupName());
                    subgroups.add(subgroupInfo);
                }
            }
            
            String subgroupListJson = gson.toJson(subgroups);
            sendMessage("SUBGROUP_LIST|" + subgroupListJson);
        }
        
        // 给所有在线用户推送各自的小组列表
        private void broadcastSubgroupListToAll() {
            for (ClientHandler client : onlineClients.values()) {
                if (client.currentUser != null) {
                    client.sendSubgroupListToCurrentUser();
                }
            }
        }
        
        // 广播给所有在线用户
        private void broadcastToAll(String message) {
            for (ClientHandler client : onlineClients.values()) {
                client.sendMessage(message);
            }
        }
        
        // 根据用户名获取客户端
        private ClientHandler getClientByUsername(String username) {
            for (ClientHandler client : onlineClients.values()) {
                if (client.currentUser != null && client.currentUser.getUsername().equals(username)) {
                    return client;
                }
            }
            return null;
        }

        // WebSocket握手处理
        private boolean handleWebSocketHandshake(String request) throws IOException {
            String[] lines = request.split("\r\n");
            String key = null;
            
            for (String line : lines) {
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    key = line.substring(19).trim();
                    break;
                }
            }
            
            if (key != null) {
                try {
                    String acceptKey = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    byte[] hash = md.digest(acceptKey.getBytes(StandardCharsets.UTF_8));
                    String accept = Base64.getEncoder().encodeToString(hash);
                    
                    String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                                    "Upgrade: websocket\r\n" +
                                    "Connection: Upgrade\r\n" +
                                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
                    
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    isWebSocket = true;
                    return true;
                } catch (NoSuchAlgorithmException e) {
                    System.err.println("SHA-1算法不可用: " + e.getMessage());
                    return false;
                }
            }
            
            return false;
        }

        private ByteArrayOutputStream fragmentBuffer = new ByteArrayOutputStream();
        private boolean isBinaryFrame = false;

        // 解析WebSocket帧
        private String parseWebSocketFrame(byte[] frame) throws IOException {
            if (frame.length < 2) {
                System.err.println("[错误] 帧长度不足");
                return null;
            }

            // 解析帧头
            boolean fin = (frame[0] & 0x80) != 0;
            int opcode = frame[0] & 0x0F;
            boolean masked = (frame[1] & 0x80) != 0;
            int payloadLength = frame[1] & 0x7F;

            // 处理操作码
            if (opcode == 0x8) { // 关闭帧
                sendCloseFrame();
                return null;
            } else if (opcode == 0x1) { // 文本帧
                isBinaryFrame = false;
            } else if (opcode == 0x2) { // 二进制帧
                isBinaryFrame = true;
            } else if (opcode == 0x9 || opcode == 0xA) { // Ping/Pong 帧
                // 忽略 Ping/Pong 帧
                return null;
            } else if (opcode != 0x0) { // 其他非延续帧
                System.err.println("[警告] 不支持的操作码: " + opcode);
                return null;
            }

            // 处理负载长度
            int offset = 2;
            if (payloadLength == 126) {
                payloadLength = ((frame[offset] & 0xFF) << 8) | (frame[offset+1] & 0xFF);
                offset += 2;
            } else if (payloadLength == 127) {
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength = (payloadLength << 8) | (frame[offset+i] & 0xFF);
                }
                offset += 8;
            }

            // 处理掩码
            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                System.arraycopy(frame, offset, mask, 0, 4);
                offset += 4;
            }

            // 检查数据完整性
            if (frame.length < offset + payloadLength) {
                System.err.println("[警告] 不完整帧，等待后续数据");
                // 可以设置一个超时机制，避免长时间等待
                return null;
            }

            // 提取负载数据
            byte[] payload = Arrays.copyOfRange(frame, offset, offset + payloadLength);

            // 解掩码
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }

            // 处理分段帧
            fragmentBuffer.write(payload);
            if (!fin) {
                System.out.println("[调试] 收到分段帧，等待后续帧");
                return null;
            }

            // 完整帧处理
            byte[] completeData = fragmentBuffer.toByteArray();
            fragmentBuffer.reset();

            if (isBinaryFrame) {
                // 二进制数据处理（如图片）
                handleBinaryData(completeData);
                return null;
            } else {
                // 文本数据处理
                return new String(completeData, StandardCharsets.UTF_8);
            }
        }

        private void handleBinaryData(byte[] data) {
            // 这里处理图片等二进制数据
            System.out.println("收到二进制数据，长度: " + data.length);

            // 示例：将二进制数据转发给文件处理器
            FileManager fileManager = new FileManager();
            String fileName = "upload_" + System.currentTimeMillis() + ".png";
            String fileData = Base64.getEncoder().encodeToString(data);

            FileManager.FileInfo fileInfo = fileManager.uploadFile(
                    fileName,
                    "image/png",
                    currentUser != null ? currentUser.getUsername() : "anonymous",
                    "binary_upload",
                    Message.MessageType.IMAGE,
                    fileData
            );

            if (fileInfo != null) {
                System.out.println("文件上传成功: " + fileInfo.getFileUrl());
            }
        }

        // 构建WebSocket帧
        private byte[] buildWebSocketFrame(String message) throws IOException {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            int length = payload.length;
            
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            
            // FIN + opcode (text frame)
            frame.write(0x81);
            
            if (length < 126) {
                frame.write(length);
            } else if (length < 65536) {
                frame.write(126);
                frame.write((length >> 8) & 0xFF);
                frame.write(length & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) {
                    frame.write((int) ((length >> (i * 8)) & 0xFF));
                }
            }
            
            frame.write(payload);
            return frame.toByteArray();
        }
        
        // 发送关闭帧
        private void sendCloseFrame() throws IOException {
            byte[] closeFrame = { (byte) 0x88, 0x00 };
            out.write(closeFrame);
            out.flush();
        }

        // 发送消息
        public void sendMessage(String message) {
            try {
                if (isWebSocket) {
                    byte[] frame = buildWebSocketFrame(message);
                    out.write(frame);
                } else {
                    out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                }
                out.flush();
            } catch (IOException e) {
                System.err.println("发送消息失败: " + e.getMessage());
            }
        }

        // 清理资源
        private void cleanup() {
            try {
                if (sessionId != null) {
                    onlineClients.remove(sessionId);
                    if (currentUser != null) {
                        userManager.logoutUser(sessionId);
                        broadcastUserList();
                        broadcastToAll("USER_LEFT|" + currentUser.getUsername());
                    }
                }
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("清理资源时出错: " + e.getMessage());
            }
        }

        private void handleImageUpload(JsonObject message) {
            try {
                if (currentUser == null) {
                    sendMessage("IMAGE_UPLOAD_FAILED|未登录用户不能上传图片");
                    return;
                }

                String target = message.get("target").getAsString();
                String chatType = message.get("chatType").getAsString();
                String fileName = message.get("fileName").getAsString();
                long fileSize = message.get("fileSize").getAsLong();
                String fileType = message.get("fileType").getAsString();
                String base64Data = message.get("data").getAsString();
                // 防御性去除前缀
                if (base64Data.contains(",")) {
                    base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                }
                // 调试输出base64内容
                System.out.println("[IMAGE_UPLOAD] base64Data 前20字符: " + base64Data.substring(0, Math.min(20, base64Data.length())));
                System.out.println("[IMAGE_UPLOAD] base64Data 是否全为合法字符: " + base64Data.matches("^[A-Za-z0-9+/=]+$"));
                // 解码Base64数据
                byte[] imageData = Base64.getDecoder().decode(base64Data);

                // 创建临时文件
                File tempDir = new File("temp_uploads");
                if (!tempDir.exists()) tempDir.mkdirs();

                String tempFileName = "upload_" + System.currentTimeMillis() + "_" + fileName;
                File tempFile = new File(tempDir, tempFileName);

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(imageData);
                }

                // 上传到COS
                String cosPath = "chat_images/" + tempFileName;
                String fileUrl = COSFileUploader.uploadFile(tempFile, cosPath);

                // Debug 输出 fileUrl
                System.out.println("[DEBUG] IMAGE_UPLOAD fileUrl: " + fileUrl);
                if (fileUrl == null || !(fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))) {
                    System.err.println("[ERROR] fileUrl 非直链，终止广播: " + fileUrl);
                    sendMessage("IMAGE_UPLOAD_FAILED|图片上传到云存储失败（fileUrl 非直链）");
                    return;
                }

                // 删除临时文件
                tempFile.delete();

                if (fileUrl == null) {
                    sendMessage("IMAGE_UPLOAD_FAILED|图片上传到云存储失败");
                    return;
                }

                // 创建消息记录
                Message msg = new Message();
                msg.setSender(currentUser.getUsername());
                msg.setTarget(target);
                msg.setContent("IMAGE:" + fileUrl);
                msg.setType(Message.MessageType.valueOf(chatType));
                msg.setTimestamp(System.currentTimeMillis());
                msg.setFileName(fileName);
                msg.setFileType("image");

                // 保存消息
                messageManager.saveMessage(msg);

                // 广播消息
                String messageStr = String.format("IMAGE_MESSAGE|%s|%s|%s|%d|%d",
                        target,
                        currentUser.getUsername(),
                        fileUrl, // 必须是 COS 直链
                        fileSize,
                        msg.getTimestamp());

                // 给自己发一份，保证前端立即显示
                sendMessage(messageStr);

                // 根据聊天类型广播给其他用户
                switch (chatType) {
                    case "GROUP":
                        List<String> groupMembers = groupManager.getGroupMembers(target);
                        for (String member : groupMembers) {
                            if (!member.equals(currentUser.getUsername())) {
                                ClientHandler memberClient = getClientByUsername(member);
                                if (memberClient != null) {
                                    memberClient.sendMessage(messageStr);
                                }
                            }
                        }
                        break;
                    case "SUBGROUP":
                        List<String> subgroupMembers = groupManager.getSubgroupMembers(target);
                        for (String member : subgroupMembers) {
                            if (!member.equals(currentUser.getUsername())) {
                                ClientHandler memberClient = getClientByUsername(member);
                                if (memberClient != null) {
                                    memberClient.sendMessage(messageStr);
                                }
                            }
                        }
                        break;
                    default: // PRIVATE
                        ClientHandler targetClient = getClientByUsername(target);
                        if (targetClient != null) {
                            targetClient.sendMessage(messageStr);
                        }
                }

                System.out.println("[DEBUG] IMAGE_MESSAGE fileUrl: " + fileUrl);

            } catch (Exception e) {
                System.err.println("处理图片上传时出错: " + e.getMessage());
                sendMessage("IMAGE_UPLOAD_FAILED|图片处理失败: " + e.getMessage());
            }
        }

        // 文件上传处理
        private void handleFileUpload(JsonObject message) {
            try {
                if (currentUser == null) {
                    sendMessage("FILE_UPLOAD_FAILED|未登录用户不能上传文件");
                    return;
                }

                String target = message.get("target").getAsString();
                String chatType = message.get("chatType").getAsString();
                String fileName = message.get("fileName").getAsString();
                long fileSize = message.get("fileSize").getAsLong();
                String fileType = message.get("fileType").getAsString();
                String base64Data = message.get("data").getAsString();
                // 防御性去除前缀
                if (base64Data.contains(",")) {
                    base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                }
                // 调试输出base64内容
                System.out.println("[FILE_UPLOAD] base64Data 前20字符: " + base64Data.substring(0, Math.min(20, base64Data.length())));
                System.out.println("[FILE_UPLOAD] base64Data 是否全为合法字符: " + base64Data.matches("^[A-Za-z0-9+/=]+$"));
                // 解码Base64数据
                byte[] fileData = java.util.Base64.getDecoder().decode(base64Data);

                // 创建临时文件
                File tempDir = new File("temp_uploads");
                if (!tempDir.exists()) tempDir.mkdirs();

                String tempFileName = "upload_" + System.currentTimeMillis() + "_" + fileName;
                File tempFile = new File(tempDir, tempFileName);

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(fileData);
                }

                // 上传到COS
                String cosPath = "chat_files/" + tempFileName;
                String fileUrl = COSFileUploader.uploadFile(tempFile, cosPath);

                // 删除临时文件
                tempFile.delete();

                if (fileUrl == null || !(fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))) {
                    sendMessage("FILE_UPLOAD_FAILED|文件上传到云存储失败（fileUrl 非直链）");
                    return;
                }

                // 创建消息记录
                Message msg = new Message();
                msg.setSender(currentUser.getUsername());
                msg.setTarget(target);
                msg.setContent("FILE:" + fileUrl);
                msg.setType(Message.MessageType.valueOf(chatType));
                msg.setTimestamp(System.currentTimeMillis());
                msg.setFileName(fileName);
                msg.setFileType("file");
                msg.setFileSize(fileSize);

                // 保存消息
                messageManager.saveMessage(msg);

                // 广播消息
                String messageStr = String.format("FILE_MESSAGE|%s|%s|%s|%s|%d|%d",
                        target,
                        currentUser.getUsername(),
                        fileUrl,
                        fileName,
                        fileSize,
                        msg.getTimestamp());

                // 给自己发一份，保证前端立即显示
                sendMessage(messageStr);

                // 根据聊天类型广播
                switch (chatType) {
                    case "GROUP":
                        List<String> groupMembers = groupManager.getGroupMembers(target);
                        for (String member : groupMembers) {
                            if (!member.equals(currentUser.getUsername())) {
                                ClientHandler memberClient = getClientByUsername(member);
                                if (memberClient != null) {
                                    memberClient.sendMessage(messageStr);
                                }
                            }
                        }
                        break;
                    case "SUBGROUP":
                        List<String> subgroupMembers = groupManager.getSubgroupMembers(target);
                        for (String member : subgroupMembers) {
                            if (!member.equals(currentUser.getUsername())) {
                                ClientHandler memberClient = getClientByUsername(member);
                                if (memberClient != null) {
                                    memberClient.sendMessage(messageStr);
                                }
                            }
                        }
                        break;
                    default: // PRIVATE
                        ClientHandler targetClient = getClientByUsername(target);
                        if (targetClient != null) {
                            targetClient.sendMessage(messageStr);
                        }
                }

                System.out.println("[DEBUG] FILE_MESSAGE fileUrl: " + fileUrl);

            } catch (Exception e) {
                System.err.println("处理文件上传时出错: " + e.getMessage());
                sendMessage("FILE_UPLOAD_FAILED|文件处理失败: " + e.getMessage());
            }
        }
    }
}