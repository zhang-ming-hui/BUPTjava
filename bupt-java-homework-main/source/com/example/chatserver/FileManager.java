package com.example.chatserver;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

public class FileManager {
    private static final String FILE_DIR = "files";
    private Map<String, FileInfo> files; // fileName -> FileInfo
    
    public FileManager() {
        this.files = new ConcurrentHashMap<>();
        new File(FILE_DIR).mkdirs();
    }

    
    // 文件信息类
    public static class FileInfo {
        private String fileName;
        private String originalName;
        private String fileType;
        private String uploader;
        private String chatId; // 群组名或用户名
        private Message.MessageType chatType;
        private long uploadTime;
        private long fileSize;
        private String fileData; // Base64编码的文件数据
        private String fileUrl; // 新增字段

        public FileInfo(String fileName, String originalName, String fileType, String uploader,
                        String chatId, Message.MessageType chatType, String fileData) {
            this.fileName = fileName;
            this.originalName = originalName;
            this.fileType = fileType;
            this.uploader = uploader;
            this.chatId = chatId;
            this.chatType = chatType;
            this.uploadTime = System.currentTimeMillis();
            this.fileData = fileData;
            this.fileSize = fileData != null ? fileData.length() : 0;
        }

        // Getters 和 Setters
        public String getFileName() { return fileName; }
        public String getOriginalName() { return originalName; }
        public String getFileType() { return fileType; }
        public String getUploader() { return uploader; }
        public String getChatId() { return chatId; }
        public Message.MessageType getChatType() { return chatType; }
        public long getUploadTime() { return uploadTime; }
        public long getFileSize() { return fileSize; }
        public String getFileData() { return fileData; }
        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        // 检查是否为图片
        public boolean isImage() {
            return fileType != null && fileType.startsWith("image/");
        }


