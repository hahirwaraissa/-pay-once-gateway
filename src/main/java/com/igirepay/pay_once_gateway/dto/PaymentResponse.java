package com.igirepay.pay_once_gateway.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String transactionId;
    private String status;
    private String message;
    private LocalDateTime timestamp;
}
