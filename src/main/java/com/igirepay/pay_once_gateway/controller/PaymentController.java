package com.igirepay.pay_once_gateway.controller;

import com.igirepay.pay_once_gateway.dto.PaymentRequest;
import com.igirepay.pay_once_gateway.dto.PaymentResponse;
import com.igirepay.pay_once_gateway.model.IdempotencyRecord;
import com.igirepay.pay_once_gateway.service.IdempotencyService;
import com.igirepay.pay_once_gateway.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/process-payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) throws Exception {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // If no key, process without idempotency (or require it)
            // The assessment says "Gateway which implements an idempotency layer", so we should probably require it.
            return ResponseEntity.badRequest().build();
        }

        Optional<IdempotencyRecord> cachedRecord = idempotencyService.checkIdempotency(idempotencyKey, request);

        if (cachedRecord.isPresent()) {
            PaymentResponse cachedResponse = objectMapper.readValue(
                    cachedRecord.get().getResponseBody(), 
                    PaymentResponse.class
            );
            return ResponseEntity.ok()
                    .header("X-Cache-Hit", "true")
                    .body(cachedResponse);
        }

        try {
            PaymentResponse response = paymentService.process(request);
            String responseJson = objectMapper.writeValueAsString(response);
            
            idempotencyService.updateRecord(idempotencyKey, 200, responseJson);

            return ResponseEntity.ok()
                    .header("X-Cache-Hit", "false")
                    .body(response);
        } catch (Exception e) {
            // Update as FAILED if needed, but for simplicity we'll just throw
            throw e;
        }
    }
}
