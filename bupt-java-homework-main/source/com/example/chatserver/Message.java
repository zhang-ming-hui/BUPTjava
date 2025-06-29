package com.example.chatserver;

public class Message {
    public enum MessageType {
        PRIVATE, GROUP, SUBGROUP, FILE, IMAGE
    }

    private String sender;
    private String target;
    private String content;
    private MessageType type;
    private String fileName;
    private String fileType;
    private String chatId;
    private long timestamp;
    private long fileSize;

    // 添加getter和setter方法
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    // 保留其他getter和setter方法
    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public boolean isFileMessage() {
        return "file".equals(fileType);
    }

    public boolean isImageMessage() {
        return "image".equals(fileType);
    }
}