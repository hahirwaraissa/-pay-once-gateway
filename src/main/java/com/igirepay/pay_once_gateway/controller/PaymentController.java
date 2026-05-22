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

/**
 * REST Controller for processing payment requests with idempotency support.
 */
@RestController
@RequestMapping("/process-payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * Processes a payment request. If an Idempotency-Key is provided, it ensures 
     * exactly-once processing by caching and returning the original result for retries.
     * 
     * @param idempotencyKey The unique key for this request.
     * @param request The payment details.
     * @return ResponseEntity containing the payment response.
     * @throws Exception if any processing error occurs.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) throws Exception {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Require idempotency key for this gateway
            return ResponseEntity.badRequest().build();
        }

        Optional<IdempotencyRecord> cachedRecord = idempotencyService.checkIdempotency(idempotencyKey, request);

        if (cachedRecord.isPresent()) {
            PaymentResponse cachedResponse = objectMapper.readValue(
                    cachedRecord.get().getResponseBody(), 
                    PaymentResponse.class
            );
            return ResponseEntity.status(cachedRecord.get().getResponseStatus())
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
            idempotencyService.deleteRecord(idempotencyKey);
            throw e;
        }
    }
}
