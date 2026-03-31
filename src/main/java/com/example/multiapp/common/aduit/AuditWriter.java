package com.example.multiapp.common.aduit;

import com.example.multiapp.audit.entity.AuditLog;

// 写入横切组件, 供其他模块调用
public interface AuditWriter {
    void append(AuditLog auditLog);
}
