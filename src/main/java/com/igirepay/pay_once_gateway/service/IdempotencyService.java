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

/**
 * Service responsible for managing the idempotency lifecycle of payment requests.
 * It handles payload hashing, state tracking, and race condition protection.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Checks if a request with the given idempotency key has already been processed.
     * 
     * @param key The unique idempotency key provided by the client.
     * @param requestBody The payload of the request to verify for consistency.
     * @return Optional containing the cached IdempotencyRecord if found and valid, 
     *         or empty if this is a new request.
     * @throws PayloadMismatchException if the key exists but the payload hash differs.
     * @throws InProgressException if a concurrent request is already being processed.
     */
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
            
            // Check if in progress (Bonus: Block and Wait)
            if (record.getStatus() == IdempotencyRecord.RequestStatus.IN_PROGRESS) {
                return waitForCompletion(key);
            }
            
            return Optional.of(record);
        }
        
        return createNewRecord(key, hash);
    }

    private Optional<IdempotencyRecord> waitForCompletion(String key) {
        int maxRetries = 30; // 30 seconds max wait
        int retries = 0;
        
        while (retries < maxRetries) {
            try {
                Thread.sleep(1000); // Wait 1 second
                retries++;
                
                Optional<IdempotencyRecord> record = repository.findByIdempotencyKey(key);
                if (record.isPresent() && record.get().getStatus() == IdempotencyRecord.RequestStatus.COMPLETED) {
                    return record;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new InProgressException("Timed out waiting for concurrent request to finish for key: " + key);
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

    /**
     * Updates an existing idempotency record with the final response details.
     * 
     * @param key The idempotency key.
     * @param status The HTTP status code of the response.
     * @param responseBody The JSON response body to cache.
     */
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
