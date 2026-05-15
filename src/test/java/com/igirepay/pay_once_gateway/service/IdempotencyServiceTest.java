package com.igirepay.pay_once_gateway.service;

import com.igirepay.pay_once_gateway.dto.PaymentRequest;
import com.igirepay.pay_once_gateway.exception.PayloadMismatchException;
import com.igirepay.pay_once_gateway.model.IdempotencyRecord;
import com.igirepay.pay_once_gateway.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    @Mock
    private IdempotencyRepository repository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idempotencyService = new IdempotencyService(repository, objectMapper);
    }

    @Test
    void testCheckIdempotency_NewKey() {
        String key = "test-key";
        PaymentRequest request = new PaymentRequest("ACC1", new BigDecimal("100.00"), "GHS", "desc");
        
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        
        Optional<IdempotencyRecord> result = idempotencyService.checkIdempotency(key, request);
        
        assertTrue(result.isEmpty());
        verify(repository, times(1)).save(any(IdempotencyRecord.class));
    }

    @Test
    void testCheckIdempotency_PayloadMismatch() {
        String key = "test-key";
        PaymentRequest request2 = new PaymentRequest("ACC1", new BigDecimal("200.00"), "GHS", "desc");
        
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash("different-hash")
                .status(IdempotencyRecord.RequestStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));
        
        assertThrows(PayloadMismatchException.class, () -> {
            idempotencyService.checkIdempotency(key, request2);
        });
    }

    @Test
    void testCheckIdempotency_ExpiredKey() {
        String key = "test-key";
        PaymentRequest request = new PaymentRequest("ACC1", new BigDecimal("100.00"), "GHS", "desc");
        
        IdempotencyRecord expiredRecord = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(expiredRecord));
        
        Optional<IdempotencyRecord> result = idempotencyService.checkIdempotency(key, request);
        
        assertTrue(result.isEmpty());
        verify(repository, times(1)).delete(expiredRecord);
        verify(repository, times(1)).save(any(IdempotencyRecord.class));
    }
}
