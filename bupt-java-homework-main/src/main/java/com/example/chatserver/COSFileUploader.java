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
import java.util.Properties;

public class COSFileUploader {
    private static final String SECRET_ID;
    private static final String SECRET_KEY;
    private static final String REGION_NAME;
    private static final String BUCKET_NAME;

    static {
        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream("src/main/resources/application.properties")) {
            props.load(input);
            SECRET_ID = props.getProperty("tencent.cos.secret-id");
            SECRET_KEY = props.getProperty("tencent.cos.secret-key");
            REGION_NAME = props.getProperty("tencent.cos.region");
            BUCKET_NAME = props.getProperty("tencent.cos.bucket-name");
            
            if (SECRET_ID == null || SECRET_KEY == null || 
                "YOUR_SECRET_ID_HERE".equals(SECRET_ID) || "YOUR_SECRET_KEY_HERE".equals(SECRET_KEY)) {
                throw new RuntimeException("Tencent Cloud credentials not configured properly. Please set real values in application.properties");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public static String uploadFile(File localFile, String key) {
        COSClient cosClient = null;
        try {
            COSCredentials cred = new BasicCOSCredentials(SECRET_ID, SECRET_KEY);
            Region region = new Region(REGION_NAME);
            ClientConfig clientConfig = new ClientConfig(region);
            cosClient = new COSClient(cred, clientConfig);

            PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, key, localFile);
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);

            return "https://" + BUCKET_NAME + ".cos." + REGION_NAME + ".myqcloud.com/" + key;
        } catch (Exception e) {
            System.err.println("上传文件到COS失败: " + e.getMessage());
            return null;
        } finally {
            if (cosClient != null) {
                cosClient.shutdown();
            }
        }
    }
}