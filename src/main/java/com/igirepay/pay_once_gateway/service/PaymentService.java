package com.igirepay.pay_once_gateway.service;

import com.igirepay.pay_once_gateway.dto.PaymentRequest;
import com.igirepay.pay_once_gateway.dto.PaymentResponse;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    public PaymentResponse process(PaymentRequest request) {
        // Simulate external payment processing delay
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return PaymentResponse.builder()
                .transactionId(UUID.randomUUID().toString())
                .status("SUCCESS")
                .message("Payment of " + request.getAmount() + " " + request.getCurrency() + " processed successfully.")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
