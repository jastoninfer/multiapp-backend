package com.example.multiapp.attachment.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// LOCAL存储实现: app.upload.dir 配置一个根目录
@Component
public class LocalAttachmentStorage implements AttachmentStorage {
    private final Path root;
    public LocalAttachmentStorage(@Value("${app.upload.dir}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public String save(UUID tenantId, UUID ticketId, UUID attachmentId, MultipartFile file) throws IOException {
        // storageKey只用系统生成, 避免目录穿越; 文件名只做展示用
        String key = tenantId + "/tickets/" + ticketId + "/attachments/" + attachmentId;
        Path target = root.resolve(key).normalize();
        if(!target.startsWith(root)) throw new SecurityException("Invalid Path");
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return key;
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        Path p = resolvePath(storageKey);
        System.out.println(storageKey);
        System.out.println(p.toAbsolutePath());
        System.out.println(">>>><<<<");
        return new FileSystemResource(p);
    }

    @Override
    public Path resolvePath(String storageKey) {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) throw new SecurityException("Invalid Path");
        return p;
    }
}