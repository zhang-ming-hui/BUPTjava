package com.example.chatserver;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Group {
    private String name;
    private String creator;
    private Set<String> members;
    private Set<String> subGroups;
    private long createTime;
    private String description;

    public Group(String name, String creator) {
        this.name = name;
        this.creator = creator;
        this.members = ConcurrentHashMap.newKeySet();
        this.subGroups = ConcurrentHashMap.newKeySet();
        this.createTime = System.currentTimeMillis();
        this.description = "";

        // 创建者自动加入群组
        this.members.add(creator);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public String getCreator() {
        return creator;
    }

    public Set<String> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(members));
    }

    public Set<String> getSubGroups() {
        return Collections.unmodifiableSet(new HashSet<>(subGroups));
    }

    public long getCreateTime() {
        return createTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // 成员管理
    public boolean addMember(String username) {
        return members.add(username);
    }

    public boolean removeMember(String username) {
        if (username.equals(creator)) {
            return false; // 创建者不能离开群组
        }
        return members.remove(username);
    }

    public boolean isMember(String username) {
        return members.contains(username);
    }

    public int getMemberCount() {
        return members.size();
    }

    // 小组管理
    public void addSubGroup(String subGroupName) {
        subGroups.add(subGroupName);
    }

    public void removeSubGroup(String subGroupName) {
        subGroups.remove(subGroupName);
    }

    public boolean hasSubGroup(String subGroupName) {
        return subGroups.contains(subGroupName);
    }

    @Override
    public String toString() {
        return String.format("Group{name='%s', creator='%s', members=%d, subGroups=%d}",
                name, creator, members.size(), subGroups.size());
    }
}