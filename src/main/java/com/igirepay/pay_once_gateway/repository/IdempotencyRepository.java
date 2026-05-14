package com.igirepay.pay_once_gateway.repository;

import com.igirepay.pay_once_gateway.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