        // 获取文件大小的人类可读格式
        public String getFileSizeString() {
            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format("%.1f KB", fileSize / 1024.0);
            } else {
                return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
            }
        }
    }
    
    // 保存文件（新方法）
    public String saveFile(String fileName, String fileData) {
        try {
            // 解码Base64数据
            String[] dataParts = fileData.split(",", 2);
            String base64Data = dataParts.length > 1 ? dataParts[1] : dataParts[0];
            byte[] data = Base64.getDecoder().decode(base64Data);
            
            // 生成唯一文件名
            String uniqueFileName = generateFileName(fileName);
            
            // 保存到磁盘
            File file = new File(FILE_DIR, uniqueFileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
            
            // 创建文件信息
            FileInfo fileInfo = new FileInfo(uniqueFileName, fileName, "application/octet-stream",
                    "system", "general", Message.MessageType.valueOf("FILE"), fileData);
            files.put(uniqueFileName, fileInfo);
            
            return uniqueFileName;
        } catch (Exception e) {
            System.err.println("保存文件失败: " + e.getMessage());
            return null;
        }
    }
    
    // 保存图片（新方法）
    public String saveImage(String imageName, String imageData) {
        try {
            // 解码Base64数据
            String[] dataParts = imageData.split(",", 2);
            String base64Data = dataParts.length > 1 ? dataParts[1] : dataParts[0];
            byte[] data = Base64.getDecoder().decode(base64Data);
            
            // 生成唯一文件名
            String uniqueFileName = generateFileName(imageName);
            
            // 保存到磁盘
            File file = new File(FILE_DIR, uniqueFileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
            
            // 创建文件信息
            FileInfo fileInfo = new FileInfo(uniqueFileName, imageName, "image/jpeg",
                    "system", "general", Message.MessageType.valueOf("IMAGE"), imageData);
            files.put(uniqueFileName, fileInfo);
            
            return uniqueFileName;
        } catch (Exception e) {
            System.err.println("保存图片失败: " + e.getMessage());
            return null;
        }
    }
    
    // 上传文件
    public FileInfo uploadFile(String originalName, String fileType, String uploader,
                               String chatId, Message.MessageType chatType, String fileData) {
        // 该方法已废弃，上传请直接用 COSFileUploader.uploadFile，记录请用 saveFile/saveImage
        return null;
    }
    
    // 下载文件
    public FileInfo downloadFile(String fileName) {
        FileInfo fileInfo = files.get(fileName);
        if (fileInfo == null) {
            return null;
        }
        
        // 如果内存中没有文件数据，从磁盘加载
        if (fileInfo.getFileData() == null) {
            String fileData = loadFileFromDisk(fileName);
            if (fileData != null) {
                // 创建一个新的FileInfo对象，包含文件数据
                fileInfo = new FileInfo(fileInfo.getFileName(), fileInfo.getOriginalName(), 
                                      fileInfo.getFileType(), fileInfo.getUploader(), 
                                      fileInfo.getChatId(), fileInfo.getChatType(), fileData);
                files.put(fileName, fileInfo);
            }
        }
        
        return fileInfo;
    }
    
    // 获取聊天中的文件列表
    public List<FileInfo> getChatFiles(String chatId, Message.MessageType chatType) {
        List<FileInfo> chatFiles = new ArrayList<>();
        for (FileInfo fileInfo : files.values()) {
            if (fileInfo.getChatId().equals(chatId) && fileInfo.getChatType() == chatType) {
                chatFiles.add(fileInfo);
            }
        }
        // 按上传时间排序
        chatFiles.sort((f1, f2) -> Long.compare(f2.getUploadTime(), f1.getUploadTime()));
        return chatFiles;
    }
    
    // 获取用户上传的文件
    public List<FileInfo> getUserFiles(String username) {
        List<FileInfo> userFiles = new ArrayList<>();
        for (FileInfo fileInfo : files.values()) {
            if (fileInfo.getUploader().equals(username)) {
                userFiles.add(fileInfo);
            }
        }
        // 按上传时间排序
        userFiles.sort((f1, f2) -> Long.compare(f2.getUploadTime(), f1.getUploadTime()));
        return userFiles;
    }
    
    // 删除文件
    public boolean deleteFile(String fileName, String username) {
        FileInfo fileInfo = files.get(fileName);
        if (fileInfo == null) {
            return false;
        }
        
        // 只有上传者可以删除文件
        if (!fileInfo.getUploader().equals(username)) {
            return false;
        }
        
        files.remove(fileName);
        
        // 删除磁盘上的文件
        File file = new File(FILE_DIR, fileName);
        return file.delete();
    }
    
    // 检查文件是否存在
    public boolean fileExists(String fileName) {
        return files.containsKey(fileName);
    }
    
    // 获取文件统计信息
    public Map<String, Object> getFileStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", files.size());
        
        long totalSize = 0;
        int imageCount = 0;
        int fileCount = 0;
        
        for (FileInfo fileInfo : files.values()) {
            totalSize += fileInfo.getFileSize();
            if (fileInfo.isImage()) {
                imageCount++;
            } else {
                fileCount++;
            }
        }
        
        stats.put("totalSize", totalSize);
        stats.put("imageCount", imageCount);
        stats.put("fileCount", fileCount);
        
        return stats;
    }
    
    // 私有辅助方法
    private String generateFileName(String originalName) {
        String extension = "";
        int lastDot = originalName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = originalName.substring(lastDot);
        }
        
        return System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8) + extension;
    }
    
    private void saveFileToDisk(String fileName, String fileData) {
        try {
            File file = new File(FILE_DIR, fileName);
            byte[] data = Base64.getDecoder().decode(fileData);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
        } catch (IOException e) {
            System.err.println("保存文件失败: " + e.getMessage());
        }
    }
    
    private String loadFileFromDisk(String fileName) {
        try {
            File file = new File(FILE_DIR, fileName);
            if (!file.exists()) {
                return null;
            }
            
            byte[] data = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(data);
            }
            
            return Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
            System.err.println("加载文件失败: " + e.getMessage());
            return null;
        }
    }
} 