package com.example.multiapp.attachment.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public interface AttachmentStorage {
    /* 返回storageKey (相对路径/对象key) */
    String save(UUID tenantId, UUID ticketId, UUID attachmentId, MultipartFile file)
        throws IOException;

    Resource loadAsResource(String storageKey);

    Path resolvePath(String storageKey); // 下载时用于推导文件名/存在性
}
