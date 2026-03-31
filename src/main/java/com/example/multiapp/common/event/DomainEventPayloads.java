package com.example.multiapp.common.event;

import com.example.multiapp.common.tenant.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainEventPayloads {
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    public static ObjectNode envelopFrom(RequestContext ctx, UUID entityId, JsonNode data) {
        Objects.requireNonNull(ctx, "ctx");
        return envelop(entityId, ctx.userId(), ctx.requestId(), 1, data);
    }

    public static ObjectNode envelop(
//        DomainEventType type,
//        UUID tenantId,
        UUID entityId,
        UUID actorUserId, // nullable
        String requestId, // nullable
        int schemaVersion,
        JsonNode data
    ) {
//        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityId, "entityId");
        var n = F.objectNode();
//        n.put("type", type.key());
        n.put("schemaVersion", schemaVersion);
        n.put("occurredAt", OffsetDateTime.now().toString());
        n.put("entityId", entityId.toString());
//        n.put("tenantId", tenantId.toString());
        if (actorUserId != null) n.put("actorUserId", actorUserId.toString());
        if(requestId != null && !requestId.isBlank()) {
            n.put("requestId", requestId);
        }
        if(data != null) n.set("data", data);
        return n;
    }
}
