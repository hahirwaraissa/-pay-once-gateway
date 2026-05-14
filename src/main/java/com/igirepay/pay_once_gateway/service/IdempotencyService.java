package com.igirepay.pay_once_gateway.service;

import com.igirepay.pay_once_gateway.exception.InProgressException;
import com.igirepay.pay_once_gateway.exception.PayloadMismatchException;
import com.igirepay.pay_once_gateway.model.IdempotencyRecord;
import com.igirepay.pay_once_gateway.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<IdempotencyRecord> checkIdempotency(String key, Object requestBody) {
        String hash = generateHash(requestBody);
        
        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKey(key);
        
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            
            // Check for expiration
            if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
                repository.delete(record);
                return createNewRecord(key, hash);
            }
            
            // Check for payload mismatch
            if (!record.getRequestHash().equals(hash)) {
                throw new PayloadMismatchException("Payload mismatch for idempotency key: " + key);
            }
            
            // Check if in progress
            if (record.getStatus() == IdempotencyRecord.RequestStatus.IN_PROGRESS) {
                throw new InProgressException("Request already in progress for key: " + key);
            }
            
            return Optional.of(record);
        }
        
        return createNewRecord(key, hash);
    }

    private Optional<IdempotencyRecord> createNewRecord(String key, String hash) {
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(hash)
                .status(IdempotencyRecord.RequestStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        
        repository.save(newRecord);
        return Optional.empty();
    }

    @Transactional
    public void updateRecord(String key, int status, String responseBody) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setResponseStatus(status);
            record.setResponseBody(responseBody);
            record.setStatus(IdempotencyRecord.RequestStatus.COMPLETED);
            repository.save(record);
        });
    }

    private String generateHash(Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating request hash", e);
        }
    }
}
