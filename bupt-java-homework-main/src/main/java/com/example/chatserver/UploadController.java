package com.example.chatserver;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin // 允许跨域
public class UploadController {

    @PostMapping(value = "/upload_image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        File temp = File.createTempFile("upload_", "_" + fileName);
        file.transferTo(temp);
        String url = COSFileUploader.uploadFile(temp, "chat_images/" + temp.getName());
        long size = temp.length();
        temp.delete();
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("fileName", fileName);
        result.put("fileSize", size);
        return result;
    }

    @PostMapping(value = "/upload_file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        File temp = File.createTempFile("upload_", "_" + fileName);
        file.transferTo(temp);
        String url = COSFileUploader.uploadFile(temp, "chat_files/" + temp.getName());
        long size = temp.length();
        temp.delete();
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("fileName", fileName);
        result.put("fileSize", size);
        return result;
    }
} 