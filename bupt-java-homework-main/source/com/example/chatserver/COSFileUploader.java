package com.example.chatserver;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class COSFileUploader {
    private static final String SECRET_ID;
    private static final String SECRET_KEY;
    private static final String REGION_NAME;
    private static final String BUCKET_NAME;
    private static final String UPLOAD_DIR;
    private static final String BASE_URL;
    private static final String STORAGE_MODE;
    private static final boolean USE_COS;

    static {
        Properties props = loadProperties();
        
        // 读取配置
        SECRET_ID = props.getProperty("tencent.cos.secret-id", "");
        SECRET_KEY = props.getProperty("tencent.cos.secret-key", "");
        REGION_NAME = props.getProperty("tencent.cos.region", "ap-beijing");
        BUCKET_NAME = props.getProperty("tencent.cos.bucket-name", "");
        UPLOAD_DIR = props.getProperty("local.file.upload-dir", "files");
        BASE_URL = props.getProperty("local.file.base-url", "http://localhost:8000/files");
        STORAGE_MODE = props.getProperty("storage.mode", "auto");
        
        // 判断是否使用COS
        USE_COS = shouldUseCOS();
        
        if (USE_COS) {
            System.out.println("✅ 图片上传模式：腾讯云COS存储");
        } else {
            System.out.println("✅ 图片上传模式：本地文件存储");
            // 确保本地上传目录存在
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
                System.out.println("创建本地文件上传目录: " + uploadDir.getAbsolutePath());
            }
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        
        // 优先读取 application-local.properties
        try (FileInputStream input = new FileInputStream("src/main/resources/application-local.properties")) {
            props.load(input);
            System.out.println("✅ 读取配置文件：application-local.properties");
        } catch (IOException e1) {
            // 如果读取失败，尝试读取 application.properties
            try (FileInputStream input = new FileInputStream("src/main/resources/application.properties")) {
                props.load(input);
                System.out.println("✅ 读取配置文件：application.properties");
            } catch (IOException e2) {
                System.err.println("❌ 无法读取配置文件，使用默认配置");
            }
        }
        
        return props;
    }

    private static boolean shouldUseCOS() {
        if ("local".equals(STORAGE_MODE)) {
            return false;
        }
        
        if ("cos".equals(STORAGE_MODE)) {
            return true;
        }
        
        // auto模式：检查COS配置是否有效
        return SECRET_ID != null && SECRET_KEY != null && 
               !SECRET_ID.isEmpty() && !SECRET_KEY.isEmpty() &&
               !"YOUR_SECRET_ID_HERE".equals(SECRET_ID) && 
               !"YOUR_SECRET_KEY_HERE".equals(SECRET_KEY) &&
               BUCKET_NAME != null && !BUCKET_NAME.isEmpty();
    }

    /**
     * 上传文件（智能选择存储方式）
     * @param localFile 要上传的本地文件
     * @param key 文件的相对路径
     * @return 文件的访问URL，失败时返回null
     */
    public static String uploadFile(File localFile, String key) {
        if (USE_COS) {
            return uploadToCOS(localFile, key);
        } else {
            return uploadToLocal(localFile, key);
        }
    }

    /**
     * 上传文件到腾讯云COS
     */
    private static String uploadToCOS(File localFile, String key) {
        COSClient cosClient = null;
        try {
            COSCredentials cred = new BasicCOSCredentials(SECRET_ID, SECRET_KEY);
            Region region = new Region(REGION_NAME);
            ClientConfig clientConfig = new ClientConfig(region);
            cosClient = new COSClient(cred, clientConfig);

            PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, key, localFile);
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);

            String fileUrl = "https://" + BUCKET_NAME + ".cos." + REGION_NAME + ".myqcloud.com/" + key;
            System.out.println("✅ COS上传成功: " + localFile.getName() + " -> " + fileUrl);
            return fileUrl;
        } catch (Exception e) {
            System.err.println("❌ COS上传失败，尝试本地存储: " + e.getMessage());
            // COS上传失败时，回退到本地存储
            return uploadToLocal(localFile, key);
        } finally {
            if (cosClient != null) {
                cosClient.shutdown();
            }
        }
    }

    /**
     * 上传文件到本地存储
     */
    private static String uploadToLocal(File localFile, String key) {
        try {
            // 确保目标目录存在
            Path targetPath = Paths.get(UPLOAD_DIR, key);
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 复制文件到目标位置
            Files.copy(localFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // 返回文件的访问URL
            String fileUrl = BASE_URL + "/" + key.replace("\\", "/");
            System.out.println("✅ 本地存储成功: " + localFile.getName() + " -> " + fileUrl);
            
            return fileUrl;
        } catch (IOException e) {
            System.err.println("❌ 本地存储失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}