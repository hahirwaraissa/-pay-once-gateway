package com.igirepay.pay_once_gateway;

import com.igirepay.pay_once_gateway.dto.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String API_KEY = "she-can-code-2024";

    @Test
    void shouldProcessNewPayment() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .targetAccount("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("GHS")
                .description("Test Payment")
                .build();

        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void shouldReturnCachedResponseForDuplicateKey() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .targetAccount("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("GHS")
                .build();

        String key = UUID.randomUUID().toString();

        // First request
        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Duplicate request
        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "true"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void shouldReturn422ForPayloadMismatch() throws Exception {
        String key = UUID.randomUUID().toString();

        PaymentRequest request1 = PaymentRequest.builder()
                .targetAccount("ACC1")
                .amount(new BigDecimal("10.00"))
                .currency("GHS")
                .build();

        PaymentRequest request2 = PaymentRequest.builder()
                .targetAccount("ACC2")
                .amount(new BigDecimal("20.00"))
                .currency("GHS")
                .build();

        // First request
        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // Second request with same key but different body
        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    void shouldReturn401ForMissingApiKey() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .targetAccount("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", "some-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
