package com.example.multiapp.idempotency.entity;

import com.example.multiapp.idempotency.model.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Entity
@Table(name = "idempotency_record", schema = "app")
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {
    @EmbeddedId
    @EqualsAndHashCode.Include
    private IdempotencyId id;

    @ToString.Include
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="response_json", columnDefinition = "jsonb")
    private String responseJson;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdempotencyStatus status;

    public IdempotencyRecord(IdempotencyId id, String requestHash){
        this.id = Objects.requireNonNull(id);
        this.requestHash = Objects.requireNonNull(requestHash);
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    public void complete(String responseJson) {
        if(this.status == IdempotencyStatus.COMPLETED) return;
        this.responseJson = Objects.requireNonNull(responseJson);
        this.status = IdempotencyStatus.COMPLETED;
    }
}
