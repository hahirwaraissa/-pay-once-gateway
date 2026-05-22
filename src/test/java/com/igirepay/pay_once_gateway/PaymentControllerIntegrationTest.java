package com.igirepay.pay_once_gateway;

import com.igirepay.pay_once_gateway.dto.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
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
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Charged 100 GHS"));
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
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Charged 100 GHS"));
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
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value("Idempotency key already used for a different request body."));
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

    @Test
    void shouldBlockAndResolveForConcurrentRequests() throws Exception {
        String key = UUID.randomUUID().toString();
        PaymentRequest request = PaymentRequest.builder()
                .targetAccount("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("RWF")
                .description("Concurrent Test")
                .build();

        AtomicReference<MvcResult> resultARef = new AtomicReference<>();
        AtomicReference<Exception> exceptionARef = new AtomicReference<>();
        
        Thread threadA = new Thread(() -> {
            try {
                MvcResult result = mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", key)
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                resultARef.set(result);
            } catch (Exception e) {
                exceptionARef.set(e);
            }
        });

        threadA.start();

        // Wait a short time to ensure Request A has started and created the record (IN_PROGRESS)
        Thread.sleep(300);

        // Perform Request B. Since Request A is still sleeping (2 seconds), Request B will find the record
        // in IN_PROGRESS status and block until Request A completes, then return its cached response.
        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "true"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Charged 100 RWF"));

        threadA.join();

        assertNull(exceptionARef.get(), "Request A should not have thrown an exception");
        assertNotNull(resultARef.get(), "Request A should have returned a result");
        
        assertEquals(200, resultARef.get().getResponse().getStatus());
        assertEquals("false", resultARef.get().getResponse().getHeader("X-Cache-Hit"));
        assertTrue(resultARef.get().getResponse().getContentAsString().contains("Charged 100 RWF"));
    }

    @Test
    void shouldAllowRetryIfFirstAttemptFailed() throws Exception {
        String key = UUID.randomUUID().toString();
        PaymentRequest request = PaymentRequest.builder()
                .targetAccount("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("ERR") // Throws runtime exception in PaymentService
                .description("Failed Attempt")
                .build();

        // First attempt - should fail and return 500
        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        // Now, retry with a valid currency. It should succeed since the key was released on failure!
        PaymentRequest retryRequest = PaymentRequest.builder()
                .targetAccount("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("RWF")
                .description("Retry Attempt")
                .build();

        mockMvc.perform(post("/process-payment")
                .header("Idempotency-Key", key)
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(retryRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Charged 100 RWF"));
    }
}
