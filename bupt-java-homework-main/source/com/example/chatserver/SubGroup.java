package com.example.chatserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SubGroup {
    private String name;
    private String groupName; // 所属群组
    private String creator;
    private Set<String> members;
    private Set<String> invitedMembers; // 被邀请的成员
    private long createTime;
    private String description;

    public SubGroup(String name, String groupName, String creator) {
        this.name = name;
        this.groupName = groupName;
        this.creator = creator;
        this.members = ConcurrentHashMap.newKeySet();
        this.invitedMembers = ConcurrentHashMap.newKeySet();
        this.createTime = System.currentTimeMillis();
        this.description = "";

        // 创建者自动加入小组
        this.members.add(creator);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getCreator() {
        return creator;
    }

    public Set<String> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(members));
    }

    public Set<String> getInvitedMembers() {
        return Collections.unmodifiableSet(new HashSet<>(invitedMembers));
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

    // 设置邀请成员列表
    public void setInvitedMembers(List<String> members) {
        this.invitedMembers.clear();
        for (String member : members) {
            if (!member.isEmpty() && !member.equals(creator)) {
                this.invitedMembers.add(member);
            }
        }
    }

    // 接受邀请
    public boolean acceptInvite(String username) {
        if (invitedMembers.contains(username)) {
            invitedMembers.remove(username);
            return members.add(username);
        }
        return false;
    }

    // 拒绝邀请
    public boolean declineInvite(String username) {
        return invitedMembers.remove(username);
    }

    // 检查是否被邀请
    public boolean isInvited(String username) {
        return invitedMembers.contains(username);
    }

    // 成员管理
    public boolean addMember(String username) {
        return members.add(username);
    }

    public boolean removeMember(String username) {
        if (username.equals(creator)) {
            return false; // 创建者不能离开小组
        }
        return members.remove(username);
    }

    public boolean isMember(String username) {
        return members.contains(username);
    }

    public int getMemberCount() {
        return members.size();
    }

    // 获取完整的小组名称（包含群组名）
    public String getFullName() {
        return groupName + "_" + name;
    }

    @Override
    public String toString() {
        return String.format("SubGroup{name='%s', groupName='%s', creator='%s', members=%d}",
                name, groupName, creator, members.size());
    }
}