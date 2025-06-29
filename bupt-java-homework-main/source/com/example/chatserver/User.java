package com.example.chatserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class User {
    private String username;
    private String password;
    private boolean isOnline;
    private String currentSessionId;
    private Set<String> joinedGroups;
    private Set<String> joinedSubGroups;
    private Map<String, Integer> unreadMessageCounts; // 未读消息计数
    private long lastLoginTime;
    private String lastLoginIP;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.isOnline = false;
        this.joinedGroups = ConcurrentHashMap.newKeySet();
        this.joinedSubGroups = ConcurrentHashMap.newKeySet();
        this.unreadMessageCounts = new ConcurrentHashMap<>();
        this.lastLoginTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    public Set<String> getJoinedGroups() {
        return joinedGroups;
    }

    public Set<String> getJoinedSubGroups() {
        return joinedSubGroups;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long time) {
        this.lastLoginTime = time;
    }

    public String getLastLoginIP() {
        return lastLoginIP;
    }

    public void setLastLoginIP(String ip) {
        this.lastLoginIP = ip;
    }

    // 未读消息管理
    public void incrementUnreadCount(String chatId) {
        unreadMessageCounts.merge(chatId, 1, Integer::sum);
    }

    public void clearUnreadCount(String chatId) {
        unreadMessageCounts.remove(chatId);
    }

    public int getUnreadCount(String chatId) {
        return unreadMessageCounts.getOrDefault(chatId, 0);
    }

    public Map<String, Integer> getAllUnreadCounts() {
        return Collections.unmodifiableMap(unreadMessageCounts);
    }

    // 群组管理
    public void joinGroup(String groupName) {
        joinedGroups.add(groupName);
    }

    public void leaveGroup(String groupName) {
        joinedGroups.remove(groupName);
    }

    public boolean isInGroup(String groupName) {
        return joinedGroups.contains(groupName);
    }

    // 小组管理
    public void joinSubGroup(String subGroupName) {
        joinedSubGroups.add(subGroupName);
    }

    public void leaveSubGroup(String subGroupName) {
        joinedSubGroups.remove(subGroupName);
    }

    public boolean isInSubGroup(String subGroupName) {
        return joinedSubGroups.contains(subGroupName);
    }

    // 检查是否可以加入新小组（不能同时加入两个小组）
    public boolean canJoinNewSubGroup() {
        return joinedSubGroups.isEmpty();
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', online=" + isOnline + "}";
    }
}