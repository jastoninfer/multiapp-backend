package com.example.multiapp.audit.service;

import com.example.multiapp.audit.dto.AuditLogQuery;
import com.example.multiapp.audit.dto.AuditLogResponse;
import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.repo.AuditLogRepository;
import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepo;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(RequestContext ctx, AuditLogQuery query, Pageable pageable) {
        Objects.requireNonNull(ctx, "ctx");
        if (ctx.role() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Access denied: audit logs require tenant admin access");
        }

        Pageable p = PageNormalizer.normalize(
                pageable,
                100,
                20,
                Sort.by(Sort.Order.desc("createdAt")),
                Set.of("createdAt", "entityType", "action")
        );
        return auditLogRepo.findAll(specification(ctx, query), p).map(AuditLogResponse::from);
    }

    private Specification<AuditLog> specification(RequestContext ctx, AuditLogQuery query) {
        return (root, cq, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("id").get("tenantId"), ctx.tenantId()));

            if (query != null) {
                if (query.entityType() != null) {
                    predicates.add(cb.equal(root.get("entityType"), query.entityType()));
                }
                if (query.entityId() != null) {
                    predicates.add(cb.equal(root.get("entityId"), query.entityId()));
                }
                String action = normalizeExact(query.action());
                if (action != null) {
                    predicates.add(cb.equal(root.get("action"), action));
                }
                String requestId = normalizeText(query.requestId());
                if (requestId != null) {
                    predicates.add(cb.equal(root.get("requestId"), requestId));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeExact(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
