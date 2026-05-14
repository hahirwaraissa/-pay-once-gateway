package com.igirepay.pay_once_gateway.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public enum RequestStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
