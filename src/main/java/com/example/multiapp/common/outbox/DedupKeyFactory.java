package com.example.multiapp.common.outbox;

import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;

import java.util.Objects;
import java.util.UUID;

public final class DedupKeyFactory {
    private DedupKeyFactory() {}

    // 创建: idemKey + eventType
    public static String forCreate(String idemKey, DomainEventType eventType) {
        return join(requireNonBlank(idemKey, "idemKey"),
                requireNonBlank(eventType.key(), "eventType"));
    }

    // 更新 (有version) : entityId + eventType + newVersion
    // 这个方法比较有风险, 暂时不考虑, 跨request去重需要前端换一个跨request不变的requestId
//    public static String forIfMatchedUpdate(UUID entityId, DomainEventType eventType,
//                                            String ifMatch) {
//        Objects.requireNonNull(entityId, "entityId");
////        if(newVersion < 0) throw new IllegalArgumentException("newVersion must be >= 0");
//        return join(entityId.toString(), requireNonBlank(eventType.key(), "eventType"),
//                ifMatch);
//    }

    // 更新 (无version): requestId + eventType
    public static String forRequestScopedUpdate(String requestId, DomainEventType eventType) {
        return join(requireNonBlank(requestId, "requestId"),
                requireNonBlank(eventType.key(), "eventType"));
    }

    // 暂未使用, 如果你觉得无version的更新也要求idemKey, 就用idemKey替换requestId
    public static String forIdempotentUpdate(String idemKey, DomainEventType eventType) {
        return forCreate(idemKey, eventType);
    }

    private static String join(String... parts) {
        return String.join(":", parts);
    }

    private static String requireNonBlank(String s, String name) {
        Objects.requireNonNull(s, name);
        String v = s.trim();
        if(v.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return v;
    }
}
