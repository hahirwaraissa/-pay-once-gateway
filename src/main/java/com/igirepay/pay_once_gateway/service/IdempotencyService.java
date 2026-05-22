package com.igirepay.pay_once_gateway.service;

import com.igirepay.pay_once_gateway.exception.InProgressException;
import com.igirepay.pay_once_gateway.exception.PayloadMismatchException;
import com.igirepay.pay_once_gateway.model.IdempotencyRecord;
import com.igirepay.pay_once_gateway.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    @Lazy
    private IdempotencyService self;

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
                return tryCreateNewRecord(key, hash);
            }
            
            // Check for payload mismatch
            if (!record.getRequestHash().equals(hash)) {
                throw new PayloadMismatchException("Idempotency key already used for a different request body.");
            }
            
            // Check if in progress (Bonus: Block and Wait)
            if (record.getStatus() == IdempotencyRecord.RequestStatus.IN_PROGRESS) {
                return handleInProgressWait(key, hash);
            }
            
            return Optional.of(record);
        }
        
        return tryCreateNewRecord(key, hash);
    }

    private Optional<IdempotencyRecord> tryCreateNewRecord(String key, String hash) {
        try {
            if (self != null) {
                self.createNewInProgressRecord(key, hash);
            } else {
                createNewInProgressRecord(key, hash);
            }
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            // Concurrent request created it at the same time
            // Clear L1 cache to fetch the freshly inserted record
            entityManager.clear();
            Optional<IdempotencyRecord> record = repository.findByIdempotencyKey(key);
            if (record.isPresent()) {
                if (record.get().getStatus() == IdempotencyRecord.RequestStatus.IN_PROGRESS) {
                    return handleInProgressWait(key, hash);
                }
                // If it is completed, validate hash
                if (!record.get().getRequestHash().equals(hash)) {
                    throw new PayloadMismatchException("Idempotency key already used for a different request body.");
                }
                return record;
            }
            throw e;
        }
    }

    private Optional<IdempotencyRecord> handleInProgressWait(String key, String hash) {
        Optional<IdempotencyRecord> completedRecord = waitForCompletion(key);
        if (completedRecord.isEmpty()) {
            // Request A must have failed and the record was deleted.
            // Try creating a new record for this request.
            return tryCreateNewRecord(key, hash);
        }
        return completedRecord;
    }

    /**
     * Creates a new IN_PROGRESS record in a separate transaction.
     * This ensures that concurrent requests immediately see the IN_PROGRESS state,
     * and that duplicate key constraint failures can be caught in the caller without
     * ruining the caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNewInProgressRecord(String key, String hash) {
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(hash)
                .status(IdempotencyRecord.RequestStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        repository.saveAndFlush(newRecord);
    }

    private Optional<IdempotencyRecord> waitForCompletion(String key) {
        int maxRetries = 30; // 30 seconds max wait
        int retries = 0;
        
        while (retries < maxRetries) {
            try {
                Thread.sleep(1000); // Wait 1 second
                retries++;
                
                // Clear the Hibernate L1 cache to force checking the database state
                entityManager.clear();
                
                Optional<IdempotencyRecord> record = repository.findByIdempotencyKey(key);
                if (record.isEmpty()) {
                    // Record was deleted (e.g., Request A failed and cleaned up)
                    return Optional.empty();
                }
                
                IdempotencyRecord rec = record.get();
                if (rec.getStatus() == IdempotencyRecord.RequestStatus.COMPLETED) {
                    return Optional.of(rec);
                } else if (rec.getStatus() == IdempotencyRecord.RequestStatus.FAILED) {
                    // Treat as failed / deleted
                    return Optional.empty();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new InProgressException("Timed out waiting for concurrent request to finish for key: " + key);
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

    /**
     * Deletes an existing idempotency record. Use this to clean up failed attempts.
     * 
     * @param key The idempotency key.
     */
    @Transactional
    public void deleteRecord(String key) {
        repository.findByIdempotencyKey(key).ifPresent(repository::delete);
    }

    /**
     * Scheduled job to automatically delete expired keys.
     * Runs every 10 minutes to maintain database size and performance.
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    @Transactional
    public void cleanExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = repository.deleteByExpiresAtBefore(now);
        // Print/log the cleanup results if needed (in production, use a logger)
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
