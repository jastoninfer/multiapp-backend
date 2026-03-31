package com.example.multiapp.common.aduit;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AuditWriterImpl implements AuditWriter{
    private final AuditLogRepository auditRepo;

    @Override
    @Transactional
    public void append(AuditLog auditLog) {
        Objects.requireNonNull(auditLog, "auditLog");
        auditRepo.save(auditLog);
    }
}
