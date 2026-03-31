package com.example.multiapp.common.aduit;

import com.example.multiapp.common.event.DomainEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.web.OffsetScrollPositionArgumentResolver;

import java.util.Objects;
import java.util.UUID;

public final class AuditPayloadBuilder {
    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private final ObjectNode root;
    private final ArrayNode fields;

    private AuditPayloadBuilder(String entityId, String eventType) {
        this.root = NF.objectNode().put("entityId", entityId)
                .put("eventType", eventType);
        this.fields = root.putArray("fields");
    }

    public static AuditPayloadBuilder forEntity(UUID entityId, DomainEventType eventType) {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(eventType, "eventType");
        return new AuditPayloadBuilder(entityId.toString(), eventType.key());
    }

    public AuditPayloadBuilder addField(String field, Object from, Object to) {
        Objects.requireNonNull(field, "field");
        ObjectNode f = fields.addObject();
        f.put("field", field);
        putNullableText(f, "from", Objects.isNull(from) ? null : from.toString());
        putNullableText(f, "to", Objects.isNull(to) ? null : to.toString());
        return this;
    }

    private AuditPayloadBuilder addField(String field, String from, String to) {
        Objects.requireNonNull(field, "field");
        ObjectNode f = fields.addObject();
        f.put("field", field);
        putNullableText(f, "from", from);
        putNullableText(f, "to", to);
        return this;
    }

    public JsonNode build() {
        return root;
    }

    private static void putNullableText(ObjectNode obj, String name, String value) {
        if (value == null) obj.set(name, NF.nullNode());
        else obj.put(name, value);
    }
}
